package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;

/**
 * Observer dành cho Seller - nhận notify về phiên đấu giá của mình.
 */
public class SellerObserver implements AuctionObserver {

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
    switch (event.getEventType()) {
      case BID_PLACED:
        System.out.printf("[THÔNG BÁO tới Seller %s] Bid mới: %d | Phiên: %s%n",
                seller.getUsername(), event.getBidAmount(), event.getAuction().getId());
        break;

      case BID_RESERVE_NOT_MET:
        System.out.printf(
                "[THÔNG BÁO tới Seller %s] Bid %d chưa đạt reserve price (%d).%n",
                seller.getUsername(),
                event.getBidAmount(),
                event.getAuction().getReservePrice());
        break;

      default:
        break;
    }
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        System.out.printf("[THÔNG BÁO tới Seller %s] Phiên đấu giá của bạn đã bắt đầu!%n",
                seller.getUsername());
        // TODO: notificationDao.save()
        break;

      case AUCTION_UPCOMING:
        // Chưa done, đang trong quá trình hoàn thiện
        // TODO: notificationDao.save()

        break;

      case AUCTION_ENDED:
        if (event.getBidder() != null) {
          System.out.printf("[THÔNG BÁO tới Seller %s] Phiên kết thúc. Winner: %s | Giá: %d. Chờ thanh toán.%n",
                  seller.getUsername(),
                  event.getBidder().getUsername(),
                  event.getBidAmount());
          // TODO: notificationDao.save()
        }
        break;

      case AUCTION_NO_WINNER:
        System.out.printf(
                "[THÔNG BÁO tới Seller %s] Phiên kết thúc không có ai đặt giá."
                        + " Phiên đã bị hủy.%n",
                seller.getUsername());
        // TODO: notificationDao.save()
        break;

      case RESERVE_NOT_MET_CLOSED:
        System.out.printf("[THÔNG BÁO tới Seller %s] Phiên \"%s\" kết thúc với mức giá cao nhất là %d nhưng chưa đạt mức giá tối thiểu của bạn. Phiên đã bị hủy.%n",
                seller.getUsername(),
                event.getAuction().getItem().getName(),
                event.getBidAmount());
        // TODO: notificationDao.save()
        break;

      case PAYMENT_COMPLETED:
        // Thông báo seller đã bán thành công
        System.out.printf("[THÔNG BÁO tới Seller %s] Sản phẩm \"%s\" đã được bán thành công với giá %d! Tiền đã được chuyển vào tài khoản (sau thuế).%n",
                seller.getUsername(),
                event.getAuction().getItem().getName(),
                event.getBidAmount());
        ratingService.rewardSeller(seller);
        // TODO: notificationDao.save()
        break;

      case AUCTION_CANCELED:
        System.out.printf("[THÔNG BÁO tới Seller %s] Phiên đấu giá đã bị hủy.%n",
                seller.getUsername());
        // TODO: notificationDao.save()
        break;

      case SECOND_CHANCE_OFFERED:
        System.out.printf(
                "[THÔNG BÁO tới Seller %s] Winner không thanh toán —"
                        + " hệ thống đang chào cơ hội mua thứ cấp cho người đặt giá tiếp theo.%n",
                seller.getUsername());
        // TODO: notificationDao.save()
        break;

      case SELLER_CANCEL_REQUEST_ACCEPTED:
        System.out.printf(
                "[THÔNG BÁO tới Seller %s] Yêu cầu hủy phiên của bạn đã được chấp thuận.%n",
                seller.getUsername());
        // TODO: notificationDao.save()
        break;
      default:
        break;
    }
  }
}