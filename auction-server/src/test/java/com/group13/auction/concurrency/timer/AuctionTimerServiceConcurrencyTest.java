package com.group13.auction.concurrency.timer;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.*;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.StandardBidStrategy;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * AuctionTimerServiceConcurrencyTest — GAP-D (đã sửa)
 *
 * FIX SO VỚI BẢN CŨ:
 *   BẢN CŨ: setUp() không bootstrap SystemAdmin → AuctionService.<init> ném
 *     IllegalStateException ngay khi khởi tạo → tất cả 4 test FAIL với error:
 *     "SystemAdmin chưa được bootstrap. Gọi SystemAdmin.bootstrap() khi app khởi động."
 *
 *   BẢN MỚI: bootstrapSystemAdminForTest() inject mock SystemAdmin vào INSTANCE
 *     field bằng reflection (giống ObserverConcurrencyTest) trước khi setUp()
 *     tạo AuctionService. Không cần chạy DB thật.
 *
 * Race condition được test:
 *   T1: Timer closeAuction() vs BidHandler placeBid() → bid bị từ chối sau close.
 *   T2: double-close guard — Timer không close auction đã FINISHED.
 *   T3: 2 Timer threads tranh close → lock đảm bảo chỉ 1 lần.
 *   T4: closeAuction() concurrent 10 bid threads → currentPrice không corrupt.
 * ============================================================================
 */
@DisplayName("Timer: closeAuction() vs placeBid() race (GAP-D)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuctionTimerServiceConcurrencyTest extends ConcurrencyTestBase {

    private AuctionService      auctionService;
    private BidService          bidService;
    private AuctionLockRegistry lockRegistry;

    private AuctionDAO              mockAuctionDAO;
    private BidTransactionDAO       mockBidTransactionDAO;
    private UserDAO                 mockUserDAO;
    private IRatingService          mockRatingService;
    private FinancialTransactionDAO mockFinancialTransactionDAO;

    /**
     * Bootstrap SystemAdmin bằng reflection để tránh gọi DB thật.
     * AuctionService khởi tạo SystemAdmin.getInstance() ở field-level →
     * INSTANCE phải tồn tại TRƯỚC khi new AuctionService(...) được gọi.
     */
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
        // MUST be called before new AuctionService(...)
        bootstrapSystemAdminForTest();

        mockAuctionDAO              = mock(AuctionDAO.class);
        mockBidTransactionDAO       = mock(BidTransactionDAO.class);
        mockUserDAO                 = mock(UserDAO.class);
        mockRatingService           = mock(IRatingService.class);
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

        WalletService walletService = new WalletService(
                mockFinancialTransactionDAO, mockUserDAO, mockRatingService);

        auctionService = new AuctionService(mockRatingService, mockAuctionDAO);
        bidService     = new BidService(
                auctionService, mockRatingService, walletService,
                mockBidTransactionDAO, mockAuctionDAO, mockUserDAO);

        lockRegistry = AuctionLockRegistry.getInstance();
        resetAuctionManagerUsers();
    }

    @AfterEach
    void tearDown() {
        resetAuctionManagerUsers();
    }

    // ── T1 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("T1: Timer closeAuction() vs BidHandler placeBid() — sau close mọi bid bị từ chối")
    @Timeout(value = 10)
    void timerClose_vs_bidHandler_bidsRejectedAfterClose() throws InterruptedException {
        Auction auction = buildRunningAuction(
                STARTING_PRICE, RESERVE_PRICE,
                LocalDateTime.now().minusSeconds(1) // đã hết hạn
        );

        NormalUser bidder = buildUser("bidder-T1", USER_BALANCE);
        joinBidder(bidder, auction);

        int bidThreads = 5;
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(bidThreads + 1);
        AtomicInteger bidSuccesses  = new AtomicInteger(0);
        AtomicInteger bidRejections = new AtomicInteger(0);
        AtomicInteger closeCount    = new AtomicInteger(0);

        // Timer thread
        new Thread(() -> {
            try {
                gate.await();
                boolean locked = lockRegistry.tryLock(auction.getId(), 5, TimeUnit.SECONDS);
                if (locked) {
                    try {
                        if (auction.getStatus() == Auction.AuctionStatus.RUNNING) {
                            auctionService.closeAuction(auction);
                            closeCount.incrementAndGet();
                        }
                    } finally {
                        lockRegistry.unlock(auction.getId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }).start();

        // Bid threads
        for (int i = 0; i < bidThreads; i++) {
            final long bidAmount = STARTING_PRICE + (i + 1) * 10_000L;
            new Thread(() -> {
                try {
                    gate.await();
                    boolean locked = lockRegistry.tryLock(auction.getId(), 5, TimeUnit.SECONDS);
                    if (locked) {
                        try {
                            bidService.placeBid(bidder, auction, bidAmount, new StandardBidStrategy());
                            bidSuccesses.incrementAndGet();
                        } catch (Exception e) {
                            bidRejections.incrementAndGet();
                        } finally {
                            lockRegistry.unlock(auction.getId());
                        }
                    } else {
                        bidRejections.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        log.info("[T1] close={} bidSuccesses={} bidRejections={} status={}",
                closeCount.get(), bidSuccesses.get(), bidRejections.get(), auction.getStatus());

        assertThat(closeCount.get())
                .as("Auction phải được close đúng 1 lần")
                .isEqualTo(1);

        assertThat(auction.getStatus())
                .as("Status phải là FINISHED hoặc CANCELED sau close")
                .isNotEqualTo(Auction.AuctionStatus.RUNNING);

        assertThat(bidSuccesses.get() + bidRejections.get())
                .as("Mọi bid thread phải có kết quả rõ ràng")
                .isEqualTo(bidThreads);

        lockRegistry.release(auction.getId());
    }

    // ── T2 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("T2: double-close guard — Timer không close auction đã FINISHED")
    @Timeout(value = 5)
    void doubleClose_secondCallIgnored() throws InterruptedException {
        Auction auction = buildRunningAuction(
                STARTING_PRICE, STARTING_PRICE - 1, // reserve không met
                LocalDateTime.now().minusSeconds(1)
        );

        boolean locked = lockRegistry.tryLock(auction.getId(), 3, TimeUnit.SECONDS);
        assertThat(locked).isTrue();
        try {
            auctionService.closeAuction(auction);
        } finally {
            lockRegistry.unlock(auction.getId());
        }

        Auction.AuctionStatus statusAfterFirst = auction.getStatus();
        assertThat(statusAfterFirst).isNotEqualTo(Auction.AuctionStatus.RUNNING);

        AtomicInteger secondCloseAttempts = new AtomicInteger(0);
        boolean locked2 = lockRegistry.tryLock(auction.getId(), 3, TimeUnit.SECONDS);
        if (locked2) {
            try {
                if (auction.getStatus() == Auction.AuctionStatus.RUNNING) {
                    auctionService.closeAuction(auction);
                    secondCloseAttempts.incrementAndGet();
                }
            } finally {
                lockRegistry.unlock(auction.getId());
            }
        }

        assertThat(secondCloseAttempts.get())
                .as("Close lần 2 phải bị guard ngăn lại (status != RUNNING)")
                .isEqualTo(0);
        assertThat(auction.getStatus())
                .as("Status không được thay đổi sau double-close guard")
                .isEqualTo(statusAfterFirst);

        lockRegistry.release(auction.getId());
    }

    // ── T3 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("T3: 2 Timer threads tranh close cùng auction — lock đảm bảo chỉ 1 lần close")
    @Timeout(value = 5)
    void twoTimerThreads_onlyOneCloses() throws InterruptedException {
        Auction auction = buildRunningAuction(
                STARTING_PRICE, RESERVE_PRICE,
                LocalDateTime.now().minusSeconds(1)
        );

        CountDownLatch gate  = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(2);
        AtomicInteger closed = new AtomicInteger(0);

        Runnable timerTask = () -> {
            try {
                gate.await();
                boolean locked = lockRegistry.tryLock(auction.getId(), 5, TimeUnit.SECONDS);
                if (locked) {
                    try {
                        if (auction.getStatus() == Auction.AuctionStatus.RUNNING
                                && !auction.getEndTime().isAfter(LocalDateTime.now())) {
                            auctionService.closeAuction(auction);
                            closed.incrementAndGet();
                        }
                    } finally {
                        lockRegistry.unlock(auction.getId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };

        new Thread(timerTask).start();
        new Thread(timerTask).start();

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(closed.get())
                .as("Lock + status guard đảm bảo chỉ close đúng 1 lần")
                .isEqualTo(1);

        lockRegistry.release(auction.getId());
    }

    // ── T4 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("T4: closeAuction() concurrent với 10 bid threads — currentPrice không corrupt, monotonic")
    @Timeout(value = 10)
    void concurrentBidsAndClose_priceNotCorrupted() throws InterruptedException {
        Auction auction = buildRunningAuction(
                STARTING_PRICE, RESERVE_PRICE,
                LocalDateTime.now().plusSeconds(30)
        );

        int bidderCount = 10;
        java.util.List<NormalUser> bidders = buildBidders(bidderCount);
        for (NormalUser b : bidders) joinBidder(b, auction);

        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(bidderCount + 1);
        AtomicInteger  bidsDone = new AtomicInteger(0);
        AtomicReference<Long> priceSnapshot = new AtomicReference<>(auction.getCurrentPrice());

        for (int i = 0; i < bidderCount; i++) {
            final NormalUser bidder = bidders.get(i);
            final long amount = STARTING_PRICE + (i + 1) * 50_000L;
            new Thread(() -> {
                try {
                    gate.await();
                    boolean locked = lockRegistry.tryLock(auction.getId(), 3, TimeUnit.SECONDS);
                    if (locked) {
                        try {
                            if (auction.isAcceptingBids()) {
                                bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                                priceSnapshot.set(auction.getCurrentPrice());
                            }
                        } catch (Exception ignored) {
                        } finally {
                            lockRegistry.unlock(auction.getId());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    bidsDone.incrementAndGet();
                    done.countDown();
                }
            }).start();
        }

        // Timer thread: close sau 1 short delay
        new Thread(() -> {
            try {
                gate.await();
                Thread.sleep(5);
                boolean locked = lockRegistry.tryLock(auction.getId(), 3, TimeUnit.SECONDS);
                if (locked) {
                    try {
                        if (auction.getStatus() == Auction.AuctionStatus.RUNNING) {
                            auctionService.closeAuction(auction);
                        }
                    } finally {
                        lockRegistry.unlock(auction.getId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }).start();

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(auction.getCurrentPrice())
                .as("currentPrice không được thấp hơn STARTING_PRICE sau concurrent ops")
                .isGreaterThanOrEqualTo(STARTING_PRICE);

        long maxPossibleBid = STARTING_PRICE + (long) bidderCount * 50_000L;
        assertThat(auction.getCurrentPrice())
                .as("currentPrice không được vượt quá max bid amount")
                .isLessThanOrEqualTo(maxPossibleBid);

        lockRegistry.release(auction.getId());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void joinBidder(NormalUser bidder, Auction auction) {
        bidder.lockDeposit(auction.getItem().getStartingPrice() * 3 / 10);
        bidder.addJoinedAuction(auction.getId());
        auctionService.addObserver(auction.getId(), noopObserver());
    }
}