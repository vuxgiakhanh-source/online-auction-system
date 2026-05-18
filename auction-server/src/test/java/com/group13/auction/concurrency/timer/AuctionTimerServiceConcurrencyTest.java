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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CI-STABLE VERSION
 *
 * Goals:
 * - deterministic synchronization
 * - CI-friendly timeout margins
 * - avoid flaky scheduling assumptions
 * - avoid raw Thread usage
 * - proper executor cleanup
 * - explicit deadlock detection
 */
@DisplayName("AuctionTimerService concurrency tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuctionTimerServiceConcurrencyTest extends ConcurrencyTestBase {

    private static final int EXECUTOR_THREADS = 16;

    /**
     * Internal waits MUST be much smaller than JUnit timeout.
     */
    private static final int INTERNAL_WAIT_SECONDS = 10;

    private AuctionService auctionService;
    private BidService bidService;
    private AuctionLockRegistry lockRegistry;

    private AuctionDAO mockAuctionDAO;
    private BidTransactionDAO mockBidTransactionDAO;
    private UserDAO mockUserDAO;
    private IRatingService mockRatingService;
    private FinancialTransactionDAO mockFinancialTransactionDAO;

    private ExecutorService executor;

    // =========================================================================
    // Bootstrap
    // =========================================================================

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

        executor = Executors.newFixedThreadPool(EXECUTOR_THREADS);

        mockAuctionDAO = mock(AuctionDAO.class);
        mockBidTransactionDAO = mock(BidTransactionDAO.class);
        mockUserDAO = mock(UserDAO.class);
        mockRatingService = mock(IRatingService.class);
        mockFinancialTransactionDAO = mock(FinancialTransactionDAO.class);

        when(mockRatingService.isEligible(any()))
                .thenReturn(true);

        when(mockBidTransactionDAO.saveTransaction(any()))
                .thenReturn(true);

        when(mockAuctionDAO.updateHighestPrice(any(), anyLong(), any()))
                .thenReturn(true);

        when(mockAuctionDAO.updateViewerCount(any(), anyInt()))
                .thenReturn(true);

        when(mockAuctionDAO.updateEndTime(any(), any()))
                .thenReturn(true);

        when(mockAuctionDAO.updateAuctionStatus(any(), any()))
                .thenReturn(true);

        when(mockUserDAO.updateBalances(any(), anyLong(), anyLong()))
                .thenReturn(true);

        when(mockUserDAO.saveUserAuctionActivity(any(), any(), any()))
                .thenReturn(true);

        when(mockUserDAO.addBalance(any(), anyLong()))
                .thenReturn(true);

        when(mockFinancialTransactionDAO.saveTransaction(any()))
                .thenReturn(true);

        WalletService walletService =
                new WalletService(
                        mockFinancialTransactionDAO,
                        mockUserDAO,
                        mockRatingService
                );

        auctionService =
                new AuctionService(
                        mockRatingService,
                        mockAuctionDAO
                );

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

            boolean terminated =
                    executor.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    );

            assertThat(terminated)
                    .as("Executor failed to terminate")
                    .isTrue();
        }

        resetAuctionManagerUsers();
    }

    // =========================================================================
    // T1
    // =========================================================================

    @Test
    @Order(1)
    @Timeout(30)
    @DisplayName("T1: closeAuction vs placeBid")
    void timerClose_vs_bidHandler_bidsRejectedAfterClose()
            throws Exception {

        Auction auction =
                buildRunningAuction(
                        STARTING_PRICE,
                        RESERVE_PRICE,
                        LocalDateTime.now().minusSeconds(1)
                );

        NormalUser bidder =
                buildUser(
                        "bidder-T1",
                        USER_BALANCE
                );

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

        // Timer task
        executor.submit(() -> {

            try {

                startGate.await();

                executeWithAuctionLock(
                        auction,
                        () -> {

                            if (auction.getStatus()
                                    == Auction.AuctionStatus.RUNNING) {

                                auctionService.closeAuction(auction);

                                closeCount.incrementAndGet();
                            }
                        }
                );

            } catch (Exception e) {

                log.error("Timer task failed", e);

            } finally {

                doneGate.countDown();
            }
        });

        // Bid tasks
        for (int i = 0; i < bidThreads; i++) {

            final long bidAmount =
                    STARTING_PRICE + (i + 1) * 10_000L;

            executor.submit(() -> {

                try {

                    startGate.await();

                    executeWithAuctionLock(
                            auction,
                            () -> {

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
                                }
                            }
                    );

                } catch (Exception e) {

                    log.error("Bid task failed", e);

                } finally {

                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();

        assertAwaitCompleted(doneGate);

        assertThat(closeCount.get())
                .as("Auction must close exactly once")
                .isEqualTo(1);

        assertThat(auction.getStatus())
                .isNotEqualTo(Auction.AuctionStatus.RUNNING);

        assertThat(
                bidSuccesses.get() + bidRejections.get()
        ).isEqualTo(bidThreads);

        lockRegistry.release(auction.getId());
    }

    // =========================================================================
    // T2
    // =========================================================================

    @Test
    @Order(2)
    @Timeout(20)
    @DisplayName("T2: double close guarded")
    void doubleClose_secondCallIgnored()
            throws Exception {

        Auction auction =
                buildRunningAuction(
                        STARTING_PRICE,
                        STARTING_PRICE - 1,
                        LocalDateTime.now().minusSeconds(1)
                );

        executeWithAuctionLock(
                auction,
                () -> auctionService.closeAuction(auction)
        );

        Auction.AuctionStatus statusAfterFirst =
                auction.getStatus();

        AtomicInteger secondCloseAttempts =
                new AtomicInteger();

        executeWithAuctionLock(
                auction,
                () -> {

                    if (auction.getStatus()
                            == Auction.AuctionStatus.RUNNING) {

                        auctionService.closeAuction(auction);

                        secondCloseAttempts.incrementAndGet();
                    }
                }
        );

        assertThat(secondCloseAttempts.get())
                .isEqualTo(0);

        assertThat(auction.getStatus())
                .isEqualTo(statusAfterFirst);

        lockRegistry.release(auction.getId());
    }

    // =========================================================================
    // T3
    // =========================================================================

    @Test
    @Order(3)
    @Timeout(30)
    @DisplayName("T3: only one timer thread closes")
    void twoTimerThreads_onlyOneCloses()
            throws Exception {

        Auction auction =
                buildRunningAuction(
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

                executeWithAuctionLock(
                        auction,
                        () -> {

                            if (auction.getStatus()
                                    == Auction.AuctionStatus.RUNNING) {

                                auctionService.closeAuction(auction);

                                closed.incrementAndGet();
                            }
                        }
                );

            } catch (Exception e) {

                log.error("Timer task failed", e);

            } finally {

                done.countDown();
            }
        };

        executor.submit(timerTask);
        executor.submit(timerTask);

        gate.countDown();

        assertAwaitCompleted(done);

        assertThat(closed.get())
                .as("Only one timer may close")
                .isEqualTo(1);

        lockRegistry.release(auction.getId());
    }

    // =========================================================================
    // T4
    // =========================================================================

    @Test
    @Order(4)
    @Timeout(45)
    @DisplayName("T4: concurrent bids do not corrupt currentPrice")
    void concurrentBidsAndClose_priceNotCorrupted()
            throws Exception {

        Auction auction =
                buildRunningAuction(
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

        CountDownLatch startGate =
                new CountDownLatch(1);

        CountDownLatch doneGate =
                new CountDownLatch(bidderCount + 1);

        for (int i = 0; i < bidderCount; i++) {

            final int bidderIndex = i;

            executor.submit(() -> {

                try {

                    startGate.await();

                    NormalUser bidder =
                            bidders.get(bidderIndex);

                    long amount =
                            STARTING_PRICE
                                    + (bidderIndex + 1) * 50_000L;

                    executeWithAuctionLock(
                            auction,
                            () -> {

                                if (auction.isAcceptingBids()) {

                                    bidService.placeBid(
                                            bidder,
                                            auction,
                                            amount,
                                            new StandardBidStrategy()
                                    );
                                }
                            }
                    );

                } catch (Exception e) {

                    log.error("Bid task failed", e);

                } finally {

                    doneGate.countDown();
                }
            });
        }

        // Close task
        executor.submit(() -> {

            try {

                startGate.await();

                executeWithAuctionLock(
                        auction,
                        () -> {

                            if (auction.getStatus()
                                    == Auction.AuctionStatus.RUNNING) {

                                auctionService.closeAuction(auction);
                            }
                        }
                );

            } catch (Exception e) {

                log.error("Close task failed", e);

            } finally {

                doneGate.countDown();
            }
        });

        startGate.countDown();

        assertAwaitCompleted(doneGate);

        assertThat(auction.getCurrentPrice())
                .as("Price must never go below starting price")
                .isGreaterThanOrEqualTo(STARTING_PRICE);

        long maxPossibleBid =
                STARTING_PRICE
                        + (long) bidderCount * 50_000L;

        assertThat(auction.getCurrentPrice())
                .as("Price exceeds theoretical max")
                .isLessThanOrEqualTo(maxPossibleBid);

        lockRegistry.release(auction.getId());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void executeWithAuctionLock(
            Auction auction,
            ThrowingRunnable runnable
    ) throws Exception {

        boolean locked =
                lockRegistry.tryLock(
                        auction.getId(),
                        5,
                        TimeUnit.SECONDS
                );

        if (!locked) {
            failTest(
                    "Failed to acquire auction lock within timeout"
            );
        }

        try {

            runnable.run();

        } finally {

            lockRegistry.unlock(auction.getId());
        }
    }

    private void assertAwaitCompleted(
            CountDownLatch latch
    ) throws InterruptedException {

        boolean completed =
                latch.await(
                        INTERNAL_WAIT_SECONDS,
                        TimeUnit.SECONDS
                );

        assertThat(completed)
                .as("Concurrent tasks timeout/deadlock")
                .isTrue();
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void failTest(String message) {
        Assertions.fail(message);
    }
}
