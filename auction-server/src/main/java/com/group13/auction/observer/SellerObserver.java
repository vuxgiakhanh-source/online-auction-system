package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.IRatingService;

/**
 * Observer dành cho Seller.
 * Nhận thông báo khi có bid mới và khi phiên kết thúc.
 * Đặc biệt thông báo khi giao dịch thành công.
 */
public class SellerObserver implements AuctionObserver {

  private final NormalUser     seller;
  private final IRatingService ratingService;

  /**
   * Khởi tạo SellerObserver.
   *
   * @param seller        seller được theo dõi
   * @param ratingService để thưởng rating khi bán thành công
   */
  public SellerObserver(NormalUser seller, IRatingService ratingService) {
    this.seller        = seller;
    this.ratingService = ratingService;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() != AuctionEvent.AuctionEventType.BID_PLACED
            && event.getEventType() != AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      return;
    }
    System.out.printf("[NOTIFY → Seller %s] Có người vừa trả %.0f cho sản phẩm \"%s\"%s%n",
            seller.getUsername(),
            event.getBidAmount(),
            event.getAuction().getItem().getName(),
            event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET
                    ? " (chưa đạt giá sàn)" : "");
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
        System.out.printf("[NOTIFY → Seller %s] Phiên đấu giá \"%s\" kết thúc với mức giá cao nhất là %.0f nhưng chưa đạt mức giá tối thiểu của bạn. Phiên sẽ được tổ chức lại sau 2 ngày.%n",
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
        System.out.printf("[NOTIFY → Seller %s] Phiên đấu giá đã bị Admin huỷ.%n",
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