package com.group13.auction.mapper;

import com.group13.auction.common.dto.bank.SystemBankDTOs;
import com.group13.auction.common.dto.bank.SystemBankDTOs.FinancialTransactionDTO;
import com.group13.auction.common.dto.bank.SystemBankDTOs.SystemBankSummaryDTO;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.admin.FinancialTransactionViewModel;
import com.group13.auction.viewmodel.admin.SystemBankSummaryViewModel;
import java.math.BigDecimal;
import java.util.List;

/** Mapper chuyển DTO System Bank từ common sang ViewModel phía JavaFX client. */
public final class SystemBankViewModelMapper {

  private SystemBankViewModelMapper() {
    // Utility class.
  }

  public static SystemBankSummaryViewModel toSummaryViewModel(SystemBankSummaryDTO dto) {
    if (dto == null) {
      return emptySummary();
    }

    return new SystemBankSummaryViewModel(
        money(dto.getTotalBalance()),
        money(dto.getTotalFundsHeld()),
        money(dto.getTotalPaymentReceived()),
        money(dto.getTotalTaxCollected()),
        money(dto.getTotalDepositForfeited()),
        money(dto.getTotalPayoutToSeller()),
        money(dto.getTotalRefundedToWinner()),
        DateTimeUtil.formatDateTime(dto.getUpdatedAt()));
  }

  public static List<FinancialTransactionViewModel> toTransactionViewModels(
      SystemBankDTOs.FinancialTransactionListDTO dto) {
    if (dto == null || dto.getTransactions() == null) {
      return List.of();
    }
    return dto.getTransactions().stream()
        .map(SystemBankViewModelMapper::toTransactionViewModel)
        .toList();
  }

  public static FinancialTransactionViewModel toTransactionViewModel(FinancialTransactionDTO dto) {
    if (dto == null) {
      return new FinancialTransactionViewModel("--", "--", "--", "--", "--", "--", "--");
    }

    return new FinancialTransactionViewModel(
        fallback(dto.getId()),
        transactionTypeText(dto.getTransactionType()),
        money(dto.getAmount()),
        fallback(dto.getSenderId()),
        fallback(dto.getReceiverId()),
        fallback(dto.getAuctionId()),
        DateTimeUtil.formatDateTime(dto.getCreatedAt()));
  }

  public static String transactionTypeText(String type) {
    if (type == null || type.isBlank()) {
      return "Không xác định";
    }
    return switch (type) {
      case "DEPOSIT_LOCK" -> "Khóa tiền cọc";
      case "DEPOSIT_UNLOCK" -> "Hoàn tiền cọc";
      case "DEPOSIT_FORFEIT" -> "Tịch thu cọc";
      case "PAYMENT_FROM_WINNER" -> "Người thắng thanh toán";
      case "TAX_COLLECTED" -> "Thuế hệ thống";
      case "PAYOUT_TO_SELLER" -> "Giải ngân cho seller";
      case "REFUND_TO_WINNER" -> "Hoàn tiền cho người mua";
      case "SECOND_CHANCE_PAYMENT" -> "Thanh toán Second Chance";
      default -> type;
    };
  }

  private static SystemBankSummaryViewModel emptySummary() {
    return new SystemBankSummaryViewModel("--", "--", "--", "--", "--", "--", "--", "--");
  }

  private static String money(long amount) {
    return CurrencyUtil.formatVnd(BigDecimal.valueOf(amount));
  }

  private static String fallback(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}