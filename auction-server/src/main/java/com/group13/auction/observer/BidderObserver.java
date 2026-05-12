package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer dành cho Bidder — nhận notify về bid và kết quả phiên.
 */
public class BidderObserver implements AuctionObserver {

  // 1. Khai báo Logger (Sử dụng SLF4J)
  private static final Logger logger = LoggerFactory.getLogger(BidderObserver.class);

  private final NormalUser bidder;
  private final IRatingService ratingService;

  public BidderObserver(NormalUser bidder, IRatingService ratingService) {
    this.bidder = bidder;
    this.ratingService = ratingService;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() == AuctionEvent.AuctionEventType.BID_PLACED) {
      // 2. Thay thế printf bằng logger.info sử dụng placeholder {}
      logger.info("[THÔNG BÁO tới Bidder {}] Bid mới: {} đặt {} | Phiên: {}",
              bidder.getUsername(),
              event.getBidder() != null ? event.getBidder().getUsername() : "?",
              event.getBidAmount(),
              event.getAuction().getId());
    } else if (event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      logger.info("[THÔNG BÁO tới Bidder {}] Bid {} chưa đạt reserve price.",
              bidder.getUsername(), event.getBidAmount());
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        logger.info("[THÔNG BÁO tới Bidder {}] Phiên đã bắt đầu!", bidder.getUsername());
        break;

      case AUCTION_EXTENDED:
        logger.info("[THÔNG BÁO tới Bidder {}] Phiên {} được gia hạn. EndTime mới: {}. {}",
                bidder.getUsername(),
                event.getAuction().getId(),
                event.getAuction().getEndTime(),
                event.getMessage() != null ? event.getMessage() : "");
        break;

      case AUCTION_UPCOMING:
        logger.info("[THÔNG BÁO tới Bidder {}] Phiên sắp bắt đầu — chuẩn bị sẵn sàng.",
                bidder.getUsername());
        break;

      case AUCTION_ENDED:
        if (event.getBidder() != null
                && event.getBidder().getUsername().equals(bidder.getUsername())) {
          logger.info("[THÔNG BÁO tới Bidder {}] Chúc mừng! Bạn thắng phiên với giá {}. Hãy thanh toán trong 24h.",
                  bidder.getUsername(), event.getBidAmount());
        } else {
          logger.info("[THÔNG BÁO tới Bidder {}] Phiên kết thúc. Winner: {}.",
                  bidder.getUsername(),
                  event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
        }
        break;

      case AUCTION_NO_WINNER:
        logger.info("[THÔNG BÁO tới Bidder {}] Phiên kết thúc không có ai đặt giá. Cọc sẽ được hoàn trả.",
                bidder.getUsername());
        break;

      case RESERVE_NOT_MET_CLOSED:
        logger.info("[THÔNG BÁO tới Bidder {}] Phiên kết thúc — giá cao nhất {} chưa đạt reserve. Cọc sẽ được hoàn trả.",
                bidder.getUsername(), event.getBidAmount());
        break;

      case AUCTION_CANCELED:
        logger.info("[THÔNG BÁO tới Bidder {}] Phiên đấu giá đã bị hủy. Cọc sẽ được hoàn trả.",
                bidder.getUsername());
        break;

      default:
        break;
    }
  }
}