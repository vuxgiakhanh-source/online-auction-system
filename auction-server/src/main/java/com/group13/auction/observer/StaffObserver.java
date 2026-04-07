package com.group13.auction.observer;

import com.group13.auction.model.user.Admin;

/**
 * Observer dành cho Staff Admin — nhận notify về các event cần can thiệp thủ công.
 *
 * <p>Staff Admin chỉ nhận:
 * <ul>
 * <li>AUCTION_CANCELED — phiên bị hủy cần kiểm tra.</li>
 * <li>FRAUD_DETECTED — phát hiện gian lận cần xử lý.</li>
 * <li>QUALITY_REPORT_APPROVED — báo cáo chất lượng cần theo dõi hoàn tiền.</li>
 * <li>SELLER_CANCEL_REQUEST — seller yêu cầu hủy, staff cần xem xét và quyết định.</li>
 * </ul>
 *
 * <p>Chỉ khi Staff joinAuction thì mới nhận thêm notify chi tiết theo phiên đó.
 * Dữ liệu log luôn có thể truy xuất sau này từ actionLog.
 */
public class StaffObserver implements AuctionObserver {

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
        switch (event.getEventType()) {
            case AUCTION_CANCELED:
                String cancelLog = String.format(
                        "[STAFF NOTIFY - %s] Phiên bị hủy: %s — kiểm tra nếu cần.",
                        staff.getUsername(), event.getAuction().getId());
                staff.addActionLog(cancelLog);
                System.out.println(cancelLog);
                break;

            case FRAUD_DETECTED:
                String fraudLog = String.format(
                        "[STAFF NOTIFY - %s] GIAN LẶN tại phiên %s: %s",
                        staff.getUsername(), event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "");
                staff.addActionLog(fraudLog);
                System.out.println(fraudLog);
                break;

            case QUALITY_REPORT_APPROVED:
                String reportLog = String.format(
                        "[STAFF NOTIFY - %s] Báo cáo chất lượng được duyệt tại phiên %s — theo dõi hoàn tiền.",
                        staff.getUsername(), event.getAuction().getId());
                staff.addActionLog(reportLog);
                System.out.println(reportLog);
                break;

            case SELLER_CANCEL_REQUEST:
                String requestLog = String.format(
                        "[STAFF NOTIFY - %s] Seller yêu cầu hủy phiên %s: %s — CẦN XEM XÉT VÀ QUYẾT ĐỊNH.",
                        staff.getUsername(), event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "Không có lý do");
                staff.addActionLog(requestLog);
                System.out.println(requestLog);
                break;

            default:
                // Staff không nhận các event khác trừ khi join phiên
                break;
        }
    }
}