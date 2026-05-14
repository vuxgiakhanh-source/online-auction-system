package com.group13.auction.common.dto.auction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuctionDTOs — unit")
class AuctionDTOsTest {

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ItemDTO")
    class ItemDTOTest {

        @Test @DisplayName("default constructor — tất cả null/zero")
        void defaults() {
            AuctionDTOs.ItemDTO dto = new AuctionDTOs.ItemDTO();
            assertThat(dto.getId()).isNull();
            assertThat(dto.getName()).isNull();
            assertThat(dto.getCategory()).isNull();
            assertThat(dto.getStartingPrice()).isZero();
            assertThat(dto.getSellerId()).isNull();
            assertThat(dto.getExtraFields()).isNull();
        }

        @Test @DisplayName("setters / getters — roundtrip đầy đủ")
        void settersGetters_fullRoundtrip() {
            AuctionDTOs.ItemDTO dto = new AuctionDTOs.ItemDTO();
            Map<String, Object> extra = new HashMap<>();
            extra.put("brand", "Sony");
            dto.setId("item-1");
            dto.setName("Laptop");
            dto.setDescription("Mới 100%");
            dto.setCategory("ELECTRONICS");
            dto.setStartingPrice(5_000_000.0);
            dto.setSellerId("seller-1");
            dto.setSellerUsername("alice");
            dto.setExtraFields(extra);
            assertThat(dto.getId()).isEqualTo("item-1");
            assertThat(dto.getName()).isEqualTo("Laptop");
            assertThat(dto.getDescription()).isEqualTo("Mới 100%");
            assertThat(dto.getCategory()).isEqualTo("ELECTRONICS");
            assertThat(dto.getStartingPrice()).isEqualTo(5_000_000.0);
            assertThat(dto.getSellerId()).isEqualTo("seller-1");
            assertThat(dto.getSellerUsername()).isEqualTo("alice");
            assertThat(dto.getExtraFields()).containsEntry("brand", "Sony");
        }

        @Test @DisplayName("category — 3 giá trị hợp lệ")
        void category_validValues() {
            for (String cat : new String[]{"ELECTRONICS", "ART", "VEHICLE"}) {
                AuctionDTOs.ItemDTO dto = new AuctionDTOs.ItemDTO();
                dto.setCategory(cat);
                assertThat(dto.getCategory()).isEqualTo(cat);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AuctionDTO")
    class AuctionDTOTest {

        @Test @DisplayName("default constructor — tất cả null/zero/false")
        void defaults() {
            AuctionDTOs.AuctionDTO dto = new AuctionDTOs.AuctionDTO();
            assertThat(dto.getId()).isNull();
            assertThat(dto.getItem()).isNull();
            assertThat(dto.getStatus()).isNull();
            assertThat(dto.getCurrentPrice()).isZero();
            assertThat(dto.getReservePrice()).isZero();
            assertThat(dto.getViewerCount()).isZero();
            assertThat(dto.isReserveMet()).isFalse();
        }

        @Test @DisplayName("setters / getters — roundtrip đầy đủ")
        void settersGetters_fullRoundtrip() {
            AuctionDTOs.AuctionDTO dto = new AuctionDTOs.AuctionDTO();
            LocalDateTime now = LocalDateTime.now();
            AuctionDTOs.ItemDTO item = new AuctionDTOs.ItemDTO();
            item.setName("Xe máy");
            dto.setId("auction-1");
            dto.setItem(item);
            dto.setStartTime(now);
            dto.setEndTime(now.plusHours(2));
            dto.setExtendedEndTime(now.plusHours(2).plusMinutes(10));
            dto.setCurrentPrice(3_000_000.0);
            dto.setReservePrice(5_000_000.0);
            dto.setStatus("RUNNING");
            dto.setCurrentLeaderId("user-1");
            dto.setCurrentLeaderUsername("bob");
            dto.setViewerCount(42);
            dto.setReserveMet(true);
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);
            assertThat(dto.getId()).isEqualTo("auction-1");
            assertThat(dto.getItem().getName()).isEqualTo("Xe máy");
            assertThat(dto.getCurrentPrice()).isEqualTo(3_000_000.0);
            assertThat(dto.getReservePrice()).isEqualTo(5_000_000.0);
            assertThat(dto.getStatus()).isEqualTo("RUNNING");
            assertThat(dto.getViewerCount()).isEqualTo(42);
            assertThat(dto.isReserveMet()).isTrue();
            assertThat(dto.getExtendedEndTime()).isAfter(dto.getEndTime());
        }

        @Test @DisplayName("status — 6 giá trị hợp lệ theo contract")
        void status_validValues() {
            for (String s : new String[]{"OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED", "RESERVE_NOT_MET"}) {
                AuctionDTOs.AuctionDTO dto = new AuctionDTOs.AuctionDTO();
                dto.setStatus(s);
                assertThat(dto.getStatus()).isEqualTo(s);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AuctionListDTO")
    class AuctionListDTOTest {

        @Test @DisplayName("default constructor — auctions null, counts zero")
        void defaults() {
            AuctionDTOs.AuctionListDTO dto = new AuctionDTOs.AuctionListDTO();
            assertThat(dto.getAuctions()).isNull();
            assertThat(dto.getTotalCount()).isZero();
        }

        @Test @DisplayName("2-arg constructor — auctions và totalCount set")
        void twoArgConstructor() {
            AuctionDTOs.AuctionDTO a = new AuctionDTOs.AuctionDTO();
            a.setId("a1");
            AuctionDTOs.AuctionListDTO dto = new AuctionDTOs.AuctionListDTO(Arrays.asList(a), 1);
            assertThat(dto.getAuctions()).hasSize(1);
            assertThat(dto.getTotalCount()).isEqualTo(1);
        }

        @Test @DisplayName("page và pageSize — set/get đúng")
        void pageFields() {
            AuctionDTOs.AuctionListDTO dto = new AuctionDTOs.AuctionListDTO();
            dto.setPage(2);
            dto.setPageSize(10);
            assertThat(dto.getPage()).isEqualTo(2);
            assertThat(dto.getPageSize()).isEqualTo(10);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("CreateAuctionRequestDTO")
    class CreateAuctionRequestDTOTest {

        @Test @DisplayName("default constructor — fields null/zero")
        void defaults() {
            AuctionDTOs.CreateAuctionRequestDTO dto = new AuctionDTOs.CreateAuctionRequestDTO();
            assertThat(dto.getItemName()).isNull();
            assertThat(dto.getItemCategory()).isNull();
            assertThat(dto.getStartingPrice()).isZero();
            assertThat(dto.getReservePrice()).isZero();
        }

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.CreateAuctionRequestDTO dto = new AuctionDTOs.CreateAuctionRequestDTO();
            LocalDateTime start = LocalDateTime.now().plusHours(1);
            LocalDateTime end = start.plusHours(3);
            dto.setItemName("Tranh sơn dầu");
            dto.setItemDescription("Tác phẩm 2024");
            dto.setItemCategory("ART");
            dto.setStartingPrice(1_000_000.0);
            dto.setReservePrice(2_000_000.0);
            dto.setStartTime(start);
            dto.setEndTime(end);
            Map<String, Object> extra = new HashMap<>();
            extra.put("medium", "oil");
            dto.setItemExtraFields(extra);
            assertThat(dto.getItemName()).isEqualTo("Tranh sơn dầu");
            assertThat(dto.getItemCategory()).isEqualTo("ART");
            assertThat(dto.getStartingPrice()).isEqualTo(1_000_000.0);
            assertThat(dto.getStartTime()).isEqualTo(start);
            assertThat(dto.getEndTime()).isEqualTo(end);
            assertThat(dto.getItemExtraFields()).containsEntry("medium", "oil");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AuctionListRequestDTO")
    class AuctionListRequestDTOTest {

        @Test @DisplayName("default constructor — page=0, pageSize=20")
        void defaults() {
            AuctionDTOs.AuctionListRequestDTO dto = new AuctionDTOs.AuctionListRequestDTO();
            assertThat(dto.getPage()).isZero();
            assertThat(dto.getPageSize()).isEqualTo(20);
            assertThat(dto.getStatusFilter()).isNull();
            assertThat(dto.getSortBy()).isNull();
        }

        @Test @DisplayName("setters — override defaults")
        void setters_overrideDefaults() {
            AuctionDTOs.AuctionListRequestDTO dto = new AuctionDTOs.AuctionListRequestDTO();
            dto.setStatusFilter("RUNNING");
            dto.setSortBy("CURRENT_PRICE");
            dto.setPage(1);
            dto.setPageSize(10);
            assertThat(dto.getStatusFilter()).isEqualTo("RUNNING");
            assertThat(dto.getSortBy()).isEqualTo("CURRENT_PRICE");
            assertThat(dto.getPage()).isEqualTo(1);
            assertThat(dto.getPageSize()).isEqualTo(10);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("UpdateAuctionDTO")
    class UpdateAuctionDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.UpdateAuctionDTO dto = new AuctionDTOs.UpdateAuctionDTO();
            LocalDateTime newEnd = LocalDateTime.now().plusHours(5);
            dto.setAuctionId("auction-99");
            dto.setNewEndTime(newEnd);
            dto.setNewReservePrice(3_000_000.0);
            assertThat(dto.getAuctionId()).isEqualTo("auction-99");
            assertThat(dto.getNewEndTime()).isEqualTo(newEnd);
            assertThat(dto.getNewReservePrice()).isEqualTo(3_000_000.0);
        }

        @Test @DisplayName("newReservePrice null — không đổi reserve")
        void newReservePrice_null() {
            AuctionDTOs.UpdateAuctionDTO dto = new AuctionDTOs.UpdateAuctionDTO();
            dto.setNewReservePrice(null);
            assertThat(dto.getNewReservePrice()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("CancelAuctionRequestDTO")
    class CancelAuctionRequestDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.CancelAuctionRequestDTO dto = new AuctionDTOs.CancelAuctionRequestDTO();
            dto.setAuctionId("auction-5");
            dto.setReason("Sản phẩm không còn");
            assertThat(dto.getAuctionId()).isEqualTo("auction-5");
            assertThat(dto.getReason()).isEqualTo("Sản phẩm không còn");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AdminCancelAuctionDTO")
    class AdminCancelAuctionDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.AdminCancelAuctionDTO dto = new AuctionDTOs.AdminCancelAuctionDTO();
            dto.setAuctionId("auction-7");
            dto.setReason("FRAUD");
            assertThat(dto.getAuctionId()).isEqualTo("auction-7");
            assertThat(dto.getReason()).isEqualTo("FRAUD");
        }

        @Test @DisplayName("reason — các giá trị hợp lệ")
        void reason_validValues() {
            for (String r : new String[]{"SELLER_REQUEST", "FRAUD", "SYSTEM_ERROR", "NO_WINNER", "RESERVE_NOT_MET"}) {
                AuctionDTOs.AdminCancelAuctionDTO dto = new AuctionDTOs.AdminCancelAuctionDTO();
                dto.setReason(r);
                assertThat(dto.getReason()).isEqualTo(r);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AuctionUpdateDTO")
    class AuctionUpdateDTOTest {

        @Test @DisplayName("setters / getters — roundtrip đầy đủ")
        void settersGetters_full() {
            AuctionDTOs.AuctionUpdateDTO dto = new AuctionDTOs.AuctionUpdateDTO();
            dto.setAuctionId("a-1");
            dto.setNewStatus("FINISHED");
            dto.setFinalPrice(8_000_000.0);
            dto.setWinnerId("user-w");
            dto.setWinnerUsername("winner");
            dto.setCancelReason("FRAUD");
            dto.setMessage("Phiên kết thúc");
            LocalDateTime ext = LocalDateTime.now().plusMinutes(10);
            dto.setExtendedEndTime(ext);
            assertThat(dto.getAuctionId()).isEqualTo("a-1");
            assertThat(dto.getNewStatus()).isEqualTo("FINISHED");
            assertThat(dto.getFinalPrice()).isEqualTo(8_000_000.0);
            assertThat(dto.getWinnerId()).isEqualTo("user-w");
            assertThat(dto.getWinnerUsername()).isEqualTo("winner");
            assertThat(dto.getCancelReason()).isEqualTo("FRAUD");
            assertThat(dto.getMessage()).isEqualTo("Phiên kết thúc");
            assertThat(dto.getExtendedEndTime()).isEqualTo(ext);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AuctionExtendedDTO")
    class AuctionExtendedDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.AuctionExtendedDTO dto = new AuctionDTOs.AuctionExtendedDTO();
            LocalDateTime newEnd = LocalDateTime.now().plusMinutes(5);
            dto.setAuctionId("a-2");
            dto.setNewEndTime(newEnd);
            dto.setExtendedBySeconds(300);
            assertThat(dto.getAuctionId()).isEqualTo("a-2");
            assertThat(dto.getNewEndTime()).isEqualTo(newEnd);
            assertThat(dto.getExtendedBySeconds()).isEqualTo(300);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SellerCancelRequestNotifyDTO")
    class SellerCancelRequestNotifyDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.SellerCancelRequestNotifyDTO dto = new AuctionDTOs.SellerCancelRequestNotifyDTO();
            LocalDateTime ts = LocalDateTime.now();
            dto.setAuctionId("a-3");
            dto.setAuctionName("Laptop cũ");
            dto.setSellerUsername("seller01");
            dto.setReason("Không muốn bán");
            dto.setRequestTime(ts);
            assertThat(dto.getAuctionId()).isEqualTo("a-3");
            assertThat(dto.getAuctionName()).isEqualTo("Laptop cũ");
            assertThat(dto.getSellerUsername()).isEqualTo("seller01");
            assertThat(dto.getReason()).isEqualTo("Không muốn bán");
            assertThat(dto.getRequestTime()).isEqualTo(ts);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("JoinAuctionResponseDTO")
    class JoinAuctionResponseDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.JoinAuctionResponseDTO dto = new AuctionDTOs.JoinAuctionResponseDTO();
            AuctionDTOs.AuctionDTO auction = new AuctionDTOs.AuctionDTO();
            auction.setId("a-10");
            dto.setAuction(auction);
            dto.setDepositAmount(500_000.0);
            dto.setNewAvailableBalance(9_500_000.0);
            assertThat(dto.getAuction().getId()).isEqualTo("a-10");
            assertThat(dto.getDepositAmount()).isEqualTo(500_000.0);
            assertThat(dto.getNewAvailableBalance()).isEqualTo(9_500_000.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AuctionUpcomingEndDTO")
    class AuctionUpcomingEndDTOTest {

        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            AuctionDTOs.AuctionUpcomingEndDTO dto = new AuctionDTOs.AuctionUpcomingEndDTO();
            dto.setAuctionId("a-99");
            dto.setRemainingSeconds(300L);
            assertThat(dto.getAuctionId()).isEqualTo("a-99");
            assertThat(dto.getRemainingSeconds()).isEqualTo(300L);
        }

        @Test @DisplayName("remainingSeconds = 0 — boundary")
        void remainingSeconds_zero() {
            AuctionDTOs.AuctionUpcomingEndDTO dto = new AuctionDTOs.AuctionUpcomingEndDTO();
            dto.setRemainingSeconds(0L);
            assertThat(dto.getRemainingSeconds()).isZero();
        }
    }
}
