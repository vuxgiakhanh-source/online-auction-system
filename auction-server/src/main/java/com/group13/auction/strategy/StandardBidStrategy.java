package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá thông thường.
 * Bid hợp lệ khi amount >= currentPrice + minIncrement.
 * Dùng khi: muốn tự kiểm soát từng lần đặt giá.
 *
 * <p>minIncrement được tính tự động theo ngưỡng giá hiện tại
 * bởi {@link BidIncrementCalculator}.
 */
public class StandardBidStrategy implements BidStrategy {

  /**
   * Khởi tạo StandardBidStrategy.
   * minIncrement được tính động tại thời điểm validate bid.
   */
  public StandardBidStrategy() {}

  @Override
  public boolean isValidBid(Auction auction, long amount) {
    long increment = BidIncrementCalculator.calculate(auction.getCurrentPrice());
    return amount >= auction.getCurrentPrice() + increment;
  }

  @Override
  public String describe() {
    return "Standard: Đặt thủ công, bước giá tối thiểu theo ngưỡng giá hiện tại"
            + " (< 1tr: 50k | 1-10tr: 200k | > 10tr: 500k).";
  }

  /**
   * Lấy bước giá tối thiểu tại mức giá đã cho.
   *
   * @param currentPrice giá hiện tại để tính increment
   * @return bước giá tối thiểu
   */
  public long getMinIncrement(long currentPrice) {
    return BidIncrementCalculator.calculate(currentPrice);
  }
}