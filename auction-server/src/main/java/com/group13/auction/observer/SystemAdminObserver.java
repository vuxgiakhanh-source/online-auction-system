package com.group13.auction.observer;

import com.group13.auction.model.user.SystemAdmin;

/**
 * Observer dành riêng cho SystemAdmin — nhận tất cả event toàn cục.
 *
 * <p>SystemAdmin nhận global notify về mọi event trong hệ thống.
 * Không cần joinAuction để nhận notify.
 */
public class SystemAdminObserver implements AuctionObserver {

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
        String log = String.format(
                "[GLOBAL - SYSTEM] Bid mới: %s đặt %.0f | Phiên: %s%s",
                event.getBidder() != null ? event.getBidder().getUsername() : "?",
                event.getBidAmount(),
                event.getAuction().getId(),
                event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET
                        ? " [RESERVE CHƯA ĐẠT]" : "");
        systemAdmin.addActionLog(log);
        System.out.println(log);
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        String log;
        switch (event.getEventType()) {
            case AUCTION_STARTED:
                log = String.format("[GLOBAL - SYSTEM] Phiên bắt đầu: %s", event.getAuction().getId());
                break;
            case AUCTION_ENDED:
                log = String.format("[GLOBAL - SYSTEM] Phiên kết thúc: %s | Winner: %s",
                        event.getAuction().getId(),
                        event.getBidder() != null ? event.getBidder().getUsername() : "Không có");
                break;
            case AUCTION_NO_WINNER:
                log = String.format("[GLOBAL - SYSTEM] Phiên %s kết thúc — không có người đặt giá. Auto-cancel.",
                        event.getAuction().getId());
                break;
            case RESERVE_NOT_MET_CLOSED:
                log = String.format("[GLOBAL - SYSTEM] Phiên %s kết thúc — giá cao nhất %.0f chưa đạt reserve. Auto-cancel.",
                        event.getAuction().getId(), event.getBidAmount());
                break;
            case PAYMENT_COMPLETED:
                log = String.format("[GLOBAL - SYSTEM] Thanh toán thành công: phiên %s",
                        event.getAuction().getId());
                break;
            case AUCTION_CANCELED:
                log = String.format("[GLOBAL - SYSTEM] Phiên bị hủy: %s", event.getAuction().getId());
                break;
            case FRAUD_DETECTED:
                log = String.format("[GLOBAL - SYSTEM] GIAN LẶN phát hiện tại phiên %s: %s",
                        event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "");
                break;
            case QUALITY_REPORT_APPROVED:
                log = String.format("[GLOBAL - SYSTEM] Báo cáo chất lượng được duyệt: phiên %s",
                        event.getAuction().getId());
                break;
            case SELLER_CANCEL_REQUEST:
                log = String.format("[GLOBAL - SYSTEM] Seller yêu cầu hủy phiên %s: %s",
                        event.getAuction().getId(),
                        event.getMessage() != null ? event.getMessage() : "");
                break;
            default:
                log = String.format("[GLOBAL - SYSTEM] Event: %s | Phiên: %s",
                        event.getEventType(), event.getAuction().getId());
                break;
        }
        systemAdmin.addActionLog(log);
        System.out.println(log);
    }
}