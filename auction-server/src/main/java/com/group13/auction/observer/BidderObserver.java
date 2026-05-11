package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer dành cho Bidder — nhận notify về bid và kết quả phiên.
 */
public class BidderObserver implements AuctionObserver {

  private static final Logger log = LoggerFactory.getLogger(BidderObserver.class);

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
    log.info("Bidder observer received bid event: observerBidderId={}, username={}, eventType={}, auctionId={}, bidderId={}, amount={}",
            bidder.getId(), bidder.getUsername(), event.getEventType(),
            event.getAuction().getId(), event.getBidder() != null ? event.getBidder().getId() : null,
            event.getBidAmount());
    if (event.getEventType() == AuctionEvent.AuctionEventType.BID_PLACED) {
    } else if (event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      // TODO: notificationDao.save()
    }

  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    log.info("Bidder observer received auction event: observerBidderId={}, username={}, eventType={}, auctionId={}, amount={}",
            bidder.getId(), bidder.getUsername(), event.getEventType(),
            event.getAuction().getId(), event.getBidAmount());
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        // TODO: notificationDao.save()
        break;

      case AUCTION_EXTENDED:
        break;

      case AUCTION_UPCOMING:
        // Chưa done, đang trong quá trình hoàn thiện
        // TODO: notificationDao.save()
        break;

      case AUCTION_ENDED:
        if (event.getBidder() != null
                && event.getBidder().getUsername().equals(bidder.getUsername())) {
          // TODO: notificationDao.save()
        } else {
          // TODO: notificationDao.save()
        }
        break;

      case AUCTION_NO_WINNER:
        // TODO: notificationDao.save()
        break;

      case RESERVE_NOT_MET_CLOSED:
        // TODO: notificationDao.save()
        break;

      case AUCTION_CANCELED:
        // TODO: notificationDao.save()
        break;

      default:
        break;
    }
  }
}
