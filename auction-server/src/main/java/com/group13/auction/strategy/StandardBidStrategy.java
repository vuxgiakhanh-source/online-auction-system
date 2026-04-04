package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá thông thường.
 * Bid hợp lệ khi amount >= currentPrice + minIncrement.
 * Dùng khi: muốn tự kiểm soát từng lần đặt giá (lỗi #23).
 */
public class StandardBidStrategy implements BidStrategy {

  private final double minIncrement;

  /**
   * Khởi tạo StandardBidStrategy.
   *
   * @param minIncrement bước giá tối thiểu (> 0)
   */
  public StandardBidStrategy(double minIncrement) {
    if (minIncrement <= 0) {
      throw new IllegalArgumentException("minIncrement phải lớn hơn 0.");
    }
    this.minIncrement = minIncrement;
  }

  @Override
  public boolean isValidBid(Auction auction, double amount) {
    return amount >= auction.getCurrentPrice() + minIncrement;
  }

  @Override
  public String describe() {
    return String.format(
        "Standard: Đặt thủ công, mỗi lần phải cao hơn ít nhất %.0f", minIncrement);
  }

  public double getMinIncrement() {
    return minIncrement;
  }
}