package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá với giá sàn bí mật.
 * Đây là strategy BẮT BUỘC được nhúng vào Auction khi tạo.
 * Seller phải thiết lập reservePrice ngay khi tạo auction.
 *
 * <p>Lưu ý: strategy này chỉ validate số tiền bid (phải >= currentPrice + minIncrement).
 * Việc so sánh với reservePrice được BidService xử lý để tạo thông báo "reserve not met".
 */
public class ReservePriceStrategy implements BidStrategy {

  private final double reservePrice;
  private final double minIncrement;

  /**
   * Khởi tạo ReservePriceStrategy.
   *
   * @param reservePrice giá sàn bí mật của Seller (> 0)
   * @param minIncrement bước giá tối thiểu (> 0)
   */
  public ReservePriceStrategy(double reservePrice, double minIncrement) {
    if (reservePrice <= 0 || minIncrement <= 0) {
      throw new IllegalArgumentException(
              "reservePrice và minIncrement phải lớn hơn 0.");
    }
    this.reservePrice = reservePrice;
    this.minIncrement = minIncrement;
  }

  /**
   * Validate bid hợp lệ về số tiền (>= currentPrice + minIncrement).
   * Reserve price được kiểm tra riêng ở BidService.
   */
  @Override
  public boolean isValidBid(Auction auction, double amount) {
    return amount >= auction.getCurrentPrice() + minIncrement;
  }

  @Override
  public String describe() {
    return String.format(
            "Reserve: Giá sàn bí mật đã được thiết lập. Mỗi bid phải cao hơn ít nhất %.0f.",
            minIncrement);
  }

  public double getReservePrice()  { return reservePrice; }
  public double getMinIncrement()  { return minIncrement; }
}