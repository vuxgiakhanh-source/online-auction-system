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

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * BidRaceConditionTest — Group B (TOP-DOWN)
 * 10 threads cùng gọi placeBid() → chỉ 1 bid thắng tại mỗi thời điểm.
 * Setup: BidService + AuctionLockRegistry. DAO + Service deps: mock.
 * ============================================================================
 */
@DisplayName("Bid: Race Condition Prevention (TOP-DOWN)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BidRaceConditionTest extends ConcurrencyTestBase {

    private static final int THREAD_COUNT = 10;

    private BidService          bidService;
    private AuctionLockRegistry lockRegistry;
    private Auction             auction;

    private IAuctionService   mockAuctionService;
    private IRatingService    mockRatingService;
    private IWalletService    mockWalletService;
    private BidTransactionDAO mockBidTransactionDAO;
    private AuctionDAO        mockAuctionDAO;
    private UserDAO           mockUserDAO;

    @BeforeEach
    void setUp() {
        mockAuctionService    = mock(IAuctionService.class);
        mockRatingService     = mock(IRatingService.class);
        mockWalletService     = mock(IWalletService.class);
        mockBidTransactionDAO = mock(BidTransactionDAO.class);
        mockAuctionDAO        = mock(AuctionDAO.class);
        mockUserDAO           = mock(UserDAO.class);

        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockBidTransactionDAO.saveTransaction(any())).thenReturn(true);
        when(mockAuctionDAO.updateHighestPrice(any(), anyLong(), any())).thenReturn(true);
        when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
        when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
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

    // ── B1 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("B1: 10 threads cùng bid — currentPrice tăng tuần tự, không bị lost update")
    @Timeout(value = 10)
    void tenThreadsConcurrentBid_noPriceLostUpdate() throws InterruptedException {
        List<NormalUser> bidders = buildBidders(THREAD_COUNT);
        bidders.forEach(b -> {
            b.addJoinedAuction(auction.getId());
            b.setBalance(USER_BALANCE);
        });

        ReentrantLock lock = lockRegistry.getLock(auction.getId());
        StandardBidStrategy strategy = new StandardBidStrategy();
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        List<Long>    bidSequence  = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final NormalUser bidder = bidders.get(i);
            final long bidAmount = STARTING_PRICE + 50_000L * (i + 1);

            new Thread(() -> {
                try {
                    gate.await();
                    lock.lock();
                    try {
                        bidService.placeBid(bidder, auction, bidAmount, strategy);
                        successCount.incrementAndGet();
                        bidSequence.add(auction.getCurrentPrice());
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(successCount.get() + failCount.get()).isEqualTo(THREAD_COUNT);

        for (int i = 1; i < bidSequence.size(); i++) {
            assertThat(bidSequence.get(i))
                    .as("Giá tại step[%d] phải >= step[%d-1]", i, i)
                    .isGreaterThanOrEqualTo(bidSequence.get(i - 1));
        }

        long expectedFinalPrice = bidSequence.isEmpty() ? STARTING_PRICE
                : bidSequence.stream().mapToLong(Long::longValue).max().getAsLong();
        assertThat(auction.getCurrentPrice()).isEqualTo(expectedFinalPrice);

        verify(mockBidTransactionDAO, times(THREAD_COUNT)).saveTransaction(any(BidTransaction.class));
    }

    // ── B2 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("B2: Cùng 1 bidder bid 5 lần song song — chỉ lần đầu hợp lệ, 4 lần sau bị reject")
    @Timeout(value = 5)
    void sameBidderConcurrentBid_onlyFirstValidBidAccepted() throws InterruptedException {
        NormalUser bidder = buildUser("singleBidder", USER_BALANCE);
        bidder.addJoinedAuction(auction.getId());

        ReentrantLock lock = lockRegistry.getLock(auction.getId());
        StandardBidStrategy strategy = new StandardBidStrategy();
        long sameBidAmount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);

        CountDownLatch gate        = new CountDownLatch(1);
        CountDownLatch done        = new CountDownLatch(5);
        AtomicInteger  acceptCount = new AtomicInteger(0);
        AtomicInteger  rejectCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    lock.lock();
                    try {
                        bidService.placeBid(bidder, auction, sameBidAmount, strategy);
                        acceptCount.incrementAndGet();
                    } catch (Exception e) {
                        rejectCount.incrementAndGet();
                    } finally {
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
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(acceptCount.get())
                .as("Chỉ 1 trong 5 bid với cùng amount được chấp nhận")
                .isEqualTo(1);
        assertThat(rejectCount.get()).isEqualTo(4);
    }

    // ── B3 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("B3: currentLeader luôn là người có mức giá cao nhất sau 10 thread bid tuần tự qua lock")
    @Timeout(value = 10)
    void currentLeaderAlwaysHighestBidder() throws InterruptedException {
        List<NormalUser> bidders = buildBidders(THREAD_COUNT);
        bidders.forEach(b -> {
            b.addJoinedAuction(auction.getId());
            b.setBalance(USER_BALANCE);
        });

        ReentrantLock lock = lockRegistry.getLock(auction.getId());
        StandardBidStrategy strategy = new StandardBidStrategy();

        long price = STARTING_PRICE;
        NormalUser expectedLeader = null;
        for (NormalUser bidder : bidders) {
            price += BidIncrementCalculator.calculate(price);
            lock.lock();
            try {
                bidService.placeBid(bidder, auction, price, strategy);
                expectedLeader = bidder;
            } finally {
                lock.unlock();
            }
        }

        assertThat(auction.getCurrentLeader())
                .as("Leader cuối là người bid cao nhất")
                .isSameAs(expectedLeader);
        assertThat(auction.getCurrentPrice()).isEqualTo(price);
    }
}
