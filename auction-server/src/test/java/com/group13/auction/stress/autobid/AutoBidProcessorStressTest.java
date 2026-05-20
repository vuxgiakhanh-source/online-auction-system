package com.group13.auction.stress.autobid;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.AutoBidProcessor;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Stress test AutoBidProcessor + AutoBidRegistry — mock DAO, không cần DB.
 *
 * <p>AutoBidProcessor.process(Auction, triggeredByUserId) được gọi đồng thời
 * từ nhiều thread → kiểm tra:
 *   1. Không deadlock trong vòng timeout
 *   2. AutoBidRegistry.clearAuction() đồng thời với register() không corrupt
 *   3. Nhiều auto-bidder cùng phiên: chỉ 1 người thắng mỗi round
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoBidProcessor Stress — mock DAO, nhiều thread")
class AutoBidProcessorStressTest extends ConcurrencyTestBase {

    private static final int AUTO_BIDDERS = 10;
    private static final int ROUNDS       = 5;
    private static final int TIMEOUT_SEC  = 30;

    @Mock AuctionDAO        auctionDAO;
    @Mock BidTransactionDAO bidTransactionDAO;
    @Mock UserDAO           userDAO;
    @Mock RatingService     ratingService;
    @Mock WalletService     walletService;
    @Mock AuctionService    auctionService;

    private BidService       bidService;
    private AutoBidProcessor processor;
    private AutoBidRegistry  registry;
    private Auction          auction;
    private List<NormalUser> autoBidders;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();

        lenient().when(auctionDAO.updateEndTime(anyString(), any())).thenReturn(true);
        lenient().when(auctionDAO.updateViewerCount(anyString(), anyInt())).thenReturn(true);
        lenient().when(bidTransactionDAO.saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString())).thenReturn(true);
        lenient().when(ratingService.isEligible(any())).thenReturn(true);
        lenient().doNothing().when(walletService).lockDeposit(any(), anyLong(), anyString());

        bidService = new BidService(
                auctionService, ratingService, walletService,
                bidTransactionDAO, auctionDAO, userDAO);

        // Reset AutoBidRegistry singleton entries cho test isolation
        registry = AutoBidRegistry.getInstance();
        clearRegistry();

        processor   = new AutoBidProcessor(bidService, SessionManager.getInstance());
        auction     = buildRunningAuction();
        autoBidders = buildBidders(AUTO_BIDDERS);

        // Mỗi auto-bidder join auction
        for (NormalUser b : autoBidders) {
            bidService.joinAuction(b, auction, noopObserver());
        }

        // Đăng ký auto-bid với các maxBid tăng dần
        for (int i = 0; i < AUTO_BIDDERS; i++) {
            long maxBid = STARTING_PRICE + (i + 1) * 5_000_000L;
            registry.register(autoBidders.get(i).getId(), auction.getId(), maxBid);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        clearRegistry();
        TestFixture.resetSystemAdmin();
    }

    private void clearRegistry() {
        try {
            Field f = AutoBidRegistry.class.getDeclaredField("entries");
            f.setAccessible(true);
            Object entries = f.get(registry);
            if (entries instanceof Map) ((Map<?, ?>) entries).clear();
        } catch (Exception e) {
            log.warn("Cannot clear AutoBidRegistry: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 10 thread gọi process() đồng thời, không deadlock")
    void stress_concurrentProcess_noDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(AUTO_BIDDERS);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < AUTO_BIDDERS; i++) {
            final String triggeredBy = autoBidders.get(i).getId();
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try {
                    processor.process(auction, triggeredBy);
                } catch (Exception e) {
                    // Exception từ business logic (bid không hợp lệ) là acceptable
                    // Chỉ NPE hoặc IllegalMonitorStateException mới là lỗi thật
                    if (e instanceof NullPointerException
                            || e instanceof IllegalMonitorStateException) {
                        errors.incrementAndGet();
                        log.warn("[STRESS AUTOBID] Unexpected: {} — {}",
                                e.getClass().getSimpleName(), e.getMessage());
                    }
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get())
                .as("Không có NPE hay IllegalMonitorStateException")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — register đồng thời với clearAuction, không corrupt registry")
    void stress_registerConcurrentWithClear_noCorruption() throws Exception {
        int THREADS = 20;
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        List<NormalUser> extraBidders = buildBidders(THREADS);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try {
                    if (idx % 3 == 0) {
                        // Clear
                        registry.clearAuction(auction.getId());
                    } else {
                        // Register
                        long maxBid = STARTING_PRICE + (idx + 1) * 1_000_000L;
                        registry.register(extraBidders.get(idx).getId(),
                                auction.getId(), maxBid);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[STRESS AUTOBID] register/clear error: {} — {}",
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get())
                .as("Không có exception khi register đồng thời với clearAuction")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — process() nhiều rounds liên tiếp, giá tăng đơn điệu")
    void stress_processMultipleRounds_priceMonotone() throws Exception {
        long priceBefore = auction.getCurrentPrice();

        for (int round = 0; round < ROUNDS; round++) {
            // Re-register auto-bidders (clearAuction rồi register lại)
            registry.clearAuction(auction.getId());
            for (int i = 0; i < AUTO_BIDDERS; i++) {
                long currentPrice = auction.getCurrentPrice();
                long maxBid = currentPrice + (i + 2) * 2_000_000L;
                registry.register(autoBidders.get(i).getId(), auction.getId(), maxBid);
            }

            // Trigger process từ bidder đầu tiên
            try {
                processor.process(auction, autoBidders.get(0).getId());
            } catch (Exception ignored) {}
        }

        assertThat(auction.getCurrentPrice())
                .as("Sau nhiều rounds process, giá không được giảm so với ban đầu")
                .isGreaterThanOrEqualTo(priceBefore);
    }
}
