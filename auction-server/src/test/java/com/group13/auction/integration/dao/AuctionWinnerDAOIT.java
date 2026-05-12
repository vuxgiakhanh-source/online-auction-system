package com.group13.auction.integration.dao;

import com.group13.auction.dao.*;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AuctionWinnerDAOIT — Bottom-up test cho AuctionWinnerDAO.
 *
 * API AuctionWinnerDAO đã xác nhận:
 *   saveWinner(AuctionWinner) → boolean
 *   updatePaymentStatus(String winnerId, String status) → boolean
 *   hasPendingPayment(String userId) → boolean
 *
 * API AuctionWinner đã xác nhận:
 *   AuctionWinner.create(NormalUser winner, String auctionId,
 *                        long finalPrice, long depositPaid, boolean isSecondOffer)
 *   getWinner(), getAuctionId(), getFinalPrice(), getDepositPaid()
 *   getPaymentStatus(), getId()
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("AuctionWinnerDAOIT — AuctionWinnerDAO × DB (Bottom-up)")
class AuctionWinnerDAOIT extends IntegrationTestBase {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql");

    private UserDAO          userDAO;
    private ItemDAO          itemDAO;
    private AuctionDAO       auctionDAO;
    private AuctionWinnerDAO auctionWinnerDAO;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() {
        userDAO          = new UserDAO();
        itemDAO          = new ItemDAO();
        auctionDAO       = new AuctionDAO();
        auctionWinnerDAO = new AuctionWinnerDAO();
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
    }

    // =========================================================================
    // TC-AW-01 — saveWinner()
    // =========================================================================

    @Nested
    @Order(1)
    @DisplayName("TC-AW-01 [CRITICAL] saveWinner() — persist vào DB")
    class SaveWinnerTests {

        @Test
        @Order(1)
        @DisplayName("TC-AW-01a: saveWinner() hợp lệ → true, hasPendingPayment = true")
        void saveWinner_valid_persistsAndHasPending() {
            Context ctx = givenContext("aw01a_s", "aw01a_b");

            AuctionWinner winner = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, false);

            boolean saved = auctionWinnerDAO.saveWinner(winner);
            trackWinner(winner.getId());

            assertThat(saved).isTrue();
            assertThat(auctionWinnerDAO.hasPendingPayment(ctx.winner.getId())).isTrue();
        }

        @Test
        @Order(2)
        @DisplayName("TC-AW-01b: saveWinner() isSecondOffer=true → true")
        void saveWinner_secondOffer_returnsTrue() {
            Context ctx = givenContext("aw01b_s", "aw01b_b");

            AuctionWinner winner = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, true);

            boolean saved = auctionWinnerDAO.saveWinner(winner);
            trackWinner(winner.getId());

            assertThat(saved).isTrue();
        }

        @Test
        @Order(3)
        @DisplayName("TC-AW-01c: saveWinner() lần 2 cùng auctionId → false (UNIQUE constraint)")
        void saveWinner_duplicateAuction_returnsFalse() {
            Context ctx = givenContext("aw01c_s", "aw01c_b");

            AuctionWinner w1 = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, false);
            auctionWinnerDAO.saveWinner(w1);
            trackWinner(w1.getId());

            AuctionWinner w2 = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, false);
            boolean saved = auctionWinnerDAO.saveWinner(w2);

            assertThat(saved).isFalse();
        }
    }

    // =========================================================================
    // TC-AW-02 — updatePaymentStatus()
    // =========================================================================

    @Nested
    @Order(2)
    @DisplayName("TC-AW-02 [CRITICAL] updatePaymentStatus() — cập nhật đúng")
    class UpdatePaymentStatusTests {

        @Test
        @Order(1)
        @DisplayName("TC-AW-02a: updatePaymentStatus(FUNDS_HELD) → true, hasPendingPayment = false")
        void updatePaymentStatus_fundsHeld_noLongerPending() {
            Context ctx = givenContext("aw02a_s", "aw02a_b");
            AuctionWinner winner = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, false);
            auctionWinnerDAO.saveWinner(winner);
            trackWinner(winner.getId());

            boolean updated = auctionWinnerDAO.updatePaymentStatus(
                    winner.getId(), PaymentStatus.FUNDS_HELD.name());

            assertThat(updated).isTrue();
            assertThat(auctionWinnerDAO.hasPendingPayment(ctx.winner.getId())).isFalse();
        }

        @Test
        @Order(2)
        @DisplayName("TC-AW-02b: updatePaymentStatus(EXPIRED) → hasPendingPayment = false")
        void updatePaymentStatus_expired_noLongerPending() {
            Context ctx = givenContext("aw02b_s", "aw02b_b");
            AuctionWinner winner = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, false);
            auctionWinnerDAO.saveWinner(winner);
            trackWinner(winner.getId());

            auctionWinnerDAO.updatePaymentStatus(winner.getId(), PaymentStatus.EXPIRED.name());

            assertThat(auctionWinnerDAO.hasPendingPayment(ctx.winner.getId())).isFalse();
        }

        @Test
        @Order(3)
        @DisplayName("TC-AW-02c: updatePaymentStatus() ID không tồn tại → false")
        void updatePaymentStatus_nonExistentId_returnsFalse() {
            boolean updated = auctionWinnerDAO.updatePaymentStatus(
                    UUID.randomUUID().toString(), PaymentStatus.FUNDS_HELD.name());

            assertThat(updated).isFalse();
        }
    }

    // =========================================================================
    // TC-AW-03 — hasPendingPayment()
    // =========================================================================

    @Nested
    @Order(3)
    @DisplayName("TC-AW-03 [HIGH] hasPendingPayment() — kiểm tra trạng thái")
    class HasPendingPaymentTests {

        @Test
        @Order(1)
        @DisplayName("TC-AW-03a: User không có record nào → false")
        void hasPendingPayment_noRecord_returnsFalse() {
            NormalUser user = buildUserWithBalance("aw03a_u", 0L, userDAO);

            assertThat(auctionWinnerDAO.hasPendingPayment(user.getId())).isFalse();
        }

        @Test
        @Order(2)
        @DisplayName("TC-AW-03b: PENDING → true; sau COMPLETED → false")
        void hasPendingPayment_pendingThenCompleted() {
            Context ctx = givenContext("aw03b_s", "aw03b_b");
            AuctionWinner winner = AuctionWinner.create(
                    ctx.winner, ctx.auction.getId(), ctx.finalPrice, ctx.deposit, false);
            auctionWinnerDAO.saveWinner(winner);
            trackWinner(winner.getId());

            assertThat(auctionWinnerDAO.hasPendingPayment(ctx.winner.getId()))
                    .as("trước khi update: PENDING").isTrue();

            auctionWinnerDAO.updatePaymentStatus(winner.getId(), "COMPLETED");

            assertThat(auctionWinnerDAO.hasPendingPayment(ctx.winner.getId()))
                    .as("sau COMPLETED").isFalse();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    record Context(NormalUser winner, Auction auction, long finalPrice, long deposit) {}

    private Context givenContext(String sellerUsername, String winnerUsername) {
        NormalUser seller = buildUserWithBalance(sellerUsername, 10_000_000L, userDAO);
        NormalUser winner = buildUserWithBalance(winnerUsername, 20_000_000L, userDAO);

        String itemId = buildItem(seller.getId(), "Item_" + sellerUsername, 2_000_000L, itemDAO);
        var item = itemDAO.findItemById(itemId);

        Auction auction = Auction.create(item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                3_000_000L);
        auctionDAO.createAuction(auction);
        trackAuction(auction.getId());

        return new Context(winner, auction, 4_000_000L, 600_000L);
    }
}
