package com.group13.auction.viewmodel.payment;

/** Dữ liệu kết quả thanh toán đã format để hiển thị trên giao diện client. */
public final class PaymentResultViewModel {

  private final String auctionId;
  private final String finalPriceText;
  private final String depositDeductedText;
  private final String remainingToPayText;
  private final String newBalanceText;
  private final String paymentStatus;
  private final String paymentStatusText;
  private final String paidAtText;
  private final boolean completed;

  /**
   * Tạo view model kết quả thanh toán.
   *
   * @param auctionId mã phiên đấu giá
   * @param finalPriceText giá chốt đã format
   * @param depositDeductedText tiền cọc đã trừ đã format
   * @param remainingToPayText phần còn phải thanh toán đã format
   * @param newBalanceText số dư mới đã format
   * @param paymentStatus trạng thái thanh toán thô từ server
   * @param paymentStatusText trạng thái thanh toán đã format
   * @param paidAtText thời điểm thanh toán đã format
   * @param completed true nếu thanh toán đã hoàn tất
   */
  public PaymentResultViewModel(
      String auctionId,
      String finalPriceText,
      String depositDeductedText,
      String remainingToPayText,
      String newBalanceText,
      String paymentStatus,
      String paymentStatusText,
      String paidAtText,
      boolean completed) {
    this.auctionId = auctionId;
    this.finalPriceText = finalPriceText;
    this.depositDeductedText = depositDeductedText;
    this.remainingToPayText = remainingToPayText;
    this.newBalanceText = newBalanceText;
    this.paymentStatus = paymentStatus;
    this.paymentStatusText = paymentStatusText;
    this.paidAtText = paidAtText;
    this.completed = completed;
  }

  public String auctionId() {
    return auctionId;
  }

  public String finalPriceText() {
    return finalPriceText;
  }

  public String depositDeductedText() {
    return depositDeductedText;
  }

  public String remainingToPayText() {
    return remainingToPayText;
  }

  public String newBalanceText() {
    return newBalanceText;
  }

  public String paymentStatus() {
    return paymentStatus;
  }

  public String paymentStatusText() {
    return paymentStatusText;
  }

  public String paidAtText() {
    return paidAtText;
  }

  public boolean completed() {
    return completed;
  }
}
