package com.group13.auction.observer;

import com.group13.auction.model.user.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        log.info("Admin observer received bid event: adminId={}, username={}, eventType={}, auctionId={}, bidderId={}, amount={}",
                admin.getId(), admin.getUsername(), event.getEventType(), event.getAuction().getId(),
                event.getBidder() != null ? event.getBidder().getId() : null, event.getBidAmount());
    }

    @Override
    public void onAuctionEnded(AuctionEvent event) {
        log.info("Admin observer received auction event: adminId={}, username={}, eventType={}, auctionId={}, amount={}",
                admin.getId(), admin.getUsername(), event.getEventType(),
                event.getAuction().getId(), event.getBidAmount());
        switch (event.getEventType()) {
            case AUCTION_STARTED:
                // TODO: notificationDao.save()
                break;
            case AUCTION_ENDED:
                // TODO: notificationDao.save()
                break;
            case AUCTION_NO_WINNER:
                // TODO: notificationDao.save()
                break;
            case RESERVE_NOT_MET_CLOSED:
                // TODO: notificationDao.save()
                break;
            case PAYMENT_COMPLETED:
                // TODO: notificationDao.save()
                break;
            case AUCTION_CANCELED:
                // TODO: notificationDao.save()
                break;
            case FRAUD_DETECTED:
                // TODO: notificationDao.save()
                break;
            case QUALITY_REPORT_APPROVED:
                // TODO: notificationDao.save()
                break;
            default:
                break;
        }
    }
}
