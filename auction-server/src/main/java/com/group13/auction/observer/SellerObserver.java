package com.group13.auction.observer;

import com.group13.auction.model.user.Seller;

/**
 * Observer dành cho Seller (lỗi #20 — cần thiết).
 * Nhận thông báo khi có bid mới và khi phiên kết thúc.
 * Đặc biệt thông báo khi giao dịch thành công (lỗi #21).
 */
public class SellerObserver implements AuctionObserver {

  private final Seller seller;

  /**
   * Khởi tạo SellerObserver.
   *
   * @param seller seller được theo dõi
   */
  public SellerObserver(Seller seller) {
    this.seller = seller;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() != AuctionEvent.AuctionEventType.BID_PLACED) {
      return;
    }
    System.out.printf(
        "[NOTIFY → Seller %s] Có người vừa trả %.0f cho sản phẩm \"%s\"%n",
        seller.getUsername(),
        event.getBidAmount(),
        event.getAuction().getItem().getName());
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        System.out.printf("[NOTIFY → Seller %s] Phiên đấu giá \"%s\" vừa bắt đầu!%n",
            seller.getUsername(),
            event.getAuction().getItem().getName());
        break;
      case AUCTION_ENDED:
        if (event.getBidder() != null) {
          System.out.printf(
              "[NOTIFY → Seller %s] Phiên kết thúc. Winner: %s | Giá: %.0f. Chờ thanh toán.%n",
              seller.getUsername(),
              event.getBidder().getUsername(),
              event.getBidAmount());
        } else {
          System.out.printf(
              "[NOTIFY → Seller %s] Phiên kết thúc mà không có người đặt giá.%n",
              seller.getUsername());
        }
        break;
      case PAYMENT_COMPLETED:
        // Thông báo seller đã bán thành công (lỗi #21)
        System.out.printf(
            "[NOTIFY → Seller %s] Sản phẩm \"%s\" đã được bán thành công với giá %.0f!%n",
            seller.getUsername(),
            event.getAuction().getItem().getName(),
            event.getBidAmount());
        seller.increaseRating(0.3); // thưởng rating khi bán thành công
        break;
      case AUCTION_CANCELED:
        System.out.printf("[NOTIFY → Seller %s] Phiên đấu giá đã bị Admin huỷ.%n",
            seller.getUsername());
        break;
      default:
        break;
    }
  }
}