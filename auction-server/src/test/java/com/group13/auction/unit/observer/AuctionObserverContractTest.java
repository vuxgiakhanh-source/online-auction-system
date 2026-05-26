package com.group13.auction.unit.observer;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.*;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract + smoke tests cho {@link AuctionObserver}.
 */
@DisplayName("AuctionObserver — contract & smoke")
class AuctionObserverContractTest {

    private NormalUser seller;
    private NormalUser bidder;
    private Auction runningAuction;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
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
        Admin staff = Admin.reconstitute(
                UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(),
                "staffCtr01", User.hashPassword("adminpass1"), "staff@test.com",
                User.AccountStatus.ACTIVE, 5.0, Admin.LEVEL_STAFF, null);
        Admin master = Admin.reconstitute(
                UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(),
                "masterCtr1", User.hashPassword("adminpass1"), "master@test.com",
                User.AccountStatus.ACTIVE, 5.0, Admin.LEVEL_MASTER, null);
        return Stream.of(
                new BidderObserver(b, TestFixture.ratingServiceAllowAll()),
                new SellerObserver(s, TestFixture.ratingServiceAllowAll()),
                new AdminObserver(master),
                new StaffObserver(staff),
                new SystemAdminObserver(SystemAdmin.getInstance())
        );
    }

    @ParameterizedTest
    @MethodSource("allConcreteObservers")
    void onBidPlaced_bidPlaced_nonThrowing(AuctionObserver observer) {
        AuctionEvent event = new AuctionEvent(
                AuctionEventType.BID_PLACED, runningAuction, bidder, 1_000_000L);
        assertDoesNotThrow(() -> observer.onBidPlaced(event));
    }

    @ParameterizedTest
    @MethodSource("allConcreteObservers")
    void onBidPlaced_nullBidder_safe(AuctionObserver observer) {
        AuctionEvent event = new AuctionEvent(
                AuctionEventType.BID_PLACED, runningAuction, null, 1_000_000L);
        assertDoesNotThrow(() -> observer.onBidPlaced(event));
    }

    @ParameterizedTest
    @MethodSource("allConcreteObservers")
    void onAuctionEnded_lifecycle_nonThrowing(AuctionObserver observer) {
        assertDoesNotThrow(() -> observer.onAuctionEnded(
                new AuctionEvent(AuctionEventType.AUCTION_STARTED, runningAuction, null, 0)));
        assertDoesNotThrow(() -> observer.onAuctionEnded(
                new AuctionEvent(AuctionEventType.AUCTION_ENDED, runningAuction, null, 0)));
    }

    @Test
    void observerIsolation_twoInstancesIndependent() {
        BidderObserver a = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
        BidderObserver b = new BidderObserver(
                TestFixture.bidderWithBalance("contractBid2", 5_000_000L),
                TestFixture.ratingServiceAllowAll());
        assertDoesNotThrow(() -> a.onBidPlaced(new AuctionEvent(
                AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L)));
        assertDoesNotThrow(() -> b.onAuctionEnded(new AuctionEvent(
                AuctionEventType.AUCTION_STARTED, runningAuction, null, 0)));
    }

    @Test
    void onBidPlaced_doesNotMutateAuction() {
        long priceBefore = runningAuction.getCurrentPrice();
        new SellerObserver(seller, TestFixture.ratingServiceAllowAll())
                .onBidPlaced(new AuctionEvent(
                        AuctionEventType.BID_PLACED, runningAuction, bidder, 9_999_999L));
        assertEquals(priceBefore, runningAuction.getCurrentPrice());
    }

    @Test
    void bidderObserver_notifiesViaNotifier() {
        List<String> titles = new ArrayList<>();
        BidderObserver observer = new BidderObserver(bidder, TestFixture.ratingServiceAllowAll());
        observer.setNotifier((target, title, message) -> titles.add(title));

        observer.onBidPlaced(new AuctionEvent(
                AuctionEventType.BID_PLACED, runningAuction, bidder, 2_000_000L));
        observer.onBidPlaced(new AuctionEvent(
                AuctionEventType.BID_RESERVE_NOT_MET, runningAuction, bidder, 1_100_000L));

        assertThat(titles).contains("BID_PLACED", "BID_RESERVE_NOT_MET");
    }
}
