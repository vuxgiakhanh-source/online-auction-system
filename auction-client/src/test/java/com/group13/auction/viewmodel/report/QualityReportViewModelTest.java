package com.group13.auction.viewmodel.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link QualityReportViewModel}. */
class QualityReportViewModelTest {

  @Test
  void shortConstructorShouldUseDefaultReporterAndSellerNames() {
    QualityReportViewModel viewModel =
        new QualityReportViewModel(
            "QR-1",
            "A-1",
            "Sản phẩm không đúng mô tả",
            "WRONG_DESCRIPTION",
            "Sản phẩm nhận được khác ảnh đăng bán.",
            "PENDING",
            "26/05/2026 20:30");

    assertEquals("QR-1", viewModel.getReportId());
    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals("Sản phẩm không đúng mô tả", viewModel.getTitle());
    assertEquals("WRONG_DESCRIPTION", viewModel.getReason());
    assertEquals("Sản phẩm nhận được khác ảnh đăng bán.", viewModel.getDescription());
    assertEquals("PENDING", viewModel.getStatus());
    assertEquals("26/05/2026 20:30", viewModel.getCreatedAtText());
    assertEquals("--", viewModel.getReporterUsername());
    assertEquals("--", viewModel.getSellerUsername());
  }

  @Test
  void fullConstructorShouldReturnReporterAndSellerNames() {
    QualityReportViewModel viewModel =
        new QualityReportViewModel(
            "QR-2",
            "A-2",
            "Hàng bị hỏng",
            "DAMAGED_ITEM",
            "Sản phẩm bị vỡ khi nhận hàng.",
            "RESOLVED",
            "26/05/2026 21:00",
            "bidder01",
            "seller01");

    assertEquals("QR-2", viewModel.getReportId());
    assertEquals("A-2", viewModel.getAuctionId());
    assertEquals("Hàng bị hỏng", viewModel.getTitle());
    assertEquals("DAMAGED_ITEM", viewModel.getReason());
    assertEquals("Sản phẩm bị vỡ khi nhận hàng.", viewModel.getDescription());
    assertEquals("RESOLVED", viewModel.getStatus());
    assertEquals("26/05/2026 21:00", viewModel.getCreatedAtText());
    assertEquals("bidder01", viewModel.getReporterUsername());
    assertEquals("seller01", viewModel.getSellerUsername());
  }
}