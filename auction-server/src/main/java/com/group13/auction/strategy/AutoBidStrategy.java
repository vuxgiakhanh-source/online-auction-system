package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá tự động - thông minh hơn Standard.
 *
 * <p>hệ thống chỉ bid mức TỐI THIỂU cần thiết để vượt
 * người đang dẫn đầu.
 *
 * <p><b>Cơ chế vượt leader:</b>
 * Khi user đặt maxBid thấp hơn giá hiện tại (currentPrice), hệ thống
 * tự động tính mức bid cần thiết để vượt người dẫn đầu:
 * {@code nextBid = currentPrice + minIncrement}.
 * Nếu nextBid > maxBid thì không tự bid nữa (trả về -1).
 *
 * <p>Dùng khi: muốn hệ thống tự đặt giá thay mình, chỉ cần đặt maxBid.
 */
public class AutoBidStrategy implements BidStrategy {

  private final long maxBid;

  /**
   * Khởi tạo AutoBidStrategy với lượng "nhỉnh hơn" tuỳ chỉnh.
   *
   * <p>Ví dụ: currentPrice = 130, minIncrement = 20, extraIncrement = 1
   * → nextBid = 130 + 20 + 1 = 151 (nhỉnh hơn người dẫn đầu 21 đơn vị).
   *
   * @param maxBid         giá tối đa sẵn sàng trả (> 0)
   * @throws IllegalArgumentException nếu maxBid <= 0 hoặc extraIncrement < 0
   */
  public AutoBidStrategy(long maxBid) {
    if (maxBid <= 0) {
      throw new IllegalArgumentException("maxBid phải lớn hơn 0.");
    }
    this.maxBid = maxBid;
  }

  @Override
  public boolean isValidBid(Auction auction, long amount) {
    long increment = BidIncrementCalculator.calculate(auction.getCurrentPrice());
    return amount <= maxBid && amount >= auction.getCurrentPrice() + increment;
  }

  /**
   * Tính số tiền tối thiểu cần đặt để vượt người đang dẫn đầu.
   *
   * <p>Hệ thống tính dựa trên {@code currentPrice} (giá người đang
   * dẫn đầu), không phải giá user đã nhập.
   *
   * @param auction phiên đấu giá
   * @return mức giá cần đặt để vượt leader, hoặc -1 nếu vượt maxBid
   */
  public long calculateNextBid(Auction auction) {
    long increment = BidIncrementCalculator.calculate(auction.getCurrentPrice());
    long next = auction.getCurrentPrice() + increment;
    if (next > maxBid) {
      return -1; // vượt quá maxBid --- không tự bid nữa
    }
    return next;
  }

  @Override
  public String describe() {
    return String.format(
            "Auto: Tự bid vượt người dẫn đầu (bước giá tự động theo ngưỡng"
                    + "), tối đa %d.", maxBid);
  }

  public long getMaxBid() { return maxBid; }

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