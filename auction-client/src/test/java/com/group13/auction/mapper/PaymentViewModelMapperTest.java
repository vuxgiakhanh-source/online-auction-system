package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import com.group13.auction.viewmodel.payment.SecondChanceOfferViewModel;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PaymentViewModelMapper}. */
class PaymentViewModelMapperTest {

  @Test
  void toPaymentResultViewModelShouldReturnEmptyResultWhenDtoIsNull() {
    PaymentResultViewModel viewModel = PaymentViewModelMapper.toPaymentResultViewModel(null);

    assertEquals("--", viewModel.auctionId());
    assertEquals("--", viewModel.finalPriceText());
    assertEquals("--", viewModel.depositDeductedText());
    assertEquals("--", viewModel.remainingToPayText());
    assertEquals("--", viewModel.newBalanceText());
    assertEquals("--", viewModel.paymentStatus());
    assertEquals("Không rõ", viewModel.paymentStatusText());
    assertEquals("--", viewModel.paidAtText());
    assertFalse(viewModel.completed());
  }

  @Test
  void toPaymentResultViewModelShouldMapCompletedPaymentResult() {
    PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
    dto.setAuctionId("A-1");
    dto.setFinalPrice(5_000_000L);
    dto.setDepositDeducted(500_000L);
    dto.setRemainingToPay(4_500_000L);
    dto.setNewBalance(10_000_000L);
    dto.setPaymentStatus("COMPLETED");
    dto.setPaidAt(LocalDateTime.of(2026, 5, 26, 20, 30));

    PaymentResultViewModel viewModel = PaymentViewModelMapper.toPaymentResultViewModel(dto);

    assertEquals("A-1", viewModel.auctionId());
    assertCurrencyTextContains(viewModel.finalPriceText(), "5.000.000");
    assertCurrencyTextContains(viewModel.depositDeductedText(), "500.000");
    assertCurrencyTextContains(viewModel.remainingToPayText(), "4.500.000");
    assertCurrencyTextContains(viewModel.newBalanceText(), "10.000.000");
    assertEquals("COMPLETED", viewModel.paymentStatus());
    assertEquals("Đã thanh toán", viewModel.paymentStatusText());
    assertEquals("26/05/2026 20:30", viewModel.paidAtText());
    assertTrue(viewModel.completed());
  }

  @Test
  void toPaymentResultViewModelShouldMapKnownPaymentStatusTexts() {
    assertPaymentStatusText("PENDING", "Đang chờ thanh toán", false);
    assertPaymentStatusText("EXPIRED", "Đã hết hạn", false);
    assertPaymentStatusText("FAILED", "Thanh toán thất bại", false);
  }

  @Test
  void toPaymentResultViewModelShouldKeepUnknownPaymentStatusAsDisplayText() {
    PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
    dto.setPaymentStatus("MANUAL_REVIEW");

    PaymentResultViewModel viewModel = PaymentViewModelMapper.toPaymentResultViewModel(dto);

    assertEquals("MANUAL_REVIEW", viewModel.paymentStatus());
    assertEquals("MANUAL_REVIEW", viewModel.paymentStatusText());
    assertFalse(viewModel.completed());
  }

  @Test
  void toPaymentResultViewModelShouldUseFallbacksForBlankFields() {
    PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
    dto.setAuctionId("   ");
    dto.setPaymentStatus("   ");
    dto.setPaidAt(null);

    PaymentResultViewModel viewModel = PaymentViewModelMapper.toPaymentResultViewModel(dto);

    assertEquals("--", viewModel.auctionId());
    assertEquals("--", viewModel.paymentStatus());
    assertEquals("--", viewModel.paymentStatusText());
    assertEquals("--", viewModel.paidAtText());
    assertFalse(viewModel.completed());
  }

  @Test
  void toSecondChanceOfferViewModelShouldReturnEmptyExpiredOfferWhenDtoIsNull() {
    SecondChanceOfferViewModel viewModel =
        PaymentViewModelMapper.toSecondChanceOfferViewModel(null);

    assertEquals("--", viewModel.offerId());
    assertEquals("--", viewModel.auctionId());
    assertEquals("--", viewModel.auctionItemName());
    assertEquals("--", viewModel.offerPriceText());
    assertEquals("--", viewModel.depositRequiredText());
    assertEquals("--", viewModel.deadlineText());
    assertTrue(viewModel.expired());
  }

  @Test
  void toSecondChanceOfferViewModelShouldMapActiveOffer() {
    PaymentDTOs.SecondChanceOfferDTO dto = new PaymentDTOs.SecondChanceOfferDTO();
    dto.setOfferId("SCO-1");
    dto.setAuctionId("A-1");
    dto.setAuctionItemName("Vintage Camera");
    dto.setOfferPrice(4_800_000L);
    dto.setDepositRequired(480_000L);
    dto.setDeadline(LocalDateTime.now().plusDays(1));

    SecondChanceOfferViewModel viewModel =
        PaymentViewModelMapper.toSecondChanceOfferViewModel(dto);

    assertEquals("SCO-1", viewModel.offerId());
    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.auctionItemName());
    assertCurrencyTextContains(viewModel.offerPriceText(), "4.800.000");
    assertCurrencyTextContains(viewModel.depositRequiredText(), "480.000");
    assertFalse(viewModel.expired());
  }

  @Test
  void toSecondChanceOfferViewModelShouldMarkExpiredOfferWhenDeadlinePassed() {
    PaymentDTOs.SecondChanceOfferDTO dto = new PaymentDTOs.SecondChanceOfferDTO();
    dto.setDeadline(LocalDateTime.now().minusMinutes(1));

    SecondChanceOfferViewModel viewModel =
        PaymentViewModelMapper.toSecondChanceOfferViewModel(dto);

    assertTrue(viewModel.expired());
  }

  @Test
  void toSecondChanceOfferViewModelShouldUseFallbacksForBlankFieldsAndNullDeadline() {
    PaymentDTOs.SecondChanceOfferDTO dto = new PaymentDTOs.SecondChanceOfferDTO();
    dto.setOfferId("   ");
    dto.setAuctionId(null);
    dto.setAuctionItemName("");
    dto.setDeadline(null);

    SecondChanceOfferViewModel viewModel =
        PaymentViewModelMapper.toSecondChanceOfferViewModel(dto);

    assertEquals("--", viewModel.offerId());
    assertEquals("--", viewModel.auctionId());
    assertEquals("--", viewModel.auctionItemName());
    assertEquals("--", viewModel.deadlineText());
    assertFalse(viewModel.expired());
  }

  private static void assertPaymentStatusText(
      String status, String expectedStatusText, boolean expectedCompleted) {
    PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
    dto.setPaymentStatus(status);

    PaymentResultViewModel viewModel = PaymentViewModelMapper.toPaymentResultViewModel(dto);

    assertEquals(status, viewModel.paymentStatus());
    assertEquals(expectedStatusText, viewModel.paymentStatusText());
    assertEquals(expectedCompleted, viewModel.completed());
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}