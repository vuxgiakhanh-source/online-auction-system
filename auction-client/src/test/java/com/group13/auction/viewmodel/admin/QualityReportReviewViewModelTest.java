package com.group13.auction.viewmodel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link QualityReportReviewViewModel}. */
class QualityReportReviewViewModelTest {

  @Test
  void gettersShouldReturnQualityReportReviewDisplayData() {
    QualityReportReviewViewModel viewModel =
        new QualityReportReviewViewModel(
            "QR-1",
            "U-1",
            "A-1",
            "Sản phẩm không đúng mô tả",
            "WRONG_DESCRIPTION",
            "Sản phẩm nhận được khác mô tả trong phiên đấu giá.",
            "PENDING",
            "26/05/2026 20:30",
            true);

    assertEquals("QR-1", viewModel.getReportId());
    assertEquals("U-1", viewModel.getReporterId());
    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals("Sản phẩm không đúng mô tả", viewModel.getTitle());
    assertEquals("WRONG_DESCRIPTION", viewModel.getReason());
    assertEquals("Sản phẩm nhận được khác mô tả trong phiên đấu giá.", viewModel.getDescription());
    assertEquals("PENDING", viewModel.getStatus());
    assertEquals("26/05/2026 20:30", viewModel.getCreatedAtText());
    assertTrue(viewModel.isReviewable());
  }

  @Test
  void reviewableShouldReturnFalseWhenReportCannotBeReviewed() {
    QualityReportReviewViewModel viewModel =
        new QualityReportReviewViewModel(
            "QR-2",
            "U-2",
            "A-2",
            "Hàng bị hỏng",
            "DAMAGED_ITEM",
            "Sản phẩm đã được xử lý khiếu nại.",
            "RESOLVED",
            "26/05/2026 21:00",
            false);

    assertFalse(viewModel.isReviewable());
  }
}