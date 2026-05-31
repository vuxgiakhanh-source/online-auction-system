package com.group13.auction.viewmodel.admin;

/** ViewModel một giao dịch tài chính trong màn System Bank. */
public final class FinancialTransactionViewModel {

  private final String id;
  private final String transactionTypeText;
  private final String amountText;
  private final String senderId;
  private final String receiverId;
  private final String auctionId;
  private final String createdAtText;

  public FinancialTransactionViewModel(
      String id,
      String transactionTypeText,
      String amountText,
      String senderId,
      String receiverId,
      String auctionId,
      String createdAtText) {
    this.id = id;
    this.transactionTypeText = transactionTypeText;
    this.amountText = amountText;
    this.senderId = senderId;
    this.receiverId = receiverId;
    this.auctionId = auctionId;
    this.createdAtText = createdAtText;
  }

  public String getId() {
    return id;
  }

  public String getTransactionTypeText() {
    return transactionTypeText;
  }

  public String getAmountText() {
    return amountText;
  }

  public String getSenderId() {
    return senderId;
  }

  public String getReceiverId() {
    return receiverId;
  }

  public String getAuctionId() {
    return auctionId;
  }

  public String getCreatedAtText() {
    return createdAtText;
  }
}