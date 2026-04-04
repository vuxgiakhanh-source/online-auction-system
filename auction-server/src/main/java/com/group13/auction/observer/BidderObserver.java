package com.group13.auction.observer;

import com.group13.auction.model.user.Bidder;

/**
 * Observer dành cho Bidder — nhận toàn bộ thông báo phiên đấu giá.
 */
public class BidderObserver implements AuctionObserver {

  private final Bidder bidder;

  /**
   * Khởi tạo BidderObserver.
   *
   * @param bidder bidder được theo dõi
   */
  public BidderObserver(Bidder bidder) {
    this.bidder = bidder;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() != AuctionEvent.AuctionEventType.BID_PLACED) {
      return;
    }
    if (event.getBidder() == this.bidder) {
      System.out.printf("[NOTIFY → %s] Bạn đang dẫn đầu với giá %.0f%n",
          bidder.getUsername(), event.getBidAmount());
    } else {
      System.out.printf("[NOTIFY → %s] Có người vừa trả %.0f — bạn bị vượt qua!%n",
          bidder.getUsername(), event.getBidAmount());
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    // Xử lý đầy đủ tất cả loại event (lỗi #18)
    switch (event.getEventType()) {
      case AUCTION_UPCOMING:
        System.out.printf("[NOTIFY → %s] Phiên sắp bắt đầu trong ít phút!%n",
            bidder.getUsername());
        break;
      case AUCTION_STARTED:
        System.out.printf("[NOTIFY → %s] Phiên đấu giá vừa bắt đầu!%n",
            bidder.getUsername());
        break;
      case AUCTION_ENDED:
        if (event.getBidder() == this.bidder) {
          System.out.printf(
              "[NOTIFY → %s] Chúc mừng! Bạn thắng với giá %.0f. Hãy thanh toán trong 24h!%n",
              bidder.getUsername(), event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → %s] Rất tiếc, bạn đã thua phiên này.%n",
              bidder.getUsername());
        }
        break;
      case PAYMENT_COMPLETED:
        System.out.printf("[NOTIFY → %s] Giao dịch hoàn tất thành công!%n",
            bidder.getUsername());
        break;
      case AUCTION_CANCELED:
        System.out.printf("[NOTIFY → %s] Phiên đấu giá đã bị huỷ.%n",
            bidder.getUsername());
        break;
      default:
        break;
    }
  }
}