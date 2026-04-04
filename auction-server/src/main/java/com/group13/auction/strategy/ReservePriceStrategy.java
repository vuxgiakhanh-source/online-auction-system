package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Kiểu đặt giá với giá sàn bí mật.
 * Bid chỉ hợp lệ khi vượt reservePrice.
 * Dùng khi: seller muốn đảm bảo sản phẩm không bán dưới giá sàn (lỗi #23).
 */
public class ReservePriceStrategy implements BidStrategy {

  private final double reservePrice;
  private final double minIncrement;

  /**
   * Khởi tạo ReservePriceStrategy.
   *
   * @param reservePrice giá sàn bí mật của Seller
   * @param minIncrement bước giá tối thiểu
   */
  public ReservePriceStrategy(double reservePrice, double minIncrement) {
    if (reservePrice <= 0 || minIncrement <= 0) {
      throw new IllegalArgumentException(
          "reservePrice và minIncrement phải lớn hơn 0.");
    }
    this.reservePrice = reservePrice;
    this.minIncrement = minIncrement;
  }

  @Override
  public boolean isValidBid(Auction auction, double amount) {
    return amount >= reservePrice
        && amount >= auction.getCurrentPrice() + minIncrement;
  }

  @Override
  public String describe() {
    return "Reserve: Bid chỉ hợp lệ khi vượt giá sàn bí mật của Seller.";
  }

  public double getReservePrice() {
    return reservePrice;
  }
}