package com.group13.auction.unit.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regression: UPDATE_AUTO_BID dùng cùng quy tắc với REGISTER (giá + bước), không so max cũ. */
@DisplayName("Update auto-bid — validation theo giá hiện tại")
class UpdateAutoBidStrategyValidationTest {

  private static final long STARTING = 500_000L;

  @Test
  @DisplayName("maxBid mới thấp hơn max cũ nhưng vẫn đủ counter → calculateNextBid >= 0")
  void lowerMaxBidThanPrevious_stillValidWhenPriceAllows() {
    NormalUser seller = TestFixture.normalSeller("seller_uab");
    NormalUser bidder = TestFixture.bidderWithBalance("bidder_uab", 20_000_000L);
    Auction auction = TestFixture.runningAuction(seller, STARTING);

    long oldMaxBid = STARTING + 200_000L;
    long newMaxBid = STARTING + 150_000L;
    AutoBidRegistry.getInstance().register(bidder.getId(), auction.getId(), oldMaxBid);

    AutoBidStrategy strategy = new AutoBidStrategy(newMaxBid);
  assertThat(strategy.calculateNextBid(auction))
        .as("maxBid mới vẫn phải đủ để đặt bid đầu tiên khi chưa có ai bid")
        .isGreaterThanOrEqualTo(0);

    AutoBidRegistry.getInstance().cancel(bidder.getId(), auction.getId());
  }

  @Test
  @DisplayName("maxBid không đủ theo giá hiện tại → calculateNextBid = -1")
  void maxBidTooLowForCurrentPrice_rejected() {
    NormalUser seller = TestFixture.normalSeller("seller_uab2");
    Auction auction = TestFixture.runningAuction(seller, STARTING);
    auction.updateBid(STARTING + 500_000L, TestFixture.bidderWithBalance("leader", 20_000_000L));

    AutoBidStrategy strategy = new AutoBidStrategy(STARTING + 100_000L);
    assertThat(strategy.calculateNextBid(auction)).isEqualTo(-1);
  }
}
