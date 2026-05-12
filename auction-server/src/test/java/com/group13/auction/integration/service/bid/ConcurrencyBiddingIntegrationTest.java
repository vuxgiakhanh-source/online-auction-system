package com.group13.auction.integration.service.bid;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidProcessor;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.strategy.BidIncrementCalculator;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.network.server.session.SessionManager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * INTEGRATION TEST: Concurrency Bidding & Auto-Bid
 * ============================================================================
 *
 * Kỹ thuật tích hợp áp dụng:
 *
 *  - TOP-DOWN: Test từ BidService (tầng cao) xuống → các thành phần cấp dưới
 *    (AuctionLockRegistry, AutoBidProcessor, Strategy) được stub/mock dần.
 *    Xác minh luồng điều phối ở tầng Service trước khi lo detail cấp dưới.
 *
 *  - SANDWICH (Bi-Directional): Tầng giữa (BidService + AuctionLockRegistry)
 *    được test với tầng trên (caller threads) và tầng dưới (DAO mock) cùng lúc.
 *    Đây là điểm giao nhau của concurrency + persistence.
 *
 *  - BIG BANG (cho nhóm test cuối): Toàn bộ thành phần thực
 *    (BidService, AuctionLockRegistry, AutoBidRegistry, AutoBidProcessor,
 *     StandardBidStrategy, AutoBidStrategy) được kết nối với nhau,
 *    chỉ mock tầng DAO và Network I/O.
 *    Mục tiêu: verify end-to-end luồng auto-bid chain không bị race condition.
 *
 * Phân nhóm test:
 *   Group A – AuctionLockRegistry: unit contract của lock per-auction
 *   Group B – TOP-DOWN: Race condition prevention (10 threads, 1 auction)
 *   Group C – SANDWICH: BidService + Lock + DAO consistency
 *   Group D – BIG BANG: Auto-Bid chain integration
 *   Group E – Edge cases & Guard conditions
 * ============================================================================
 */
@DisplayName("Integration: Concurrency Bidding & Auto-Bid")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyBiddingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyBiddingIntegrationTest.class);

    // ── Thresholds & constants ────────────────────────────────────────────────
    private static final long  STARTING_PRICE   = 500_000L;   // 500k VNĐ (tier LOW → increment 50k)
    private static final long  RESERVE_PRICE    = 800_000L;
    private static final long  USER_BALANCE     = 50_000_000L;
    private static final int   THREAD_COUNT     = 10;
    private static final long  TEST_TIMEOUT_MS  = 10_000L;

    // ── Shared mocks (reset per test) ─────────────────────────────────────────
    private IAuctionService   mockAuctionService;
    private IRatingService    mockRatingService;
    private IWalletService    mockWalletService;
    private BidTransactionDAO mockBidTransactionDAO;
    private AuctionDAO        mockAuctionDAO;
    private UserDAO           mockUserDAO;
    private SessionManager    mockSessionManager;

    // ── SUT ───────────────────────────────────────────────────────────────────
    private BidService        bidService;
    private AuctionLockRegistry lockRegistry;
    private AutoBidRegistry   autoBidRegistry;

    // ── Test fixtures ─────────────────────────────────────────────────────────
    private Auction           auction;
    private NormalUser        seller;

    // ═════════════════════════════════════════════════════════════════════════
    // SETUP / TEARDOWN
    // ═════════════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        // 1. Khởi tạo tất cả mocks
        mockAuctionService    = mock(IAuctionService.class);
        mockRatingService     = mock(IRatingService.class);
        mockWalletService     = mock(IWalletService.class);
        mockBidTransactionDAO = mock(BidTransactionDAO.class);
        mockAuctionDAO        = mock(AuctionDAO.class);
        mockUserDAO           = mock(UserDAO.class);
        mockSessionManager    = mock(SessionManager.class);

        // 2. Stub mặc định: mọi user đều eligible, mọi DAO call đều return true
        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockBidTransactionDAO.saveTransaction(any())).thenReturn(true);
        when(mockAuctionDAO.updateHighestPrice(any(), anyLong(), any())).thenReturn(true);
        when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
        when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
        // lockDeposit là void → doNothing() đúng
        doNothing().when(mockWalletService).lockDeposit(any(), anyLong(), any());

        // Reset AuctionManager để tránh user state leak giữa các test
        resetAuctionManagerUsers();

        // 3. Khởi tạo BidService (SUT)
        bidService = new BidService(
                mockAuctionService,
                mockRatingService,
                mockWalletService,
                mockBidTransactionDAO,
                mockAuctionDAO,
                mockUserDAO
        );

        // 4. Lấy singleton lock registry (reset bằng reflection để tránh pollution giữa tests)
        lockRegistry    = AuctionLockRegistry.getInstance();
        autoBidRegistry = AutoBidRegistry.getInstance();

        // 5. Tạo seller (cần cho Item)
        seller = buildUser("seller", 0L);
        // Seller cần có role SELLER – thêm bằng reflection để tránh phụ thuộc AccountService
        addSellerRole(seller);

        // 6. Tạo auction đang ở trạng thái RUNNING
        Item item = Electronics.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                "Laptop Test", "Mô tả test",
                STARTING_PRICE,
                seller, "TestBrand", 12, "New"
        );
        auction = Auction.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now(),
                item,
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusHours(2),
                STARTING_PRICE,
                Auction.AuctionStatus.RUNNING,
                RESERVE_PRICE
        );
    }

    @AfterEach
    void tearDown() {
        // Dọn sạch auto-bid registry sau mỗi test để tránh state leak
        autoBidRegistry.clearAuction(auction.getId());
        // Release lock của phiên test
        lockRegistry.release(auction.getId());
        // Reset AuctionManager users để tránh user state leak
        resetAuctionManagerUsers();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GROUP A – AuctionLockRegistry: unit contract
    // Kỹ thuật: Bottom-up (test component nhỏ nhất trước)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Group A – AuctionLockRegistry: Lock Contract")
    @Order(1)
    class AuctionLockRegistryContractTest {

        @Test
        @DisplayName("A1: Cùng auctionId phải trả về cùng một ReentrantLock instance (identity)")
        void sameLockInstanceForSameAuctionId() {
            String auctionId = auction.getId();

            ReentrantLock lock1 = lockRegistry.getLock(auctionId);
            ReentrantLock lock2 = lockRegistry.getLock(auctionId);

            assertThat(lock1).isSameAs(lock2);
        }

        @Test
        @DisplayName("A2: Hai auctionId khác nhau phải trả về hai lock khác nhau")
        void differentLockInstancesForDifferentAuctions() {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();

            ReentrantLock lock1 = lockRegistry.getLock(id1);
            ReentrantLock lock2 = lockRegistry.getLock(id2);

            assertThat(lock1).isNotSameAs(lock2);

            // Cleanup
            lockRegistry.release(id1);
            lockRegistry.release(id2);
        }

        @Test
        @DisplayName("A3: Lock phải đảm bảo mutual exclusion – chỉ 1 thread lock được tại một thời điểm")
        @Timeout(value = 5)
        void lockEnsuresMutualExclusion() throws InterruptedException {
            ReentrantLock lock = lockRegistry.getLock(auction.getId());
            AtomicInteger concurrentCount = new AtomicInteger(0);
            AtomicInteger maxConcurrent   = new AtomicInteger(0);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            for (int i = 0; i < THREAD_COUNT; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        lock.lock();
                        try {
                            // Đang giữ lock – kiểm tra không ai khác vào được
                            int current = concurrentCount.incrementAndGet();
                            maxConcurrent.accumulateAndGet(current, Math::max);
                            Thread.sleep(2); // giả lập critical section
                            concurrentCount.decrementAndGet();
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startGate.countDown();
            doneLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Trong critical section, không bao giờ có > 1 thread đồng thời
            assertThat(maxConcurrent.get())
                    .as("Tối đa 1 thread được phép vào critical section tại một thời điểm")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("A4: release() phải xóa lock khỏi registry – tránh memory leak")
        void releaseRemovesLockFromRegistry() {
            String tempId = UUID.randomUUID().toString();
            int sizeBefore = lockRegistry.size();

            lockRegistry.getLock(tempId);
            assertThat(lockRegistry.size()).isEqualTo(sizeBefore + 1);

            lockRegistry.release(tempId);
            assertThat(lockRegistry.size()).isEqualTo(sizeBefore);
        }

        @Test
        @DisplayName("A5: computeIfAbsent đảm bảo atomic creation – không tạo 2 lock cùng id dù 50 thread tranh nhau")
        @Timeout(value = 5)
        void concurrentGetLockReturnsSameInstance() throws InterruptedException {
            String auctionId = UUID.randomUUID().toString();
            Set<ReentrantLock> lockSet = ConcurrentHashMap.newKeySet();
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(50);

            for (int i = 0; i < 50; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        lockSet.add(lockRegistry.getLock(auctionId));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startGate.countDown();
            doneLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            lockRegistry.release(auctionId);

            // Dù 50 thread cùng gọi, vẫn chỉ có 1 lock instance
            assertThat(lockSet).hasSize(1);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GROUP B – TOP-DOWN: Race Condition Prevention
    // 10 thread cùng gọi placeBid() → chỉ 1 bid thắng tại mỗi thời điểm
    // Kỹ thuật: Top-down stub (IAuctionService, IWalletService đã mock)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Group B – TOP-DOWN: Race Condition với 10 Threads")
    @Order(2)
    class RaceConditionPreventionTest {

        @Test
        @DisplayName("B1: 10 threads cùng bid – currentPrice tăng tuần tự, không bị lost update")
        @Timeout(value = 10)
        void tenThreadsConcurrentBid_noPriceLostUpdate() throws InterruptedException {
            // Setup: 10 bidders, mỗi người đã join auction
            List<NormalUser> bidders = buildBidders(THREAD_COUNT);
            bidders.forEach(b -> {
                b.addJoinedAuction(auction.getId());
                b.setBalance(USER_BALANCE);
            });

            ReentrantLock auctionLock = lockRegistry.getLock(auction.getId());
            StandardBidStrategy strategy = new StandardBidStrategy();
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);
            List<Long>    bidSequence  = new CopyOnWriteArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final NormalUser bidder = bidders.get(i);
                // Mỗi bidder đặt giá = startingPrice + 50k*(i+1) → giá khác nhau, tránh race tie
                final long bidAmount = STARTING_PRICE + 50_000L * (i + 1);

                new Thread(() -> {
                    try {
                        startGate.await(); // Chờ tín hiệu "bắt đầu" đồng loạt

                        auctionLock.lock();
                        try {
                            bidService.placeBid(bidder, auction, bidAmount, strategy);
                            successCount.incrementAndGet();
                            bidSequence.add(auction.getCurrentPrice());
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            auctionLock.unlock();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startGate.countDown(); // Khai hỏa tất cả 10 threads cùng lúc
            doneLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Assertion 1: Tổng bid success + fail = THREAD_COUNT
            assertThat(successCount.get() + failCount.get())
                    .as("Tổng số thread phải hoàn thành đúng %d", THREAD_COUNT)
                    .isEqualTo(THREAD_COUNT);

            // Assertion 2: Giá luôn tăng (không bao giờ giảm) → không có lost update
            for (int i = 1; i < bidSequence.size(); i++) {
                assertThat(bidSequence.get(i))
                        .as("Giá tại step [%d] phải >= giá tại step [%d-1]", i, i)
                        .isGreaterThanOrEqualTo(bidSequence.get(i - 1));
            }

            // Assertion 3: currentPrice cuối cùng là max trong số giá đã bid thành công
            long expectedFinalPrice = bidSequence.isEmpty() ? STARTING_PRICE
                    : bidSequence.stream().mapToLong(Long::longValue).max().getAsLong();
            assertThat(auction.getCurrentPrice()).isEqualTo(expectedFinalPrice);

            // Assertion 4: Số BidTransaction được persist = TỔNG số bid attempt (success + rejected)
            // BidService.recordTransaction() luôn gọi saveTransaction() cho cả bid thành công lẫn bị reject
            verify(mockBidTransactionDAO, times(THREAD_COUNT))
                    .saveTransaction(any(BidTransaction.class));
        }

        @Test
        @DisplayName("B2: Cùng 1 bidder gọi placeBid 5 lần song song – chỉ lần đầu hợp lệ, 4 lần sau bị reject do giá không đủ increment")
        @Timeout(value = 5)
        void sameBidderConcurrentBid_onlyFirstValidBidAccepted() throws InterruptedException {
            NormalUser bidder = buildUser("singleBidder", USER_BALANCE);
            bidder.addJoinedAuction(auction.getId());

            ReentrantLock auctionLock  = lockRegistry.getLock(auction.getId());
            StandardBidStrategy strategy = new StandardBidStrategy();
            long sameBidAmount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);
            // Tất cả 5 lần bid cùng một amount → chỉ lần 1 pass, các lần sau fail (not enough increment)

            CountDownLatch startGate    = new CountDownLatch(1);
            CountDownLatch doneLatch    = new CountDownLatch(5);
            AtomicInteger  acceptCount  = new AtomicInteger(0);
            AtomicInteger  rejectCount  = new AtomicInteger(0);

            for (int i = 0; i < 5; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        auctionLock.lock();
                        try {
                            bidService.placeBid(bidder, auction, sameBidAmount, strategy);
                            acceptCount.incrementAndGet();
                        } catch (Exception e) {
                            rejectCount.incrementAndGet();
                        } finally {
                            auctionLock.unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startGate.countDown();
            doneLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Chỉ 1 bid được chấp nhận; các bid còn lại fail vì giá không đủ increment sau khi giá đã tăng
            assertThat(acceptCount.get())
                    .as("Chỉ 1 trong 5 bid với cùng amount được chấp nhận")
                    .isEqualTo(1);
            assertThat(rejectCount.get()).isEqualTo(4);
        }

        @Test
        @DisplayName("B3: currentLeader luôn là người có mức giá cao nhất sau 10 thread bid tuần tự qua lock")
        @Timeout(value = 10)
        void currentLeaderAlwaysHighestBidder() throws InterruptedException {
            List<NormalUser> bidders = buildBidders(THREAD_COUNT);
            bidders.forEach(b -> {
                b.addJoinedAuction(auction.getId());
                b.setBalance(USER_BALANCE);
            });

            ReentrantLock auctionLock = lockRegistry.getLock(auction.getId());
            StandardBidStrategy strategy = new StandardBidStrategy();

            // Bid tuần tự qua lock, mỗi người bid cao hơn người trước
            long price = STARTING_PRICE;
            NormalUser expectedLeader = null;
            for (NormalUser bidder : bidders) {
                price += BidIncrementCalculator.calculate(price);
                auctionLock.lock();
                try {
                    bidService.placeBid(bidder, auction, price, strategy);
                    expectedLeader = bidder;
                } finally {
                    auctionLock.unlock();
                }
            }

            assertThat(auction.getCurrentLeader())
                    .as("Leader cuối cùng là người bid cao nhất")
                    .isSameAs(expectedLeader);
            assertThat(auction.getCurrentPrice()).isEqualTo(price);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GROUP C – SANDWICH: BidService + Lock + DAO Consistency
    // Tầng trên (caller): test code
    // Tầng giữa (SUT):   BidService + AuctionLockRegistry
    // Tầng dưới (stub):  Mock DAO
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Group C – SANDWICH: BidService + Lock + DAO Consistency")
    @Order(3)
    class SandwichBidServiceDaoConsistencyTest {

        @Test
        @DisplayName("C1: Mỗi bid thành công phải gọi saveTransaction() và updateHighestPrice() đúng 1 lần")
        void eachSuccessfulBidPersistsExactlyOnce() {
            NormalUser bidder = buildUser("bidderC1", USER_BALANCE);
            bidder.addJoinedAuction(auction.getId());
            long bidAmount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);

            ReentrantLock lock = lockRegistry.getLock(auction.getId());
            lock.lock();
            try {
                bidService.placeBid(bidder, auction, bidAmount, new StandardBidStrategy());
            } finally {
                lock.unlock();
            }

            verify(mockBidTransactionDAO, times(1)).saveTransaction(any());
            verify(mockAuctionDAO, times(1)).updateHighestPrice(
                    eq(auction.getId()), eq(bidAmount), eq(bidder.getId()));
        }

        @Test
        @DisplayName("C2: Bid bị reject (giá thấp hơn currentPrice) → saveTransaction với BidResult.REJECTED, không gọi updateHighestPrice")
        void invalidBid_savesRejectedTransaction_noAuctionUpdate() {
            NormalUser bidder = buildUser("bidderC2", USER_BALANCE);
            bidder.addJoinedAuction(auction.getId());

            // Đặt giá thấp hơn giá khởi điểm (sẽ bị reject)
            long invalidBid = STARTING_PRICE - 1;

            assertThatThrownBy(() ->
                    bidService.placeBid(bidder, auction, invalidBid, new StandardBidStrategy())
            ).isInstanceOf(RuntimeException.class);

            // Verify: transaction REJECTED được lưu
            ArgumentCaptor<BidTransaction> txCaptor = ArgumentCaptor.forClass(BidTransaction.class);
            verify(mockBidTransactionDAO, times(1)).saveTransaction(txCaptor.capture());
            assertThat(txCaptor.getValue().getResult())
                    .isEqualTo(BidTransaction.BidResult.REJECTED);

            // Verify: auction price KHÔNG bị cập nhật
            verify(mockAuctionDAO, never()).updateHighestPrice(any(), anyLong(), any());
        }

        @Test
        @DisplayName("C3: Anti-sniping – bid trong 30s cuối phải gọi updateEndTime() và extend đúng 60s")
        void antiSniping_bidInLastWindow_extendsEndTime() {
            NormalUser bidder = buildUser("bidderC3", USER_BALANCE);
            bidder.addJoinedAuction(auction.getId());

            // Tạo auction với endTime còn 20 giây (< 30s threshold)
            Auction sniping = buildRunningAuction(STARTING_PRICE, RESERVE_PRICE,
                    LocalDateTime.now().plusSeconds(20));
            bidder.addJoinedAuction(sniping.getId());

            long bidAmount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);
            LocalDateTime endTimeBefore = sniping.getEndTime();

            lockRegistry.getLock(sniping.getId()).lock();
            try {
                bidService.placeBid(bidder, sniping, bidAmount, new StandardBidStrategy());
            } finally {
                lockRegistry.getLock(sniping.getId()).unlock();
                lockRegistry.release(sniping.getId());
            }

            // EndTime phải được gia hạn thêm 60s
            assertThat(sniping.getEndTime())
                    .as("EndTime phải gia hạn thêm 60s sau anti-sniping")
                    .isAfterOrEqualTo(endTimeBefore.plusSeconds(59));

            verify(mockAuctionDAO, atLeastOnce()).updateEndTime(eq(sniping.getId()), any());
        }

        @Test
        @DisplayName("C4: 5 threads bid song song – tổng lần gọi saveTransaction() = tổng số bid (success + rejected)")
        @Timeout(value = 8)
        void concurrentBids_totalSaveTransactionCallsMatchTotalAttempts() throws InterruptedException {
            List<NormalUser> bidders = buildBidders(5);
            bidders.forEach(b -> {
                b.addJoinedAuction(auction.getId());
                b.setBalance(USER_BALANCE);
            });

            ReentrantLock lock    = lockRegistry.getLock(auction.getId());
            CountDownLatch gate   = new CountDownLatch(1);
            CountDownLatch done   = new CountDownLatch(5);
            AtomicInteger attempts = new AtomicInteger(0);

            for (int i = 0; i < 5; i++) {
                NormalUser bidder = bidders.get(i);
                long amount = STARTING_PRICE + 50_000L * (i + 1);
                new Thread(() -> {
                    try {
                        gate.await();
                        lock.lock();
                        try {
                            bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                        } catch (Exception ignored) {
                        } finally {
                            attempts.incrementAndGet();
                            lock.unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            done.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Mọi attempt đều phải được ghi nhận (dù success hay rejected)
            verify(mockBidTransactionDAO, times(attempts.get())).saveTransaction(any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GROUP D – BIG BANG: Auto-Bid Chain Integration
    // Tất cả thành phần thực: BidService, AuctionLockRegistry, AutoBidRegistry,
    // AutoBidProcessor, StandardBidStrategy, AutoBidStrategy
    // Chỉ mock: DAO, SessionManager, IAuctionService (không test network I/O)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Group D – BIG BANG: Auto-Bid Chain Integration")
    @Order(4)
    class AutoBidChainIntegrationTest {

        private AutoBidProcessor autoBidProcessor;

        @BeforeEach
        void setUpAutoBid() {
            // SessionManager mock: không gửi packet thực
            autoBidProcessor = new AutoBidProcessor(bidService, mockSessionManager);
        }
        @AfterEach
        void cleanUpAutoBid() {
            autoBidRegistry.clearAuction(auction.getId());
        }

        /**
         * D1: Kịch bản cơ bản
         * - AutoBidder A đăng ký maxBid = 1_000_000
         * - ManualBidder B bid thủ công 600_000
         * - Hệ thống phải tự động trigger bid của A lên 650_000 (600k + 50k increment)
         * - currentPrice = 650_000, currentLeader = A
         */
        @Test
        @DisplayName("D1: Auto-bid counter-trigger sau khi manual bidder vượt qua")
        void autoBid_triggersCounterBid_afterManualBidderSurpasses() {
            NormalUser autoBidder = buildUser("autoBidderD1", USER_BALANCE);
            NormalUser manualBidder = buildUser("manualBidderD1", USER_BALANCE);
            autoBidder.addJoinedAuction(auction.getId());
            manualBidder.addJoinedAuction(auction.getId());

            // Đăng ký vào AuctionManager để AutoBidProcessor.findNormalUserById() tìm thấy
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

            long maxBid = 1_000_000L;
            autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

            // Manual bid 600k
            long manualBidAmount = 600_000L; // 500k start + 50k = 550k minimum; 600k valid
            ReentrantLock lock = lockRegistry.getLock(auction.getId());
            lock.lock();
            try {
                bidService.placeBid(manualBidder, auction, manualBidAmount, new StandardBidStrategy());
                // Sau manual bid thành công, trigger auto-bid processor
                autoBidProcessor.process(auction, manualBidder.getId());
            } finally {
                lock.unlock();
            }

            long expectedAutoPrice = manualBidAmount + BidIncrementCalculator.calculate(manualBidAmount);

            // A) Giá phải được nâng lên bởi auto-bid
            assertThat(auction.getCurrentPrice())
                    .as("Auto-bid phải counter lên mức manualBid + increment")
                    .isEqualTo(expectedAutoPrice);

            // B) Leader phải là autoBidder
            assertThat(auction.getCurrentLeader())
                    .as("AutoBidder phải là leader sau khi counter thành công")
                    .isSameAs(autoBidder);

            // C) Giá không được vượt maxBid
            assertThat(auction.getCurrentPrice())
                    .as("Giá cuối không được vượt maxBid của autoBidder")
                    .isLessThanOrEqualTo(maxBid);
        }

        /**
         * D2: Auto-bid KHÔNG trigger nếu giá tiếp theo vượt maxBid
         * - AutoBidder A: maxBid = 560_000
         * - ManualBidder B bid 550_000 → nextBid cho A = 600_000 > maxBid
         * - Hệ thống KHÔNG trigger bid cho A
         * - A bị xóa khỏi registry (exhausted)
         */
        @Test
        @DisplayName("D2: Auto-bid không trigger khi nextBid vượt maxBid – entry bị xóa khỏi registry")
        void autoBid_doesNotTrigger_whenNextBidExceedsMaxBid() {
            NormalUser autoBidder  = buildUser("autoBidderD2", USER_BALANCE);
            NormalUser manualBidder = buildUser("manualBidderD2", USER_BALANCE);
            autoBidder.addJoinedAuction(auction.getId());
            manualBidder.addJoinedAuction(auction.getId());

            // Đăng ký vào AuctionManager để AutoBidProcessor.findNormalUserById() tìm thấy
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

            // maxBid = 560k, manualBid = 550k → nextBid = 600k > 560k → KHÔNG trigger
            long maxBid       = 560_000L;
            long manualAmount = 550_000L; // 500k + 50k = 550k (valid bid)
            autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

            ReentrantLock lock = lockRegistry.getLock(auction.getId());
            lock.lock();
            try {
                bidService.placeBid(manualBidder, auction, manualAmount, new StandardBidStrategy());
                autoBidProcessor.process(auction, manualBidder.getId());
            } finally {
                lock.unlock();
            }

            // A) Giá vẫn là manualAmount (auto-bid không được trigger)
            assertThat(auction.getCurrentPrice())
                    .as("Giá phải là manualAmount vì auto-bid exhausted")
                    .isEqualTo(manualAmount);

            // B) Leader là manualBidder, KHÔNG phải autoBidder
            assertThat(auction.getCurrentLeader())
                    .as("Leader phải là manualBidder vì auto-bid không counter được")
                    .isSameAs(manualBidder);

            // C) autoBidder phải bị xóa khỏi registry
            assertThat(autoBidRegistry.hasActiveBid(autoBidder.getId(), auction.getId()))
                    .as("AutoBidder phải bị xóa khỏi registry khi maxBid cạn")
                    .isFalse();
        }

        /**
         * D3: Chuỗi auto-bid (chain) giữa 2 auto-bidders
         * - A: maxBid = 800_000
         * - B: maxBid = 900_000
         * - ManualBidder C bid 600_000
         * - A counter → B counter → chuỗi dừng khi B dẫn đầu
         * - Giá cuối <= min(maxBid_B, B_threshold)
         */
        @Test
        @DisplayName("D3: Chuỗi auto-bid giữa 2 auto-bidders – giá cuối không vượt maxBid của bất kỳ ai")
        void autoBidChain_twoAutoBidders_finalPriceWithinBothMaxBids() {
            NormalUser autoBidderA  = buildUser("autoBidderA_D3", USER_BALANCE);
            NormalUser autoBidderB  = buildUser("autoBidderB_D3", USER_BALANCE);
            NormalUser manualBidder = buildUser("manualBidder_D3", USER_BALANCE);

            autoBidderA.addJoinedAuction(auction.getId());
            autoBidderB.addJoinedAuction(auction.getId());
            manualBidder.addJoinedAuction(auction.getId());

            // Đăng ký vào AuctionManager để AutoBidProcessor.findNormalUserById() tìm thấy
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderA);
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderB);

            long maxBidA = 800_000L;
            long maxBidB = 900_000L;
            // A đăng ký trước → tie-breaking: A được ưu tiên khi maxBid bằng nhau
            autoBidRegistry.register(autoBidderA.getId(), auction.getId(), maxBidA);
            autoBidRegistry.register(autoBidderB.getId(), auction.getId(), maxBidB);

            long manualBid = 600_000L;
            ReentrantLock lock = lockRegistry.getLock(auction.getId());
            lock.lock();
            try {
                bidService.placeBid(manualBidder, auction, manualBid, new StandardBidStrategy());
                autoBidProcessor.process(auction, manualBidder.getId());
            } finally {
                lock.unlock();
            }

            long finalPrice = auction.getCurrentPrice();

            // A) Giá cuối không vượt maxBid của bất kỳ ai đang ở cuộc
            assertThat(finalPrice)
                    .as("Giá cuối phải <= maxBidA hoặc <= maxBidB tùy ai thắng")
                    .isLessThanOrEqualTo(Math.max(maxBidA, maxBidB));

            // B) Giá cuối phải hợp lệ (>= manualBid ban đầu)
            assertThat(finalPrice).isGreaterThanOrEqualTo(manualBid);

            // C) Leader là một trong hai auto-bidder (người thắng chuỗi)
            NormalUser leader = auction.getCurrentLeader();
            assertThat(leader)
                    .as("Leader sau chuỗi auto-bid phải là một trong 2 auto-bidders")
                    .isIn(autoBidderA, autoBidderB);

            // D) Giá cuối không vượt maxBid của người thắng
            long winnerMaxBid = leader.equals(autoBidderA) ? maxBidA : maxBidB;
            assertThat(finalPrice)
                    .as("Giá cuối không được vượt maxBid của người thắng: %d", winnerMaxBid)
                    .isLessThanOrEqualTo(winnerMaxBid);
        }

        /**
         * D4: 10 threads cùng kích hoạt AutoBidProcessor – không có race condition,
         * giá tăng monotonically và chỉ leader thay đổi theo chiều có lợi.
         */
        @Test
        @DisplayName("D4: 10 threads concurrent với AutoBidProcessor – giá tăng monotonically, không race condition")
        @Timeout(value = 15)
        void concurrentManualBids_withAutoBidProcessor_noConcurrencyBug() throws InterruptedException {
            // Setup: 1 auto-bidder, 10 manual bidders
            NormalUser autoBidder = buildUser("autoBidder_D4", USER_BALANCE * 10);
            autoBidder.addJoinedAuction(auction.getId());

            // Đăng ký vào AuctionManager để AutoBidProcessor.findNormalUserById() tìm thấy
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

            List<NormalUser> manualBidders = buildBidders(THREAD_COUNT);
            manualBidders.forEach(b -> {
                b.addJoinedAuction(auction.getId());
                b.setBalance(USER_BALANCE);
            });

            // Auto-bidder với maxBid rất cao = 5_000_000
            long maxBid = 5_000_000L;
            autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

            ReentrantLock lock = lockRegistry.getLock(auction.getId());
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
            List<Long> priceSnapshots = new CopyOnWriteArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                NormalUser manualBidder = manualBidders.get(i);
                final long bid = STARTING_PRICE + 50_000L * (i + 1);

                new Thread(() -> {
                    try {
                        startGate.await();
                        lock.lock();
                        try {
                            bidService.placeBid(manualBidder, auction, bid, new StandardBidStrategy());
                            autoBidProcessor.process(auction, manualBidder.getId());
                            priceSnapshots.add(auction.getCurrentPrice());
                        } catch (Exception ignored) {
                            // bid thấp hơn currentPrice sẽ bị reject → bình thường
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startGate.countDown();
            doneLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // A) Giá không bao giờ giảm trong chuỗi snapshot
            for (int i = 1; i < priceSnapshots.size(); i++) {
                assertThat(priceSnapshots.get(i))
                        .as("Giá tại snapshot[%d] phải >= snapshot[%d-1]", i, i)
                        .isGreaterThanOrEqualTo(priceSnapshots.get(i - 1));
            }

            // B) Giá cuối không vượt maxBid của autoBidder
            assertThat(auction.getCurrentPrice())
                    .as("Giá cuối không được vượt maxBid")
                    .isLessThanOrEqualTo(maxBid);

            // C) AutoBidder vẫn còn trong registry (chưa cạn maxBid)
            //    chỉ verify nếu giá < maxBid - increment
            long increment = BidIncrementCalculator.calculate(auction.getCurrentPrice());
            if (auction.getCurrentPrice() + increment <= maxBid) {
                assertThat(autoBidRegistry.hasActiveBid(autoBidder.getId(), auction.getId()))
                        .as("AutoBidder vẫn active vì maxBid chưa cạn")
                        .isTrue();
            }
        }

        /**
         * D5: Xác minh không có infinite loop trong AutoBidProcessor
         * khi hai auto-bidder có cùng maxBid (tie-breaking scenario)
         */
        @Test
        @DisplayName("D5: Hai auto-bidder cùng maxBid – AutoBidProcessor không bị infinite loop, kết thúc trong giới hạn maxIterations")
        @Timeout(value = 5)
        void autoBidProcessor_sameMaxBid_noInfiniteLoop() {
            NormalUser autoBidderX = buildUser("autoBidderX_D5", USER_BALANCE);
            NormalUser autoBidderY = buildUser("autoBidderY_D5", USER_BALANCE);
            NormalUser manualBidder = buildUser("manualBidder_D5", USER_BALANCE);

            autoBidderX.addJoinedAuction(auction.getId());
            autoBidderY.addJoinedAuction(auction.getId());
            manualBidder.addJoinedAuction(auction.getId());

            // Đăng ký vào AuctionManager để AutoBidProcessor.findNormalUserById() tìm thấy
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderX);
            com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderY);

            // Cả hai cùng maxBid → tie-break bằng registeredAt (X đăng ký trước sẽ thắng)
            long sameMaxBid = 700_000L;
            autoBidRegistry.register(autoBidderX.getId(), auction.getId(), sameMaxBid);
            // Sleep nhỏ để registeredAt của Y muộn hơn X
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            autoBidRegistry.register(autoBidderY.getId(), auction.getId(), sameMaxBid);

            long manualBid = 550_000L;
            ReentrantLock lock = lockRegistry.getLock(auction.getId());

            // Test không bị timeout = không có infinite loop
            assertThatCode(() -> {
                lock.lock();
                try {
                    bidService.placeBid(manualBidder, auction, manualBid, new StandardBidStrategy());
                    autoBidProcessor.process(auction, manualBidder.getId());
                } finally {
                    lock.unlock();
                }
            }).doesNotThrowAnyException();

            // Giá cuối hợp lệ (không âm, không vượt maxBid)
            assertThat(auction.getCurrentPrice())
                    .isGreaterThanOrEqualTo(manualBid)
                    .isLessThanOrEqualTo(sameMaxBid);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GROUP E – Edge Cases & Guard Conditions
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Group E – Edge Cases & Guard Conditions")
    @Order(5)
    class EdgeCasesTest {

        @Test
        @DisplayName("E1: Bidder chưa join auction → bid bị từ chối với AuctionBusinessException")
        void bidder_notJoined_throwsException() {
            NormalUser notJoinedBidder = buildUser("notJoined", USER_BALANCE);
            long bidAmount = STARTING_PRICE + 50_000L;

            assertThatThrownBy(() ->
                    bidService.placeBid(notJoinedBidder, auction, bidAmount, new StandardBidStrategy())
            ).isInstanceOf(com.group13.auction.exception.AuctionBusinessException.class);
        }

        @Test
        @DisplayName("E2: User bị BANNED → bid bị từ chối với AuthenticationException ngay cả khi đã join")
        void bannedBidder_throwsAuthenticationException() {
            NormalUser bannedBidder = buildUser("bannedBidder", USER_BALANCE);
            bannedBidder.addJoinedAuction(auction.getId());

            // Stub: user không eligible (bị banned)
            when(mockRatingService.isEligible(bannedBidder)).thenReturn(false);
            bannedBidder.setAccountStatus(User.AccountStatus.BANNED);

            long bidAmount = STARTING_PRICE + 50_000L;

            assertThatThrownBy(() ->
                    bidService.placeBid(bannedBidder, auction, bidAmount, new StandardBidStrategy())
            ).isInstanceOf(com.group13.auction.exception.AuthenticationException.class);
        }

        @Test
        @DisplayName("E3: Bid vào phiên FINISHED → AuctionClosedException được ném ra")
        void bid_onFinishedAuction_throwsAuctionClosedException() {
            // Tạo phiên ở trạng thái FINISHED
            Auction finishedAuction = Auction.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now().minusHours(2), LocalDateTime.now(),
                    auction.getItem(),
                    LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1),
                    1_000_000L,
                    Auction.AuctionStatus.FINISHED,
                    RESERVE_PRICE
            );

            NormalUser bidder = buildUser("bidderE3", USER_BALANCE);
            bidder.addJoinedAuction(finishedAuction.getId());

            assertThatThrownBy(() ->
                    bidService.placeBid(bidder, finishedAuction, 1_200_000L, new StandardBidStrategy())
            ).isInstanceOf(com.group13.auction.exception.AuctionClosedException.class);
        }

        @Test
        @DisplayName("E4: AutoBidStrategy với maxBid <= 0 → IllegalArgumentException ngay tại constructor")
        void autoBidStrategy_negativeMaxBid_throwsIllegalArgument() {
            assertThatThrownBy(() -> new AutoBidStrategy(0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxBid");

            assertThatThrownBy(() -> new AutoBidStrategy(-1000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("E5: clearAuction() trên AutoBidRegistry xóa sạch toàn bộ entry của phiên, không ảnh hưởng phiên khác")
        void autoBidRegistry_clearAuction_isolatesOtherAuctions() {
            String otherAuctionId = UUID.randomUUID().toString();
            NormalUser userA = buildUser("userA_E5", 0L);
            NormalUser userB = buildUser("userB_E5", 0L);

            autoBidRegistry.register(userA.getId(), auction.getId(), 1_000_000L);
            autoBidRegistry.register(userB.getId(), otherAuctionId, 2_000_000L);

            // Clear chỉ auction hiện tại
            autoBidRegistry.clearAuction(auction.getId());

            assertThat(autoBidRegistry.hasActiveBid(userA.getId(), auction.getId()))
                    .as("userA phải bị xóa sau clearAuction")
                    .isFalse();
            assertThat(autoBidRegistry.hasActiveBid(userB.getId(), otherAuctionId))
                    .as("userB trong phiên khác phải còn nguyên")
                    .isTrue();

            // Cleanup
            autoBidRegistry.clearAuction(otherAuctionId);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /** Tạo NormalUser với username/balance cụ thể, status ACTIVE, rating 3.0 */
    private NormalUser buildUser(String username, long balance) {
        NormalUser user = NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                username, "hashedPass", username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0, balance, 0L,
                java.util.EnumSet.of(User.UserRole.BIDDER),
                false, false, null
        );
        return user;
    }

    /** Tạo list bidders với tên đánh số */
    private List<NormalUser> buildBidders(int count) {
        List<NormalUser> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buildUser("bidder_" + i + "_" + UUID.randomUUID().toString().substring(0, 4), USER_BALANCE));
        }
        return list;
    }

    /**
     * Tạo Auction ở trạng thái RUNNING với endTime tùy chỉnh.
     * Dùng cho test anti-sniping (C3).
     */
    private Auction buildRunningAuction(long startingPrice, long reservePrice, LocalDateTime endTime) {
        return Auction.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now().minusHours(1), LocalDateTime.now(),
                auction.getItem(),
                LocalDateTime.now().minusMinutes(30),
                endTime,
                startingPrice,
                Auction.AuctionStatus.RUNNING,
                reservePrice
        );
    }

    /**
     * Thêm SELLER role vào NormalUser qua reflection
     * (roles là EnumSet private final, không có addRole() public trong production code)
     */
    private void addSellerRole(NormalUser user) {
        try {
            java.lang.reflect.Field rolesField = NormalUser.class.getDeclaredField("roles");
            rolesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<User.UserRole> roles = (java.util.Set<User.UserRole>) rolesField.get(user);
            roles.add(User.UserRole.SELLER);
        } catch (Exception e) {
            // Nếu field không accessible, test vẫn pass với bidder-only role
            log.warn("[TEST WARN] Không thể add SELLER role qua reflection: {}", e.getMessage());
        }
    }

    /**
     * Reset allUsers map trong AuctionManager Singleton qua reflection để tránh state leak
     * giữa các test (AutoBidProcessor tìm user qua AuctionManager).
     */
    private void resetAuctionManagerUsers() {
        try {
            java.lang.reflect.Field usersField =
                    com.group13.auction.manager.AuctionManager.class.getDeclaredField("allUsers");
            usersField.setAccessible(true);
            ((java.util.Map<?, ?>) usersField.get(
                    com.group13.auction.manager.AuctionManager.getInstance())).clear();
        } catch (Exception e) {
            log.warn("[TEST WARN] Không thể reset AuctionManager.allUsers: {}", e.getMessage());
        }
    }
}