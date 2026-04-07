package com.group13.auction.observer;

/** Observer interface — nhận notify về sự kiện phiên đấu giá. */
public interface AuctionObserver {

  /**
   * Được gọi khi có bid mới được đặt.
   *
   * @param event sự kiện bid
   */
  void onBidPlaced(AuctionEvent event);

  /**
   * Được gọi khi phiên kết thúc hoặc có sự kiện hệ thống.
   *
   * @param event sự kiện kết thúc / hệ thống
   */
  void onAuctionEnded(AuctionEvent event);
}