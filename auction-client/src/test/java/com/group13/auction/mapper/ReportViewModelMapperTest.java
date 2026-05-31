package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.viewmodel.admin.QualityReportReviewViewModel;
import com.group13.auction.viewmodel.report.QualityReportViewModel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ReportViewModelMapper}. */
class ReportViewModelMapperTest {

  @Test
  void toViewModelShouldReturnEmptyReportWhenDtoIsNull() {
    QualityReportViewModel viewModel = ReportViewModelMapper.toViewModel(null);

    assertEquals("--", viewModel.getReportId());
    assertEquals("--", viewModel.getAuctionId());
    assertEquals("--", viewModel.getTitle());
    assertEquals("--", viewModel.getReason());
    assertEquals("--", viewModel.getDescription());
    assertEquals("--", viewModel.getStatus());
    assertEquals("--", viewModel.getCreatedAtText());
    assertEquals("--", viewModel.getReporterUsername());
    assertEquals("--", viewModel.getSellerUsername());
  }

  @Test
  void toViewModelShouldMapQualityReportForUserDisplay() {
    ReportDTOs.QualityReportDTO dto = createReport("QR-1", "PENDING");
    dto.setReporterUsername("bidder01");
    dto.setSellerUsername("seller01");

    QualityReportViewModel viewModel = ReportViewModelMapper.toViewModel(dto);

    assertEquals("QR-1", viewModel.getReportId());
    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals("Vintage Camera", viewModel.getTitle());
    assertEquals("Đang chờ duyệt", viewModel.getReason());
    assertEquals("Sản phẩm nhận được khác mô tả.", viewModel.getDescription());
    assertEquals("Đang chờ duyệt", viewModel.getStatus());
    assertEquals("26/05/2026 20:30", viewModel.getCreatedAtText());
    assertEquals("bidder01", viewModel.getReporterUsername());
    assertEquals("seller01", viewModel.getSellerUsername());
  }

  @Test
  void toViewModelShouldUseFallbacksForBlankFieldsAndNullDate() {
    ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
    dto.setReportId("   ");
    dto.setAuctionId(null);
    dto.setAuctionItemName("");
    dto.setDescription("   ");
    dto.setStatus(null);
    dto.setCreatedAt(null);
    dto.setReporterUsername("");
    dto.setSellerUsername("   ");

    QualityReportViewModel viewModel = ReportViewModelMapper.toViewModel(dto);

    assertEquals("--", viewModel.getReportId());
    assertEquals("--", viewModel.getAuctionId());
    assertEquals("Quality report", viewModel.getTitle());
    assertEquals("Không rõ", viewModel.getReason());
    assertEquals("--", viewModel.getDescription());
    assertEquals("Không rõ", viewModel.getStatus());
    assertEquals("--", viewModel.getCreatedAtText());
    assertEquals("--", viewModel.getReporterUsername());
    assertEquals("--", viewModel.getSellerUsername());
  }

  @Test
  void toViewModelsShouldReturnEmptyListWhenReportsAreNull() {
    assertTrue(ReportViewModelMapper.toViewModels(null).isEmpty());
  }

  @Test
  void toViewModelsShouldMapReportsInOrder() {
    ReportDTOs.QualityReportDTO first = createReport("QR-1", "PENDING");
    ReportDTOs.QualityReportDTO second = createReport("QR-2", "APPROVED");

    List<QualityReportViewModel> viewModels =
        ReportViewModelMapper.toViewModels(List.of(first, second));

    assertEquals(2, viewModels.size());
    assertEquals("QR-1", viewModels.get(0).getReportId());
    assertEquals("QR-2", viewModels.get(1).getReportId());
    assertEquals("Đã duyệt", viewModels.get(1).getStatus());
  }

  @Test
  void toReviewViewModelShouldReturnEmptyNotReviewableReportWhenDtoIsNull() {
    QualityReportReviewViewModel viewModel = ReportViewModelMapper.toReviewViewModel(null);

    assertEquals("--", viewModel.getReportId());
    assertEquals("--", viewModel.getReporterId());
    assertEquals("--", viewModel.getAuctionId());
    assertEquals("--", viewModel.getTitle());
    assertEquals("--", viewModel.getReason());
    assertEquals("--", viewModel.getDescription());
    assertEquals("--", viewModel.getStatus());
    assertEquals("--", viewModel.getCreatedAtText());
    assertFalse(viewModel.isReviewable());
  }

  @Test
  void toReviewViewModelShouldMapPendingReportAsReviewable() {
    ReportDTOs.QualityReportDTO dto = createReport("QR-1", "PENDING");
    dto.setReporterId("U-1");

    QualityReportReviewViewModel viewModel = ReportViewModelMapper.toReviewViewModel(dto);

    assertEquals("QR-1", viewModel.getReportId());
    assertEquals("U-1", viewModel.getReporterId());
    assertEquals("A-1", viewModel.getAuctionId());
    assertEquals("Vintage Camera", viewModel.getTitle());
    assertEquals("Đang chờ duyệt", viewModel.getReason());
    assertEquals("Sản phẩm nhận được khác mô tả.", viewModel.getDescription());
    assertEquals("Đang chờ duyệt", viewModel.getStatus());
    assertEquals("26/05/2026 20:30", viewModel.getCreatedAtText());
    assertTrue(viewModel.isReviewable());
  }

  @Test
  void toReviewViewModelShouldMapApprovedReportAsNotReviewable() {
    ReportDTOs.QualityReportDTO dto = createReport("QR-2", "APPROVED");

    QualityReportReviewViewModel viewModel = ReportViewModelMapper.toReviewViewModel(dto);

    assertEquals("Đã duyệt", viewModel.getStatus());
    assertFalse(viewModel.isReviewable());
  }

  @Test
  void toReviewViewModelsShouldReturnEmptyListWhenReportsAreNull() {
    assertTrue(ReportViewModelMapper.toReviewViewModels(null).isEmpty());
  }

  @Test
  void toReviewViewModelsShouldMapReportsInOrder() {
    ReportDTOs.QualityReportDTO first = createReport("QR-1", "PENDING");
    ReportDTOs.QualityReportDTO second = createReport("QR-2", "REJECTED");

    List<QualityReportReviewViewModel> viewModels =
        ReportViewModelMapper.toReviewViewModels(List.of(first, second));

    assertEquals(2, viewModels.size());
    assertEquals("QR-1", viewModels.get(0).getReportId());
    assertTrue(viewModels.get(0).isReviewable());
    assertEquals("QR-2", viewModels.get(1).getReportId());
    assertEquals("Đã từ chối", viewModels.get(1).getStatus());
    assertFalse(viewModels.get(1).isReviewable());
  }

  @Test
  void statusTextShouldKeepUnknownStatusValue() {
    ReportDTOs.QualityReportDTO dto = createReport("QR-3", "ESCALATED");

    QualityReportViewModel userViewModel = ReportViewModelMapper.toViewModel(dto);
    QualityReportReviewViewModel reviewViewModel = ReportViewModelMapper.toReviewViewModel(dto);

    assertEquals("ESCALATED", userViewModel.getStatus());
    assertEquals("ESCALATED", reviewViewModel.getStatus());
    assertFalse(reviewViewModel.isReviewable());
  }

  @Test
  void toReviewViewModelShouldMapEvidenceUrls() {
    ReportDTOs.QualityReportDTO dto = createReport("QR-4", "PENDING");
    dto.setEvidenceUrls(List.of("/uploads/reports/img1.jpg", "/uploads/reports/img2.jpg"));

    QualityReportReviewViewModel viewModel = ReportViewModelMapper.toReviewViewModel(dto);

    assertEquals(
        List.of("/uploads/reports/img1.jpg", "/uploads/reports/img2.jpg"),
        viewModel.getEvidenceUrls());
  }

  private static ReportDTOs.QualityReportDTO createReport(String reportId, String status) {
    ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
    dto.setReportId(reportId);
    dto.setAuctionId("A-1");
    dto.setAuctionItemName("Vintage Camera");
    dto.setReporterId("U-1");
    dto.setReporterUsername("bidder01");
    dto.setSellerUsername("seller01");
    dto.setDescription("Sản phẩm nhận được khác mô tả.");
    dto.setStatus(status);
    dto.setCreatedAt(LocalDateTime.of(2026, 5, 26, 20, 30));
    return dto;
  }
}
