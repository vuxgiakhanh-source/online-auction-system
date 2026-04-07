package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;

/** Observer dành cho Bidder/NormalUser — nhận toàn bộ thông báo phiên đấu giá. */
public class BidderObserver implements AuctionObserver {

  private final NormalUser user;

  /**
   * Khởi tạo BidderObserver.
   *
   * @param user người dùng được theo dõi
   */
  public BidderObserver(NormalUser user) {
    this.user = user;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    switch (event.getEventType()) {
      case BID_PLACED:
        if (event.getBidder() == this.user) {
          System.out.printf("[NOTIFY → %s] Bạn đang dẫn đầu với giá %.0f%n",
                  user.getUsername(), event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → %s] Có người vừa trả %.0f — bạn bị vượt qua!%n",
                  user.getUsername(), event.getBidAmount());
        }
        break;
      case BID_RESERVE_NOT_MET:
        if (event.getBidder() == this.user) {
          System.out.printf("[NOTIFY → %s] Bid %.0f được chấp nhận nhưng chưa đạt mức giá tối thiểu của người bán (Reserve not met).%n",
                  user.getUsername(), event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → %s] Có bid mới %.0f nhưng chưa đạt giá sàn.%n",
                  user.getUsername(), event.getBidAmount());
        }
        break;
      default:
        break;
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_UPCOMING:
        System.out.printf("[NOTIFY → %s] Phiên sắp bắt đầu trong ít phút!%n",
                user.getUsername());
        break;
      case AUCTION_STARTED:
        System.out.printf("[NOTIFY → %s] Phiên đấu giá vừa bắt đầu!%n",
                user.getUsername());
        break;
      case AUCTION_ENDED:
        if (event.getBidder() == this.user) {
          System.out.printf("[NOTIFY → %s] Chúc mừng! Bạn thắng với giá %.0f. Hãy thanh toán phần còn lại trong 24h!%n",
                  user.getUsername(), event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → %s] Rất tiếc, bạn đã thua phiên này.%n",
                  user.getUsername());
        }
        break;
      case RESERVE_NOT_MET_CLOSED:
        if (event.getBidder() == this.user) {
          // Thông báo riêng cho winner (người bid cao nhất)
          System.out.printf("[NOTIFY → %s] Phiên đấu giá kết thúc. Tuy bạn trả cao nhất (%.0f) nhưng chưa đạt mức giá tối thiểu của người bán. Giao dịch không thực hiện.%n",
                  user.getUsername(), event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → %s] Phiên đấu giá kết thúc với mức giá cao nhất là %.0f nhưng chưa đạt mức giá tối thiểu.%n",
                  user.getUsername(), event.getBidAmount());
        }
        break;
      case AUCTION_NO_WINNER:
        System.out.printf("[NOTIFY → %s] Phiên đấu giá kết thúc không có người tham gia.%n",
                user.getUsername());
        break;
      case PAYMENT_COMPLETED:
        System.out.printf("[NOTIFY → %s] Giao dịch hoàn tất thành công!%n",
                user.getUsername());
        break;
      case AUCTION_CANCELED:
        System.out.printf("[NOTIFY → %s] Phiên đấu giá đã bị huỷ.%n",
                user.getUsername());
        break;
      case SECOND_CHANCE_OFFERED:
        System.out.printf("[NOTIFY → %s] Bạn có cơ hội mua thứ cấp với giá %.0f. Quyết định trong 24h (không mua sẽ không bị trừ điểm).%n",
                user.getUsername(), event.getBidAmount());
        break;
      default:
        break;
    }
  }
}