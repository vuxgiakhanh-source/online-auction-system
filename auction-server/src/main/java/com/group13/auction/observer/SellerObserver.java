package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer dành cho Seller - nhận notify về phiên đấu giá của mình.
 */
public class SellerObserver implements AuctionObserver {

  private static final Logger log = LoggerFactory.getLogger(SellerObserver.class);

  private final NormalUser seller;
  private final IRatingService ratingService;

  /**
   * Khởi tạo SellerObserver.
   *
   * @param seller seller được theo dõi
   * @param ratingService dùng để thưởng rating sau khi bán thành công
   */
  public SellerObserver(NormalUser seller, IRatingService ratingService) {
    this.seller = seller;
    this.ratingService = ratingService;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    log.info("Seller observer received bid event: sellerId={}, username={}, eventType={}, auctionId={}, amount={}",
            seller.getId(), seller.getUsername(), event.getEventType(),
            event.getAuction().getId(), event.getBidAmount());
    switch (event.getEventType()) {
      case BID_PLACED:
        break;

      case BID_RESERVE_NOT_MET:
        break;

      default:
        break;
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    log.info("Seller observer received auction event: sellerId={}, username={}, eventType={}, auctionId={}, amount={}",
            seller.getId(), seller.getUsername(), event.getEventType(),
            event.getAuction().getId(), event.getBidAmount());
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        // TODO: notificationDao.save()
        break;

      case AUCTION_UPCOMING:
        // Chưa done, đang trong quá trình hoàn thiện
        // TODO: notificationDao.save()

        break;

      case AUCTION_ENDED:
        if (event.getBidder() != null) {
          // TODO: notificationDao.save()
        }
        break;

      case AUCTION_NO_WINNER:
        // TODO: notificationDao.save()
        break;

      case RESERVE_NOT_MET_CLOSED:
        // TODO: notificationDao.save()
        break;

      case PAYMENT_COMPLETED:
        // Thông báo seller đã bán thành công
        ratingService.rewardSeller(seller);
        // TODO: notificationDao.save()
        break;

      case AUCTION_CANCELED:
        // TODO: notificationDao.save()
        break;

      case SECOND_CHANCE_OFFERED:
        // TODO: notificationDao.save()
        break;

      case SELLER_CANCEL_REQUEST_ACCEPTED:
        // TODO: notificationDao.save()
        break;
      default:
        break;
    }
  }
}
