package com.group13.auction.viewmodel.admin;

/**
 * View model hiển thị báo cáo chất lượng trong màn Admin Report Review.
 */
public class QualityReportReviewViewModel {

    private final String reportId;
    private final String reporterId;
    private final String auctionId;
    private final String title;
    private final String reason;
    private final String description;
    private final String status;
    private final String createdAtText;
    private final boolean reviewable;

    /**
     * Tạo dữ liệu hiển thị cho một báo cáo chất lượng.
     *
     * @param reportId mã báo cáo
     * @param reporterId mã người gửi báo cáo
     * @param auctionId mã phiên đấu giá liên quan
     * @param title tiêu đề báo cáo
     * @param reason lý do báo cáo
     * @param description mô tả chi tiết
     * @param status trạng thái báo cáo
     * @param createdAtText thời gian tạo đã format
     * @param reviewable true nếu admin có thể review báo cáo này
     */
    public QualityReportReviewViewModel(
            String reportId,
            String reporterId,
            String auctionId,
            String title,
            String reason,
            String description,
            String status,
            String createdAtText,
            boolean reviewable) {
        this.reportId = reportId;
        this.reporterId = reporterId;
        this.auctionId = auctionId;
        this.title = title;
        this.reason = reason;
        this.description = description;
        this.status = status;
        this.createdAtText = createdAtText;
        this.reviewable = reviewable;
    }

    public String getReportId() {
        return reportId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getTitle() {
        return title;
    }

    public String getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAtText() {
        return createdAtText;
    }

    public boolean isReviewable() {
        return reviewable;
    }
}