package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuctionCardViewModel}. */
class AuctionCardViewModelTest {

  @Test
  void constructorShouldConvertNullImageUrlToEmptyString() {
    AuctionCardViewModel viewModel =
        createViewModel(null, true);

    assertEquals("", viewModel.primaryImageUrl());
    assertFalse(viewModel.hasImage());
  }

  @Test
  void hasImageShouldReturnFalseWhenImageUrlIsBlank() {
    AuctionCardViewModel viewModel =
        createViewModel("   ", true);

    assertFalse(viewModel.hasImage());
  }

  @Test
  void hasImageShouldReturnTrueWhenImageUrlHasText() {
    AuctionCardViewModel viewModel =
        createViewModel("items/camera.png", true);

    assertEquals("items/camera.png", viewModel.primaryImageUrl());
    assertTrue(viewModel.hasImage());
  }

  @Test
  void gettersShouldReturnAuctionCardDisplayData() {
    AuctionCardViewModel viewModel =
        new AuctionCardViewModel(
            "A-1",
            "Vintage Camera",
            "Electronics",
            "Đang diễn ra",
            "2.000.000 ₫",
            "1.000.000 ₫",
            "1 giờ 20 phút",
            "26/05/2026 21:00",
            "Người bán: seller01",
            "12 lượt truy cập",
            "items/camera.png",
            true);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.itemName());
    assertEquals("Electronics", viewModel.categoryText());
    assertEquals("Đang diễn ra", viewModel.statusText());
    assertEquals("2.000.000 ₫", viewModel.currentPriceText());
    assertEquals("1.000.000 ₫", viewModel.startingPriceText());
    assertEquals("1 giờ 20 phút", viewModel.remainingTimeText());
    assertEquals("26/05/2026 21:00", viewModel.endTimeText());
    assertEquals("Người bán: seller01", viewModel.sellerText());
    assertEquals("12 lượt truy cập", viewModel.viewerCountText());
    assertEquals("items/camera.png", viewModel.primaryImageUrl());
    assertTrue(viewModel.joinable());
  }

  private static AuctionCardViewModel createViewModel(String imageUrl, boolean joinable) {
    return new AuctionCardViewModel(
        "A-1",
        "Vintage Camera",
        "Electronics",
        "Đang diễn ra",
        "2.000.000 ₫",
        "1.000.000 ₫",
        "1 giờ 20 phút",
        "26/05/2026 21:00",
        "Người bán: seller01",
        "12 lượt truy cập",
        imageUrl,
        joinable);
  }
}