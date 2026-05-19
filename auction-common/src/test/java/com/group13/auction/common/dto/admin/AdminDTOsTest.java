package com.group13.auction.common.dto.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho {@link AdminDTOs} — mọi inner DTO class.
 */
@DisplayName("AdminDTOs — unit")
class AdminDTOsTest {

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AdminBanUserDTO")
    class AdminBanUserDTOTest {

        @Test
        @DisplayName("default constructor — tất cả field null")
        void defaultConstructor_allNull() {
            AdminDTOs.AdminBanUserDTO dto = new AdminDTOs.AdminBanUserDTO();
            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getReason()).isNull();
        }

        @Test
        @DisplayName("setters / getters — roundtrip đúng")
        void settersGetters_roundtrip() {
            AdminDTOs.AdminBanUserDTO dto = new AdminDTOs.AdminBanUserDTO();
            dto.setUserId("user-123");
            dto.setReason("FRAUD");
            assertThat(dto.getUserId()).isEqualTo("user-123");
            assertThat(dto.getReason()).isEqualTo("FRAUD");
        }

        @Test
        @DisplayName("reason — các giá trị hợp lệ theo contract")
        void reason_validValues() {
            for (String reason : new String[]{"FRAUD", "LOW_RATING", "SELLER_REFUND_DEFAULT", "OTHER"}) {
                AdminDTOs.AdminBanUserDTO dto = new AdminDTOs.AdminBanUserDTO();
                dto.setReason(reason);
                assertThat(dto.getReason()).isEqualTo(reason);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("CreateStaffAdminDTO")
    class CreateStaffAdminDTOTest {

        @Test
        @DisplayName("default constructor — tất cả field null")
        void defaultConstructor_allNull() {
            AdminDTOs.CreateStaffAdminDTO dto = new AdminDTOs.CreateStaffAdminDTO();
            assertThat(dto.getUsername()).isNull();
            assertThat(dto.getPassword()).isNull();
            assertThat(dto.getEmail()).isNull();
        }

        @Test
        @DisplayName("setters / getters — roundtrip đúng")
        void settersGetters_roundtrip() {
            AdminDTOs.CreateStaffAdminDTO dto = new AdminDTOs.CreateStaffAdminDTO();
            dto.setUsername("staff01");
            dto.setPassword("pass123");
            dto.setEmail("staff01@test.com");
            assertThat(dto.getUsername()).isEqualTo("staff01");
            assertThat(dto.getPassword()).isEqualTo("pass123");
            assertThat(dto.getEmail()).isEqualTo("staff01@test.com");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("FraudDetectedDTO")
    class FraudDetectedDTOTest {

        @Test
        @DisplayName("default constructor — tất cả field null")
        void defaultConstructor_allNull() {
            AdminDTOs.FraudDetectedDTO dto = new AdminDTOs.FraudDetectedDTO();
            assertThat(dto.getAuctionId()).isNull();
            assertThat(dto.getSuspectedUserId()).isNull();
            assertThat(dto.getSuspectedUsername()).isNull();
            assertThat(dto.getDescription()).isNull();
        }

        @Test
        @DisplayName("setters / getters — roundtrip đúng")
        void settersGetters_roundtrip() {
            AdminDTOs.FraudDetectedDTO dto = new AdminDTOs.FraudDetectedDTO();
            dto.setAuctionId("auction-1");
            dto.setSuspectedUserId("user-99");
            dto.setSuspectedUsername("shill_bidder");
            dto.setDescription("Shill bidding detected");
            assertThat(dto.getAuctionId()).isEqualTo("auction-1");
            assertThat(dto.getSuspectedUserId()).isEqualTo("user-99");
            assertThat(dto.getSuspectedUsername()).isEqualTo("shill_bidder");
            assertThat(dto.getDescription()).isEqualTo("Shill bidding detected");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SystemAnnouncementDTO")
    class SystemAnnouncementDTOTest {

        @Test
        @DisplayName("default constructor — tất cả field null")
        void defaultConstructor_allNull() {
            AdminDTOs.SystemAnnouncementDTO dto = new AdminDTOs.SystemAnnouncementDTO();
            assertThat(dto.getMessage()).isNull();
            assertThat(dto.getSeverity()).isNull();
        }

        @Test
        @DisplayName("setters / getters — roundtrip đúng")
        void settersGetters_roundtrip() {
            AdminDTOs.SystemAnnouncementDTO dto = new AdminDTOs.SystemAnnouncementDTO();
            dto.setMessage("Bảo trì lúc 2am");
            dto.setSeverity("WARNING");
            assertThat(dto.getMessage()).isEqualTo("Bảo trì lúc 2am");
            assertThat(dto.getSeverity()).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("severity — các giá trị hợp lệ theo contract")
        void severity_validValues() {
            for (String sev : new String[]{"INFO", "WARNING", "CRITICAL"}) {
                AdminDTOs.SystemAnnouncementDTO dto = new AdminDTOs.SystemAnnouncementDTO();
                dto.setSeverity(sev);
                assertThat(dto.getSeverity()).isEqualTo(sev);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ServerShutdownDTO")
    class ServerShutdownDTOTest {

        @Test
        @DisplayName("default constructor — reason null, shutdownInSeconds = 0")
        void defaultConstructor_defaults() {
            AdminDTOs.ServerShutdownDTO dto = new AdminDTOs.ServerShutdownDTO();
            assertThat(dto.getReason()).isNull();
            assertThat(dto.getShutdownInSeconds()).isZero();
        }

        @Test
        @DisplayName("setters / getters — roundtrip đúng")
        void settersGetters_roundtrip() {
            AdminDTOs.ServerShutdownDTO dto = new AdminDTOs.ServerShutdownDTO();
            dto.setReason("Maintenance");
            dto.setShutdownInSeconds(300);
            assertThat(dto.getReason()).isEqualTo("Maintenance");
            assertThat(dto.getShutdownInSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("shutdownInSeconds = 0 — vẫn set được")
        void shutdownInSeconds_zero() {
            AdminDTOs.ServerShutdownDTO dto = new AdminDTOs.ServerShutdownDTO();
            dto.setShutdownInSeconds(0);
            assertThat(dto.getShutdownInSeconds()).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("NotificationDTO")
    class NotificationDTOTest {

        @Test
        @DisplayName("default constructor — tất cả field null/false")
        void defaultConstructor_defaults() {
            AdminDTOs.NotificationDTO dto = new AdminDTOs.NotificationDTO();
            assertThat(dto.getId()).isNull();
            assertThat(dto.getType()).isNull();
            assertThat(dto.getTitle()).isNull();
            assertThat(dto.getBody()).isNull();
            assertThat(dto.isRead()).isFalse();
            assertThat(dto.getCreatedAt()).isNull();
            assertThat(dto.getRelatedAuctionId()).isNull();
        }

        @Test
        @DisplayName("setters / getters — roundtrip đúng toàn bộ field")
        void settersGetters_allFields() {
            AdminDTOs.NotificationDTO dto = new AdminDTOs.NotificationDTO();
            LocalDateTime now = LocalDateTime.now();
            dto.setId("notif-1");
            dto.setType("BID_PLACED");
            dto.setTitle("Bid mới");
            dto.setBody("Ai đó vừa bid cao hơn bạn");
            dto.setRead(true);
            dto.setCreatedAt(now);
            dto.setRelatedAuctionId("auction-42");

            assertThat(dto.getId()).isEqualTo("notif-1");
            assertThat(dto.getType()).isEqualTo("BID_PLACED");
            assertThat(dto.getTitle()).isEqualTo("Bid mới");
            assertThat(dto.getBody()).isEqualTo("Ai đó vừa bid cao hơn bạn");
            assertThat(dto.isRead()).isTrue();
            assertThat(dto.getCreatedAt()).isEqualTo(now);
            assertThat(dto.getRelatedAuctionId()).isEqualTo("auction-42");
        }

        @Test
        @DisplayName("setRead(false) — isRead trả false")
        void setRead_false() {
            AdminDTOs.NotificationDTO dto = new AdminDTOs.NotificationDTO();
            dto.setRead(true);
            dto.setRead(false);
            assertThat(dto.isRead()).isFalse();
        }
    }
}
