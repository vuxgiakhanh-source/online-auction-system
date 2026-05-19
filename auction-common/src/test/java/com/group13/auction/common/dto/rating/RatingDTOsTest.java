package com.group13.auction.common.dto.rating;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RatingDTOs — unit")
class RatingDTOsTest {

    @Nested @DisplayName("RateSellerRequestDTO")
    class RateSellerRequestDTOTest {
        @Test @DisplayName("defaults — null/zero")
        void defaults() {
            RatingDTOs.RateSellerRequestDTO dto = new RatingDTOs.RateSellerRequestDTO();
            assertThat(dto.getSellerId()).isNull();
            assertThat(dto.getRating()).isZero();
            assertThat(dto.getComment()).isNull();
            assertThat(dto.getAuctionId()).isNull();
        }
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            RatingDTOs.RateSellerRequestDTO dto = new RatingDTOs.RateSellerRequestDTO();
            dto.setSellerId("s-1"); dto.setRating(4.5);
            dto.setComment("Tốt"); dto.setAuctionId("a-1");
            assertThat(dto.getSellerId()).isEqualTo("s-1");
            assertThat(dto.getRating()).isEqualTo(4.5);
            assertThat(dto.getComment()).isEqualTo("Tốt");
        }
    }

    @Nested @DisplayName("RateBidderRequestDTO")
    class RateBidderRequestDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            RatingDTOs.RateBidderRequestDTO dto = new RatingDTOs.RateBidderRequestDTO();
            dto.setBidderId("b-1"); dto.setRating(3.0);
            dto.setComment("Bình thường"); dto.setAuctionId("a-2");
            assertThat(dto.getBidderId()).isEqualTo("b-1");
            assertThat(dto.getRating()).isEqualTo(3.0);
        }
    }

    @Nested @DisplayName("RatingEntryDTO")
    class RatingEntryDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            RatingDTOs.RatingEntryDTO dto = new RatingDTOs.RatingEntryDTO();
            LocalDateTime ts = LocalDateTime.now();
            dto.setFromUserId("u-1"); dto.setFromUsername("alice");
            dto.setRating(5.0); dto.setComment("Xuất sắc"); dto.setCreatedAt(ts);
            assertThat(dto.getFromUserId()).isEqualTo("u-1");
            assertThat(dto.getRating()).isEqualTo(5.0);
            assertThat(dto.getCreatedAt()).isEqualTo(ts);
        }
    }

    @Nested @DisplayName("RatingHistoryDTO")
    class RatingHistoryDTOTest {
        @Test @DisplayName("defaults — null/zero")
        void defaults() {
            RatingDTOs.RatingHistoryDTO dto = new RatingDTOs.RatingHistoryDTO();
            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getAverageRating()).isZero();
            assertThat(dto.getTotalRatings()).isZero();
            assertThat(dto.getEntries()).isNull();
        }
        @Test @DisplayName("setters / getters — roundtrip với entries")
        void withEntries() {
            RatingDTOs.RatingHistoryDTO dto = new RatingDTOs.RatingHistoryDTO();
            RatingDTOs.RatingEntryDTO entry = new RatingDTOs.RatingEntryDTO();
            entry.setRating(4.0);
            dto.setUserId("u-1"); dto.setAverageRating(4.0);
            dto.setTotalRatings(1); dto.setEntries(Arrays.asList(entry));
            assertThat(dto.getEntries()).hasSize(1);
            assertThat(dto.getAverageRating()).isEqualTo(4.0);
        }
    }

    @Nested @DisplayName("AccountSuspendedDTO")
    class AccountSuspendedDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            RatingDTOs.AccountSuspendedDTO dto = new RatingDTOs.AccountSuspendedDTO();
            dto.setCurrentRating(1.2); dto.setThreshold(1.5); dto.setReason("LOW_RATING");
            assertThat(dto.getCurrentRating()).isEqualTo(1.2);
            assertThat(dto.getThreshold()).isEqualTo(1.5);
            assertThat(dto.getReason()).isEqualTo("LOW_RATING");
        }
    }

    @Nested @DisplayName("AccountRestoredDTO")
    class AccountRestoredDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            RatingDTOs.AccountRestoredDTO dto = new RatingDTOs.AccountRestoredDTO();
            dto.setNewRating(3.0); dto.setNewStatus("ACTIVE");
            assertThat(dto.getNewRating()).isEqualTo(3.0);
            assertThat(dto.getNewStatus()).isEqualTo("ACTIVE");
        }
    }

    @Nested @DisplayName("AccountBannedDTO")
    class AccountBannedDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            RatingDTOs.AccountBannedDTO dto = new RatingDTOs.AccountBannedDTO();
            dto.setReason("FRAUD"); dto.setBannedBy("staff01");
            assertThat(dto.getReason()).isEqualTo("FRAUD");
            assertThat(dto.getBannedBy()).isEqualTo("staff01");
        }
    }
}
