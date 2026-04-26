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
import com.group13.auction.service.iservice.IWalletService;

/**
 * QualityReport: submit, approve, reject và hoàn tiền cho winner.
 * TODO: SOS: Chưa có hướng xử lí gửi thông báo tới reporter - seller
 * -> đã có hướng giải quyết qua notificationDao
 *
 * <ol>
 * <li>Winner gọi {@link #submitReport} -> report ở PENDING.</li>
 * <li>Admin gọi {@link #approveReport} -> trừ rating Seller, bắt đầu đếm 24h hoàn tiền,
 * notify Staff.</li>
 * <li>Seller hoàn tiền trong 24h; nếu không -> {@link #handleSellerRefundDefault} ban Seller.</li>
 * </ol>
 */
public class
QualityReportService implements IQualityReportService {

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
            throw new IllegalArgumentException("QualityReport không được null.");
        }
        System.out.printf(
                "[QUALITY] Winner %s gửi báo cáo chất lượng cho phiên %s.%n",
                report.getReporter().getUsername(), report.getAuctionId());

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
     * @param admin admin thực hiện approve
     * @param report report cần approve
     * @param auction phiên liên quan (để lấy seller và notify)
     * @throws IllegalStateException nếu report không ở PENDING
     */
    @Override
    public void approveReport(Admin admin, QualityReport report, Auction auction) {
        if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
            throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
        }

        NormalUser winner = report.getReporter();
        NormalUser seller = auction.getItem().getSeller();

        // Approve report (set deadline 24h cho Seller)
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
        System.out.println(log);

        // Thực hiện TODO: qualityReportDAO.update(report) — cập nhật status + sellerRefundDeadline xuống DB
        qualityReportDAO.updateReport(report);

        // Thực hiện TODO: userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name())
        userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name());
    }

    /**
     * Admin reject QualityReport.
     *
     * @param admin admin thực hiện reject
     * @param report report cần reject
     * @throws IllegalStateException nếu report không ở PENDING
     */
    @Override
    public void rejectReport(Admin admin, QualityReport report) {
        if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
            throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
        }

        report.reject();

        String log = String.format(
                "[QUALITY] Admin %s từ chối report của %s | Phiên: %s",
                admin.getUsername(), report.getReporter().getUsername(), report.getAuctionId());
        admin.addActionLog(log);
        System.out.println(log);

        // Thực hiện TODO: qualityReportDAO.update(report)
        qualityReportDAO.updateReport(report);
    }

    /**
     * Kiểm tra Seller đã quá hạn hoàn tiền chưa và xử lý nếu có.
     *
     * Nếu quá hạn -> cho Seller ăn ban vĩnh viễn.
     *
     * @param report report đã APPROVED
     * @param auction phiên liên quan
     */
    @Override
    public void handleSellerRefundDefault(QualityReport report, NormalUser reporter, Auction auction) {
        if (!report.isSellerRefundOverdue()) {
            return;
        }
        if (report.isRefundCompleted()) {
            return;
        }

        NormalUser seller = auction.getItem().getSeller();
        seller.setAccountStatus(NormalUser.AccountStatus.BANNED);

        String log = String.format(
                "[QUALITY] Seller %s bị BAN VĨNH VIỄN do không hoàn trả trong 24h | Phiên: %s",
                seller.getUsername(), auction.getId());
        SystemAdmin.getInstance().addActionLog(log);
        System.out.println(log);

        paymentService.refundToWinnerFromBank(auction);

        // Thực hiện TODO: ban xuống DB
        userDAO.updateAccountStatus(seller.getId(), "BANNED");

        // Thực hiện TODO: qualityReportDAO.update(report) nếu cần ghi nhận trạng thái xử lý
        // qualityReportDAO.updateReport(report); // Có thể bỏ comment dòng này nếu DB của bạn cần cập nhật thêm gì đó khi seller bị phạt.
    }
}