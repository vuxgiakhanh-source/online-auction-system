package com.group13.auction.common.dto.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReportDTOs — unit")
class ReportDTOsTest {

  @Nested
  @DisplayName("QualityReportRequestDTO")
  class QualityReportRequestDTOTest {
    @Test
    @DisplayName("defaults — null")
    void defaults() {
      ReportDTOs.QualityReportRequestDTO dto = new ReportDTOs.QualityReportRequestDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getDescription()).isNull();
      assertThat(dto.getEvidenceUrls()).isNull();
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      ReportDTOs.QualityReportRequestDTO dto = new ReportDTOs.QualityReportRequestDTO();
      dto.setAuctionId("a-1");
      dto.setDescription("Hàng giả");
      dto.setEvidenceUrls(Arrays.asList("http://img1.jpg", "http://img2.jpg"));
      assertThat(dto.getAuctionId()).isEqualTo("a-1");
      assertThat(dto.getDescription()).isEqualTo("Hàng giả");
      assertThat(dto.getEvidenceUrls()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("QualityReportDTO")
  class QualityReportDTOTest {
    @Test
    @DisplayName("defaults — null/false")
    void defaults() {
      ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
      assertThat(dto.getReportId()).isNull();
      assertThat(dto.getStatus()).isNull();
      assertThat(dto.isRefundCompleted()).isFalse();
    }

    @Test
    @DisplayName("setters / getters — roundtrip đầy đủ")
    void settersGetters_full() {
      ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
      LocalDateTime ts = LocalDateTime.now();
      dto.setReportId("r-1");
      dto.setAuctionId("a-1");
      dto.setAuctionItemName("Laptop");
      dto.setReporterId("u-1");
      dto.setReporterUsername("buyer");
      dto.setSellerId("s-1");
      dto.setSellerUsername("seller");
      dto.setDescription("Hàng giả");
      dto.setEvidenceUrls(Arrays.asList("url1"));
      dto.setStatus("PENDING");
      dto.setCreatedAt(ts);
      dto.setSellerRefundDeadline(ts.plusDays(1));
      dto.setRefundCompleted(false);
      assertThat(dto.getReportId()).isEqualTo("r-1");
      assertThat(dto.getStatus()).isEqualTo("PENDING");
      assertThat(dto.getEvidenceUrls()).hasSize(1);
      assertThat(dto.getSellerRefundDeadline()).isAfter(ts);
    }

    @Test
    @DisplayName("status — PENDING / APPROVED / REJECTED")
    void status_validValues() {
      for (String s : new String[] {"PENDING", "APPROVED", "REJECTED"}) {
        ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
        dto.setStatus(s);
        assertThat(dto.getStatus()).isEqualTo(s);
      }
    }
  }

  @Nested
  @DisplayName("QualityReportResultDTO")
  class QualityReportResultDTOTest {
    @Test
    @DisplayName("defaults — null/zero/false")
    void defaults() {
      ReportDTOs.QualityReportResultDTO dto = new ReportDTOs.QualityReportResultDTO();
      assertThat(dto.getReportId()).isNull();
      assertThat(dto.getRefundedAmount()).isZero();
      assertThat(dto.isSellerBanned()).isFalse();
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      ReportDTOs.QualityReportResultDTO dto = new ReportDTOs.QualityReportResultDTO();
      dto.setReportId("r-1");
      dto.setAuctionId("a-1");
      dto.setRefundedAmount(5_000_000.0);
      dto.setSellerRatingPenalty(1.0);
      dto.setSellerNewRating(2.5);
      dto.setSellerBanned(true);
      assertThat(dto.getRefundedAmount()).isEqualTo(5_000_000.0);
      assertThat(dto.getSellerRatingPenalty()).isEqualTo(1.0);
      assertThat(dto.getSellerNewRating()).isEqualTo(2.5);
      assertThat(dto.isSellerBanned()).isTrue();
    }
  }
}
