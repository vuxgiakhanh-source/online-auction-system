package com.group13.auction.observer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.model.user.Admin;

/**
 * Observer dành cho Admin khi join một phiên cụ thể - nhận notify chi tiết theo phiên
 * như người bth.
 *
 * <p>Vẫn nhận notify global về:
 * gian lận, lỗi hệ thống, phiên không có winner, reserve not met
 * qua {@link StaffObserver} hoặc {@link SystemAdminObserver}.
 *
 * <p>Khi admin joinAuction -> dùng observer này để nhận thêm notify chi tiết theo phiên đó.
 */
public class AdminObserver implements AuctionObserver {

    private static final Logger log = LoggerFactory.getLogger(AdminObserver.class);

    private final Admin admin;

    /**
     * Khởi tạo AdminObserver.
     *
     * @param admin admin được theo dõi
     */
    public AdminObserver(Admin admin) {
        this.admin = admin;
    }

    @Override
    public void onBidPlaced(AuctionEvent event) {
        if (event.getEventType() != AuctionEvent.AuctionEventType.BID_PLACED
                && event.getEventType() != AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
            return;
        }
        log.info("[LOG - THÔNG BÁO tới Admin {}] Bid mới: {} đặt {} | Phiên: {}{}",
                admin.getUsername(),
                event.getBidder() != null ? event.getBidder().getUsername() : "?",
                event.getBidAmount(),
                event.getAuction().getId(),
                event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET
                        ? " [RESERVE CHƯA ĐẠT]" : "");
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        switch (event.getEventType()) {
            case AUCTION_STARTED:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Phiên bắt đầu: {}",
                        admin.getUsername(), event.getAuction().getId());
                // TODO: notificationDao.save()
                break;
            case AUCTION_ENDED:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Phiên kết thúc: {} | Winner: {}",
                        admin.getUsername(),
                        event.getAuction().getId(),
                        event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
                // TODO: notificationDao.save()
                break;
            case AUCTION_NO_WINNER:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Phiên {} kết thúc KHÔNG có người đặt giá.",
                        admin.getUsername(), event.getAuction().getId());
                // TODO: notificationDao.save()
                break;
            case RESERVE_NOT_MET_CLOSED:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Phiên {} kết thúc với giá cao nhất {} nhưng CHƯA ĐẠT mức tối thiểu.",
                        admin.getUsername(),
                        event.getAuction().getId(),
                        event.getBidAmount());
                // TODO: notificationDao.save()
                break;
            case PAYMENT_COMPLETED:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Thanh toán thành công: phiên {}",
                        admin.getUsername(), event.getAuction().getId());
                // TODO: notificationDao.save()
                break;
            case AUCTION_CANCELED:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Phiên bị hủy: {}",
                        admin.getUsername(), event.getAuction().getId());
                // TODO: notificationDao.save()
                break;
            case FRAUD_DETECTED:
                log.info("[CẢNH BÁO - THÔNG BÁO tới Admin {}] GIAN LẶN phát hiện tại phiên {}: {}",
                        admin.getUsername(),
                        event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "");
                // TODO: notificationDao.save()
                break;
            case QUALITY_REPORT_APPROVED:
                log.info("[LOG - THÔNG BÁO tới Admin {}] Báo cáo chất lượng được duyệt: phiên {}",
                        admin.getUsername(), event.getAuction().getId());
                // TODO: notificationDao.save()
                break;
            default:
                break;
        }
    }
}