package com.group13.auction.unit.protocol;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit test cho {@link DTOMapper} — đảm bảo mapping đúng và null-safe.
 * Không DB, không network.
 */
@DisplayName("DTOMapper — domain model → DTO")
class DTOMapperTest {

    private NormalUser seller;
    private NormalUser bidder;
    private Auction runningAuction;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        seller        = TestFixture.normalSeller("dtoseller11");
        bidder        = TestFixture.bidderWithBalance("dtobidder11", 5_000_000L);
        runningAuction = TestFixture.runningAuction(seller, 1_000_000L);
    }

    @AfterEach
    void tearDown() throws Exception { TestFixture.resetSystemAdmin(); }

    // =========================================================================
    // toUserDTO — tất cả test cũ giữ nguyên
    // =========================================================================

    @Nested
    @DisplayName("toUserDTO")
    class ToUserDTO {

        @Test
        @DisplayName("NormalUser với showBalance=true → tất cả fields được map")
        void normalUser_showBalanceTrue_allFieldsMapped() {
            UserDTO dto = DTOMapper.toUserDTO(bidder, true);
            assertThat(dto.getId()).isEqualTo(bidder.getId());
            assertThat(dto.getUsername()).isEqualTo(bidder.getUsername());
            assertThat(dto.getAccountStatus()).isEqualTo("ACTIVE");
            assertThat(dto.getBalance()).isEqualTo(5_000_000L);
            assertThat(dto.getAvailableBalance()).isEqualTo(5_000_000L);
            assertThat(dto.getEmail()).isNotBlank();
        }

        @Test
        @DisplayName("NormalUser với showBalance=false → balance fields không được set")
        void normalUser_showBalanceFalse_noBalanceFields() {
            UserDTO dto = DTOMapper.toUserDTO(bidder, false);
            assertThat(dto.getId()).isEqualTo(bidder.getId());
            assertThat(dto.getBalance()).isZero();
            assertThat(dto.getLockedDeposit()).isZero();
        }

        @Test
        @DisplayName("BIDDER role được include trong roles list")
        void normalUser_bidderRole_inRoles() {
            assertThat(DTOMapper.toUserDTO(bidder, false).getRoles()).contains("BIDDER");
        }

        @Test
        @DisplayName("SELLER role được include khi user có cả 2 roles")
        void normalSeller_bothRoles_inRoles() {
            assertThat(DTOMapper.toUserDTO(seller, false).getRoles()).contains("BIDDER", "SELLER");
        }

        @Test
        @DisplayName("banned user → accountStatus = BANNED")
        void bannedUser_statusMapped() {
            NormalUser banned = TestFixture.bannedBidder("bannedusr1");
            assertThat(DTOMapper.toUserDTO(banned, false).getAccountStatus()).isEqualTo("BANNED");
        }

        @Test
        @DisplayName("Admin MASTER (reconstitute) → adminType MASTER")
        void adminMaster_adminTypeMapped() {
            Admin master = Admin.reconstitute(
                    "admin-master-1",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    "superadmin",
                    "hash",
                    "master@test.com",
                    User.AccountStatus.ACTIVE,
                    5.0,
                    Admin.LEVEL_MASTER,
                    null);
            assertThat(DTOMapper.toUserDTO(master, false).getAdminType()).isEqualTo(Admin.LEVEL_MASTER);
        }

        @Test
        @DisplayName("Admin STAFF (reconstitute) → adminType STAFF")
        void adminStaff_adminTypeMapped() {
            Admin staff = Admin.reconstitute(
                    "admin-staff-1",
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    "staff1",
                    "hash",
                    "staff@test.com",
                    User.AccountStatus.ACTIVE,
                    5.0,
                    Admin.LEVEL_STAFF,
                    null);
            assertThat(DTOMapper.toUserDTO(staff, false).getAdminType()).isEqualTo(Admin.LEVEL_STAFF);
        }
    }

    // =========================================================================
    // toAuctionDTO — tất cả test cũ giữ nguyên
    // =========================================================================

    @Nested
    @DisplayName("toAuctionDTO")
    class ToAuctionDTO {

        @Test
        @DisplayName("RUNNING auction → tất cả fields được map đúng")
        void runningAuction_allFieldsMapped() {
            AuctionDTOs.AuctionDTO dto = DTOMapper.toAuctionDTO(runningAuction);
            assertThat(dto.getId()).isEqualTo(runningAuction.getId());
            assertThat(dto.getStatus()).isEqualTo("RUNNING");
            assertThat(dto.getItem()).isNotNull();
        }

        @Test
        @DisplayName("auction với leader → leaderId và leaderUsername được set")
        void auctionWithLeader_leaderFieldsMapped() {
            runningAuction.updateBid(1_500_000L, bidder);
            AuctionDTOs.AuctionDTO dto = DTOMapper.toAuctionDTO(runningAuction);
            assertThat(dto.getCurrentLeaderId()).isEqualTo(bidder.getId());
            assertThat(dto.getCurrentLeaderUsername()).isEqualTo(bidder.getUsername());
        }

        @Test
        @DisplayName("auction không có leader → leaderId và leaderUsername null")
        void auctionWithoutLeader_leaderFieldsNull() {
            AuctionDTOs.AuctionDTO dto = DTOMapper.toAuctionDTO(runningAuction);
            assertThat(dto.getCurrentLeaderId()).isNull();
            assertThat(dto.getCurrentLeaderUsername()).isNull();
        }

        @Test
        @DisplayName("reserveMet = true khi currentPrice >= reservePrice")
        void reserveMet_mappedCorrectly() {
            runningAuction.updateBid(runningAuction.getReservePrice() + 100_000L, bidder);
            assertThat(DTOMapper.toAuctionDTO(runningAuction).isReserveMet()).isTrue();
        }
    }

    // =========================================================================
    // toItemDTO — test cũ giữ nguyên + test mới imageUrls
    // =========================================================================

    @Nested
    @DisplayName("toItemDTO")
    class ToItemDTO {

        @Test
        @DisplayName("item với seller → sellerId và sellerUsername được map")
        void item_withSeller_sellerFieldsMapped() {
            Item item = runningAuction.getItem();
            AuctionDTOs.ItemDTO dto = DTOMapper.toItemDTO(item);
            assertThat(dto.getId()).isEqualTo(item.getId());
            assertThat(dto.getName()).isEqualTo(item.getName());
            assertThat(dto.getStartingPrice()).isEqualTo(item.getStartingPrice());
            assertThat(dto.getSellerId()).isEqualTo(seller.getId());
            assertThat(dto.getSellerUsername()).isEqualTo(seller.getUsername());
        }

        @Test
        @DisplayName("item không có ảnh → imageUrls trong DTO là list rỗng, không null")
        void item_noImages_dtoImageUrlsEmptyNotNull() {
            Item item = runningAuction.getItem();
            AuctionDTOs.ItemDTO dto = DTOMapper.toItemDTO(item);
            assertThat(dto.getImageUrls()).isNotNull().isEmpty();
            assertThat(dto.hasImages()).isFalse();
        }

        @Test
        @DisplayName("item có ảnh → imageUrls trong DTO khớp với item.getImageUrls()")
        void item_withImages_dtoImageUrlsMapped() {
            NormalUser s = TestFixture.normalSeller("dtoImgSeller1");
            List<String> imgs = List.of("/uploads/items/x.jpg", "/uploads/items/y.png");
            Art artWithImages = Art.reconstitute(
                "art-img-1", LocalDateTime.now(), LocalDateTime.now(),
                "Tranh có ảnh", "desc", 1_000_000L, s,
                "Nghệ sĩ", 2020, "Sơn dầu", imgs);

            AuctionDTOs.ItemDTO dto = DTOMapper.toItemDTO(artWithImages);

            assertThat(dto.getImageUrls())
                .hasSize(2)
                .containsExactlyElementsOf(imgs);
            assertThat(dto.hasImages()).isTrue();
        }

        @Test
        @DisplayName("item có ảnh → toAuctionDTO cũng truyền imageUrls xuống")
        void toAuctionDTO_propagatesImageUrls() {
            NormalUser s = TestFixture.normalSeller("dtoImgSeller2");
            List<String> imgs = List.of("/uploads/items/main.jpg");
            Art artWithImages = Art.reconstitute(
                "art-img-2", LocalDateTime.now(), LocalDateTime.now(),
                "Tranh test", "desc", 500_000L, s,
                "Nghệ sĩ", 2021, "Màu nước", imgs);

            Auction auction = Auction.create(artWithImages,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                1_000_000L);
            auction.transitionToRunning();

            AuctionDTOs.AuctionDTO auctionDto = DTOMapper.toAuctionDTO(auction);

            assertThat(auctionDto.getItem().getImageUrls())
                .containsExactly("/uploads/items/main.jpg");
        }
    }

    // =========================================================================
    // toAuctionUpdateDTO — tất cả test cũ giữ nguyên
    // =========================================================================

    @Nested
    @DisplayName("toAuctionUpdateDTO")
    class ToAuctionUpdateDTO {

        @Test
        @DisplayName("FINISHED auction với winner → winner fields được map")
        void finishedAuction_withWinner_winnerFieldsMapped() {
            Auction finished = TestFixture.finishedAuction(seller, bidder, 1_000_000L, 2_000_000L);
            AuctionWinner aw = AuctionWinner.create(bidder, finished.getId(), 2_000_000L, 300_000L, false);
            finished.setWinner(aw);

            AuctionDTOs.AuctionUpdateDTO dto = DTOMapper.toAuctionUpdateDTO(finished, null);

            assertThat(dto.getAuctionId()).isEqualTo(finished.getId());
            assertThat(dto.getNewStatus()).isEqualTo("FINISHED");
            assertThat(dto.getFinalPrice()).isEqualTo(2_000_000L);
            assertThat(dto.getWinnerId()).isEqualTo(bidder.getId());
        }

        @Test
        @DisplayName("CANCELED auction với cancelReason → reason được map")
        void canceledAuction_reasonMapped() {
            Auction canceled = TestFixture.canceledFromRunningAuction(seller, 1_000_000L);
            AuctionDTOs.AuctionUpdateDTO dto = DTOMapper.toAuctionUpdateDTO(canceled, "NO_WINNER");
            assertThat(dto.getCancelReason()).isEqualTo("NO_WINNER");
        }

        @Test
        @DisplayName("auction không có winner → winner fields null (không NPE)")
        void auctionNoWinner_nullSafe() {
            assertThatNoException().isThrownBy(() -> {
                AuctionDTOs.AuctionUpdateDTO dto =
                    DTOMapper.toAuctionUpdateDTO(runningAuction, null);
                assertThat(dto.getWinnerId()).isNull();
                assertThat(dto.getFinalPrice()).isZero();
            });
        }
    }

    // =========================================================================
    // toBidUpdateDTO — tất cả test cũ giữ nguyên
    // =========================================================================

    @Nested
    @DisplayName("toBidUpdateDTO")
    class ToBidUpdateDTO {

        @Test
        @DisplayName("bid update với leader → tất cả fields được map đúng")
        void bidUpdate_withLeader_allFieldsMapped() {
            runningAuction.updateBid(1_800_000L, bidder);
            BidDTOs.BidUpdateDTO dto = DTOMapper.toBidUpdateDTO(runningAuction, 1_800_000L, 0L);
            assertThat(dto.getAuctionId()).isEqualTo(runningAuction.getId());
            assertThat(dto.getNewCurrentPrice()).isEqualTo(1_800_000L);
            assertThat(dto.getLeaderId()).isEqualTo(bidder.getId());
            assertThat(dto.getTimestamp()).isNotNull();
        }
    }

    // =========================================================================
    // toBidChartPoint — tất cả test cũ giữ nguyên
    // =========================================================================

    @Nested
    @DisplayName("toBidChartPoint")
    class ToBidChartPoint {

        @Test
        @DisplayName("chart point → fields được map đúng")
        void chartPoint_allFieldsMapped() {
            BidDTOs.BidChartPointDTO dto = DTOMapper.toBidChartPoint(
                "auc-test-1", 2_500_000L, "alice_user", false);
            assertThat(dto.getAuctionId()).isEqualTo("auc-test-1");
            assertThat(dto.getPrice()).isEqualTo(2_500_000L);
            assertThat(dto.getBidderUsername()).isEqualTo("alice_user");
            assertThat(dto.isAutoBid()).isFalse();
            assertThat(dto.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("auto-bid chart point → isAutoBid = true")
        void chartPoint_autoBid_flagTrue() {
            BidDTOs.BidChartPointDTO dto = DTOMapper.toBidChartPoint(
                "auc-test-2", 3_000_000L, "bot_user", true);
            assertThat(dto.isAutoBid()).isTrue();
        }
    }
}