package com.group13.auction.viewmodel.report;

/**
 * View model hiển thị báo cáo chất lượng ở phía người dùng.
 */
public class QualityReportViewModel {

    private final String reportId;
    private final String auctionId;
    private final String title;
    private final String reason;
    private final String description;
    private final String status;
    private final String createdAtText;

    /**
     * Tạo dữ liệu hiển thị cho một báo cáo chất lượng.
     *
     * @param reportId mã báo cáo
     * @param auctionId mã phiên đấu giá liên quan
     * @param title tiêu đề báo cáo
     * @param reason lý do báo cáo
     * @param description mô tả chi tiết
     * @param status trạng thái báo cáo
     * @param createdAtText thời gian tạo đã format
     */
    public QualityReportViewModel(
            String reportId,
            String auctionId,
            String title,
            String reason,
            String description,
            String status,
            String createdAtText) {
        this.reportId = reportId;
        this.auctionId = auctionId;
        this.title = title;
        this.reason = reason;
        this.description = description;
        this.status = status;
        this.createdAtText = createdAtText;
    }

    public String getReportId() {
        return reportId;
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
}