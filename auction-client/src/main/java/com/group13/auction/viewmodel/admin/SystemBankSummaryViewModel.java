package com.group13.auction.viewmodel.admin;

/** ViewModel tổng quan System Bank đã được format cho UI. */
public final class SystemBankSummaryViewModel {

  private final String totalBalanceText;
  private final String totalFundsHeldText;
  private final String totalPaymentReceivedText;
  private final String totalTaxCollectedText;
  private final String totalDepositForfeitedText;
  private final String totalPayoutToSellerText;
  private final String totalRefundedToWinnerText;
  private final String updatedAtText;

  public SystemBankSummaryViewModel(
      String totalBalanceText,
      String totalFundsHeldText,
      String totalPaymentReceivedText,
      String totalTaxCollectedText,
      String totalDepositForfeitedText,
      String totalPayoutToSellerText,
      String totalRefundedToWinnerText,
      String updatedAtText) {
    this.totalBalanceText = totalBalanceText;
    this.totalFundsHeldText = totalFundsHeldText;
    this.totalPaymentReceivedText = totalPaymentReceivedText;
    this.totalTaxCollectedText = totalTaxCollectedText;
    this.totalDepositForfeitedText = totalDepositForfeitedText;
    this.totalPayoutToSellerText = totalPayoutToSellerText;
    this.totalRefundedToWinnerText = totalRefundedToWinnerText;
    this.updatedAtText = updatedAtText;
  }

  public String getTotalBalanceText() {
    return totalBalanceText;
  }

  public String getTotalFundsHeldText() {
    return totalFundsHeldText;
  }

  public String getTotalPaymentReceivedText() {
    return totalPaymentReceivedText;
  }

  public String getTotalTaxCollectedText() {
    return totalTaxCollectedText;
  }

  public String getTotalDepositForfeitedText() {
    return totalDepositForfeitedText;
  }

  public String getTotalPayoutToSellerText() {
    return totalPayoutToSellerText;
  }

  public String getTotalRefundedToWinnerText() {
    return totalRefundedToWinnerText;
  }

  public String getUpdatedAtText() {
    return updatedAtText;
  }
}