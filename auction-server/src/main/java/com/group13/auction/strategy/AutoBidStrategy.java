package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá tự động — thông minh hơn Standard.
 *
 * <p>Logic đúng: hệ thống chỉ bid mức TỐI THIỂU cần thiết để vượt
 * người trả cao thứ hai, không phải tăng một khoảng cố định mỗi lần.
 * Điều này bảo toàn ngân sách tối đa cho người dùng.
 *
 * <p>Dùng khi: muốn hệ thống tự đặt giá thay mình, chỉ cần đặt maxBid.
 */
public class AutoBidStrategy implements BidStrategy {

  private final double maxBid;
  private final double minIncrement;

  /**
   * Khởi tạo AutoBidStrategy.
   *
   * @param maxBid       giá tối đa sẵn sàng trả
   * @param minIncrement bước giá tối thiểu mỗi lần tự động bid
   */
  public AutoBidStrategy(double maxBid, double minIncrement) {
    if (maxBid <= 0 || minIncrement <= 0) {
      throw new IllegalArgumentException("maxBid và minIncrement phải lớn hơn 0.");
    }
    this.maxBid       = maxBid;
    this.minIncrement = minIncrement;
  }

  @Override
  public boolean isValidBid(Auction auction, double amount) {
    return amount <= maxBid && amount >= auction.getCurrentPrice() + minIncrement;
  }

  /**
   * Tính số tiền tối thiểu cần đặt để vượt giá hiện tại.
   * Hệ thống dùng mức tối thiểu, không tăng cố định.
   *
   * @param auction phiên đấu giá
   * @return mức giá tối thiểu cần đặt, hoặc -1 nếu vượt maxBid
   */
  public double calculateNextBid(Auction auction) {
    double next = auction.getCurrentPrice() + minIncrement;
    if (next > maxBid) {
      return -1; // vượt quá maxBid — không tự bid nữa
    }
    return next;
  }

  @Override
  public String describe() {
    return String.format(
            "Auto: Tự bid mức tối thiểu vượt đối thủ, tối đa %.0f", maxBid);
  }

  public double getMaxBid()      { return maxBid; }
  public double getMinIncrement() { return minIncrement; }
}