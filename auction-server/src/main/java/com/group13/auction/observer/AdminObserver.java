package com.group13.auction.observer;

import com.group13.auction.model.user.Admin;

/**
 * Observer dành cho Admin — ghi log và giám sát toàn hệ thống.
 */
public class AdminObserver implements AuctionObserver {

  private final Admin admin;

  /**
   * Khởi tạo AdminObserver.
   *
   * @param admin admin được theo dõi
   */
  public AdminObserver(Admin admin) {
    this.admin = admin;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() != AuctionEvent.AuctionEventType.BID_PLACED) {
      return;
    }
    System.out.printf("[LOG - Admin %s] Bid mới: %s đặt %.0f | Phiên: %s%n",
        admin.getUsername(),
        event.getBidder().getUsername(),
        event.getBidAmount(),
        event.getAuction().getId());
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        System.out.printf("[LOG - Admin %s] Phiên bắt đầu: %s%n",
            admin.getUsername(), event.getAuction().getId());
        break;
      case AUCTION_ENDED:
        System.out.printf("[LOG - Admin %s] Phiên kết thúc: %s | Winner: %s%n",
            admin.getUsername(),
            event.getAuction().getId(),
            event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
        break;
      case PAYMENT_COMPLETED:
        System.out.printf("[LOG - Admin %s] Thanh toán thành công: phiên %s%n",
            admin.getUsername(), event.getAuction().getId());
        break;
      case AUCTION_CANCELED:
        System.out.printf("[LOG - Admin %s] Phiên bị huỷ: %s%n",
            admin.getUsername(), event.getAuction().getId());
        break;
      default:
        break;
    }
  }
}