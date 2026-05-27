package com.group13.auction.common.dto.bid;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BidDTOs — unit")
class BidDTOsTest {

  @Nested
  @DisplayName("BidRequestDTO")
  class BidRequestDTOTest {
    @Test
    @DisplayName("default constructor — null/zero")
    void defaults() {
      BidDTOs.BidRequestDTO dto = new BidDTOs.BidRequestDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getAmount()).isZero();
    }

    @Test
    @DisplayName("all-args constructor — fields set")
    void allArgsConstructor() {
      BidDTOs.BidRequestDTO dto = new BidDTOs.BidRequestDTO("a-1", 2_000_000L);
      assertThat(dto.getAuctionId()).isEqualTo("a-1");
      assertThat(dto.getAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      BidDTOs.BidRequestDTO dto = new BidDTOs.BidRequestDTO();
      dto.setAuctionId("a-2");
      dto.setAmount(999L);
      assertThat(dto.getAuctionId()).isEqualTo("a-2");
      assertThat(dto.getAmount()).isEqualTo(999L);
    }
  }

  @Nested
  @DisplayName("BidResultDTO")
  class BidResultDTOTest {
    @Test
    @DisplayName("defaults — null/zero/false")
    void defaults() {
      BidDTOs.BidResultDTO dto = new BidDTOs.BidResultDTO();
      assertThat(dto.getBidId()).isNull();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getAmount()).isZero();
      assertThat(dto.isReserveMet()).isFalse();
      assertThat(dto.getCurrentPrice()).isZero();
      assertThat(dto.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("setters / getters — roundtrip đầy đủ")
    void settersGetters() {
      BidDTOs.BidResultDTO dto = new BidDTOs.BidResultDTO();
      LocalDateTime ts = LocalDateTime.now();
      dto.setBidId("bid-1");
      dto.setAuctionId("a-1");
      dto.setAmount(3_000_000L);
      dto.setReserveMet(true);
      dto.setCurrentPrice(3_000_000L);
      dto.setTimestamp(ts);
      assertThat(dto.getBidId()).isEqualTo("bid-1");
      assertThat(dto.isReserveMet()).isTrue();
      assertThat(dto.getCurrentPrice()).isEqualTo(3_000_000L);
      assertThat(dto.getTimestamp()).isEqualTo(ts);
    }
  }

  @Nested
  @DisplayName("BidUpdateDTO")
  class BidUpdateDTOTest {
    @Test
    @DisplayName("defaults — null/zero/false")
    void defaults() {
      BidDTOs.BidUpdateDTO dto = new BidDTOs.BidUpdateDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getNewCurrentPrice()).isZero();
      assertThat(dto.isReserveMet()).isFalse();
      assertThat(dto.getNewEndTime()).isNull();
    }

    @Test
    @DisplayName("setters / getters — roundtrip đầy đủ")
    void settersGetters() {
      BidDTOs.BidUpdateDTO dto = new BidDTOs.BidUpdateDTO();
      LocalDateTime ts = LocalDateTime.now();
      dto.setAuctionId("a-1");
      dto.setNewCurrentPrice(4_000_000L);
      dto.setLeaderId("u-1");
      dto.setLeaderUsername("alice");
      dto.setReserveMet(true);
      dto.setTimestamp(ts);
      dto.setNewEndTime(ts.plusMinutes(10));
      assertThat(dto.getLeaderUsername()).isEqualTo("alice");
      assertThat(dto.isReserveMet()).isTrue();
      assertThat(dto.getNewEndTime()).isAfter(ts);
    }
  }

  @Nested
  @DisplayName("AutoBidRequestDTO")
  class AutoBidRequestDTOTest {
    @Test
    @DisplayName("default constructor — null/zero")
    void defaults() {
      BidDTOs.AutoBidRequestDTO dto = new BidDTOs.AutoBidRequestDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getMaxBid()).isZero();
    }

    @Test
    @DisplayName("all-args constructor — set đúng")
    void allArgsConstructor() {
      BidDTOs.AutoBidRequestDTO dto = new BidDTOs.AutoBidRequestDTO("a-1", 10_000_000L);
      assertThat(dto.getAuctionId()).isEqualTo("a-1");
      assertThat(dto.getMaxBid()).isEqualTo(10_000_000L);
    }
  }

  @Nested
  @DisplayName("AutoBidRegistrationDTO")
  class AutoBidRegistrationDTOTest {
    @Test
    @DisplayName("defaults — null/zero/false")
    void defaults() {
      BidDTOs.AutoBidRegistrationDTO dto = new BidDTOs.AutoBidRegistrationDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getMaxBid()).isZero();
      assertThat(dto.getCurrentSystemBid()).isZero();
      assertThat(dto.isActive()).isFalse();
      assertThat(dto.getRegisteredAt()).isNull();
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      BidDTOs.AutoBidRegistrationDTO dto = new BidDTOs.AutoBidRegistrationDTO();
      LocalDateTime ts = LocalDateTime.now();
      dto.setAuctionId("a-1");
      dto.setMaxBid(5_000_000L);
      dto.setCurrentSystemBid(3_000_000L);
      dto.setActive(true);
      dto.setRegisteredAt(ts);
      assertThat(dto.getMaxBid()).isEqualTo(5_000_000L);
      assertThat(dto.isActive()).isTrue();
      assertThat(dto.getRegisteredAt()).isEqualTo(ts);
    }
  }

  @Nested
  @DisplayName("AutoBidTriggeredDTO")
  class AutoBidTriggeredDTOTest {
    @Test
    @DisplayName("defaults — null/zero/false")
    void defaults() {
      BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getBidAmount()).isZero();
      assertThat(dto.isNowLeading()).isFalse();
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
      LocalDateTime ts = LocalDateTime.now();
      dto.setAuctionId("a-1");
      dto.setBidAmount(2_000_000L);
      dto.setNewCurrentPrice(2_000_000L);
      dto.setRemainingMaxBid(3_000_000L);
      dto.setNowLeading(true);
      dto.setTimestamp(ts);
      assertThat(dto.getBidAmount()).isEqualTo(2_000_000L);
      assertThat(dto.getRemainingMaxBid()).isEqualTo(3_000_000L);
      assertThat(dto.isNowLeading()).isTrue();
    }
  }

  @Nested
  @DisplayName("AutoBidExhaustedDTO")
  class AutoBidExhaustedDTOTest {
    @Test
    @DisplayName("defaults — null/zero")
    void defaults() {
      BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getMaxBid()).isZero();
      assertThat(dto.getCurrentPrice()).isZero();
      assertThat(dto.getLeadingBidderUsername()).isNull();
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
      dto.setAuctionId("a-1");
      dto.setMaxBid(5_000_000L);
      dto.setCurrentPrice(5_100_000L);
      dto.setLeadingBidderUsername("rival");
      assertThat(dto.getMaxBid()).isEqualTo(5_000_000L);
      assertThat(dto.getCurrentPrice()).isEqualTo(5_100_000L);
      assertThat(dto.getLeadingBidderUsername()).isEqualTo("rival");
    }
  }

  @Nested
  @DisplayName("BidChartPointDTO")
  class BidChartPointDTOTest {
    @Test
    @DisplayName("defaults — null/zero/false")
    void defaults() {
      BidDTOs.BidChartPointDTO dto = new BidDTOs.BidChartPointDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getPrice()).isZero();
      assertThat(dto.isAutoBid()).isFalse();
    }

    @Test
    @DisplayName("setters / getters — roundtrip")
    void settersGetters() {
      BidDTOs.BidChartPointDTO dto = new BidDTOs.BidChartPointDTO();
      LocalDateTime ts = LocalDateTime.now();
      dto.setAuctionId("a-1");
      dto.setPrice(3_000_000L);
      dto.setBidderUsername("alice");
      dto.setTimestamp(ts);
      dto.setAutoBid(true);
      assertThat(dto.getPrice()).isEqualTo(3_000_000L);
      assertThat(dto.getBidderUsername()).isEqualTo("alice");
      assertThat(dto.isAutoBid()).isTrue();
    }
  }

  @Nested
  @DisplayName("BidHistoryResponseDTO")
  class BidHistoryResponseDTOTest {
    @Test
    @DisplayName("defaults — null/zero")
    void defaults() {
      BidDTOs.BidHistoryResponseDTO dto = new BidDTOs.BidHistoryResponseDTO();
      assertThat(dto.getAuctionId()).isNull();
      assertThat(dto.getPoints()).isNull();
      assertThat(dto.getStartingPrice()).isZero();
      assertThat(dto.getReservePrice()).isZero();
    }

    @Test
    @DisplayName("setters / getters — roundtrip với danh sách điểm")
    void settersGetters_withPoints() {
      BidDTOs.BidHistoryResponseDTO dto = new BidDTOs.BidHistoryResponseDTO();
      BidDTOs.BidChartPointDTO pt = new BidDTOs.BidChartPointDTO();
      pt.setPrice(1_000_000L);
      dto.setAuctionId("a-1");
      dto.setPoints(Arrays.asList(pt));
      dto.setStartingPrice(500_000L);
      dto.setReservePrice(2_000_000L);
      assertThat(dto.getPoints()).hasSize(1);
      assertThat(dto.getPoints().get(0).getPrice()).isEqualTo(1_000_000L);
      assertThat(dto.getStartingPrice()).isEqualTo(500_000L);
    }
  }
}
