package com.group13.auction.viewmodel.seller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.viewmodel.auction.ProductSpecificationViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SellerAuctionRowViewModel}. */
class SellerAuctionRowViewModelTest {

  @Test
  void imageHelpersShouldReturnEmptyStateWhenImageListIsNullOrEmpty() {
    SellerAuctionRowViewModel nullImages = createViewModel(null, List.of(), true, true);
    SellerAuctionRowViewModel emptyImages = createViewModel(List.of(), List.of(), true, true);

    assertFalse(nullImages.hasImages());
    assertEquals("", nullImages.primaryImageUrl());
    assertFalse(emptyImages.hasImages());
    assertEquals("", emptyImages.primaryImageUrl());
  }

  @Test
  void imageHelpersShouldReturnPrimaryImageWhenImagesExist() {
    SellerAuctionRowViewModel viewModel =
        createViewModel(List.of("items/camera-1.png", "items/camera-2.png"), List.of(), true, true);

    assertTrue(viewModel.hasImages());
    assertEquals("items/camera-1.png", viewModel.primaryImageUrl());
  }

  @Test
  void productSpecificationHelpersShouldReturnSpecificationState() {
    ProductSpecificationViewModel specification =
        new ProductSpecificationViewModel("Condition", "Like new");
    SellerAuctionRowViewModel viewModel =
        createViewModel(List.of(), List.of(specification), true, true);

    assertTrue(viewModel.hasProductSpecifications());
    assertEquals(List.of(specification), viewModel.productSpecifications());
  }

  @Test
  void imageAndSpecificationListsShouldBeDefensiveCopies() {
    List<String> images = new ArrayList<>();
    images.add("items/camera.png");
    List<ProductSpecificationViewModel> specifications = new ArrayList<>();
    specifications.add(new ProductSpecificationViewModel("Condition", "New"));

    SellerAuctionRowViewModel viewModel = createViewModel(images, specifications, true, true);

    images.add("items/changed.png");
    specifications.add(new ProductSpecificationViewModel("Brand", "Sony"));

    assertEquals(List.of("items/camera.png"), viewModel.imageUrls());
    assertEquals(1, viewModel.productSpecifications().size());
    assertThrows(UnsupportedOperationException.class, () -> viewModel.imageUrls().add("x.png"));
  }

  @Test
  void gettersShouldReturnSellerAuctionRowDisplayData() {
    LocalDateTime startTime = LocalDateTime.of(2026, 5, 26, 20, 0);
    LocalDateTime endTime = LocalDateTime.of(2026, 5, 26, 21, 0);
    SellerAuctionRowViewModel viewModel =
        new SellerAuctionRowViewModel(
            "A-1",
            "Vintage Camera",
            "Electronics",
            "Đang diễn ra",
            "2.500.000 ₫",
            "1.000.000 ₫",
            "Không hiển thị",
            "26/05/2026 20:00",
            "26/05/2026 21:00",
            startTime,
            endTime,
            "12 lượt truy cập",
            List.of("items/camera.png"),
            List.of(new ProductSpecificationViewModel("Condition", "New")),
            true,
            false);

    assertEquals("A-1", viewModel.auctionId());
    assertEquals("Vintage Camera", viewModel.itemName());
    assertEquals("Electronics", viewModel.categoryText());
    assertEquals("Đang diễn ra", viewModel.statusText());
    assertEquals("2.500.000 ₫", viewModel.currentPriceText());
    assertEquals("1.000.000 ₫", viewModel.startingPriceText());
    assertEquals("Không hiển thị", viewModel.reservePriceText());
    assertEquals("26/05/2026 20:00", viewModel.startTimeText());
    assertEquals("26/05/2026 21:00", viewModel.endTimeText());
    assertEquals(startTime, viewModel.startTime());
    assertEquals(endTime, viewModel.endTime());
    assertEquals("12 lượt truy cập", viewModel.viewerCountText());
    assertTrue(viewModel.editable());
    assertFalse(viewModel.cancelRequestAllowed());
  }

  private static SellerAuctionRowViewModel createViewModel(
      List<String> imageUrls,
      List<ProductSpecificationViewModel> productSpecifications,
      boolean editable,
      boolean cancelRequestAllowed) {
    return new SellerAuctionRowViewModel(
        "A-1",
        "Vintage Camera",
        "Electronics",
        "Đang diễn ra",
        "2.500.000 ₫",
        "1.000.000 ₫",
        "Không hiển thị",
        "26/05/2026 20:00",
        "26/05/2026 21:00",
        LocalDateTime.of(2026, 5, 26, 20, 0),
        LocalDateTime.of(2026, 5, 26, 21, 0),
        "12 lượt truy cập",
        imageUrls,
        productSpecifications,
        editable,
        cancelRequestAllowed);
  }
}
