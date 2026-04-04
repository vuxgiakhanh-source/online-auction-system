package com.group13.auction.observer;

/**
 * Interface Observer — lắng nghe mọi sự kiện từ Auction.
 * Mỗi lớp implement tự quyết định phản ứng thế nào.
 */
public interface AuctionObserver {

  /**
   * Gọi khi có bid mới trong phiên.
   *
   * @param event thông tin sự kiện
   */
  void onBidPlaced(AuctionEvent event);

  /**
   * Gọi khi có thay đổi trạng thái auction (lỗi #18).
   * Bao gồm: STARTED, ENDED, PAID, CANCELED, UPCOMING.
   *
   * @param event thông tin sự kiện
   */
  void onAuctionEnded(AuctionEvent event);
}