package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuctionDetailViewModel}. */
class AuctionDetailViewModelTest {

  @Test
  void constructorShouldUseSafeDefaultsForNullableStatusAndLeaderFields() {
    AuctionDetailViewModel viewModel =
        createViewModel(null, null, null, null, null, List.of(), List.of(), false, false);

    assertEquals("", viewModel.rawStatus());
    assertEquals("", viewModel.currentLeaderId());
    assertEquals("", viewModel.currentLeaderUsername());
    assertFalse(viewModel.finished());
    assertFalse(viewModel.paid());
  }

  @Test
  void imageHelpersShouldReturnPrimaryImageAndImageState() {
    AuctionDetailViewModel viewModel =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10),
            List.of("items/camera-1.png", "items/camera-2.png"),
            List.of(),
            true,
            true);

    assertTrue(viewModel.hasImages());
    assertEquals("items/camera-1.png", viewModel.primaryImageUrl());
  }

  @Test
  void imageHelpersShouldReturnEmptyPrimaryImageWhenImageListIsNullOrEmpty() {
    AuctionDetailViewModel nullImages =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10),
            null,
            List.of(),
            true,
            true);
    AuctionDetailViewModel emptyImages =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10),
            List.of(),
            List.of(),
            true,
            true);

    assertFalse(nullImages.hasImages());
    assertEquals("", nullImages.primaryImageUrl());
    assertFalse(emptyImages.hasImages());
    assertEquals("", emptyImages.primaryImageUrl());
  }

  @Test
  void productSpecificationHelpersShouldReturnSpecificationState() {
    ProductSpecificationViewModel specification =
        new ProductSpecificationViewModel("Condition", "Like new");
    AuctionDetailViewModel viewModel =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10),
            List.of(),
            List.of(specification),
            true,
            true);

    assertTrue(viewModel.hasProductSpecifications());
    assertEquals(List.of(specification), viewModel.productSpecifications());
  }

  @Test
  void imageAndSpecificationListsShouldBeDefensiveCopies() {
    List<String> images = new ArrayList<>();
    images.add("items/camera.png");
    List<ProductSpecificationViewModel> specifications = new ArrayList<>();
    specifications.add(new ProductSpecificationViewModel("Condition", "New"));

    AuctionDetailViewModel viewModel =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10),
            images,
            specifications,
            true,
            true);

    images.add("items/changed.png");
    specifications.add(new ProductSpecificationViewModel("Brand", "Sony"));

    assertEquals(List.of("items/camera.png"), viewModel.imageUrls());
    assertEquals(1, viewModel.productSpecifications().size());
    assertThrows(UnsupportedOperationException.class, () -> viewModel.imageUrls().add("x.png"));
  }

  @Test
  void finishedAndPaidShouldUseRawStatusIgnoringCase() {
    assertTrue(createViewModelWithStatus("FINISHED").finished());
    assertTrue(createViewModelWithStatus("finished").finished());
    assertFalse(createViewModelWithStatus("RUNNING").finished());

    assertTrue(createViewModelWithStatus("PAID").paid());
    assertTrue(createViewModelWithStatus("paid").paid());
    assertFalse(createViewModelWithStatus("FINISHED").paid());
  }

  @Test
  void canRequestPaymentShouldReturnTrueOnlyForFinishedUnpaidWinner() {
    AuctionDetailViewModel viewModel =
        createViewModel(
            "FINISHED",
            "U-1",
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusMinutes(1),
            List.of(),
            List.of(),
            false,
            false);

    assertTrue(viewModel.canRequestPayment("U-1"));
    assertFalse(viewModel.canRequestPayment("U-2"));
    assertFalse(viewModel.canRequestPayment(""));
    assertFalse(viewModel.canRequestPayment(null));
  }

  @Test
  void canRequestPaymentShouldReturnFalseWhenAuctionIsPaidOrNotFinished() {
    AuctionDetailViewModel paidAuction = createViewModelWithStatus("PAID");
    AuctionDetailViewModel runningAuction = createViewModelWithStatus("RUNNING");

    assertFalse(paidAuction.canRequestPayment("U-1"));
    assertFalse(runningAuction.canRequestPayment("U-1"));
  }

  @Test
  void pastTwoThirdsElapsedShouldReturnFalseWhenRawTimeRangeIsInvalid() {
    AuctionDetailViewModel missingStart =
        createViewModel("RUNNING", "U-1", null, LocalDateTime.now().plusMinutes(10));
    AuctionDetailViewModel missingEnd =
        createViewModel("RUNNING", "U-1", LocalDateTime.now().minusMinutes(10), null);
    AuctionDetailViewModel invalidRange =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().plusMinutes(10),
            LocalDateTime.now().minusMinutes(10));

    assertFalse(missingStart.pastTwoThirdsElapsed());
    assertFalse(missingEnd.pastTwoThirdsElapsed());
    assertFalse(invalidRange.pastTwoThirdsElapsed());
  }

  @Test
  void pastTwoThirdsElapsedShouldReturnTrueWhenAuctionAlreadyPassedTwoThirds() {
    AuctionDetailViewModel viewModel =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(8),
            LocalDateTime.now().plusMinutes(1));

    assertTrue(viewModel.pastTwoThirdsElapsed());
  }

  @Test
  void pastTwoThirdsElapsedShouldReturnFalseWhenAuctionHasNotPassedTwoThirds() {
    AuctionDetailViewModel viewModel =
        createViewModel(
            "RUNNING",
            "U-1",
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(8));

    assertFalse(viewModel.pastTwoThirdsElapsed());
  }

  private static AuctionDetailViewModel createViewModelWithStatus(String rawStatus) {
    return createViewModel(
        rawStatus,
        "U-1",
        LocalDateTime.now().minusMinutes(10),
        LocalDateTime.now().plusMinutes(10));
  }

  private static AuctionDetailViewModel createViewModel(
      String rawStatus,
      String currentLeaderId,
      LocalDateTime rawStartTime,
      LocalDateTime rawEndTime) {
    return createViewModel(
        rawStatus,
        currentLeaderId,
        rawStartTime,
        rawEndTime,
        List.of("items/camera.png"),
        List.of(new ProductSpecificationViewModel("Condition", "New")),
        true,
        true);
  }

  private static AuctionDetailViewModel createViewModel(
      String rawStatus,
      String currentLeaderId,
      LocalDateTime rawStartTime,
      LocalDateTime rawEndTime,
      List<String> imageUrls,
      List<ProductSpecificationViewModel> productSpecifications,
      boolean joinable,
      boolean liveBiddingAllowed) {
    return createViewModel(
        rawStatus,
        currentLeaderId,
        "bidder01",
        rawStartTime,
        rawEndTime,
        imageUrls,
        productSpecifications,
        joinable,
        liveBiddingAllowed);
  }

  private static AuctionDetailViewModel createViewModel(
      String rawStatus,
      String currentLeaderId,
      String currentLeaderUsername,
      LocalDateTime rawStartTime,
      LocalDateTime rawEndTime,
      List<String> imageUrls,
      List<ProductSpecificationViewModel> productSpecifications,
      boolean joinable,
      boolean liveBiddingAllowed) {
    return new AuctionDetailViewModel(
        "A-1",
        "Vintage Camera",
        "Camera film cổ.",
        "Electronics",
        "Người bán: seller01",
        rawStatus,
        "Đang diễn ra",
        currentLeaderId,
        currentLeaderUsername,
        "2.500.000 ₫",
        "1.000.000 ₫",
        "Không hiển thị",
        "Đang dẫn đầu: bidder01",
        "12 lượt truy cập",
        "26/05/2026 20:00",
        "26/05/2026 21:00",
        "1 giờ",
        rawStartTime,
        rawEndTime,
        imageUrls,
        productSpecifications,
        joinable,
        liveBiddingAllowed,
        2_500_000D);
  }
}
