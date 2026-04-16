package com.group13.auction.model.bid;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Báo cáo của winner khi hàng không đúng chất lượng.
 *
 * <p>Sau khi admin phê duyệt, hệ thống trừ rating seller và yêu cầu
 * seller hoàn trả tiền trong 24h. Nếu không hoàn trả, seller bị ban vĩnh viễn.
 *
 * <p>Report phải đính kèm ảnh minh chứng ({@link #imageUrls}).
 */
public class QualityReport extends Entity {

    public enum ReportStatus {
        PENDING,  // chờ admin xét duyệt
        APPROVED, // admin chấp nhận — bắt đầu quy trình hoàn tiền
        REJECTED  // admin từ chối
    }

    private final NormalUser reporter; // winner
    private final String auctionId;
    private final String description;
    /** Danh sách URL ảnh minh chứng — bắt buộc phải có ít nhất 1 ảnh. */
    private final List<String> imageUrls;
    /**
     * Hạn hoàn tiền của Seller — 24h kể từ khi Admin APPROVED.
     * null cho đến khi report được approve.
     */
    private LocalDateTime sellerRefundDeadline;
    private ReportStatus status;

    // Static factory method

    /**
     * Khai sinh QualityReport khi winner / runner-up gửi báo cáo.
     * Bắt buộc phải đính kèm ít nhất 1 ảnh minh chứng.
     *
     * @param reporter winner / runner-up
     * @param auctionId id phiên
     * @param description mô tả vi phạm chất lượng
     * @param imageUrls danh sách URL ảnh minh chứng (không được rỗng)
     * @return QualityReport mới
     * @throws IllegalArgumentException nếu không có ảnh đính kèm
     */
    public static QualityReport create(
            NormalUser reporter,
            String auctionId,
            String description,
            List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException(
                    "Báo cáo chất lượng phải đính kèm ít nhất 1 ảnh minh chứng.");
        }
        return new QualityReport(reporter, auctionId, description, imageUrls);
    }

    public static QualityReport reconstitute(
            String id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            NormalUser reporter,
            String auctionId,
            String description,
            List<String> imageUrls,
            ReportStatus status,
            LocalDateTime sellerRefundDeadline) {
        return new QualityReport(
                id, createdAt, updatedAt, reporter, auctionId, description,
                imageUrls, status, sellerRefundDeadline);
    }

    // Private constructors

    private QualityReport(
            NormalUser reporter,
            String auctionId,
            String description,
            List<String> imageUrls) {
        super();
        this.reporter = reporter;
        this.auctionId = auctionId;
        this.description = description;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.status = ReportStatus.PENDING;
        this.sellerRefundDeadline = null;
    }

    private QualityReport(
            String id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            NormalUser reporter,
            String auctionId,
            String description,
            List<String> imageUrls,
            ReportStatus status,
            LocalDateTime sellerRefundDeadline) {
        super(id, createdAt, updatedAt);
        this.reporter = reporter;
        this.auctionId = auctionId;
        this.description = description;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.status = status;
        this.sellerRefundDeadline = sellerRefundDeadline;
    }

    // Getters

    public NormalUser getReporter() {
        return reporter;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getImageUrls() {
        return Collections.unmodifiableList(imageUrls);
    }

    public ReportStatus getStatus() {
        return status;
    }

    public LocalDateTime getSellerRefundDeadline() {
        return sellerRefundDeadline;
    }

    /**
     * Kiểm tra Seller đã quá hạn hoàn tiền chưa.
     *
     * @return true nếu đã quá hạn 24h và report đang ở APPROVED
     */
    public boolean isSellerRefundOverdue() {
        return status == ReportStatus.APPROVED
                && sellerRefundDeadline != null
                && LocalDateTime.now().isAfter(sellerRefundDeadline);
    }

    // Setters — chỉ QualityReportService / PaymentService gọi

    /**
     * Admin approve report.
     * Tự động set hạn hoàn tiền 24h cho Seller.
     * Chỉ {@link com.group13.auction.service.QualityReportService} gọi.
     */
    public void approve() {
        this.status = ReportStatus.APPROVED;
        this.sellerRefundDeadline = LocalDateTime.now().plusHours(24);
        markUpdated();
    }

    /**
     * Admin reject report.
     * Chỉ {@link com.group13.auction.service.QualityReportService} gọi.
     */
    public void reject() {
        this.status = ReportStatus.REJECTED;
        markUpdated();
    }

    @Override
    public void printInfo() {
        System.out.printf(
                "[QUALITY REPORT] %s | Auction: %s | Status: %s | Ảnh: %d%n",
                reporter.getUsername(), auctionId, status, imageUrls.size());
    }
}