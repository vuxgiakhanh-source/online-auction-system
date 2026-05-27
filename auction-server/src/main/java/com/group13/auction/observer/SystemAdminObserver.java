package com.group13.auction.observer;

import com.group13.auction.model.user.SystemAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer dành riêng cho SystemAdmin - nhận tất cả event toàn cục.
 *
 * <p>SystemAdmin nhận global notify về mọi event trong hệ thống.
 */
public class SystemAdminObserver implements AuctionObserver {

  private static final Logger logger = LoggerFactory.getLogger(SystemAdminObserver.class);

  private final SystemAdmin systemAdmin;

  /**
   * Khởi tạo SystemAdminObserver.
   *
   * @param systemAdmin SystemAdmin duy nhất của hệ thống
   */
  public SystemAdminObserver(SystemAdmin systemAdmin) {
    this.systemAdmin = systemAdmin;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    String log =
        String.format(
            "[SYSTEM] Bid mới: %s đặt %d | Phiên: %s%s",
            event.getBidder() != null ? event.getBidder().getUsername() : "?",
            event.getBidAmount(),
            event.getAuction().getId(),
            event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET
                ? " [RESERVE CHƯA ĐẠT]"
                : "");
    log(log);
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    String log;
    switch (event.getEventType()) {
      case AUCTION_UPCOMING:
        log = String.format("[SYSTEM] Phiên sắp bắt đầu: %s", event.getAuction().getId());
        break;

      case AUCTION_STARTED:
        log = String.format("[SYSTEM] Phiên bắt đầu: %s", event.getAuction().getId());
        break;

      case AUCTION_ENDED:
        log =
            String.format(
                "[SYSTEM] Phiên kết thúc: %s | Winner: %s | Giá: %d",
                event.getAuction().getId(),
                event.getBidder() != null ? event.getBidder().getUsername() : "Không có",
                event.getBidAmount());
        break;

      case AUCTION_NO_WINNER:
        log =
            String.format(
                "[SYSTEM] Phiên %s kết thúc — không có người đặt giá. Auto-cancel.",
                event.getAuction().getId());
        break;

      case RESERVE_NOT_MET_CLOSED:
        log =
            String.format(
                "[SYSTEM] Phiên %s kết thúc — giá cao nhất %d chưa đạt reserve." + " Auto-cancel.",
                event.getAuction().getId(), event.getBidAmount());
        break;

      case PAYMENT_COMPLETED:
        log =
            String.format(
                "[SYSTEM] Thanh toán thành công | Phiên: %s | Winner: %s",
                event.getAuction().getId(),
                event.getBidder() != null ? event.getBidder().getUsername() : "?");
        break;

      case AUCTION_CANCELED:
        log = String.format("[SYSTEM] Phiên bị hủy: %s", event.getAuction().getId());
        break;

      case SECOND_CHANCE_OFFERED:
        log =
            String.format(
                "[SYSTEM] Second Chance Offer tạo cho %s | Phiên: %s | Giá: %d",
                event.getBidder() != null ? event.getBidder().getUsername() : "?",
                event.getAuction().getId(),
                event.getBidAmount());
        break;

      case QUALITY_REPORT_APPROVED:
        log =
            String.format(
                "[SYSTEM] Báo cáo chất lượng được duyệt | Phiên: %s", event.getAuction().getId());
        break;

      case FRAUD_DETECTED:
        log =
            String.format(
                "[SYSTEM] GIAN LẬN phát hiện tại phiên %s: %s",
                event.getAuction().getId(), event.getMessage() != null ? event.getMessage() : "");
        break;

      case SELLER_CANCEL_REQUEST:
        log =
            String.format(
                "[SYSTEM] Seller yêu cầu hủy phiên %s: %s",
                event.getAuction().getId(), event.getMessage() != null ? event.getMessage() : "");
        break;

      case SELLER_CANCEL_REQUEST_ACCEPTED:
        log =
            String.format(
                "[SYSTEM] Yêu cầu hủy phiên %s được chấp thuận.", event.getAuction().getId());
        break;

      default:
        log =
            String.format(
                "[SYSTEM] Event: %s | Phiên: %s", event.getEventType(), event.getAuction().getId());
        break;
    }
    log(log);
  }

  // Private helper

  private void log(String message) {
    systemAdmin.addActionLog(message);
    logger.info(
        "System admin observer notification: systemAdminId={}, username={}, message={}",
        systemAdmin.getId(),
        systemAdmin.getUsername(),
        message);
  }
}
