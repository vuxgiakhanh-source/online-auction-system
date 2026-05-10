package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;

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
      System.out.printf("[THÔNG BÁO tới Bidder %s] Bid mới: %s đặt %d | Phiên: %s%n",
              bidder.getUsername(),
              event.getBidder() != null ? event.getBidder().getUsername() : "?",
              event.getBidAmount(),
              event.getAuction().getId());
    } else if (event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      System.out.printf("[THÔNG BÁO tới Bidder %s] Bid %d chưa đạt reserve price.%n",
              bidder.getUsername(), event.getBidAmount());
      // TODO: notificationDao.save()
    }

  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        System.out.printf("[THÔNG BÁO tới Bidder %s] Phiên đã bắt đầu!%n", bidder.getUsername());
        // TODO: notificationDao.save()
        break;

      case AUCTION_EXTENDED:
        System.out.printf("[THÔNG BÁO tới Bidder %s] Phiên %s được gia hạn. EndTime mới: %s. %s%n",
                bidder.getUsername(),
                event.getAuction().getId(),
                event.getAuction().getEndTime(),
                event.getMessage() != null ? event.getMessage() : "");
        break;

      case AUCTION_UPCOMING:
        // Chưa done, đang trong quá trình hoàn thiện
        System.out.printf("[THÔNG BÁO tới Bidder %s] Phiên sắp bắt đầu — chuẩn bị sẵn sàng.%n",
                bidder.getUsername());
        // TODO: notificationDao.save()
        break;

      case AUCTION_ENDED:
        if (event.getBidder() != null
                && event.getBidder().getUsername().equals(bidder.getUsername())) {
          System.out.printf("[THÔNG BÁO tới Bidder %s] Chúc mừng! Bạn thắng phiên với giá %d. Hãy thanh toán trong 24h.%n",
                  bidder.getUsername(), event.getBidAmount());
          // TODO: notificationDao.save()
        } else {
          System.out.printf("[THÔNG BÁO tới Bidder %s] Phiên kết thúc. Winner: %s.%n",
                  bidder.getUsername(),
                  event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
          // TODO: notificationDao.save()
        }
        break;

      case AUCTION_NO_WINNER:
        System.out.printf(
                "[THÔNG BÁO tới Bidder %s] Phiên kết thúc không có ai đặt giá."
                        + " Cọc sẽ được hoàn trả.%n",
                bidder.getUsername());
        // TODO: notificationDao.save()
        break;

      case RESERVE_NOT_MET_CLOSED:
        System.out.printf(
                "[THÔNG BÁO tới Bidder %s] Phiên kết thúc — giá cao nhất %d"
                        + " chưa đạt reserve. Cọc sẽ được hoàn trả.%n",
                bidder.getUsername(), event.getBidAmount());
        // TODO: notificationDao.save()
        break;

      case AUCTION_CANCELED:
        System.out.printf("[THÔNG BÁO tới Bidder %s] Phiên đấu giá đã bị hủy. Cọc sẽ được hoàn trả.%n",
                bidder.getUsername());
        // TODO: notificationDao.save()
        break;

      default:
        break;
    }
  }
}