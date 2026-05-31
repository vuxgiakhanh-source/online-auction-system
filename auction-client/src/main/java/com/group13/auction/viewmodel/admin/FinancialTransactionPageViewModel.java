package com.group13.auction.viewmodel.admin;

import java.util.List;

/** ViewModel mot trang giao dich tai chinh cho man System Bank. */
public final class FinancialTransactionPageViewModel {

  private final List<FinancialTransactionViewModel> transactions;
  private final int page;
  private final int pageSize;
  private final int totalItems;
  private final int totalPages;

  public FinancialTransactionPageViewModel(
      List<FinancialTransactionViewModel> transactions, int page, int pageSize, int totalItems) {
    this.page = Math.max(1, page);
    this.pageSize = Math.max(1, pageSize);
    this.totalItems = Math.max(0, totalItems);
    this.totalPages = calculateTotalPages(this.totalItems, this.pageSize);
    this.transactions = transactions == null ? List.of() : List.copyOf(transactions);
  }

  public List<FinancialTransactionViewModel> getTransactions() {
    return transactions;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public int getTotalItems() {
    return totalItems;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public boolean hasPreviousPage() {
    return page > 1;
  }

  public boolean hasNextPage() {
    return page < totalPages;
  }

  private static int calculateTotalPages(int totalItems, int pageSize) {
    if (totalItems <= 0) {
      return 1;
    }
    return (int) Math.ceil((double) totalItems / pageSize);
  }
}
