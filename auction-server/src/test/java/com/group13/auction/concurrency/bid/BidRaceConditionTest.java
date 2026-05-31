package com.group13.auction.concurrency.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

/** Race trên {@link BidService#placeBid} — không khóa registry bên ngoài. */
@DisplayName("Bid: Race Condition Prevention")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BidRaceConditionTest extends ConcurrencyTestBase {

  private static final int THREAD_COUNT = 10;

  private BidService bidService;
  private AuctionLockRegistry lockRegistry;
  private Auction auction;

  private BidTransactionDAO mockBidTransactionDAO;

  @BeforeEach
  void setUp() {
    IAuctionService mockAuctionService = mock(IAuctionService.class);
    IRatingService mockRatingService = mock(IRatingService.class);
    IWalletService mockWalletService = mock(IWalletService.class);
    mockBidTransactionDAO = mock(BidTransactionDAO.class);
    AuctionDAO mockAuctionDAO = mock(AuctionDAO.class);
    UserDAO mockUserDAO = mock(UserDAO.class);

    when(mockRatingService.isEligible(any())).thenReturn(true);
    when(mockBidTransactionDAO.saveTransactionAndUpdatePrice(
            any(), anyString(), anyLong(), anyString()))
        .thenReturn(true);
    when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
    when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
    doNothing().when(mockWalletService).lockDeposit(any(), anyLong(), any());

    resetAuctionManagerUsers();

    bidService =
        new BidService(
            mockAuctionService,
            mockRatingService,
            mockWalletService,
            mockBidTransactionDAO,
            mockAuctionDAO,
            mockUserDAO);
    lockRegistry = AuctionLockRegistry.getInstance();
    auction = buildRunningAuction();
  }

  @AfterEach
  void tearDown() {
    lockRegistry.release(auction.getId());
    resetAuctionManagerUsers();
  }

  @Test
  @Order(1)
  @DisplayName("B1: 10 threads placeBid song song — giá cuối đúng, không lost update")
  @Timeout(15)
  void tenThreadsConcurrentBid_noPriceLostUpdate() throws Exception {
    List<NormalUser> bidders = buildBidders(THREAD_COUNT);
    bidders.forEach(
        b -> {
          b.addJoinedAuction(auction.getId());
          b.setBalance(USER_BALANCE);
        });

    StandardBidStrategy strategy = new StandardBidStrategy();
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);
    AtomicInteger successCount = new AtomicInteger();
    AtomicLong maxSuccessfulPrice = new AtomicLong(STARTING_PRICE);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final NormalUser bidder = bidders.get(i);
      final int idx = i;
      new Thread(
              () -> {
                try {
                  gate.await();
                  for (int attempt = 0; attempt < 5; attempt++) {
                    try {
                      long current = auction.getCurrentPrice();
                      long inc = BidIncrementCalculator.calculate(current);
                      long bidAmount = current + inc + idx * 1_000L;
                      bidService.placeBid(bidder, auction, bidAmount, strategy);
                      successCount.incrementAndGet();
                      maxSuccessfulPrice.updateAndGet(p -> Math.max(p, auction.getCurrentPrice()));
                      break;
                    } catch (com.group13.auction.exception.InvalidBidException ignored) {
                      // Giá vừa nhảy — thử lại với current mới
                    }
                  }
                } catch (Exception ignored) {
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(successCount.get()).isGreaterThan(0);
    assertThat(auction.getCurrentPrice()).isGreaterThanOrEqualTo(maxSuccessfulPrice.get());
    verify(mockBidTransactionDAO, times(successCount.get()))
        .saveTransactionAndUpdatePrice(
            any(BidTransaction.class), anyString(), anyLong(), anyString());
  }

  @Test
  @Order(2)
  @DisplayName("B2: Cùng bidder bid song song cùng amount — chỉ 1 thành công")
  @Timeout(10)
  void sameBidderConcurrentBid_onlyFirstValidBidAccepted() throws Exception {
    NormalUser bidder = buildUser("singleBidder", USER_BALANCE);
    bidder.addJoinedAuction(auction.getId());

    StandardBidStrategy strategy = new StandardBidStrategy();
    long sameBidAmount = STARTING_PRICE + BidIncrementCalculator.calculate(STARTING_PRICE);

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(5);
    AtomicInteger acceptCount = new AtomicInteger();
    AtomicInteger rejectCount = new AtomicInteger();

    for (int i = 0; i < 5; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bidService.placeBid(bidder, auction, sameBidAmount, strategy);
                  acceptCount.incrementAndGet();
                } catch (Exception e) {
                  rejectCount.incrementAndGet();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(acceptCount.get()).isEqualTo(1);
    assertThat(rejectCount.get()).isEqualTo(4);
  }

  @Test
  @Order(3)
  @DisplayName("B3: Nhiều bidder bid tuần tự qua placeBid — leader là người giá cao nhất")
  @Timeout(10)
  void currentLeaderAlwaysHighestBidder() {
    List<NormalUser> bidders = buildBidders(THREAD_COUNT);
    bidders.forEach(
        b -> {
          b.addJoinedAuction(auction.getId());
          b.setBalance(USER_BALANCE);
        });

    StandardBidStrategy strategy = new StandardBidStrategy();
    long price = STARTING_PRICE;
    NormalUser expectedLeader = null;
    for (NormalUser bidder : bidders) {
      price += BidIncrementCalculator.calculate(price);
      bidService.placeBid(bidder, auction, price, strategy);
      expectedLeader = bidder;
    }

    assertThat(auction.getCurrentLeader()).isSameAs(expectedLeader);
    assertThat(auction.getCurrentPrice()).isEqualTo(price);
  }
}
