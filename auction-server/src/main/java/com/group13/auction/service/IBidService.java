package com.group13.auction.service;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.strategy.BidStrategy;

/**
 * Hợp đồng xử lý đặt giá: join, watch, placeBid.
 */
public interface IBidService {

  /**
   * Bidder tham gia phiên đấu giá.
   * Tự động vào watchList và addObserver.
   * Yêu cầu: rating >= 2.0 và số dư đủ cọc (30% giá khởi điểm).
   *
   * @param bidder   bidder muốn tham gia
   * @param auction  phiên muốn tham gia
   * @param observer observer của bidder để nhận notify
   */
  void joinAuction(NormalUser bidder, Auction auction, AuctionObserver observer);

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   *
   * @param bidder   bidder muốn theo dõi
   * @param auction  phiên muốn theo dõi
   * @param observer observer để nhận notify
   */
  void watchAuction(NormalUser bidder, Auction auction, AuctionObserver observer);

  /**
   * Đặt giá cho một phiên đấu giá.
   *
   * @param bidder   người đặt giá
   * @param auction  phiên đấu giá
   * @param amount   số tiền đặt
   * @param strategy strategy kiểm tra tính hợp lệ
   */
  void placeBid(NormalUser bidder, Auction auction,
                double amount, BidStrategy strategy);
}