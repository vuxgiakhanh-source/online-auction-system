package com.group13.auction.viewmodel.order;

/** Dữ liệu đã format để hiển thị một đơn hàng đấu giá đã thắng. */
public final class WonOrderViewModel {

  private final String auctionId;
  private final String itemName;
  private final String sellerUsername;
  private final String sellerText;
  private final String winningPriceText;
  private final String auctionStatus;
  private final String paymentStatus;
  private final String statusText;
  private final String actionHintText;
  private final String primaryImageUrl;
  private final String confirmReceiptDeadlineText;
  private final String reportDeadlineText;
  private final boolean canPay;
  private final boolean canConfirmReceipt;
  private final boolean canSubmitReport;
  private final boolean completed;
  private final boolean finished;

  /** Tạo view model cho một đơn hàng đã thắng. */
  public WonOrderViewModel(
      String auctionId,
      String itemName,
      String sellerUsername,
      String sellerText,
      String winningPriceText,
      String auctionStatus,
      String paymentStatus,
      String statusText,
      String actionHintText,
      String primaryImageUrl,
      String confirmReceiptDeadlineText,
      String reportDeadlineText,
      boolean canPay,
      boolean canConfirmReceipt,
      boolean canSubmitReport,
      boolean completed,
      boolean finished) {
    this.auctionId = safe(auctionId);
    this.itemName = isBlank(itemName) ? "Sản phẩm đấu giá" : itemName.trim();
    this.sellerUsername = isBlank(sellerUsername) ? "--" : sellerUsername.trim();
    this.sellerText = isBlank(sellerText) ? "Người bán: --" : sellerText.trim();
    this.winningPriceText = isBlank(winningPriceText) ? "--" : winningPriceText.trim();
    this.auctionStatus = safe(auctionStatus);
    this.paymentStatus = safe(paymentStatus);
    this.statusText = isBlank(statusText) ? "Không rõ" : statusText.trim();
    this.actionHintText = safe(actionHintText);
    this.primaryImageUrl = safe(primaryImageUrl);
    this.confirmReceiptDeadlineText =
        isBlank(confirmReceiptDeadlineText) ? "--" : confirmReceiptDeadlineText.trim();
    this.reportDeadlineText = isBlank(reportDeadlineText) ? "--" : reportDeadlineText.trim();
    this.canPay = canPay;
    this.canConfirmReceipt = canConfirmReceipt;
    this.canSubmitReport = canSubmitReport;
    this.completed = completed;
    this.finished = finished;
  }

  public String auctionId() {
    return auctionId;
  }

  public String itemName() {
    return itemName;
  }

  public String sellerUsername() {
    return sellerUsername;
  }

  public String sellerText() {
    return sellerText;
  }

  public String winningPriceText() {
    return winningPriceText;
  }

  public String auctionStatus() {
    return auctionStatus;
  }

  /** Giữ method cũ để các controller đang dùng không bị vỡ compile. */
  public String status() {
    return auctionStatus;
  }

  public String paymentStatus() {
    return paymentStatus;
  }

  public String statusText() {
    return statusText;
  }

  public String actionHintText() {
    return actionHintText;
  }

  public String primaryImageUrl() {
    return primaryImageUrl;
  }

  public String confirmReceiptDeadlineText() {
    return confirmReceiptDeadlineText;
  }

  public String reportDeadlineText() {
    return reportDeadlineText;
  }

  public boolean hasImage() {
    return !primaryImageUrl.isBlank();
  }

  public boolean canPay() {
    return canPay;
  }

  public boolean canConfirmReceipt() {
    return canConfirmReceipt;
  }

  public boolean canSubmitReport() {
    return canSubmitReport;
  }

  public boolean completed() {
    return completed;
  }

  /** Giữ method cũ để các controller đang dùng không bị vỡ compile. */
  public boolean paid() {
    return "PAID".equalsIgnoreCase(auctionStatus);
  }

  public boolean finished() {
    return finished;
  }

  /** Đơn hàng chỉ gửi báo cáo sau khi đã xác nhận nhận hàng. */
  public boolean reportable() {
    return canSubmitReport;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
