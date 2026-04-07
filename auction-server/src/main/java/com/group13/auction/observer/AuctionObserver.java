package com.group13.auction.observer;

/** Interface Observer — lắng nghe mọi sự kiện từ Auction. */
public interface AuctionObserver {

  /**
   * Gọi khi có bid mới trong phiên.
   *
   * @param event thông tin sự kiện
   */
  void onBidPlaced(AuctionEvent event);

  /**
   * Gọi khi có thay đổi trạng thái auction.
   * Bao gồm: STARTED, ENDED, PAID, CANCELED, UPCOMING, RESERVE_NOT_MET, NO_WINNER.
   *
   * @param event thông tin sự kiện
   */
  void onAuctionEnded(AuctionEvent event);
}