package com.group13.auction.observer;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer dành cho Seller - nhận notify về phiên đấu giá của mình.
 */
public class SellerObserver implements AuctionObserver {

  // 1. Khai báo Logger cho SellerObserver
  private static final Logger logger = LoggerFactory.getLogger(SellerObserver.class);

  private final NormalUser seller;
  private final IRatingService ratingService;

  public SellerObserver(NormalUser seller, IRatingService ratingService) {
    this.seller = seller;
    this.ratingService = ratingService;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    switch (event.getEventType()) {
      case BID_PLACED:
        // 2. Sử dụng logger.info với placeholder {}
        logger.info("[THÔNG BÁO tới Seller {}] Bid mới: {} | Phiên: {}",
                seller.getUsername(), event.getBidAmount(), event.getAuction().getId());
        break;

      case BID_RESERVE_NOT_MET:
        logger.info("[THÔNG BÁO tới Seller {}] Bid {} chưa đạt reserve price ({}).",
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
        logger.info("[THÔNG BÁO tới Seller {}] Phiên đấu giá của bạn đã bắt đầu!",
                seller.getUsername());
        break;

      case AUCTION_UPCOMING:
        // Giữ nguyên placeholder cho logic sau này của bạn
        break;

      case AUCTION_ENDED:
        if (event.getBidder() != null) {
          logger.info("[THÔNG BÁO tới Seller {}] Phiên kết thúc. Winner: {} | Giá: {}. Chờ thanh toán.",
                  seller.getUsername(),
                  event.getBidder().getUsername(),
                  event.getBidAmount());
        }
        break;

      case AUCTION_NO_WINNER:
        logger.info("[THÔNG BÁO tới Seller {}] Phiên kết thúc không có ai đặt giá. Phiên đã bị hủy.",
                seller.getUsername());
        break;

      case RESERVE_NOT_MET_CLOSED:
        logger.info("[THÔNG BÁO tới Seller {}] Phiên \"{}\" kết thúc với mức giá cao nhất là {} nhưng chưa đạt mức giá tối thiểu của bạn. Phiên đã bị hủy.",
                seller.getUsername(),
                event.getAuction().getItem().getName(),
                event.getBidAmount());
        break;

      case PAYMENT_COMPLETED:
        logger.info("[THÔNG BÁO tới Seller {}] Sản phẩm \"{}\" đã được bán thành công với giá {}! Tiền đã được chuyển vào tài khoản (sau thuế).",
                seller.getUsername(),
                event.getAuction().getItem().getName(),
                event.getBidAmount());
        ratingService.rewardSeller(seller);
        break;

      case AUCTION_CANCELED:
        logger.info("[THÔNG BÁO tới Seller {}] Phiên đấu giá đã bị hủy.",
                seller.getUsername());
        break;

      case SECOND_CHANCE_OFFERED:
        logger.info("[THÔNG BÁO tới Seller {}] Winner không thanh toán — hệ thống đang chào cơ hội mua thứ cấp cho người đặt giá tiếp theo.",
                seller.getUsername());
        break;

      case SELLER_CANCEL_REQUEST_ACCEPTED:
        logger.info("[THÔNG BÁO tới Seller {}] Yêu cầu hủy phiên của bạn đã được chấp thuận.",
                seller.getUsername());
        break;

      default:
        break;
    }
  }
}