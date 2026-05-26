package com.group13.auction.viewmodel.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SecondChanceOfferViewModel}. */
class SecondChanceOfferViewModelTest {

  @Test
  void gettersShouldReturnSecondChanceOfferDisplayData() {
    SecondChanceOfferViewModel viewModel =
        new SecondChanceOfferViewModel(
            "SCO-1",
            "A-1",
            "Vintage Camera",
            "4.800.000 ₫",
            "480.000 ₫",
            "27/05/2026 20:30",
            false);

    assertEquals("SCO-1", viewModel.offerId());
    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.auctionItemName());
    assertEquals("4.800.000 ₫", viewModel.offerPriceText());
    assertEquals("480.000 ₫", viewModel.depositRequiredText());
    assertEquals("27/05/2026 20:30", viewModel.deadlineText());
    assertFalse(viewModel.expired());
  }

  @Test
  void expiredShouldReturnTrueWhenOfferIsExpired() {
    SecondChanceOfferViewModel viewModel =
        new SecondChanceOfferViewModel(
            "SCO-2",
            "A-2",
            "Art Print",
            "1.200.000 ₫",
            "120.000 ₫",
            "26/05/2026 10:00",
            true);

    assertTrue(viewModel.expired());
  }
}