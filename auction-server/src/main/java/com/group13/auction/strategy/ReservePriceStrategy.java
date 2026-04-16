package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá với giá sàn bí mật.
 * Đây là strategy được kèm vào Auction khi tạo.
 * Seller phải thiết lập reservePrice ngay khi tạo auction.
 *
 * <p>Validate số tiền bid (phải >= currentPrice + minIncrement).
 *
 * <p>minIncrement được tính tự động theo ngưỡng giá hiện tại
 * bởi {@link BidIncrementCalculator}.
 */
public class ReservePriceStrategy implements BidStrategy {

  private final double reservePrice;

  /**
   * Khởi tạo ReservePriceStrategy.
   * minIncrement được tính động tại thời điểm validate bid.
   *
   * @param reservePrice giá sàn bí mật của Seller (> 0)
   */
  public ReservePriceStrategy(double reservePrice) {
    if (reservePrice <= 0) {
      throw new IllegalArgumentException("reservePrice phải lớn hơn 0.");
    }
    this.reservePrice = reservePrice;
  }

  /**
   * Validate bid hợp lệ về số tiền (>= currentPrice + minIncrement).
   * Reserve price được kiểm tra riêng ở BidService.
   */
  @Override
  public boolean isValidBid(Auction auction, double amount) {
    double increment = BidIncrementCalculator.calculate(auction.getCurrentPrice());
    return amount >= auction.getCurrentPrice() + increment;
  }

  @Override
  public String describe() {
    return String.format(
            "Reserve: Giá sàn bí mật đã được thiết lập (BÍ MẬT). "
                    + "Bước giá tối thiểu theo ngưỡng giá hiện tại.");
  }

  public double getReservePrice() { return reservePrice; }

  /**
   * Lấy bước giá tối thiểu tại mức giá đã cho.
   *
   * @param currentPrice giá hiện tại để tính increment
   * @return bước giá tối thiểu
   */
  public double getMinIncrement(double currentPrice) {
    return BidIncrementCalculator.calculate(currentPrice);
  }
}