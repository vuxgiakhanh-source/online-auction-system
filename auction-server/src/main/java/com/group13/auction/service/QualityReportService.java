package com.group13.auction.service;

import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IQualityReportService;
import com.group13.auction.service.iservice.IRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * QualityReport: submit, approve, reject và hoàn tiền cho winner.
 *
 * <ol>
 * <li>Winner gọi {@link #submitReport} → report ở PENDING, notify winner xác nhận.</li>
 * <li>Admin gọi {@link #approveReport} → trừ rating Seller, hoàn tiền Winner,
 *     notify cả hai bên.</li>
 * <li>Admin gọi {@link #rejectReport} → notify winner biết kết quả.</li>
 * </ol>
 */
public class QualityReportService implements IQualityReportService {

    private static final Logger log = LoggerFactory.getLogger(QualityReportService.class);

    /** Per-report locks để tránh synchronized trên method parameter. */
    private final ConcurrentHashMap<String, Object> reportLocks = new ConcurrentHashMap<>();

    private final IRatingService   ratingService;
    private final IPaymentService  paymentService;
    private final QualityReportDAO qualityReportDAO;
    private final UserDAO          userDAO;
    private final NotificationDAO  notificationDAO;

    public QualityReportService(
        IRatingService ratingService,
        IPaymentService paymentService,
        QualityReportDAO qualityReportDAO,
        UserDAO userDAO) {
        this(ratingService, paymentService, qualityReportDAO, userDAO, new NotificationDAO());
    }

    public QualityReportService(
        IRatingService ratingService,
        IPaymentService paymentService,
        QualityReportDAO qualityReportDAO,
        UserDAO userDAO,
        NotificationDAO notificationDAO) {
        this.ratingService    = ratingService;
        this.paymentService   = paymentService;
        this.qualityReportDAO = qualityReportDAO;
        this.userDAO          = userDAO;
        this.notificationDAO  = notificationDAO;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    /**
     * Winner gửi báo cáo chất lượng hàng hóa.
     */
    @Override
    public QualityReport submitReport(QualityReport report) {
        if (report == null) {
            log.warn("Quality report submit rejected because report is null");
            throw new IllegalArgumentException("QualityReport không được null.");
        }

        // Gate: chỉ winner mới được report, sau khi đã xác nhận nhận hàng
        Auction auction = AuctionManager.getInstance().findAuctionById(report.getAuctionId());
        if (auction != null && auction.getWinner() != null) {
            com.group13.auction.model.auction.AuctionWinner auctionWinner = auction.getWinner();

            String reporterId = report.getReporter() != null ? report.getReporter().getId() : null;
            String winnerId   = auctionWinner.getWinner() != null ? auctionWinner.getWinner().getId() : null;
            if (reporterId == null || !reporterId.equals(winnerId)) {
                log.warn("Quality report rejected — reporter is not the auction winner: auctionId={}, reporterId={}, winnerId={}",
                    report.getAuctionId(), reporterId, winnerId);
                throw new IllegalStateException(
                    "Chỉ người thắng phiên đấu giá mới có thể gửi báo cáo chất lượng.");
            }

            com.group13.auction.model.auction.AuctionWinner.PaymentStatus status =
                auctionWinner.getPaymentStatus();
            if (status != com.group13.auction.model.auction.AuctionWinner.PaymentStatus.ITEM_RECEIVED) {
                log.warn("Quality report rejected — winner has not confirmed item received: auctionId={}, status={}",
                    report.getAuctionId(), status);
                throw new IllegalStateException(
                    "Vui lòng xác nhận đã nhận hàng trước khi gửi báo cáo chất lượng.");
            }
        }

        qualityReportDAO.saveReport(report);

        log.info("Quality report submitted: reportId={}, auctionId={}, reporterId={}, username={}",
            report.getId(), report.getAuctionId(), report.getReporter().getId(),
            report.getReporter().getUsername());

        // Notify reporter: báo cáo đã được tiếp nhận
        saveNotification(report.getReporter().getId(), report.getAuctionId(),
            "Báo cáo chất lượng đã gửi",
            "Báo cáo của bạn đã được tiếp nhận và đang chờ quản trị viên xem xét. "
                + "Chúng tôi sẽ thông báo khi có kết quả.");

        return report;
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    /**
     * Admin approve QualityReport — trừ rating Seller, hoàn tiền Winner.
     */
    @Override
    public void approveReport(Admin admin, QualityReport report, Auction auction) {
        Object lock = reportLocks.computeIfAbsent(report.getId(), id -> new Object());
        synchronized (lock) {
            if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
                log.warn("Approve quality report rejected because status is not PENDING: reportId={}, status={}",
                    report.getId(), report.getStatus());
                throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
            }

            NormalUser winner = report.getReporter();
            NormalUser seller = auction.getItem().getSeller();

            report.approve();
            ratingService.penalizeSeller(seller);
            SystemAdmin.getInstance().autoBanIfNeeded(seller);

            long finalPrice = auction.getWinner() != null
                ? auction.getWinner().getFinalPrice() : 0L;
            if (finalPrice > 0) {
                paymentService.refundToWinnerFromBank(auction);
                report.markRefundCompleted();
            }

            qualityReportDAO.updateReport(report);
            userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name());

            // Notify Staff
            AuctionEvent event = new AuctionEvent(
                AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED,
                auction, winner, 0L,
                String.format("Admin %s chấp nhận report của %s", admin.getUsername(), winner.getUsername()));
            AuctionManager.getInstance().notifyStaffObservers(event);
            AuctionManager.getInstance().notifyGlobalObservers(event);

            String entry = String.format(
                "[QUALITY] Admin %s chấp nhận report | Seller %s bị phạt | Winner %s được hoàn %d",
                admin.getUsername(), seller.getUsername(), winner.getUsername(), finalPrice);
            admin.addActionLog(entry);
            SystemAdmin.getInstance().addActionLog(entry);
            log.info("Quality report approved: reportId={}, auctionId={}, adminId={}, sellerId={}, winnerId={}, refundAmount={}",
                report.getId(), report.getAuctionId(), admin.getId(),
                seller.getId(), winner.getId(), finalPrice);

            // Notify winner: được hoàn tiền
            saveNotification(winner.getId(), report.getAuctionId(),
                "Báo cáo chất lượng được chấp thuận",
                String.format("Báo cáo của bạn đã được chấp thuận. "
                    + "Số tiền %d đã được hoàn vào tài khoản.", finalPrice));

            // Notify seller: bị phạt
            saveNotification(seller.getId(), report.getAuctionId(),
                "Cảnh báo: báo cáo chất lượng bị duyệt",
                String.format("Báo cáo chất lượng sản phẩm của bạn tại phiên %s đã được quản trị viên xác nhận. "
                        + "Điểm uy tín của bạn bị trừ. Trạng thái tài khoản: %s.",
                    report.getAuctionId(), seller.getAccountStatus().name()));
        }
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    /**
     * Admin reject QualityReport.
     */
    @Override
    public void rejectReport(Admin admin, QualityReport report) {
        Object lock = reportLocks.computeIfAbsent(report.getId(), id -> new Object());
        synchronized (lock) {
            if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
                log.warn("Reject quality report rejected because status is not PENDING: reportId={}, status={}",
                    report.getId(), report.getStatus());
                throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
            }

            report.reject();
            qualityReportDAO.updateReport(report);

            String entry = String.format(
                "[QUALITY] Admin %s từ chối report của %s | Phiên: %s",
                admin.getUsername(), report.getReporter().getUsername(), report.getAuctionId());
            admin.addActionLog(entry);
            log.info("Quality report rejected: reportId={}, auctionId={}, adminId={}, reporterId={}",
                report.getId(), report.getAuctionId(), admin.getId(), report.getReporter().getId());

            // Notify winner: báo cáo bị từ chối
            saveNotification(report.getReporter().getId(), report.getAuctionId(),
                "Báo cáo chất lượng bị từ chối",
                "Sau khi xem xét, quản trị viên quyết định không chấp thuận báo cáo chất lượng của bạn tại phiên "
                    + report.getAuctionId() + ".");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void saveNotification(String userId, String auctionId, String title, String body) {
        try {
            notificationDAO.save(Notification.create(userId, auctionId, title, body));
        } catch (Exception e) {
            log.warn("Không thể lưu notification: userId={}, title={}", userId, title, e);
        }
    }
}