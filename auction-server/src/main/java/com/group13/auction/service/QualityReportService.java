package com.group13.auction.service;

import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.serviceInterface.IAuctionService;
import com.group13.auction.service.serviceInterface.IRatingService;
import com.group13.auction.service.serviceInterface.IWalletService;

/**
 * QualityReport: submit, approve, reject và hoàn tiền cho winner.
 *
 * <ol>
 *   <li>Winner gọi {@link #submitReport} → report ở PENDING.</li>
 *   <li>Admin gọi {@link #approveReport} → trừ rating Seller, bắt đầu đếm 24h hoàn tiền,
 *       notify Staff.</li>
 *   <li>Seller hoàn tiền trong 24h; nếu không → {@link #handleSellerRefundDefault} ban Seller.</li>
 * </ol>
 */
public class QualityReportService {

    private final IRatingService ratingService;
    private final IWalletService walletService;
    private final IAuctionService auctionService;

    // TODO: inject QualityReportDAO khi tích hợp DB
    // TODO: inject UserDAO để cập nhật trạng thái Seller sau khi bị ban

    /**
     * Constructor nhận dependency qua constructor (DIP).
     *
     * @param ratingService service quản lý rating
     * @param walletService service quản lý tài chính
     * @param auctionService service quản lý phiên
     */
    public QualityReportService(
            IRatingService ratingService,
            IWalletService walletService,
            IAuctionService auctionService) {
        this.ratingService = ratingService;
        this.walletService = walletService;
        this.auctionService = auctionService;
    }

    /**
     * Winner gửi báo cáo chất lượng hàng hóa.
     *
     * @param report report đã được tạo bởi winner
     * @return report vừa lưu
     * @throws IllegalArgumentException nếu report null hoặc không có ảnh
     */
    public QualityReport submitReport(QualityReport report) {
        if (report == null) {
            throw new IllegalArgumentException("QualityReport không được null.");
        }
        System.out.printf(
                "[QUALITY] Winner %s gửi báo cáo chất lượng cho phiên %s.%n",
                report.getReporter().getUsername(), report.getAuctionId());

        // TODO: qualityReportDAO.save(report) — lưu report xuống DB
        return report;
    }

    /**
     * Admin approve QualityReport.
     *
     * <p>Khi approve:
     * <ol>
     *   <li>Report chuyển sang APPROVED + set hạn 24h cho Seller.</li>
     *   <li>Trừ rating Seller, có thể auto-ban nếu rating xuống dưới ngưỡng.</li>
     *   <li>Hoàn tiền từ SystemBank về winner ngay lập tức.</li>
     *   <li>Notify Staff Admin để theo dõi.</li>
     * </ol>
     *
     * @param admin admin thực hiện approve
     * @param report report cần approve
     * @param auction phiên liên quan (để lấy seller và notify)
     * @throws IllegalStateException nếu report không ở PENDING
     */
    public void approveReport(Admin admin, QualityReport report, Auction auction) {
        if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
            throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
        }

        NormalUser winner = report.getReporter();
        NormalUser seller = auction.getItem().getSeller();

        // 1. Approve report (set deadline 24h cho Seller)
        report.approve();

        // 2. Phạt rating Seller
        ratingService.penalizeSeller(seller);
        SystemAdmin.getInstance().autoBanIfNeeded(seller);

        // 3. Hoàn tiền từ SystemBank + Seller về Winner
        double finalPrice = auction.getWinner() != null
                ? auction.getWinner().getFinalPrice()
                : 0;
        if (finalPrice > 0) {
            walletService.executeRefundToWinner(winner, seller, finalPrice, auction.getId());
        }

        // 4. Notify Staff Admin theo dõi
        AuctionEvent event = new AuctionEvent(
                AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED,
                auction, winner, 0,
                String.format("Admin %s approve report của %s", admin.getUsername(), winner.getUsername()));
        AuctionManager.getInstance().notifyStaffObservers(event);
        AuctionManager.getInstance().notifyGlobalObservers(event);

        String log = String.format(
                "[QUALITY] Admin %s approve report | Seller %s bị phạt | Winner %s được hoàn %.0f",
                admin.getUsername(), seller.getUsername(), winner.getUsername(), finalPrice);
        admin.addActionLog(log);
        SystemAdmin.getInstance().addActionLog(log);
        System.out.println(log);

        // TODO: qualityReportDAO.update(report) — cập nhật status + sellerRefundDeadline xuống DB
        // TODO: userDAO.updateAccountStatus(seller.getId(), seller.getAccountStatus().name())
    }

    /**
     * Admin reject QualityReport.
     *
     * @param admin admin thực hiện reject
     * @param report report cần reject
     * @throws IllegalStateException nếu report không ở PENDING
     */
    public void rejectReport(Admin admin, QualityReport report) {
        if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
            throw new IllegalStateException(
                    "Report không ở trạng thái PENDING: " + report.getStatus());
        }

        report.reject();

        String log = String.format(
                "[QUALITY] Admin %s reject report của %s | Phiên: %s",
                admin.getUsername(), report.getReporter().getUsername(), report.getAuctionId());
        admin.addActionLog(log);
        System.out.println(log);

        // TODO: qualityReportDAO.update(report)
    }

    /**
     * Kiểm tra Seller đã quá hạn hoàn tiền chưa và xử lý nếu có.
     *
     * <p>Được gọi bởi scheduler định kỳ (mỗi giờ).
     * Nếu quá hạn: ban Seller vĩnh viễn.
     *
     * @param report report đã APPROVED
     * @param auction phiên liên quan
     */
    public void handleSellerRefundDefault(QualityReport report, Auction auction) {
        if (!report.isSellerRefundOverdue()) {
            return;
        }

        NormalUser seller = auction.getItem().getSeller();
        seller.setAccountStatus(NormalUser.AccountStatus.BANNED);

        String log = String.format(
                "[QUALITY] Seller %s bị BAN VĨNH VIỄN do không hoàn trả trong 24h | Phiên: %s",
                seller.getUsername(), auction.getId());
        SystemAdmin.getInstance().addActionLog(log);
        System.out.println(log);

        // TODO: userDAO.updateAccountStatus(seller.getId(), "BANNED") — ban xuống DB
        // TODO: qualityReportDAO.update(report) nếu cần ghi nhận trạng thái xử lý
    }
}