package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuctionTimerViewModel}. */
class AuctionTimerViewModelTest {

  @Test
  void gettersShouldReturnActiveTimerDisplayData() {
    AuctionTimerViewModel viewModel =
        new AuctionTimerViewModel("1 giờ 20 phút", "26/05/2026 21:00", false);

    assertEquals("1 giờ 20 phút", viewModel.remainingTimeText());
    assertEquals("26/05/2026 21:00", viewModel.endTimeText());
    assertFalse(viewModel.ended());
  }

  @Test
  void endedShouldReturnTrueWhenAuctionTimerEnded() {
    AuctionTimerViewModel viewModel =
        new AuctionTimerViewModel("Đã kết thúc", "26/05/2026 21:00", true);

    assertEquals("Đã kết thúc", viewModel.remainingTimeText());
    assertTrue(viewModel.ended());
  }
}