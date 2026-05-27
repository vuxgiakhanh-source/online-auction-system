package com.group13.auction.common.dto.report;

import java.time.LocalDateTime;
import java.util.List;

/** Namespace class chứa toàn bộ DTO liên quan đến QualityReport. */
public final class ReportDTOs {

  private ReportDTOs() {}

  /** Payload của SUBMIT_QUALITY_REPORT. */
  public static class QualityReportRequestDTO {
    private String auctionId;
    private String description;

    /** Danh sách URL ảnh bằng chứng (upload trước, gửi link). */
    private List<String> evidenceUrls;

    public QualityReportRequestDTO() {}

    public String getAuctionId() {
      return auctionId;
    }

    public void setAuctionId(String auctionId) {
      this.auctionId = auctionId;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public List<String> getEvidenceUrls() {
      return evidenceUrls;
    }

    public void setEvidenceUrls(List<String> evidenceUrls) {
      this.evidenceUrls = evidenceUrls;
    }
  }

  /** DTO đầy đủ của một QualityReport — dùng để hiển thị cho Admin. */
  public static class QualityReportDTO {
    private String reportId;
    private String auctionId;
    private String auctionItemName;
    private String reporterId;
    private String reporterUsername;
    private String sellerId;
    private String sellerUsername;
    private String description;
    private List<String> evidenceUrls;

    /** "PENDING" | "APPROVED" | "REJECTED" */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime sellerRefundDeadline;
    private boolean refundCompleted;

    public QualityReportDTO() {}

    public String getReportId() {
      return reportId;
    }

    public void setReportId(String reportId) {
      this.reportId = reportId;
    }

    public String getAuctionId() {
      return auctionId;
    }

    public void setAuctionId(String auctionId) {
      this.auctionId = auctionId;
    }

    public String getAuctionItemName() {
      return auctionItemName;
    }

    public void setAuctionItemName(String auctionItemName) {
      this.auctionItemName = auctionItemName;
    }

    public String getReporterId() {
      return reporterId;
    }

    public void setReporterId(String reporterId) {
      this.reporterId = reporterId;
    }

    public String getReporterUsername() {
      return reporterUsername;
    }

    public void setReporterUsername(String reporterUsername) {
      this.reporterUsername = reporterUsername;
    }

    public String getSellerId() {
      return sellerId;
    }

    public void setSellerId(String sellerId) {
      this.sellerId = sellerId;
    }

    public String getSellerUsername() {
      return sellerUsername;
    }

    public void setSellerUsername(String sellerUsername) {
      this.sellerUsername = sellerUsername;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public List<String> getEvidenceUrls() {
      return evidenceUrls;
    }

    public void setEvidenceUrls(List<String> evidenceUrls) {
      this.evidenceUrls = evidenceUrls;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public LocalDateTime getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
    }

    public LocalDateTime getSellerRefundDeadline() {
      return sellerRefundDeadline;
    }

    public void setSellerRefundDeadline(LocalDateTime sellerRefundDeadline) {
      this.sellerRefundDeadline = sellerRefundDeadline;
    }

    public boolean isRefundCompleted() {
      return refundCompleted;
    }

    public void setRefundCompleted(boolean refundCompleted) {
      this.refundCompleted = refundCompleted;
    }
  }

  /** Payload của ADMIN_APPROVE_QUALITY_REPORT_SUCCESS và QUALITY_REPORT_APPROVED_NOTIFY. */
  public static class QualityReportResultDTO {
    private String reportId;
    private String auctionId;
    private double refundedAmount;
    private double sellerRatingPenalty;
    private double sellerNewRating;
    private boolean sellerBanned;

    public QualityReportResultDTO() {}

    public String getReportId() {
      return reportId;
    }

    public void setReportId(String reportId) {
      this.reportId = reportId;
    }

    public String getAuctionId() {
      return auctionId;
    }

    public void setAuctionId(String auctionId) {
      this.auctionId = auctionId;
    }

    public double getRefundedAmount() {
      return refundedAmount;
    }

    public void setRefundedAmount(double refundedAmount) {
      this.refundedAmount = refundedAmount;
    }

    public double getSellerRatingPenalty() {
      return sellerRatingPenalty;
    }

    public void setSellerRatingPenalty(double sellerRatingPenalty) {
      this.sellerRatingPenalty = sellerRatingPenalty;
    }

    public double getSellerNewRating() {
      return sellerNewRating;
    }

    public void setSellerNewRating(double sellerNewRating) {
      this.sellerNewRating = sellerNewRating;
    }

    public boolean isSellerBanned() {
      return sellerBanned;
    }

    public void setSellerBanned(boolean sellerBanned) {
      this.sellerBanned = sellerBanned;
    }
  }
}
