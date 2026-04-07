package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.serviceInterface.IRatingService;

/**
 * Observer dành cho Bidder — nhận notify về bid và kết quả phiên.
 */
public class BidderObserver implements AuctionObserver {

  private final NormalUser bidder;
  private final IRatingService ratingService;

  /**
   * Khởi tạo BidderObserver.
   *
   * @param bidder bidder được theo dõi
   * @param ratingService dùng để thưởng rating sau khi thanh toán
   */
  public BidderObserver(NormalUser bidder, IRatingService ratingService) {
    this.bidder = bidder;
    this.ratingService = ratingService;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() == AuctionEvent.AuctionEventType.BID_PLACED) {
      System.out.printf("[NOTIFY → Bidder %s] Bid mới: %s đặt %.0f | Phiên: %s%n",
              bidder.getUsername(),
              event.getBidder() != null ? event.getBidder().getUsername() : "?",
              event.getBidAmount(),
              event.getAuction().getId());
    } else if (event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      System.out.printf("[NOTIFY → Bidder %s] Bid %.0f chưa đạt reserve price.%n",
              bidder.getUsername(), event.getBidAmount());
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        System.out.printf("[NOTIFY → Bidder %s] Phiên đã bắt đầu!%n", bidder.getUsername());
        break;
      case AUCTION_UPCOMING:
        System.out.printf("[NOTIFY → Bidder %s] Phiên sắp bắt đầu — chuẩn bị sẵn sàng.%n",
                bidder.getUsername());
        break;
      case AUCTION_ENDED:
        if (event.getBidder() != null
                && event.getBidder().getUsername().equals(bidder.getUsername())) {
          System.out.printf("[NOTIFY → Bidder %s] Chúc mừng! Bạn thắng phiên với giá %.0f. Hãy thanh toán trong 24h.%n",
                  bidder.getUsername(), event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → Bidder %s] Phiên kết thúc. Winner: %s.%n",
                  bidder.getUsername(),
                  event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
        }
        break;
      case AUCTION_CANCELED:
        System.out.printf("[NOTIFY → Bidder %s] Phiên đấu giá đã bị hủy. Cọc sẽ được hoàn trả.%n",
                bidder.getUsername());
        break;
      case SECOND_CHANCE_OFFERED:
        System.out.printf("[NOTIFY → Bidder %s] Bạn nhận được Second Chance Offer với giá %.0f! Có 24h để quyết định.%n",
                bidder.getUsername(), event.getBidAmount());
        break;
      case PAYMENT_COMPLETED:
        System.out.printf("[NOTIFY → Bidder %s] Thanh toán thành công!%n", bidder.getUsername());
        ratingService.rewardBidder(bidder);
        break;
      default:
        break;
    }
  }
}