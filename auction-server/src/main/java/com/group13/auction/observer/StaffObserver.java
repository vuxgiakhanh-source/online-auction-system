package com.group13.auction.observer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.model.user.Admin;

/**
 * Observer dành cho Staff Admin - nhận notify về các event cần can thiệp thủ công.
 *
 * <p>Staff Admin chỉ nhận:
 * <ul>
 * <li>các trường hợp phiên bị hủy cần kiểm tra.</li>
 * <li>FRAUD_DETECTED - phát hiện gian lận cần xử lý.</li>
 * <li>QUALITY_REPORT_APPROVED - báo cáo chất lượng cần theo dõi hoàn tiền.</li>
 * <li>SELLER_CANCEL_REQUEST - seller yêu cầu hủy, staff cần xem xét và quyết định.</li>
 * </ul>
 *
 * <p>Chỉ khi Staff joinAuction thì mới nhận thêm notify chi tiết theo phiên đó.
 * Dữ liệu log luôn có thể truy xuất sau này từ actionLog.
 */
public class StaffObserver implements AuctionObserver {

    private static final Logger log = LoggerFactory.getLogger(StaffObserver.class);

    private final Admin staff;

    /**
     * Khởi tạo StaffObserver.
     *
     * @param staff Staff Admin được theo dõi
     */
    public StaffObserver(Admin staff) {
        if (!staff.isStaff()) {
            throw new IllegalArgumentException("StaffObserver chỉ dùng cho Staff Admin.");
        }
        this.staff = staff;
    }

    @Override
    public void onBidPlaced(AuctionEvent event) {
        // Staff không nhận bid event trừ khi join phiên đó
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        String log = null;

        switch (event.getEventType()) {
            case AUCTION_CANCELED:
                log = String.format(
                        "[THÔNG BÁO tới STAFF - %s] Phiên bị hủy: %s — kiểm tra nếu cần.",
                        staff.getUsername(), event.getAuction().getId());
                break;

            case AUCTION_NO_WINNER:
                log = String.format(
                        "[THÔNG BÁO tới STAFF - %s] Phiên %s kết thúc không có ai đặt giá — auto-cancel.",
                        staff.getUsername(), event.getAuction().getId());
                break;

            case RESERVE_NOT_MET_CLOSED:
                log = String.format(
                        "[THÔNG BÁO tới STAFF - %s] Phiên %s kết thúc — giá cao nhất %d"
                                + " chưa đạt reserve — auto-cancel.",
                        staff.getUsername(), event.getAuction().getId(), event.getBidAmount());
                break;

            case FRAUD_DETECTED:
                log = String.format(
                        "[THÔNG BÁO tới STAFF - %s] GIAN LẶN tại phiên %s: %s — CẦN XỬ LÝ.",
                        staff.getUsername(), event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "");
                break;

            case QUALITY_REPORT_APPROVED:
                log = String.format(
                        "[THÔNG BÁO tới STAFF - %s] Báo cáo chất lượng được duyệt tại phiên %s"
                                + " - theo dõi hoàn tiền của Seller.",
                        staff.getUsername(), event.getAuction().getId());
                break;

            case SELLER_CANCEL_REQUEST:
                log = String.format(
                        "[THÔNG BÁO tới STAFF - %s] Seller yêu cầu hủy phiên %s: %s"
                                + " - SYSTEM sẽ xem xét và quết định.",
                        staff.getUsername(), event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "Không có lý do");
                break;

            // Các event còn lại Staff không cần nhận qua channel này
            default:
                break;
        }
        if (log != null) {
            log(log);
        }
    }
    // Private helper

    private void log(String message) {
        staff.addActionLog(message);
        log.info("{}", message);
    }
}