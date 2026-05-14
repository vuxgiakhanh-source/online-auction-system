package com.group13.auction.concurrency.join;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * JoinAuctionConcurrencyTest — GAP 1 + GAP 6 + GAP 7
 *
 * THAY ĐỔI SO VỚI BẢN CŨ:
 *   - G1-1 / G7-1: BugRoot là joinAuction() thiếu synchronized trên bidder.
 *     → Test giờ ENFORCE assert chính xác (times(1)), không chỉ document bug.
 *     → Khi BidService.joinAuction() được fix (bọc synchronized(bidder)),
 *       hai test này sẽ GREEN.
 *   - G6-1 / G6-2: giữ nguyên known-bug assertion (< N) vì fix cần đổi source.
 *   - G7-1: delay nhân tạo 50ms được giữ để stress-test race window.
 * ============================================================================
 */
@DisplayName("Join: double-join race & viewerCount (GAP 1, GAP 6, GAP 7)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JoinAuctionConcurrencyTest extends ConcurrencyTestBase {

    private BidService          bidService;
    private AuctionLockRegistry lockRegistry;
    private Auction             auction;

    private IWalletService mockWalletService;

    @BeforeEach
    void setUp() {
        IAuctionService   mockAuctionService    = mock(IAuctionService.class);
        IRatingService    mockRatingService     = mock(IRatingService.class);
        mockWalletService                       = mock(IWalletService.class);
        BidTransactionDAO mockBidTransactionDAO = mock(BidTransactionDAO.class);
        AuctionDAO        mockAuctionDAO        = mock(AuctionDAO.class);
        UserDAO           mockUserDAO           = mock(UserDAO.class);

        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockBidTransactionDAO.saveTransaction(any())).thenReturn(true);
        when(mockAuctionDAO.updateHighestPrice(any(), anyLong(), any())).thenReturn(true);
        when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
        when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
        when(mockUserDAO.saveUserAuctionActivity(any(), any(), any())).thenReturn(true);
        doNothing().when(mockWalletService).lockDeposit(any(), anyLong(), any());

        resetAuctionManagerUsers();

        bidService   = new BidService(mockAuctionService, mockRatingService,
                mockWalletService, mockBidTransactionDAO, mockAuctionDAO, mockUserDAO);
        lockRegistry = AuctionLockRegistry.getInstance();
        auction      = buildRunningAuction();
    }

    @AfterEach
    void tearDown() {
        lockRegistry.release(auction.getId());
        resetAuctionManagerUsers();
    }

    // ── G1-1 ─────────────────────────────────────────────────────────────────
    // BUG ROOT: BidService.joinAuction() kiểm tra hasJoined() rồi mới gọi
    //   lockDeposit() nhưng không synchronized → 10 threads đều thấy false,
    //   đều chạy lockDeposit().
    // FIX CẦN: bọc synchronized(bidder) { if hasJoined → return; ... lockDeposit; addJoinedAuction }
    // EXPECTED SAU FIX: test GREEN — lockDeposit() chỉ gọi đúng 1 lần.

    @Test
    @Order(1)
    @DisplayName("G1-1: 10 threads cùng join cùng 1 bidder — lockDeposit() phải gọi đúng 1 lần [ENFORCE]")
    @Timeout(value = 10)
    void concurrentJoin_singleBidder_lockDepositCalledOnce() throws InterruptedException {
        NormalUser bidder = buildUser("bidder-G1-1", USER_BALANCE);
        AuctionObserver obs = noopObserver();

        int N = 10;
        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(N);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        for (int i = 0; i < N; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    bidService.joinAuction(bidder, auction, obs);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // ENFORCE: lockDeposit() phải gọi đúng 1 lần — không double-charge
        verify(mockWalletService, times(1))
                .lockDeposit(eq(bidder), anyLong(), eq(auction.getId()));

        assertThat(bidder.hasJoined(auction.getId()))
                .as("Bidder phải join thành công")
                .isTrue();

        log.info("[G1-1] successes={}, failures={}", successes.get(), failures.get());
    }

    // ── G1-2 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("G1-2: 20 bidders khác nhau join đồng thời — tổng lockDeposit() = 20")
    @Timeout(value = 10)
    void concurrentJoin_20DifferentBidders_eachLockedExactlyOnce() throws InterruptedException {
        int N = 20;
        List<NormalUser> bidders = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            bidders.add(buildUser("bidder-G1-2-" + i, USER_BALANCE));
        }

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        for (NormalUser bidder : bidders) {
            AuctionObserver obs = noopObserver();
            new Thread(() -> {
                try {
                    gate.await();
                    bidService.joinAuction(bidder, auction, obs);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        verify(mockWalletService, times(N))
                .lockDeposit(any(NormalUser.class), anyLong(), eq(auction.getId()));

        for (NormalUser bidder : bidders) {
            assertThat(bidder.hasJoined(auction.getId()))
                    .as("Bidder [%s] phải join thành công", bidder.getUsername())
                    .isTrue();
        }
    }

    // ── G1-3 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("G1-3: joinAuction() idempotent — lần 2 không gọi lockDeposit()")
    void joinAuction_alreadyJoined_isIdempotent() {
        NormalUser bidder = buildUser("bidder-G1-3", USER_BALANCE);
        AuctionObserver obs = noopObserver();

        bidService.joinAuction(bidder, auction, obs);
        bidService.joinAuction(bidder, auction, obs);

        verify(mockWalletService, times(1)).lockDeposit(any(), anyLong(), any());
    }

    // ── G6-1 ─────────────────────────────────────────────────────────────────
    // BUG ROOT: Auction.incrementViewerCount() dùng plain int++ — không atomic.
    // FIX CẦN: đổi viewerCount sang AtomicInteger + getAndIncrement().
    // NOTE: test giữ known-bug assertion vì fix nằm ở source Auction.java.

    @Test
    @Order(4)
    @DisplayName("G6-1: [KNOWN BUG] 100 threads incrementViewerCount() — plain int++ có thể bị lost update")
    @Timeout(value = 5)
    void incrementViewerCount_concurrent_lostUpdateDocumented() throws InterruptedException {
        int N = 100;
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    auction.incrementViewerCount();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        int count = auction.getViewerCount();
        assertThat(count).isGreaterThan(0);
        assertThat(count).isLessThanOrEqualTo(N);

        if (count < N) {
            log.warn("[G6-1 KNOWN BUG] viewerCount={} < {} — lost update do plain int++. "
                    + "Fix: đổi sang AtomicInteger.", count, N);
        }
        // TODO: Sau khi fix Auction.viewerCount → AtomicInteger, thêm assert:
        //   assertThat(count).isEqualTo(N);
    }

    // ── G6-2 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("G6-2: watchAuction() + joinAuction() concurrent — viewerCount là lower-bound")
    @Timeout(value = 8)
    void watchAndJoin_concurrent_viewerCountIsLowerBound() throws InterruptedException {
        int watcherCount = 10;
        int joinerCount  = 10;
        int total        = watcherCount + joinerCount;

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);

        for (int i = 0; i < watcherCount; i++) {
            NormalUser watcher = buildUser("watcher-G6-2-" + i, 0L);
            AuctionObserver obs = noopObserver();
            new Thread(() -> {
                try {
                    gate.await();
                    bidService.watchAuction(watcher, auction, obs);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        for (int i = 0; i < joinerCount; i++) {
            NormalUser joiner = buildUser("joiner-G6-2-" + i, USER_BALANCE);
            AuctionObserver obs = noopObserver();
            new Thread(() -> {
                try {
                    gate.await();
                    bidService.joinAuction(joiner, auction, obs);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        int count = auction.getViewerCount();
        assertThat(count).isGreaterThan(0);
        assertThat(count).isLessThanOrEqualTo(total);
    }

    // ── G7-1 ─────────────────────────────────────────────────────────────────
    // BUG ROOT: race window giữa hasJoined() check và lockDeposit() call.
    //   Delay 50ms nhân tạo phóng đại window → mọi thread đều lọt qua check.
    // FIX CẦN: synchronized(bidder) bao trùm cả check lẫn lockDeposit.
    // EXPECTED SAU FIX: test GREEN.

    @Test
    @Order(6)
    @DisplayName("G7-1: Window race với delay nhân tạo — lockDeposit() phải chỉ gọi 1 lần dù 3 threads race [ENFORCE]")
    @Timeout(value = 15)
    void joinWindow_raceCondition_withDelay() throws InterruptedException {
        NormalUser bidder = buildUser("bidder-G7-1", USER_BALANCE);
        AuctionObserver obs = noopObserver();

        AtomicInteger lockDepositCallCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            lockDepositCallCount.incrementAndGet();
            Thread.sleep(50); // mở rộng race window
            return null;
        }).when(mockWalletService).lockDeposit(any(), anyLong(), any());

        int N = 3;
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    bidService.joinAuction(bidder, auction, obs);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        int callCount = lockDepositCallCount.get();
        if (callCount > 1) {
            log.warn("[G7-1 BUG CONFIRMED] lockDeposit() gọi {} lần thay vì 1! "
                    + "Race window giữa hasJoined() và lockDeposit() cần synchronized.", callCount);
        }

        // ENFORCE: phải đúng 1 lần
        assertThat(callCount)
                .as("lockDeposit() phải gọi đúng 1 lần dù %d threads race", N)
                .isEqualTo(1);
    }

    // ── G7-2 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("G7-2: Sau join thành công, hasJoined()=true và lần join tiếp theo bị ignore")
    void afterJoin_hasJoined_isTrue_subsequentJoinIgnored() {
        NormalUser bidder = buildUser("bidder-G7-2", USER_BALANCE);
        AuctionObserver obs = noopObserver();

        bidService.joinAuction(bidder, auction, obs);
        assertThat(bidder.hasJoined(auction.getId())).isTrue();

        assertThatCode(() -> bidService.joinAuction(bidder, auction, obs))
                .doesNotThrowAnyException();

        verify(mockWalletService, times(1)).lockDeposit(any(), anyLong(), any());
    }
}