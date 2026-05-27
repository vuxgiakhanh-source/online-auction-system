package com.group13.auction.service.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link QualityReportService}. */
class QualityReportServiceValidationTest {

  @Test
  void submitQualityReportShouldFailWhenAuctionIdIsBlank() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReport("   ", "Sản phẩm không đúng mô tả.", List.of("evidence.png")),
        "Thiếu mã phiên đấu giá cần báo cáo.");
  }

  @Test
  void submitQualityReportShouldFailWhenDescriptionIsBlank() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReport("A-1", "   ", List.of("evidence.png")),
        "Vui lòng nhập mô tả báo cáo.");
  }

  @Test
  void submitQualityReportShouldFailWhenDescriptionIsTooShort() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReport("A-1", "quá ngắn", List.of("evidence.png")),
        "Mô tả báo cáo cần có ít nhất 10 ký tự.");
  }

  @Test
  void submitQualityReportShouldFailWhenDescriptionIsTooLong() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReport("A-1", "x".repeat(1001), List.of("evidence.png")),
        "Mô tả báo cáo không được vượt quá 1000 ký tự.");
  }

  @Test
  void submitQualityReportShouldFailWhenEvidenceUrlsAreNull() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReport("A-1", "Sản phẩm không đúng mô tả.", null),
        "Vui lòng chọn ít nhất một ảnh bằng chứng.");
  }

  @Test
  void submitQualityReportShouldFailWhenEvidenceUrlsAreBlankAfterNormalization() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReport("A-1", "Sản phẩm không đúng mô tả.", List.of("   ")),
        "Vui lòng chọn ít nhất một ảnh bằng chứng.");
  }

  @Test
  void submitQualityReportWithImagesShouldFailWhenAuctionIdIsBlank() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReportWithImages(
            "   ", "Sản phẩm không đúng mô tả.", List.of(Path.of("evidence.png"))),
        "Thiếu mã phiên đấu giá cần báo cáo.");
  }

  @Test
  void submitQualityReportWithImagesShouldFailWhenDescriptionIsBlank() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReportWithImages("A-1", "   ", List.of(Path.of("evidence.png"))),
        "Vui lòng nhập mô tả báo cáo.");
  }

  @Test
  void submitQualityReportWithImagesShouldFailWhenImagePathsAreNull() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReportWithImages("A-1", "Sản phẩm không đúng mô tả.", null),
        "Vui lòng chọn ít nhất một ảnh bằng chứng.");
  }

  @Test
  void submitQualityReportWithImagesShouldFailWhenImagePathsAreEmptyAfterNormalization() {
    QualityReportService service = createService();

    assertFutureFailsWithMessage(
        service.submitQualityReportWithImages("A-1", "Sản phẩm không đúng mô tả.", List.of()),
        "Vui lòng chọn ít nhất một ảnh bằng chứng.");
  }

  private static QualityReportService createService() {
    return new QualityReportService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
