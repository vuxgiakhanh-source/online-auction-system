package com.group13.auction.viewmodel.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PaymentResultViewModel}. */
class PaymentResultViewModelTest {

  @Test
  void gettersShouldReturnPaymentResultDisplayData() {
    PaymentResultViewModel viewModel =
        new PaymentResultViewModel(
            "A-1",
            "5.000.000 ₫",
            "500.000 ₫",
            "4.500.000 ₫",
            "10.000.000 ₫",
            "PAID",
            "Đã thanh toán",
            "26/05/2026 20:30",
            true);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("5.000.000 ₫", viewModel.finalPriceText());
    assertEquals("500.000 ₫", viewModel.depositDeductedText());
    assertEquals("4.500.000 ₫", viewModel.remainingToPayText());
    assertEquals("10.000.000 ₫", viewModel.newBalanceText());
    assertEquals("PAID", viewModel.paymentStatus());
    assertEquals("Đã thanh toán", viewModel.paymentStatusText());
    assertEquals("26/05/2026 20:30", viewModel.paidAtText());
    assertTrue(viewModel.completed());
  }
}
