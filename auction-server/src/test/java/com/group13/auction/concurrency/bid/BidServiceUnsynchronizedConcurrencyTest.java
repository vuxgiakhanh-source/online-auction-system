package com.group13.auction.concurrency.bid;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.BidIncrementCalculator;
import com.group13.auction.strategy.StandardBidStrategy;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bid đồng thời thật — không bọc {@link AuctionLockRegistry} bên ngoài.
 * Mô phỏng nhiều client gọi {@link BidService#placeBid} song song (như load test).
 */
@DisplayName("BidService: unsynchronized concurrent placeBid")
class BidServiceUnsynchronizedConcurrencyTest extends ConcurrencyTestBase {

    private static final int THREAD_COUNT = 24;
    private static final int ROUNDS_PER_THREAD = 8;

    private BidService bidService;
    private Auction auction;
    private AuctionLockRegistry lockRegistry;

    @BeforeEach
    void setUp() {
        IAuctionService mockAuctionService = mock(IAuctionService.class);
        IRatingService mockRatingService = mock(IRatingService.class);
        IWalletService mockWalletService = mock(IWalletService.class);
        BidTransactionDAO mockBidTx = mock(BidTransactionDAO.class);
        AuctionDAO mockAuctionDAO = mock(AuctionDAO.class);
        UserDAO mockUserDAO = mock(UserDAO.class);

        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockBidTx.saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString())).thenReturn(true);
        when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
        doNothing().when(mockWalletService).lockDeposit(any(), anyLong(), any());

        resetAuctionManagerUsers();
        bidService = new BidService(mockAuctionService, mockRatingService, mockWalletService,
                mockBidTx, mockAuctionDAO, mockUserDAO);
        lockRegistry = AuctionLockRegistry.getInstance();
        auction = buildRunningAuction();
    }

    @AfterEach
    void tearDown() {
        lockRegistry.release(auction.getId());
        resetAuctionManagerUsers();
    }

    @Test
    @DisplayName("24×8 placeBid song song — giá RAM monotonic, không âm, có bid thành công")
    @Timeout(30)
    void manyThreadsPlaceBidWithoutOuterLock_invariantsHold() throws Exception {
        List<NormalUser> bidders = buildBidders(THREAD_COUNT);
        bidders.forEach(b -> {
            b.addJoinedAuction(auction.getId());
            b.setBalance(USER_BALANCE);
        });

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicLong maxPriceSeen = new AtomicLong(STARTING_PRICE);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final NormalUser bidder = bidders.get(i);
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int r = 0; r < ROUNDS_PER_THREAD; r++) {
                        long current = auction.getCurrentPrice();
                        long inc = BidIncrementCalculator.calculate(current);
                        long amount = current + 2 * inc + idx * 1_000L;
                        try {
                            bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                            successes.incrementAndGet();
                            maxPriceSeen.updateAndGet(prev -> Math.max(prev, auction.getCurrentPrice()));
                        } catch (com.group13.auction.exception.InvalidBidException ignored) {
                            // race: giá vừa nhảy — retry vòng sau
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get())
                .as("Dưới tải cao vẫn phải có đủ bid thành công (một phần bị InvalidBid do race)")
                .isGreaterThan(THREAD_COUNT / 2);
        assertThat(auction.getCurrentPrice()).isGreaterThanOrEqualTo(maxPriceSeen.get());
        assertThat(auction.getCurrentLeader()).isNotNull();
    }

    @Test
    @DisplayName("Cùng bidder, 8 thread cùng amount — đúng 1 bid thành công")
    @Timeout(10)
    void sameBidderParallelSameAmount_onlyOneAccepted() throws Exception {
        NormalUser bidder = buildUser("solo", USER_BALANCE);
        bidder.addJoinedAuction(auction.getId());

        long amount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(8, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(fail.get()).isEqualTo(7);
        assertThat(auction.getCurrentPrice()).isEqualTo(amount);
    }
}
