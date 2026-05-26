package com.group13.auction.viewmodel.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link RatingHistoryViewModel}. */
class RatingHistoryViewModelTest {

  @Test
  void gettersShouldReturnRatingHistoryDisplayData() {
    RatingHistoryViewModel viewModel =
        new RatingHistoryViewModel(
            "R-1",
            "U-1",
            "U-2",
            "A-1",
            5,
            "5/5",
            "Người bán giao hàng đúng mô tả.",
            "26/05/2026 20:30");

    assertEquals("R-1", viewModel.getRatingId());
    assertEquals("U-1", viewModel.getReviewerId());
    assertEquals("U-2", viewModel.getTargetUserId());
    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals(5, viewModel.getScore());
    assertEquals("5/5", viewModel.getScoreText());
    assertEquals("Người bán giao hàng đúng mô tả.", viewModel.getComment());
    assertEquals("26/05/2026 20:30", viewModel.getCreatedAtText());
  }
}