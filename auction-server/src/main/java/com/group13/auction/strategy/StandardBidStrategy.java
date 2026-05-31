package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Đặt giá thường: amount phải >= giá hiện tại + bước giá tối thiểu. */
public class StandardBidStrategy implements BidStrategy {

  private static final Logger log = LoggerFactory.getLogger(StandardBidStrategy.class);

  // Cache bước giá theo tier để validate nhanh hơn.
  private static final long TIER_LOW = 1_000_000L;
  private static final long TIER_MID = 10_000_000L;
  private static final long INCREMENT_LOW = 50_000L;
  private static final long INCREMENT_MID = 200_000L;
  private static final long INCREMENT_HIGH = 500_000L;

  public StandardBidStrategy() {}

  /**
   * Kiểm tra bid hợp lệ: amount >= currentPrice + minIncrement.
   *
   * <p>FIX #4: Inline tier logic thay vì gọi BidIncrementCalculator.calculate() → tránh method
   * dispatch overhead trong hot path (gọi hàng nghìn lần/giây). Tính minBid một lần duy nhất, so
   * sánh trực tiếp.
   */
  @Override
  public boolean isValidBid(Auction auction, long amount) {
    long currentPrice = auction.getCurrentPrice();

    // FIX #4: inline increment tính trực tiếp, không gọi qua BidIncrementCalculator
    long increment;
    if (currentPrice < TIER_LOW) {
      increment = INCREMENT_LOW;
    } else if (currentPrice <= TIER_MID) {
      increment = INCREMENT_MID;
    } else {
      increment = INCREMENT_HIGH;
    }

    long minBid = currentPrice + increment;
    boolean valid = amount >= minBid;

    // FIX #1: Chỉ log khi reject (WARN), không log khi accept
    // → loại bỏ hàng triệu dòng log/s trong load test
    if (!valid) {
      log.warn(
          "Bid rejected by strategy: auctionId={}, amount={}, currentPrice={}, minBid={}",
          auction.getId(),
          amount,
          currentPrice,
          minBid);
    }
    return valid;
  }

  @Override
  public String describe() {
    return "Standard: Đặt thủ công, bước giá tối thiểu theo ngưỡng giá hiện tại"
        + " (< 1tr: 50k | 1-10tr: 200k | > 10tr: 500k).";
  }

  /**
   * Lấy bước giá tối thiểu tại mức giá đã cho. Giữ nguyên để tương thích với các caller bên ngoài.
   */
  public long getMinIncrement(long currentPrice) {
    return BidIncrementCalculator.calculate(currentPrice);
  }
}
