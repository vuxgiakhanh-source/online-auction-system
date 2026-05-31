package com.group13.auction.mapper;

import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.admin.QualityReportReviewViewModel;
import com.group13.auction.viewmodel.report.QualityReportViewModel;
import java.util.List;

/** Mapper chuyển quality report DTO từ auction-common sang view model phía client. */
public final class ReportViewModelMapper {

  private ReportViewModelMapper() {
    // Utility class.
  }

  /**
   * Chuyển report DTO sang view model phía người dùng.
   *
   * @param dto quality report DTO
   * @return quality report view model
   */
  public static QualityReportViewModel toViewModel(ReportDTOs.QualityReportDTO dto) {
    if (dto == null) {
      return new QualityReportViewModel("--", "--", "--", "--", "--", "--", "--", "--", "--");
    }

    return new QualityReportViewModel(
        fallback(dto.getReportId()),
        fallback(dto.getAuctionId()),
        reportTitle(dto),
        statusText(dto.getStatus()),
        fallback(dto.getDescription()),
        statusText(dto.getStatus()),
        DateTimeUtil.formatDateTime(dto.getCreatedAt()),
        fallback(dto.getReporterUsername()),
        fallback(dto.getSellerUsername()));
  }

  /** Chuyển danh sách report DTO sang view model phía người dùng. */
  public static List<QualityReportViewModel> toViewModels(
      List<ReportDTOs.QualityReportDTO> reports) {
    if (reports == null) {
      return List.of();
    }

    return reports.stream().map(ReportViewModelMapper::toViewModel).toList();
  }

  /**
   * Chuyển danh sách report DTO sang danh sách view model dành cho admin review.
   *
   * @param reports danh sách DTO server trả về
   * @return danh sách view model
   */
  public static List<QualityReportReviewViewModel> toReviewViewModels(
      List<ReportDTOs.QualityReportDTO> reports) {
    if (reports == null) {
      return List.of();
    }

    return reports.stream().map(ReportViewModelMapper::toReviewViewModel).toList();
  }

  /**
   * Chuyển một report DTO sang view model dành cho admin review.
   *
   * @param dto quality report DTO
   * @return report review view model
   */
  public static QualityReportReviewViewModel toReviewViewModel(ReportDTOs.QualityReportDTO dto) {
    if (dto == null) {
      return new QualityReportReviewViewModel(
          "--", "--", "--", "--", "--", "--", "--", "--", List.of(), false);
    }

    String status = dto.getStatus();

    return new QualityReportReviewViewModel(
        fallback(dto.getReportId()),
        fallback(dto.getReporterId()),
        fallback(dto.getAuctionId()),
        reportTitle(dto),
        statusText(status),
        fallback(dto.getDescription()),
        statusText(status),
        DateTimeUtil.formatDateTime(dto.getCreatedAt()),
        dto.getEvidenceUrls() == null ? List.of() : List.copyOf(dto.getEvidenceUrls()),
        "PENDING".equalsIgnoreCase(fallback(status)));
  }

  private static String reportTitle(ReportDTOs.QualityReportDTO dto) {
    if (dto == null || dto.getAuctionItemName() == null || dto.getAuctionItemName().isBlank()) {
      return "Quality report";
    }

    return dto.getAuctionItemName();
  }

  private static String statusText(String status) {
    if (status == null || status.isBlank()) {
      return "Không rõ";
    }

    return switch (status.trim().toUpperCase()) {
      case "PENDING" -> "Đang chờ duyệt";
      case "APPROVED" -> "Đã duyệt";
      case "REJECTED" -> "Đã từ chối";
      default -> status;
    };
  }

  private static String fallback(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}
