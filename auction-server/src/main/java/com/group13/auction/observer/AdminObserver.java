package com.group13.auction.observer;

import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.user.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer dành cho Admin khi join một phiên cụ thể. Nhận notify chi tiết theo phiên như người bình
 * thường.
 *
 * <p>Khi admin joinAuction → dùng observer này để nhận thêm notify chi tiết theo phiên đó và
 * persist vào bảng notifications.
 */
public class AdminObserver implements AuctionObserver {

  private static final Logger log = LoggerFactory.getLogger(AdminObserver.class);

  private final Admin admin;
  private final NotificationDAO notificationDAO;

  public AdminObserver(Admin admin) {
    this(admin, new NotificationDAO());
  }

  public AdminObserver(Admin admin, NotificationDAO notificationDAO) {
    this.admin = admin;
    this.notificationDAO = notificationDAO;
  }

  @Override
  public void onBidPlaced(AuctionEvent event) {
    if (event.getEventType() != AuctionEvent.AuctionEventType.BID_PLACED
        && event.getEventType() != AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      return;
    }
    log.info(
        "[LOG - THÔNG BÁO tới Admin {}] Bid mới: {} đặt {} | Phiên: {}{}",
        admin.getUsername(),
        event.getBidder() != null ? event.getBidder().getUsername() : "?",
        event.getBidAmount(),
        event.getAuction().getId(),
        event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET
            ? " [RESERVE CHƯA ĐẠT]"
            : "");
    // Bid events không lưu notification để tránh spam inbox admin
  }

  @Override
  public void onAuctionEnded(AuctionEvent event) {
    String auctionId = event.getAuction().getId();

    switch (event.getEventType()) {
      case AUCTION_STARTED -> {
        log.info(
            "[LOG - THÔNG BÁO tới Admin {}] Phiên bắt đầu: {}", admin.getUsername(), auctionId);
        saveNotification(
            auctionId,
            "Phiên đấu giá bắt đầu",
            "Phiên " + auctionId + " đã chuyển sang trạng thái RUNNING.");
      }
      case AUCTION_ENDED -> {
        String winner = event.getBidder() != null ? event.getBidder().getUsername() : "Không có";
        log.info(
            "[LOG - THÔNG BÁO tới Admin {}] Phiên kết thúc: {} | Winner: {}",
            admin.getUsername(),
            auctionId,
            winner);
        saveNotification(
            auctionId,
            "Phiên đấu giá kết thúc",
            "Phiên " + auctionId + " kết thúc. Người thắng: " + winner + ".");
      }
      case AUCTION_NO_WINNER -> {
        log.info(
            "[LOG - THÔNG BÁO tới Admin {}] Phiên {} kết thúc KHÔNG có người đặt giá.",
            admin.getUsername(),
            auctionId);
        saveNotification(
            auctionId,
            "Phiên kết thúc không có người đặt giá",
            "Phiên " + auctionId + " bị hủy vì không có ai đặt giá.");
      }
      case RESERVE_NOT_MET_CLOSED -> {
        log.info(
            "[LOG - THÔNG BÁO tới Admin {}] Phiên {} kết thúc với giá cao nhất {} nhưng CHƯA ĐẠT"
                + " mức tối thiểu.",
            admin.getUsername(),
            auctionId,
            event.getBidAmount());
        saveNotification(
            auctionId,
            "Phiên kết thúc chưa đạt giá sàn",
            "Phiên "
                + auctionId
                + " bị hủy. Giá cao nhất "
                + event.getBidAmount()
                + " chưa đạt mức reserve.");
      }
      case PAYMENT_COMPLETED -> {
        log.info(
            "[LOG - THÔNG BÁO tới Admin {}] Thanh toán thành công: phiên {}",
            admin.getUsername(),
            auctionId);
        saveNotification(
            auctionId,
            "Thanh toán thành công",
            "Phiên " + auctionId + " đã được thanh toán đầy đủ.");
      }
      case AUCTION_CANCELED -> {
        log.info("[LOG - THÔNG BÁO tới Admin {}] Phiên bị hủy: {}", admin.getUsername(), auctionId);
        saveNotification(auctionId, "Phiên đấu giá bị hủy", "Phiên " + auctionId + " đã bị hủy.");
      }
      case FRAUD_DETECTED -> {
        String msg = event.getMessage() != null ? event.getMessage() : "";
        log.info(
            "[CẢNH BÁO - THÔNG BÁO tới Admin {}] GIAN LẬN phát hiện tại phiên {}: {}",
            admin.getUsername(),
            auctionId,
            msg);
        saveNotification(
            auctionId,
            "⚠ Cảnh báo gian lận",
            "Phát hiện gian lận tại phiên " + auctionId + ". " + msg);
      }
      case QUALITY_REPORT_APPROVED -> {
        log.info(
            "[LOG - THÔNG BÁO tới Admin {}] Báo cáo chất lượng được duyệt: phiên {}",
            admin.getUsername(),
            auctionId);
        saveNotification(
            auctionId,
            "Báo cáo chất lượng được duyệt",
            "Báo cáo chất lượng sản phẩm tại phiên " + auctionId + " đã được xử lý.");
      }
      default -> {
        /* ignore */
      }
    }
  }

  // ── Private ───────────────────────────────────────────────────────────

  private void saveNotification(String auctionId, String title, String body) {
    try {
      Notification n = Notification.create(admin.getId(), auctionId, title, body);
      notificationDAO.save(n);
    } catch (Exception e) {
      log.warn("Không thể lưu notification cho admin {}: title={}", admin.getUsername(), title, e);
    }
  }
}
