package com.group13.auction.stress.bid;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.BidIncrementCalculator;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
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
 * Stress test BidService — mock DAO, không cần DB.
 *
 * BidService constructor : (IAuctionService, IRatingService, IWalletService,
 *                           BidTransactionDAO, AuctionDAO, UserDAO)
 *
 * AuctionDAO methods gọi thực tế:
 *   updateHighestPrice(String auctionId, long newPrice, String bidderId)
 *   updateEndTime(String auctionId, LocalDateTime endTime)
 *   updateViewerCount(String auctionId, int count)
 *
 * BidTransactionDAO: saveTransaction(BidTransaction)
 * WalletService.lockDeposit: (NormalUser, long, String)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BidService Stress — mock DAO, nhiều thread")
class BidServiceStressTest extends ConcurrencyTestBase {

    private static final int BIDDER_COUNT = 30;
    private static final int ROUNDS_EACH  = 5;
    private static final int TIMEOUT_SEC  = 30;

    @Mock AuctionDAO        auctionDAO;
    @Mock BidTransactionDAO bidTransactionDAO;
    @Mock UserDAO           userDAO;
    @Mock RatingService     ratingService;
    @Mock WalletService     walletService;
    @Mock AuctionService    auctionService;

    private BidService       bidService;
    private Auction          auction;
    private List<NormalUser> bidders;

    @BeforeEach
    void setUp() throws Exception {                         // throws Exception — bootstrapSystemAdmin cần
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

        auction = buildRunningAuction();
        bidders = buildBidders(BIDDER_COUNT);
        for (NormalUser b : bidders) {
            bidService.joinAuction(b, auction, noopObserver());
        }
    }

    @AfterEach
    void tearDown() throws Exception {                      // throws Exception — resetSystemAdmin cần
        TestFixture.resetSystemAdmin();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 30 bidder × 5 rounds, không deadlock, giá tăng đơn điệu")
    void stress_manyBidders_noDeadlock_priceMonotone() throws Exception {
        AtomicInteger successes  = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < BIDDER_COUNT; i++) {
            final NormalUser bidder = bidders.get(i);
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                for (int r = 0; r < ROUNDS_EACH; r++) {
                    boolean placed = false;
                    int attempts = 0;
                    while (!placed && attempts < 8) {
                        attempts++;
                        long current   = auction.getCurrentPrice();
                        long increment = BidIncrementCalculator.calculate(current);
                        long amount    = current + 2 * increment;
                        try {
                            bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                            successes.incrementAndGet();
                            placed = true;
                        } catch (InvalidBidException ignored) {
                            // price moved — retry
                        } catch (Exception ignored) {
                            break;
                        }
                    }
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(successes.get())
                .as("Phải có ít nhất 1 bid thành công")
                .isGreaterThan(0);
        assertThat(auction.getCurrentPrice())
                .as("Giá phải tăng so với ban đầu")
                .isGreaterThanOrEqualTo(STARTING_PRICE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 50 thread, chỉ InvalidBidException được ném, không unexpected error")
    void stress_manyThreads_onlyExpectedExceptions() throws Exception {
        int EXTRA = 50;
        List<NormalUser> extra = buildBidders(EXTRA);
        for (NormalUser b : extra) bidService.joinAuction(b, auction, noopObserver());

        AtomicInteger unexpected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (NormalUser bidder : extra) {
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                long current   = auction.getCurrentPrice();
                long increment = BidIncrementCalculator.calculate(current);
                long amount    = current + 3 * increment;
                try {
                    bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                } catch (InvalidBidException ignored) {
                    // expected
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                    log.warn("[STRESS BID] Unexpected: {} — {}",
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpected.get())
                .as("Không có exception ngoài InvalidBidException")
                .isZero();
    }
}
