package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link BidHistoryPointViewModel}. */
class BidHistoryPointViewModelTest {

  @Test
  void gettersShouldReturnManualBidHistoryPointData() {
    BidHistoryPointViewModel viewModel =
        new BidHistoryPointViewModel(
            "A-1", 2_500_000L, "2.500.000 ₫", "bidder01", "26/05/2026 20:30", false);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals(2_500_000L, viewModel.price());
    assertEquals("2.500.000 ₫", viewModel.priceText());
    assertEquals("bidder01", viewModel.bidderUsername());
    assertEquals("26/05/2026 20:30", viewModel.timestampText());
    assertFalse(viewModel.autoBid());
  }

  @Test
  void autoBidShouldReturnTrueForAutoBidHistoryPoint() {
    BidHistoryPointViewModel viewModel =
        new BidHistoryPointViewModel(
            "A-1", 3_000_000L, "3.000.000 ₫", "bidder02", "26/05/2026 20:35", true);

    assertTrue(viewModel.autoBid());
  }
}
