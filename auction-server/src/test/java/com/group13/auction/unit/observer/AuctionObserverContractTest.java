package com.group13.auction.unit.observer;

import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.dao.NotificationDAO;
import org.mockito.Mockito;
import com.group13.auction.observer.*;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Contract tests cho {@link AuctionObserver} — non-throwing, null-safe, isolation. */
@DisplayName("AuctionObserver — contract")
class AuctionObserverContractTest {

  private NormalUser seller;
  private NormalUser bidder;
  private Auction runningAuction;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    // FIX [P2]: BidderObserver/SellerObserver.onBidPlaced gọi ServerBroadcastNotifier → DB.
    TestFixture.silenceGlobalSingletons();
    seller = TestFixture.normalSeller("contractSel1");
    bidder = TestFixture.bidderWithBalance("contractBid1", 5_000_000L);
    runningAuction = TestFixture.runningAuction(seller, 1_000_000L);
  }

  @AfterEach
  void tearDown() throws Exception {
    TestFixture.resetSystemAdmin();
  }

  static Stream<AuctionObserver> allConcreteObservers() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    NormalUser s = TestFixture.normalSeller("contractSelZ");
    NormalUser b = TestFixture.bidderWithBalance("contractBidZ", 5_000_000L);
    Admin staff =
        Admin.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            "staffCtr01",
            User.hashPassword("adminpass1"),
            "staff@test.com",
            User.AccountStatus.ACTIVE,
            5.0,
            Admin.LEVEL_STAFF,
            null);
    Admin master =
        Admin.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            "masterCtr1",
            User.hashPassword("adminpass1"),
            "master@test.com",
            User.AccountStatus.ACTIVE,
            5.0,
            Admin.LEVEL_MASTER,
            null);
    return Stream.of(
        new BidderObserver(b, TestFixture.ratingServiceAllowAll()),
        new SellerObserver(s, TestFixture.ratingServiceAllowAll()),
        new AdminObserver(master, Mockito.mock(NotificationDAO.class)),
        new StaffObserver(staff),
        new SystemAdminObserver(SystemAdmin.getInstance()));
  }

  @ParameterizedTest
  @MethodSource("allConcreteObservers")
  @DisplayName("mọi implementation là AuctionObserver")
  void implementsContract(AuctionObserver observer) {
    assertNotNull(observer);
  }

  @ParameterizedTest
  @MethodSource("allConcreteObservers")
  @DisplayName("onBidPlaced(BID_PLACED) — không ném exception")
  void onBidPlaced_bidPlaced_nonThrowing(AuctionObserver observer) {
    AuctionEvent event =
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, bidder, 1_000_000L);
    assertDoesNotThrow(() -> observer.onBidPlaced(event));
  }

  @ParameterizedTest
  @MethodSource("allConcreteObservers")
  @DisplayName("onBidPlaced — bidder null không crash")
  void onBidPlaced_nullBidder_safe(AuctionObserver observer) {
    AuctionEvent event =
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, null, 1_000_000L);
    assertDoesNotThrow(() -> observer.onBidPlaced(event));
  }

  @ParameterizedTest
  @MethodSource("allConcreteObservers")
  @DisplayName("onAuctionEnded — lifecycle events không crash")
  void onAuctionEnded_lifecycle_nonThrowing(AuctionObserver observer) {
    assertDoesNotThrow(
        () ->
            observer.onAuctionEnded(
                new AuctionEvent(AuctionEventType.AUCTION_STARTED, runningAuction, null, 0)));
    assertDoesNotThrow(
        () ->
            observer.onAuctionEnded(
                new AuctionEvent(AuctionEventType.AUCTION_ENDED, runningAuction, null, 0)));
  }

  @Test
  @DisplayName("hai observer độc lập — gọi observer A không ảnh hưởng B")
  void observerIsolation() {
    BidderObserver a = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
    BidderObserver b =
        new BidderObserver(
            TestFixture.bidderWithBalance("contractBid2", 5_000_000L),
            TestFixture.ratingServiceAllowAll());
    AuctionEvent event =
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L);

    AuctionEvent otherEvent =
        new AuctionEvent(AuctionEventType.AUCTION_STARTED, runningAuction, null, 0);
    assertDoesNotThrow(() -> a.onBidPlaced(event));
    assertDoesNotThrow(() -> b.onAuctionEnded(otherEvent));
  }

  @Test
  @DisplayName("onBidPlaced không mutate auction price")
  void onBidPlaced_doesNotMutateAuction() {
    long priceBefore = runningAuction.getCurrentPrice();
    AuctionObserver observer = new SellerObserver(seller, TestFixture.ratingServiceAllowAll());
    observer.onBidPlaced(
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, bidder, 9_999_999L));
    assertEquals(priceBefore, runningAuction.getCurrentPrice());
  }
}