package com.group13.auction.common.dto.payment;

import java.time.LocalDateTime;

/**
 * Payload của CONFIRM_ITEM_RECEIVED_SUCCESS. Server trả về sau khi winner xác nhận đã nhận hàng.
 *
 * <p>{@code canSubmitReport = true} — client dùng để enable nút báo cáo chất lượng. {@code
 * reportDeadline} — deadline để submit Quality Report (3 ngày kể từ khi xác nhận).
 */
public class ConfirmItemReceivedResultDTO {

  private String auctionId;
  private boolean canSubmitReport;
  private LocalDateTime confirmedAt;
  private LocalDateTime reportDeadline;

  public ConfirmItemReceivedResultDTO() {}

  public String getAuctionId() {
    return auctionId;
  }

  public void setAuctionId(String auctionId) {
    this.auctionId = auctionId;
  }

  public boolean isCanSubmitReport() {
    return canSubmitReport;
  }

  public void setCanSubmitReport(boolean canSubmitReport) {
    this.canSubmitReport = canSubmitReport;
  }

  public LocalDateTime getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(LocalDateTime confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public LocalDateTime getReportDeadline() {
    return reportDeadline;
  }

  public void setReportDeadline(LocalDateTime reportDeadline) {
    this.reportDeadline = reportDeadline;
  }
}
