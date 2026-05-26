package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link LiveBidViewModel}. */
class LiveBidViewModelTest {

  @Test
  void gettersShouldReturnLiveBidDisplayData() {
    LiveBidViewModel viewModel =
        new LiveBidViewModel(
            "A-1",
            "2.500.000 ₫",
            "Đang dẫn đầu: bidder01",
            "Đã đạt giá sàn",
            "26/05/2026 20:30",
            "26/05/2026 21:00",
            2_500_000L,
            true);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("2.500.000 ₫", viewModel.currentPriceText());
    assertEquals("Đang dẫn đầu: bidder01", viewModel.leaderText());
    assertEquals("Đã đạt giá sàn", viewModel.reserveText());
    assertEquals("26/05/2026 20:30", viewModel.timestampText());
    assertEquals("26/05/2026 21:00", viewModel.endTimeText());
    assertEquals(2_500_000L, viewModel.currentPrice());
    assertTrue(viewModel.reserveMet());
  }

  @Test
  void reserveMetShouldReturnFalseWhenReservePriceIsNotMet() {
    LiveBidViewModel viewModel =
        new LiveBidViewModel(
            "A-2",
            "900.000 ₫",
            "Chưa có người dẫn đầu",
            "Chưa đạt giá sàn",
            "26/05/2026 20:30",
            "26/05/2026 21:00",
            900_000L,
            false);

    assertFalse(viewModel.reserveMet());
  }
}