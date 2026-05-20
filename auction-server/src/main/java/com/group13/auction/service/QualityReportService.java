package com.group13.auction.service;

import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
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
 * TODO: SOS: Chưa có hướng xử lí gửi thông báo tới reporter - seller
 * -> đã có hướng giải quyết qua notificationDao
 *
 * <ol>
 * <li>Winner gọi {@link #submitReport} -> report ở PENDING.</li>
 * <li>Admin gọi {@link #approveReport} -> trừ rating Seller, PaymentService hoàn tiền cho Winner
 * </ol>
 */
public class
QualityReportService implements IQualityReportService {

    private static final Logger log = LoggerFactory.getLogger(QualityReportService.class);

    /**
     * Per-report locks để tránh synchronized trên method parameter.
     * Key = report.getId(). Lock được tạo lazily và dùng chung cho mọi
     * thread cùng thao tác trên cùng 1 report.
     */
    private final ConcurrentHashMap<String, Object> reportLocks = new ConcurrentHashMap<>();

    private final IRatingService ratingService;
    private final IPaymentService paymentService;

    // Đã thực hiện TODO: inject QualityReportDAO và UserDAO
    private final QualityReportDAO qualityReportDAO;
    private final UserDAO userDAO;

    /**
     * Constructor nhận dependency qua constructor (DIP).
     *
     * @param ratingService service quản lý rating
     */
    public QualityReportService(
        IRatingService ratingService,
        IPaymentService paymentService,
        QualityReportDAO qualityReportDAO,
        UserDAO userDAO) {
        this.ratingService = ratingService;
        this.paymentService = paymentService;
        this.qualityReportDAO = qualityReportDAO;
        this.userDAO = userDAO;
    }

    /**
     * Winner gửi báo cáo chất lượng hàng hóa.
     *
     * @param report report đã được tạo bởi winner
     * @return report vừa lưu
     * @throws IllegalArgumentException nếu report null hoặc không có ảnh
     */
    @Override
    public QualityReport submitReport(QualityReport report) {
        if (report == null) {
            log.warn("Quality report submit rejected because report is null");
            throw new IllegalArgumentException("QualityReport không được null.");
        }

        // Gate: chỉ được report sau khi đã xác nhận nhận hàng (ITEM_RECEIVED)
        com.group13.auction.model.auction.Auction auction =
            AuctionManager.getInstance().findAuctionById(report.getAuctionId());
        if (auction != null && auction.getWinner() != null) {
            com.group13.auction.model.auction.AuctionWinner.PaymentStatus status =
                auction.getWinner().getPaymentStatus();
            if (status != com.group13.auction.model.auction.AuctionWinner.PaymentStatus.ITEM_RECEIVED) {
                log.warn("Quality report rejected — winner has not confirmed item received: auctionId={}, status={}",
                    report.getAuctionId(), status);
                throw new IllegalStateException(
                    "Vui lòng xác nhận đã nhận hàng trước khi gửi báo cáo chất lượng.");
            }
        }

        log.info("Quality report submitted: reportId={}, auctionId={}, reporterId={}, username={}",
            report.getId(), report.getAuctionId(), report.getReporter().getId(),
            report.getReporter().getUsername());

        // Thực hiện TODO: qualityReportDAO.save(report) — lưu report xuống DB
        qualityReportDAO.saveReport(report);
        // TODO: notificationDao.save() - báo cho người report

        return report;
    }

    /**
     * Admin approve QualityReport.
     *
     * <ol>
     * <li>Report chuyển sang APPROVED + set hạn 24h cho Seller.</li>
     * <li>Trừ rating Seller, có thể auto-ban nếu rating xuống dưới ngưỡng.</li>
     * <li>Hoàn tiền từ SystemBank về winner ngay lập tức.</li>
     * <li>Notify Staff Admin để theo dõi.</li>
     * </ol>
     *
     * <p><b>Thread-safety:</b> synchronized trên {@code report} để chặn race condition
     * khi 2 admin cùng approve/reject cùng một report lúc đó — chỉ admin đầu tiên
     * qua được, admin thứ hai sẽ thấy status != PENDING và nhận {@link IllegalStateException}.</p>
     *
     * @param admin   admin thực hiện approve
     * @param report  report cần approve
     * @param auction phiên liên quan (để lấy seller và notify)
     * @throws IllegalStateException nếu report không ở PENDING
     */
    @Override
    public void approveReport(Admin admin, QualityReport report, Auction auction) {
        Object lock = reportLocks.computeIfAbsent(report.getId(), id -> new Object());
        synchronized (lock) {
            if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
                log.warn("Approve quality report rejected because status is not PENDING: reportId={}, auctionId={}, status={}",
                    report.getId(), report.getAuctionId(), report.getStatus());
                throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
            }

            NormalUser winner = report.getReporter();
            NormalUser seller = auction.getItem().getSeller();

            // Approve report
            report.approve();

            // Phạt rating Seller
            ratingService.penalizeSeller(seller);
            SystemAdmin.getInstance().autoBanIfNeeded(seller);

            // Hoàn tiền từ SystemBank + Seller về Winner
            long finalPrice = auction.getWinner() != null
                ? auction.getWinner().getFinalPrice()
                : 0L;
            if (finalPrice > 0) {
                paymentService.refundToWinnerFromBank(auction);
                report.markRefundCompleted();
            }

            // Cập nhật status + refund_completed xuống DB
            qualityReportDAO.updateReport(report);

            // Cập nhật account status của seller (có thể đã bị ban)
            userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name());

            // Notify Staff Admin theo dõi
            AuctionEvent event = new AuctionEvent(
                AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED,
                auction, winner, 0L,
                String.format("Admin %s chấp nhận report của %s", admin.getUsername(), winner.getUsername()));
            AuctionManager.getInstance().notifyStaffObservers(event);
            AuctionManager.getInstance().notifyGlobalObservers(event);

            String log = String.format(
                "[QUALITY] Admin %s chấp nhận report | Seller %s bị phạt | Winner %s được hoàn %d",
                admin.getUsername(), seller.getUsername(), winner.getUsername(), finalPrice);
            admin.addActionLog(log);
            SystemAdmin.getInstance().addActionLog(log);
            QualityReportService.log.info("Quality report approved: reportId={}, auctionId={}, adminId={}, sellerId={}, winnerId={}, refundAmount={}",
                report.getId(), report.getAuctionId(), admin.getId(), seller.getId(), winner.getId(), finalPrice);
        }
    }

    /**
     * Admin reject QualityReport.
     *
     * <p><b>Thread-safety:</b> synchronized trên {@code report} — xem javadoc
     * {@link #approveReport} để biết lý do.</p>
     *
     * @param admin  admin thực hiện reject
     * @param report report cần reject
     * @throws IllegalStateException nếu report không ở PENDING
     */
    @Override
    public void rejectReport(Admin admin, QualityReport report) {
        Object lock = reportLocks.computeIfAbsent(report.getId(), id -> new Object());
        synchronized (lock) {
            if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
                log.warn("Reject quality report rejected because status is not PENDING: reportId={}, auctionId={}, status={}",
                    report.getId(), report.getAuctionId(), report.getStatus());
                throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
            }

            report.reject();

            // Cập nhật status xuống DB
            qualityReportDAO.updateReport(report);

            String log = String.format(
                "[QUALITY] Admin %s từ chối report của %s | Phiên: %s",
                admin.getUsername(), report.getReporter().getUsername(), report.getAuctionId());
            admin.addActionLog(log);
            QualityReportService.log.info("Quality report rejected: reportId={}, auctionId={}, adminId={}, reporterId={}",
                report.getId(), report.getAuctionId(), admin.getId(), report.getReporter().getId());
        }
    }
}