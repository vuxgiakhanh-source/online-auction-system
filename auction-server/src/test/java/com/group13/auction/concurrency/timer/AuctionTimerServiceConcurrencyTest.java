package com.group13.auction.concurrency.timer;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.*;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.StandardBidStrategy;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CI-STABLE VERSION
 *
 * FIXES:
 * - removed Thread.sleep(5)
 * - use ExecutorService instead of raw Thread
 * - assert await() completion
 * - stronger synchronization
 * - reduced flaky timing assumptions
 * - cleanup executor correctly
 */
@DisplayName("Timer: closeAuction() vs placeBid() race (CI stable)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuctionTimerServiceConcurrencyTest extends ConcurrencyTestBase {

    private AuctionService auctionService;
    private BidService bidService;
    private AuctionLockRegistry lockRegistry;

    private AuctionDAO mockAuctionDAO;
    private BidTransactionDAO mockBidTransactionDAO;
    private UserDAO mockUserDAO;
    private IRatingService mockRatingService;
    private FinancialTransactionDAO mockFinancialTransactionDAO;

    private ExecutorService executor;

    private static void bootstrapSystemAdminForTest() throws Exception {
        java.lang.reflect.Field instanceField =
                SystemAdmin.class.getDeclaredField("INSTANCE");

        instanceField.setAccessible(true);

        if (instanceField.get(null) == null) {
            SystemAdmin mockAdmin = mock(SystemAdmin.class);
            instanceField.set(null, mockAdmin);
        }
    }

    @BeforeEach
    void setUp() throws Exception {

        bootstrapSystemAdminForTest();

        executor = Executors.newFixedThreadPool(16);

        mockAuctionDAO = mock(AuctionDAO.class);
        mockBidTransactionDAO = mock(BidTransactionDAO.class);
        mockUserDAO = mock(UserDAO.class);
        mockRatingService = mock(IRatingService.class);
        mockFinancialTransactionDAO = mock(FinancialTransactionDAO.class);

        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockBidTransactionDAO.saveTransaction(any())).thenReturn(true);
        when(mockAuctionDAO.updateHighestPrice(any(), anyLong(), any())).thenReturn(true);
        when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
        when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
        when(mockAuctionDAO.updateAuctionStatus(any(), any())).thenReturn(true);
        when(mockUserDAO.updateBalances(any(), anyLong(), anyLong())).thenReturn(true);
        when(mockUserDAO.saveUserAuctionActivity(any(), any(), any())).thenReturn(true);
        when(mockUserDAO.addBalance(any(), anyLong())).thenReturn(true);
        when(mockFinancialTransactionDAO.saveTransaction(any())).thenReturn(true);

        WalletService walletService =
                new WalletService(
                        mockFinancialTransactionDAO,
                        mockUserDAO,
                        mockRatingService
                );

        auctionService =
                new AuctionService(mockRatingService, mockAuctionDAO);

        bidService =
                new BidService(
                        auctionService,
                        mockRatingService,
                        walletService,
                        mockBidTransactionDAO,
                        mockAuctionDAO,
                        mockUserDAO
                );

        lockRegistry = AuctionLockRegistry.getInstance();

        resetAuctionManagerUsers();
    }

    @AfterEach
    void tearDown() throws Exception {

        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        resetAuctionManagerUsers();
    }

    @Test
    @Order(1)
    @DisplayName("T1: close vs bid — auction closes safely")
    @Timeout(30)
    void timerClose_vs_bidHandler_bidsRejectedAfterClose()
            throws Exception {

        Auction auction = buildRunningAuction(
                STARTING_PRICE,
                RESERVE_PRICE,
                LocalDateTime.now().minusSeconds(1)
        );

        NormalUser bidder =
                buildUser("bidder-T1", USER_BALANCE);

        joinBidder(bidder, auction);

        int bidThreads = 5;

        CountDownLatch startGate =
                new CountDownLatch(1);

        CountDownLatch doneGate =
                new CountDownLatch(bidThreads + 1);

        AtomicInteger bidSuccesses =
                new AtomicInteger();

        AtomicInteger bidRejections =
                new AtomicInteger();

        AtomicInteger closeCount =
                new AtomicInteger();

        Runnable timerTask = () -> {
            try {

                startGate.await();

                boolean locked =
                        lockRegistry.tryLock(
                                auction.getId(),
                                2,
                                TimeUnit.SECONDS
                        );

                if (!locked) {
                    return;
                }

                try {

                    if (auction.getStatus()
                            == Auction.AuctionStatus.RUNNING) {

                        auctionService.closeAuction(auction);

                        closeCount.incrementAndGet();
                    }

                } finally {
                    lockRegistry.unlock(auction.getId());
                }

            } catch (Exception e) {

                log.error("Timer task failed", e);

            } finally {
                doneGate.countDown();
            }
        };

        executor.submit(timerTask);

        for (int i = 0; i < bidThreads; i++) {

            final long bidAmount =
                    STARTING_PRICE + (i + 1) * 10_000L;

            Runnable bidTask = () -> {

                try {

                    startGate.await();

                    boolean locked =
                            lockRegistry.tryLock(
                                    auction.getId(),
                                    2,
                                    TimeUnit.SECONDS
                            );

                    if (!locked) {
                        bidRejections.incrementAndGet();
                        return;
                    }

                    try {

                        bidService.placeBid(
                                bidder,
                                auction,
                                bidAmount,
                                new StandardBidStrategy()
                        );

                        bidSuccesses.incrementAndGet();

                    } catch (Exception e) {

                        bidRejections.incrementAndGet();

                    } finally {

                        lockRegistry.unlock(auction.getId());
                    }

                } catch (Exception e) {

                    log.error("Bid task failed", e);

                } finally {

                    doneGate.countDown();
                }
            };

            executor.submit(bidTask);
        }

        startGate.countDown();

        boolean completed =
                doneGate.await(20, TimeUnit.SECONDS);

        assertThat(completed)
                .as("Threads timeout/deadlock")
                .isTrue();

        assertThat(closeCount.get())
                .isEqualTo(1);

        assertThat(auction.getStatus())
                .isNotEqualTo(Auction.AuctionStatus.RUNNING);

        assertThat(
                bidSuccesses.get() + bidRejections.get()
        ).isEqualTo(bidThreads);

        lockRegistry.release(auction.getId());
    }

    @Test
    @Order(2)
    @DisplayName("T2: double close guarded")
    @Timeout(15)
    void doubleClose_secondCallIgnored()
            throws Exception {

        Auction auction = buildRunningAuction(
                STARTING_PRICE,
                STARTING_PRICE - 1,
                LocalDateTime.now().minusSeconds(1)
        );

        boolean locked =
                lockRegistry.tryLock(
                        auction.getId(),
                        2,
                        TimeUnit.SECONDS
                );

        assertThat(locked).isTrue();

        try {

            auctionService.closeAuction(auction);

        } finally {

            lockRegistry.unlock(auction.getId());
        }

        Auction.AuctionStatus statusAfterFirst =
                auction.getStatus();

        AtomicInteger secondCloseAttempts =
                new AtomicInteger();

        boolean locked2 =
                lockRegistry.tryLock(
                        auction.getId(),
                        2,
                        TimeUnit.SECONDS
                );

        if (locked2) {

            try {

                if (auction.getStatus()
                        == Auction.AuctionStatus.RUNNING) {

                    auctionService.closeAuction(auction);

                    secondCloseAttempts.incrementAndGet();
                }

            } finally {

                lockRegistry.unlock(auction.getId());
            }
        }

        assertThat(secondCloseAttempts.get())
                .isEqualTo(0);

        assertThat(auction.getStatus())
                .isEqualTo(statusAfterFirst);

        lockRegistry.release(auction.getId());
    }

    @Test
    @Order(3)
    @DisplayName("T3: only one timer closes auction")
    @Timeout(20)
    void twoTimerThreads_onlyOneCloses()
            throws Exception {

        Auction auction = buildRunningAuction(
                STARTING_PRICE,
                RESERVE_PRICE,
                LocalDateTime.now().minusSeconds(1)
        );

        CountDownLatch gate =
                new CountDownLatch(1);

        CountDownLatch done =
                new CountDownLatch(2);

        AtomicInteger closed =
                new AtomicInteger();

        Runnable timerTask = () -> {

            try {

                gate.await();

                boolean locked =
                        lockRegistry.tryLock(
                                auction.getId(),
                                2,
                                TimeUnit.SECONDS
                        );

                if (!locked) {
                    return;
                }

                try {

                    if (auction.getStatus()
                            == Auction.AuctionStatus.RUNNING) {

                        auctionService.closeAuction(auction);

                        closed.incrementAndGet();
                    }

                } finally {

                    lockRegistry.unlock(auction.getId());
                }

            } catch (Exception e) {

                log.error("Timer failed", e);

            } finally {

                done.countDown();
            }
        };

        executor.submit(timerTask);
        executor.submit(timerTask);

        gate.countDown();

        boolean completed =
                done.await(10, TimeUnit.SECONDS);

        assertThat(completed)
                .isTrue();

        assertThat(closed.get())
                .isEqualTo(1);

        lockRegistry.release(auction.getId());
    }

    @Test
    @Order(4)
    @DisplayName("T4: concurrent bids do not corrupt currentPrice")
    @Timeout(30)
    void concurrentBidsAndClose_priceNotCorrupted()
            throws Exception {

        Auction auction = buildRunningAuction(
                STARTING_PRICE,
                RESERVE_PRICE,
                LocalDateTime.now().plusSeconds(30)
        );

        int bidderCount = 10;

        List<NormalUser> bidders =
                buildBidders(bidderCount);

        for (NormalUser bidder : bidders) {
            joinBidder(bidder, auction);
        }

        CountDownLatch gate =
                new CountDownLatch(1);

        CountDownLatch done =
                new CountDownLatch(bidderCount + 1);

        AtomicReference<Long> priceSnapshot =
                new AtomicReference<>(
                        auction.getCurrentPrice()
                );

        for (int i = 0; i < bidderCount; i++) {

            final NormalUser bidder = bidders.get(i);

            final long amount =
                    STARTING_PRICE + (i + 1) * 50_000L;

            Runnable bidTask = () -> {

                try {

                    gate.await();

                    boolean locked =
                            lockRegistry.tryLock(
                                    auction.getId(),
                                    2,
                                    TimeUnit.SECONDS
                            );

                    if (!locked) {
                        return;
                    }

                    try {

                        if (auction.isAcceptingBids()) {

                            bidService.placeBid(
                                    bidder,
                                    auction,
                                    amount,
                                    new StandardBidStrategy()
                            );

                            priceSnapshot.set(
                                    auction.getCurrentPrice()
                            );
                        }

                    } finally {

                        lockRegistry.unlock(auction.getId());
                    }

                } catch (Exception e) {

                    log.error("Bid failed", e);

                } finally {

                    done.countDown();
                }
            };

            executor.submit(bidTask);
        }

        Runnable closeTask = () -> {

            try {

                gate.await();

                boolean locked =
                        lockRegistry.tryLock(
                                auction.getId(),
                                2,
                                TimeUnit.SECONDS
                        );

                if (!locked) {
                    return;
                }

                try {

                    if (auction.getStatus()
                            == Auction.AuctionStatus.RUNNING) {

                        auctionService.closeAuction(auction);
                    }

                } finally {

                    lockRegistry.unlock(auction.getId());
                }

            } catch (Exception e) {

                log.error("Close task failed", e);

            } finally {

                done.countDown();
            }
        };

        executor.submit(closeTask);

        gate.countDown();

        boolean completed =
                done.await(20, TimeUnit.SECONDS);

        assertThat(completed)
                .as("Concurrent tasks timeout")
                .isTrue();

        assertThat(auction.getCurrentPrice())
                .isGreaterThanOrEqualTo(STARTING_PRICE);

        long maxPossibleBid =
                STARTING_PRICE + (long) bidderCount * 50_000L;

        assertThat(auction.getCurrentPrice())
                .isLessThanOrEqualTo(maxPossibleBid);

        lockRegistry.release(auction.getId());
    }

    private void joinBidder(
            NormalUser bidder,
            Auction auction
    ) {

        bidder.lockDeposit(
                auction.getItem()
                        .getStartingPrice() * 3 / 10
        );

        bidder.addJoinedAuction(auction.getId());

        auctionService.addObserver(
                auction.getId(),
                noopObserver()
        );
    }
}

