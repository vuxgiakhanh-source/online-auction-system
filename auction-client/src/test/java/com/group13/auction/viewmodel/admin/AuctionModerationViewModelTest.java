package com.group13.auction.viewmodel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuctionModerationViewModel}. */
class AuctionModerationViewModelTest {

  @Test
  void gettersShouldReturnAuctionModerationDisplayData() {
    AuctionModerationViewModel viewModel =
        new AuctionModerationViewModel(
            "A-1",
            "Vintage Camera",
            "seller01",
            "2.500.000 ₫",
            "RUNNING",
            "Đang đấu giá",
            "26/05/2026 20:00",
            "26/05/2026 21:00",
            true);

    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals("Vintage Camera", viewModel.getTitle());
    assertEquals("seller01", viewModel.getSellerName());
    assertEquals("2.500.000 ₫", viewModel.getCurrentPriceText());
    assertEquals("RUNNING", viewModel.getRawStatus());
    assertEquals("Đang đấu giá", viewModel.getStatus());
    assertEquals("26/05/2026 20:00", viewModel.getStartTimeText());
    assertEquals("26/05/2026 21:00", viewModel.getEndTimeText());
    assertTrue(viewModel.isCancellable());
    assertTrue(viewModel.isLiveWatchable());
    assertFalse(viewModel.isHistoryViewable());
  }

  @Test
  void cancellableShouldReturnFalseWhenAuctionCannotBeCancelled() {
    AuctionModerationViewModel viewModel =
        new AuctionModerationViewModel(
            "A-2",
            "Paid Auction",
            "seller02",
            "5.000.000 ₫",
            "PAID",
            "Đã thanh toán",
            "26/05/2026 19:00",
            "26/05/2026 20:00",
            false);

    assertEquals("PAID", viewModel.getRawStatus());
    assertEquals("Đã thanh toán", viewModel.getStatus());
    assertFalse(viewModel.isCancellable());
    assertFalse(viewModel.isLiveWatchable());
    assertTrue(viewModel.isHistoryViewable());
  }
}