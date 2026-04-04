package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;

/**
 * Interface Strategy — định nghĩa cách kiểm tra bid hợp lệ.
 */
public interface BidStrategy {

  /**
   * Kiểm tra bid có hợp lệ không.
   *
   * @param auction phiên đấu giá
   * @param amount  số tiền muốn đặt
   * @return true nếu hợp lệ
   */
  boolean isValidBid(Auction auction, double amount);

  /**
   * Mô tả strategy này để hiển thị cho người dùng.
   *
   * @return mô tả ngắn
   */
  String describe();
}