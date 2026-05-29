package com.group13.auction.unit.observer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Smoke tests cho các {@link AuctionObserver} — không crash, hành vi thông báo cơ bản. */
@DisplayName("Observer notification smoke")
class ObserverNotificationSmokeTest {

  private NormalUser seller;
  private NormalUser bidder;
  private Auction runningAuction;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    // FIX [P2]: Observer-driven notify*() chạm ServerBroadcastNotifier → DB.
    TestFixture.silenceGlobalSingletons();
    seller = TestFixture.normalSeller("obsSeller1");
    bidder = TestFixture.bidderWithBalance("obsBidder1", 5_000_000L);
    runningAuction = TestFixture.runningAuction(seller, 1_000_000L);
  }

  @AfterEach
  void tearDown() throws Exception {
    TestFixture.resetSystemAdmin();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roleObservers")
  @DisplayName("onBidPlaced(BID_PLACED) — không ném exception")
  void onBidPlaced_happyPath_noException(String role, AuctionObserver observer) {
    AuctionEvent event =
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, bidder, 1_500_000L);
    assertDoesNotThrow(() -> observer.onBidPlaced(event));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roleObservers")
  @DisplayName("onBidPlaced — bidder null không NPE")
  void onBidPlaced_nullBidder_noNpe(String role, AuctionObserver observer) {
    AuctionEvent event =
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, null, 1_500_000L);
    assertDoesNotThrow(() -> observer.onBidPlaced(event));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roleObservers")
  @DisplayName("onAuctionEnded — winner null không NPE")
  void onAuctionEnded_nullWinner_noNpe(String role, AuctionObserver observer) {
    AuctionEvent event = new AuctionEvent(AuctionEventType.AUCTION_ENDED, runningAuction, null, 0L);
    assertDoesNotThrow(() -> observer.onAuctionEnded(event));
  }

  @Test
  @DisplayName("BidderObserver — BID_PLACED gửi notification qua INotifier")
  void bidderObserver_notifiesOnBidPlaced() {
    List<String> titles = new ArrayList<>();
    BidderObserver observer = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
    observer.setNotifier((target, title, message) -> titles.add(title));

    observer.onBidPlaced(
        new AuctionEvent(AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L));

    assertThat(titles).contains("BID_PLACED");
  }

  @Test
  @DisplayName("BidderObserver — BID_RESERVE_NOT_MET gửi notification")
  void bidderObserver_notifiesReserveNotMet() {
    List<String> titles = new ArrayList<>();
    BidderObserver observer = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
    observer.setNotifier((target, title, message) -> titles.add(title));

    observer.onBidPlaced(
        new AuctionEvent(AuctionEventType.BID_RESERVE_NOT_MET, runningAuction, bidder, 1_100_000L));

    assertThat(titles).contains("BID_RESERVE_NOT_MET");
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> roleObservers() throws Exception {
    TestFixture.bootstrapSystemAdmin();

    NormalUser s = TestFixture.normalSeller("obsSellerZ");
    NormalUser b = TestFixture.bidderWithBalance("obsBidderZ", 5_000_000L);

    Admin staff =
        Admin.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            "staffObs01",
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
            "masterObs1",
            User.hashPassword("adminpass1"),
            "master@test.com",
            User.AccountStatus.ACTIVE,
            5.0,
            Admin.LEVEL_MASTER,
            null);

    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            "Bidder", new BidderObserver(b, TestFixture.ratingServiceAllowAll())),
        org.junit.jupiter.params.provider.Arguments.of(
            "Seller", new SellerObserver(s, TestFixture.ratingServiceAllowAll())),
        org.junit.jupiter.params.provider.Arguments.of("Admin", new AdminObserver(master, Mockito.mock(NotificationDAO.class))),
        org.junit.jupiter.params.provider.Arguments.of("Staff", new StaffObserver(staff)),
        org.junit.jupiter.params.provider.Arguments.of(
            "SystemAdmin", new SystemAdminObserver(SystemAdmin.getInstance())));
  }
}