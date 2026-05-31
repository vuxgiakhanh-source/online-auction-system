package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.common.dto.bank.SystemBankDTOs.FinancialTransactionDTO;
import com.group13.auction.common.dto.bank.SystemBankDTOs.FinancialTransactionListDTO;
import com.group13.auction.common.dto.bank.SystemBankDTOs.FinancialTransactionListRequestDTO;
import com.group13.auction.common.dto.bank.SystemBankDTOs.SystemBankSummaryDTO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.SystemBankDAO;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import java.util.List;

/** Service đọc dữ liệu System Bank cho màn đối soát của Admin. */
public class SystemBankAdminService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 200;

  private final SystemBank systemBank;
  private final SystemBankDAO systemBankDAO;
  private final FinancialTransactionDAO financialTransactionDAO;

  /** Khởi tạo service bằng các DAO mặc định. */
  public SystemBankAdminService() {
    this(SystemBank.getInstance(), new SystemBankDAO(), new FinancialTransactionDAO());
  }

  /** Constructor hỗ trợ test hoặc inject DAO tùy chỉnh. */
  public SystemBankAdminService(
      SystemBank systemBank,
      SystemBankDAO systemBankDAO,
      FinancialTransactionDAO financialTransactionDAO) {
    this.systemBank = systemBank;
    this.systemBankDAO = systemBankDAO;
    this.financialTransactionDAO = financialTransactionDAO;
  }

  /** Lấy tổng quan số dư và dòng tiền System Bank. */
  public SystemBankSummaryDTO getSummary() {
    long totalPaymentReceived =
        financialTransactionDAO.sumAmountByTypes(
            TransactionType.PAYMENT_FROM_WINNER, TransactionType.SECOND_CHANCE_PAYMENT);
    long totalTaxCollected = financialTransactionDAO.sumAmountByType(TransactionType.TAX_COLLECTED);
    long totalDepositForfeited =
        financialTransactionDAO.sumAmountByType(TransactionType.DEPOSIT_FORFEIT);
    long totalPayoutToSeller =
        financialTransactionDAO.sumAmountByType(TransactionType.PAYOUT_TO_SELLER);
    long totalRefundedToWinner =
        financialTransactionDAO.sumAmountByType(TransactionType.REFUND_TO_WINNER);

    long totalFundsHeld =
        Math.max(
            0L,
            totalPaymentReceived
                - totalTaxCollected
                - totalPayoutToSeller
                - totalRefundedToWinner);

    SystemBankSummaryDTO dto = new SystemBankSummaryDTO();
    dto.setTotalBalance(systemBank.getTotalBalanceFromDatabase());
    dto.setTotalFundsHeld(totalFundsHeld);
    dto.setTotalPaymentReceived(totalPaymentReceived);
    dto.setTotalTaxCollected(totalTaxCollected);
    dto.setTotalDepositForfeited(totalDepositForfeited);
    dto.setTotalPayoutToSeller(totalPayoutToSeller);
    dto.setTotalRefundedToWinner(totalRefundedToWinner);
    dto.setUpdatedAt(systemBankDAO.loadUpdatedAt());
    return dto;
  }

  /** Lấy danh sách giao dịch tài chính có lọc và phân trang. */
  public FinancialTransactionListDTO getTransactions(FinancialTransactionListRequestDTO request) {
    FinancialTransactionListRequestDTO safeRequest = request != null ? request : emptyRequest();
    int page = normalizePage(safeRequest.getPage());
    int pageSize = normalizePageSize(safeRequest.getPageSize());
    String transactionType = normalizeTransactionType(safeRequest.getTransactionType());
    String auctionId = normalizeBlank(safeRequest.getAuctionId());

    List<FinancialTransactionDTO> transactions =
        financialTransactionDAO.findTransactions(transactionType, auctionId, page, pageSize).stream()
            .map(this::toDto)
            .toList();

    FinancialTransactionListDTO response = new FinancialTransactionListDTO();
    response.setTransactions(transactions);
    response.setPage(page);
    response.setPageSize(pageSize);
    response.setTotalItems(financialTransactionDAO.countTransactions(transactionType, auctionId));
    return response;
  }

  private FinancialTransactionDTO toDto(FinancialTransaction transaction) {
    FinancialTransactionDTO dto = new FinancialTransactionDTO();
    dto.setId(transaction.getId());
    dto.setSenderId(transaction.getFromUserId());
    dto.setReceiverId(transaction.getToUserId());
    dto.setAmount(transaction.getAmount());
    dto.setTransactionType(transaction.getType().name());
    dto.setAuctionId(transaction.getAuctionId());
    dto.setCreatedAt(transaction.getCreatedAt());
    return dto;
  }

  private FinancialTransactionListRequestDTO emptyRequest() {
    return new FinancialTransactionListRequestDTO();
  }

  private int normalizePage(int page) {
    return Math.max(DEFAULT_PAGE, page);
  }

  private int normalizePageSize(int pageSize) {
    if (pageSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private String normalizeTransactionType(String transactionType) {
    String normalized = normalizeBlank(transactionType);
    if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
      return null;
    }
    TransactionType.valueOf(normalized);
    return normalized;
  }

  private String normalizeBlank(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}