package com.group13.auction.unit.auction;

import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.unit.TestFixture;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Auction — bid & timing rules")
class AuctionTest {

  private NormalUser seller;
  private NormalUser bidder;

  @BeforeEach
  void setUp() {
    seller = TestFixture.normalSeller("sellerAA1");
    bidder = TestFixture.bidderWithBalance("bidderBB1", 10_000_000L);
  }

  @Test
  void updateBid_setsPriceAndLeader() {
    Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
    auction.updateBid(1_500_000L, bidder);
    assertEquals(1_500_000L, auction.getCurrentPrice());
    assertSame(bidder, auction.getCurrentLeader());
  }

  @Test
  void isReserveMet_whenAtOrAboveReserve() {
    Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
    assertFalse(auction.isReserveMet());
    auction.updateBid(auction.getReservePrice(), bidder);
    assertTrue(auction.isReserveMet());
  }

  @Test
  void extendEndTime_addsDuration() {
    Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
    LocalDateTime before = auction.getEndTime();
    auction.extendEndTime(Duration.ofMinutes(5));
    assertTrue(auction.getEndTime().isAfter(before));
  }

  @Test
  void extendEndTime_invalidExtension_throws() {
    Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
    assertThrows(IllegalArgumentException.class, () -> auction.extendEndTime(Duration.ZERO));
  }

  @Test
  void incrementViewerCount_increases() {
    Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
    int before = auction.getViewerCount();
    auction.incrementViewerCount();
    assertEquals(before + 1, auction.getViewerCount());
  }
}
