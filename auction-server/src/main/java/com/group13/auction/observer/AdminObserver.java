package com.group13.auction.observer;

import com.group13.auction.model.user.Admin;

/**
 * Observer dành cho Admin khi join một phiên cụ thể — nhận notify chi tiết theo phiên.
 *
 * <p>Admin tự động nhận notify global về:
 * gian lận, lỗi hệ thống, phiên không có winner, reserve not met
 * qua {@link StaffObserver} hoặc {@link SystemAdminObserver}.
 *
 * <p>Khi admin joinAuction → dùng observer này để nhận thêm notify chi tiết theo phiên đó.
 */
public class AdminObserver implements AuctionObserver {

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
        System.out.printf("[LOG - Admin %s] Bid mới: %s đặt %.0f | Phiên: %s%s%n",
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
                System.out.printf("[LOG - Admin %s] Phiên bắt đầu: %s%n",
                        admin.getUsername(), event.getAuction().getId());
                break;
            case AUCTION_ENDED:
                System.out.printf("[LOG - Admin %s] Phiên kết thúc: %s | Winner: %s%n",
                        admin.getUsername(),
                        event.getAuction().getId(),
                        event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
                break;
            case AUCTION_NO_WINNER:
                System.out.printf("[LOG - Admin %s] Phiên %s kết thúc KHÔNG có người đặt giá.%n",
                        admin.getUsername(), event.getAuction().getId());
                break;
            case RESERVE_NOT_MET_CLOSED:
                System.out.printf("[LOG - Admin %s] Phiên %s kết thúc với giá cao nhất %.0f nhưng CHƯA ĐẠT mức tối thiểu.%n",
                        admin.getUsername(),
                        event.getAuction().getId(),
                        event.getBidAmount());
                break;
            case PAYMENT_COMPLETED:
                System.out.printf("[LOG - Admin %s] Thanh toán thành công: phiên %s%n",
                        admin.getUsername(), event.getAuction().getId());
                break;
            case AUCTION_CANCELED:
                System.out.printf("[LOG - Admin %s] Phiên bị hủy: %s%n",
                        admin.getUsername(), event.getAuction().getId());
                break;
            case FRAUD_DETECTED:
                System.out.printf("[CẢNH BÁO - Admin %s] GIAN LẶN phát hiện tại phiên %s: %s%n",
                        admin.getUsername(),
                        event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "");
                break;
            case QUALITY_REPORT_APPROVED:
                System.out.printf("[LOG - Admin %s] Báo cáo chất lượng được duyệt: phiên %s%n",
                        admin.getUsername(), event.getAuction().getId());
                break;
            default:
                break;
        }
    }
}