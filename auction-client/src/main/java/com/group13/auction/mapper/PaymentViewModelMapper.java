package com.group13.auction.mapper;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import com.group13.auction.viewmodel.payment.SecondChanceOfferViewModel;
import java.time.LocalDateTime;

/** Mapper chuyển DTO payment từ {@code auction-common} sang view model phía client. */
public final class PaymentViewModelMapper {

  private PaymentViewModelMapper() {
    // Utility class.
  }

  /**
   * Chuyển kết quả thanh toán từ server sang view model.
   *
   * @param dto payment result DTO từ server
   * @return view model kết quả thanh toán
   */
  public static PaymentResultViewModel toPaymentResultViewModel(PaymentDTOs.PaymentResultDTO dto) {
    if (dto == null) {
      return emptyPaymentResult();
    }

    String status = fallback(dto.getPaymentStatus());
    boolean completed = "COMPLETED".equalsIgnoreCase(status);

    return new PaymentResultViewModel(
        fallback(dto.getAuctionId()),
        CurrencyUtil.formatVnd(dto.getFinalPrice()),
        CurrencyUtil.formatVnd(dto.getDepositDeducted()),
        CurrencyUtil.formatVnd(dto.getRemainingToPay()),
        CurrencyUtil.formatVnd(dto.getNewBalance()),
        status,
        paymentStatusText(status),
        DateTimeUtil.formatDateTime(dto.getPaidAt()),
        completed);
  }

  /**
   * Chuyển Second Chance Offer từ server sang view model.
   *
   * @param dto second chance offer DTO từ server
   * @return view model second chance offer
   */
  public static SecondChanceOfferViewModel toSecondChanceOfferViewModel(
      PaymentDTOs.SecondChanceOfferDTO dto) {
    if (dto == null) {
      return emptySecondChanceOffer();
    }

    LocalDateTime deadline = dto.getDeadline();
    boolean expired = deadline != null && !deadline.isAfter(LocalDateTime.now());

    return new SecondChanceOfferViewModel(
        fallback(dto.getOfferId()),
        fallback(dto.getAuctionId()),
        fallback(dto.getAuctionItemName()),
        CurrencyUtil.formatVnd(dto.getOfferPrice()),
        CurrencyUtil.formatVnd(dto.getDepositRequired()),
        DateTimeUtil.formatDateTime(deadline),
        expired);
  }

  private static PaymentResultViewModel emptyPaymentResult() {
    return new PaymentResultViewModel("--", "--", "--", "--", "--", "--", "Không rõ", "--", false);
  }

  private static SecondChanceOfferViewModel emptySecondChanceOffer() {
    return new SecondChanceOfferViewModel("--", "--", "--", "--", "--", "--", true);
  }

  private static String paymentStatusText(String status) {
    if (status == null || status.isBlank()) {
      return "Không rõ";
    }

    return switch (status.trim().toUpperCase()) {
      case "COMPLETED" -> "Đã thanh toán";
      case "PENDING" -> "Đang chờ thanh toán";
      case "EXPIRED" -> "Đã hết hạn";
      case "FAILED" -> "Thanh toán thất bại";
      default -> status;
    };
  }

  private static String fallback(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}
