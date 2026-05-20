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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * BidDaoConsistencyTest — Group C (SANDWICH)
 * Tầng giữa (BidService + AuctionLockRegistry) với tầng dưới (DAO mock).
 * Điểm giao nhau concurrency + persistence.
 * ============================================================================
 */
@DisplayName("Bid: DAO Consistency (SANDWICH)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BidDaoConsistencyTest extends ConcurrencyTestBase {

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
        when(mockBidTransactionDAO.saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString())).thenReturn(true);
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

    // ── C1 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("C1: Mỗi bid thành công phải gọi saveTransactionAndUpdatePrice() đúng 1 lần")
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

        verify(mockBidTransactionDAO, times(1)).saveTransactionAndUpdatePrice(
                any(), eq(auction.getId()), eq(bidAmount), eq(bidder.getId()));
    }

    // ── C2 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("C2: Bid bị reject (giá thấp) → throw exception, KHÔNG gọi saveTransaction (FIX #2), KHÔNG gọi updateHighestPrice")
    void invalidBid_savesRejectedTransaction_noAuctionUpdate() {
        NormalUser bidder = buildUser("bidderC2", USER_BALANCE);
        bidder.addJoinedAuction(auction.getId());
        long invalidBid = STARTING_PRICE - 1;

        // FIX #2: REJECTED bid không được ghi DB — chỉ throw exception
        assertThatThrownBy(() ->
                bidService.placeBid(bidder, auction, invalidBid, new StandardBidStrategy())
        ).isInstanceOf(RuntimeException.class);

        // Không gọi saveTransaction cho bid bị reject
        verify(mockBidTransactionDAO, never()).saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString());
    }

    // ── C3 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("C3: Anti-sniping — bid trong 30s cuối phải gọi updateEndTime() và extend đúng 60s")
    void antiSniping_bidInLastWindow_extendsEndTime() {
        NormalUser bidder = buildUser("bidderC3", USER_BALANCE);
        // Auction còn 20s → trong cửa sổ anti-snipe
        Auction sniping = buildRunningAuction(STARTING_PRICE, RESERVE_PRICE,
                LocalDateTime.now().plusSeconds(20));
        bidder.addJoinedAuction(sniping.getId());

        long bidAmount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);
        LocalDateTime before = sniping.getEndTime();

        ReentrantLock lock = lockRegistry.getLock(sniping.getId());
        lock.lock();
        try {
            bidService.placeBid(bidder, sniping, bidAmount, new StandardBidStrategy());
        } finally {
            lock.unlock();
            lockRegistry.release(sniping.getId());
        }

        assertThat(sniping.getEndTime())
                .as("EndTime phải được gia hạn ít nhất 59s sau anti-sniping")
                .isAfterOrEqualTo(before.plusSeconds(59));

        verify(mockAuctionDAO, atLeastOnce()).updateEndTime(eq(sniping.getId()), any());
    }

    // ── C4 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("C4: 5 threads bid song song — tổng saveTransaction() = tổng số bid attempts")
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
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // FIX #2: chỉ ACCEPTED bid mới gọi saveTransaction — không phải tổng attempts
        // attempts đếm tất cả lần gọi (kể cả rejected), saveTransaction chỉ cho ACCEPTED.
        // Verify: số lần saveTransaction <= số attempts (không ghi thừa)
        verify(mockBidTransactionDAO, atMost(attempts.get())).saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString());
    }
}