package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.serviceInterface.IRatingService;

/**
 * Observer dành cho Seller — nhận notify về phiên đấu giá của mình.
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
    System.out.printf("[NOTIFY → Seller %s] Bid mới: %.0f | Phiên: %s%n",
            seller.getUsername(), event.getBidAmount(), event.getAuction().getId());
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    switch (event.getEventType()) {
      case AUCTION_STARTED:
        System.out.printf("[NOTIFY → Seller %s] Phiên đấu giá của bạn đã bắt đầu!%n",
                seller.getUsername());
        break;
      case AUCTION_ENDED:
        if (event.getBidder() != null) {
          System.out.printf("[NOTIFY → Seller %s] Phiên kết thúc. Winner: %s | Giá: %.0f. Chờ thanh toán.%n",
                  seller.getUsername(),
                  event.getBidder().getUsername(),
                  event.getBidAmount());
        } else {
          System.out.printf("[NOTIFY → Seller %s] Phiên kết thúc mà không có người đặt giá.%n",
                  seller.getUsername());
        }
        break;
      case RESERVE_NOT_MET_CLOSED:
        System.out.printf("[NOTIFY → Seller %s] Phiên \"%s\" kết thúc với mức giá cao nhất là %.0f nhưng chưa đạt mức giá tối thiểu của bạn. Phiên đã bị hủy.%n",
                seller.getUsername(),
                event.getAuction().getItem().getName(),
                event.getBidAmount());
        break;
      case PAYMENT_COMPLETED:
        // Thông báo seller đã bán thành công
        System.out.printf("[NOTIFY → Seller %s] Sản phẩm \"%s\" đã được bán thành công với giá %.0f! Tiền đã được chuyển vào tài khoản (sau thuế).%n",
                seller.getUsername(),
                event.getAuction().getItem().getName(),
                event.getBidAmount());
        ratingService.rewardSeller(seller);
        break;
      case AUCTION_CANCELED:
        System.out.printf("[NOTIFY → Seller %s] Phiên đấu giá đã bị hủy.%n",
                seller.getUsername());
        break;
      case QUALITY_REPORT_APPROVED:
        System.out.printf("[NOTIFY → Seller %s] Báo cáo chất lượng của buyer được phê duyệt. Bạn phải hoàn trả tiền trong 24h, nếu không sẽ bị ban vĩnh viễn.%n",
                seller.getUsername());
        break;
      default:
        break;
    }
  }
}