package com.group13.auction.common.dto.bank;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** DTO namespace cho màn System Bank của Admin. */
public final class SystemBankDTOs {

  private SystemBankDTOs() {
    // Namespace class.
  }

  /** Tổng quan dòng tiền đang được SystemBank quản lý. */
  public static class SystemBankSummaryDTO {
    private long totalBalance;
    private long totalFundsHeld;
    private long totalPaymentReceived;
    private long totalTaxCollected;
    private long totalDepositForfeited;
    private long totalPayoutToSeller;
    private long totalRefundedToWinner;
    private LocalDateTime updatedAt;

    public SystemBankSummaryDTO() {}

    public long getTotalBalance() {
      return totalBalance;
    }

    public void setTotalBalance(long totalBalance) {
      this.totalBalance = totalBalance;
    }

    public long getTotalFundsHeld() {
      return totalFundsHeld;
    }

    public void setTotalFundsHeld(long totalFundsHeld) {
      this.totalFundsHeld = totalFundsHeld;
    }

    public long getTotalPaymentReceived() {
      return totalPaymentReceived;
    }

    public void setTotalPaymentReceived(long totalPaymentReceived) {
      this.totalPaymentReceived = totalPaymentReceived;
    }

    public long getTotalTaxCollected() {
      return totalTaxCollected;
    }

    public void setTotalTaxCollected(long totalTaxCollected) {
      this.totalTaxCollected = totalTaxCollected;
    }

    public long getTotalDepositForfeited() {
      return totalDepositForfeited;
    }

    public void setTotalDepositForfeited(long totalDepositForfeited) {
      this.totalDepositForfeited = totalDepositForfeited;
    }

    public long getTotalPayoutToSeller() {
      return totalPayoutToSeller;
    }

    public void setTotalPayoutToSeller(long totalPayoutToSeller) {
      this.totalPayoutToSeller = totalPayoutToSeller;
    }

    public long getTotalRefundedToWinner() {
      return totalRefundedToWinner;
    }

    public void setTotalRefundedToWinner(long totalRefundedToWinner) {
      this.totalRefundedToWinner = totalRefundedToWinner;
    }

    public LocalDateTime getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  /** Một bản ghi đối soát dòng tiền trong hệ thống. */
  public static class FinancialTransactionDTO {
    private String id;
    private String senderId;
    private String receiverId;
    private long amount;
    private String transactionType;
    private String auctionId;
    private LocalDateTime createdAt;

    public FinancialTransactionDTO() {}

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getSenderId() {
      return senderId;
    }

    public void setSenderId(String senderId) {
      this.senderId = senderId;
    }

    public String getReceiverId() {
      return receiverId;
    }

    public void setReceiverId(String receiverId) {
      this.receiverId = receiverId;
    }

    public long getAmount() {
      return amount;
    }

    public void setAmount(long amount) {
      this.amount = amount;
    }

    public String getTransactionType() {
      return transactionType;
    }

    public void setTransactionType(String transactionType) {
      this.transactionType = transactionType;
    }

    public String getAuctionId() {
      return auctionId;
    }

    public void setAuctionId(String auctionId) {
      this.auctionId = auctionId;
    }

    public LocalDateTime getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
    }
  }

  /** Request lấy danh sách giao dịch tài chính có phân trang và lọc nhẹ. */
  public static class FinancialTransactionListRequestDTO {
    private String transactionType;
    private String auctionId;
    private int page = 1;
    private int pageSize = 50;

    public FinancialTransactionListRequestDTO() {}

    public String getTransactionType() {
      return transactionType;
    }

    public void setTransactionType(String transactionType) {
      this.transactionType = transactionType;
    }

    public String getAuctionId() {
      return auctionId;
    }

    public void setAuctionId(String auctionId) {
      this.auctionId = auctionId;
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = page;
    }

    public int getPageSize() {
      return pageSize;
    }

    public void setPageSize(int pageSize) {
      this.pageSize = pageSize;
    }
  }

  /** Response danh sách giao dịch tài chính. */
  public static class FinancialTransactionListDTO {
    private List<FinancialTransactionDTO> transactions = new ArrayList<>();
    private int page;
    private int pageSize;
    private int totalItems;

    public FinancialTransactionListDTO() {}

    public List<FinancialTransactionDTO> getTransactions() {
      return transactions;
    }

    public void setTransactions(List<FinancialTransactionDTO> transactions) {
      this.transactions = transactions != null ? transactions : new ArrayList<>();
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = page;
    }

    public int getPageSize() {
      return pageSize;
    }

    public void setPageSize(int pageSize) {
      this.pageSize = pageSize;
    }

    public int getTotalItems() {
      return totalItems;
    }

    public void setTotalItems(int totalItems) {
      this.totalItems = totalItems;
    }
  }
}