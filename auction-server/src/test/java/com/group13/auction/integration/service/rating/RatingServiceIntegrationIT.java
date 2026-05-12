package com.group13.auction.integration.service.rating;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.integration.base.IntegrationTestBase;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.RatingService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ════════════════════════════════════════════════════════════════════
 *  RatingServiceIntegrationIT — Integration Tests cho RatingService
 *  Kỹ thuật: Bottom-up (RatingService + UserDAO + DB thật)
 * ════════════════════════════════════════════════════════════════════
 *
 *  TC-08 [HIGH]: Rating penalty chain
 *    BUG RISK: penalizeLatePayment() phải trừ rating, set penalized flag,
 *    auto-suspend nếu rating ≤ 1.5, persist tất cả xuống DB — atomic.
 *    Nếu persist một phần (rating OK nhưng flag quên) → user có thể
 *    tiếp tục đặt giá dù đã vi phạm.
 *
 *  TC-09 [HIGH]: checkAndRestoreSuspended() — chỉ 1 lần, đúng threshold
 *    BUG RISK: restore nhiều lần → user phục hồi rating vô hạn.
 *    Restore khi chưa đủ 3 tháng → bypass suspension bằng trick time.
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("RatingServiceIntegrationIT — RatingService × UserDAO × DB (Bottom-up)")
class RatingServiceIntegrationIT extends IntegrationTestBase {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql");

    private UserDAO       userDAO;
    private RatingService ratingService;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() {
        userDAO       = new UserDAO();
        ratingService = new RatingService(userDAO);
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
    }

    // =========================================================================
    // TC-08 — penalizeLatePayment() chain
    // =========================================================================

    @Nested
    @Order(1)
    @DisplayName("TC-08 [HIGH] penalizeLatePayment() — Rating trừ, flag set, persist DB")
    class PenalizeLatePaymentTests {

        @Test
        @Order(1)
        @DisplayName("TC-08a: penalizeLatePayment() — rating giảm 1.0, hasEverBeenPenalized = true, DB nhất quán")
        void penalizeLatePayment_reducesRatingAndSetsFlag_dbConsistent() {
            NormalUser bidder = givenUser("pen_b1", 3.0);

            ratingService.penalizeLatePayment(bidder);

            assertAll("RAM sau penalize",
                () -> assertThat(bidder.getRating())
                        .as("rating phải giảm đúng 1.0")
                        .isEqualTo(2.0),
                () -> assertThat(bidder.isHasEverBeenPenalized())
                        .as("hasEverBeenPenalized phải = true")
                        .isTrue()
            );

            NormalUser fromDB = userDAO.findNormalUserById(bidder.getId());
            assertAll("DB khớp RAM",
                () -> assertThat(fromDB.getRating()).isEqualTo(2.0),
                () -> assertThat(fromDB.isHasEverBeenPenalized()).isTrue()
            );
        }

        @Test
        @Order(2)
        @DisplayName("TC-08b: penalizeLatePayment() khi rating ≤ 1.5 → auto-suspend, DB status = SUSPENDED")
        void penalizeLatePayment_belowSuspendThreshold_autoSuspends() {
            // rating = 2.0, sau penalize = 1.0 → <= 1.5 → SUSPENDED
            NormalUser bidder = givenUser("pen_b2", 2.0);

            ratingService.penalizeLatePayment(bidder);

            assertAll("Auto-suspend sau penalize",
                () -> assertThat(bidder.getRating())
                        .as("rating phải = 1.0")
                        .isEqualTo(1.0),
                () -> assertThat(bidder.getAccountStatus())
                        .as("Status phải = SUSPENDED sau khi rating ≤ 1.5")
                        .isEqualTo(User.AccountStatus.SUSPENDED)
            );

            NormalUser fromDB = userDAO.findNormalUserById(bidder.getId());
            assertThat(fromDB.getAccountStatus())
                    .as("DB phải phản ánh SUSPENDED")
                    .isEqualTo(User.AccountStatus.SUSPENDED);
        }

        @Test
        @Order(3)
        @DisplayName("TC-08c: rewardBidder() sau penalize — rating tăng, DB cập nhật")
        void rewardBidder_afterPenalize_ratingIncreasesAndDbUpdated() {
            NormalUser bidder = givenUser("pen_b3", 3.0);
            ratingService.penalizeLatePayment(bidder); // rating = 2.0

            ratingService.rewardBidder(bidder); // +0.2 → 2.2

            assertThat(bidder.getRating()).isEqualTo(2.2, within(0.001));

            NormalUser fromDB = userDAO.findNormalUserById(bidder.getId());
            assertThat(fromDB.getRating()).isEqualTo(2.2, within(0.001));
        }

        @Test
        @Order(4)
        @DisplayName("TC-08d: isEligible() sau penalize đủ → false (rating < 2.0 sau 2 lần phạt từ 3.0)")
        void isEligible_afterTwoPenalties_returnsFalse() {
            NormalUser bidder = givenUser("pen_b4", 3.0);
            ratingService.penalizeLatePayment(bidder); // 2.0
            ratingService.penalizeLatePayment(bidder); // 1.0 → SUSPENDED

            assertThat(ratingService.isEligible(bidder))
                    .as("User bị suspend phải không eligible")
                    .isFalse();
        }
    }

    // =========================================================================
    // TC-09 — checkAndRestoreSuspended()
    // =========================================================================

    @Nested
    @Order(2)
    @DisplayName("TC-09 [HIGH] checkAndRestoreSuspended() — 1 lần duy nhất, đúng threshold")
    class RestoreSuspendedTests {

        @Test
        @Order(1)
        @DisplayName("TC-09a: Sau đúng 3 tháng + 1 ngày → rating tăng, ACTIVE, hasEverBeenRestored = true")
        void restore_afterThreeMonths_ratingIncreasedAndStatusActive() throws Exception {
            // Tạo user bị suspend (rating = 1.0)
            NormalUser bidder = givenUser("restore_b1", 2.0);
            ratingService.penalizeLatePayment(bidder); // → 1.0, SUSPENDED

            // Simulate: suspendedAt = 3 tháng + 1 ngày trước (dùng reflection vì field private)
            LocalDateTime suspendTime = LocalDateTime.now().minusMonths(3).minusDays(1);
            bidder.setAccountStatus(User.AccountStatus.SUSPENDED);
            setSuspendedAt(bidder, suspendTime);

            ratingService.checkAndRestoreSuspended(bidder,
                    LocalDateTime.now()); // currentTime = now > threshold

            assertAll("Sau restore",
                () -> assertThat(bidder.getRating())
                        .as("rating phải tăng thêm 0.6")
                        .isGreaterThan(1.0),
                () -> assertThat(bidder.isHasEverBeenRestored())
                        .as("hasEverBeenRestored phải = true")
                        .isTrue()
            );

            NormalUser fromDB = userDAO.findNormalUserById(bidder.getId());
            assertThat(fromDB.isHasEverBeenRestored())
                    .as("DB phải persist hasEverBeenRestored = true")
                    .isTrue();
        }

        @Test
        @Order(2)
        @DisplayName("TC-09b: Restore 2 lần — lần 2 bị chặn (guard hasEverBeenRestored)")
        void restore_calledTwice_secondCallIsNoOp() throws Exception {
            NormalUser bidder = givenUser("restore_b2", 2.0);
            ratingService.penalizeLatePayment(bidder); // SUSPENDED

            LocalDateTime suspendTime = LocalDateTime.now().minusMonths(4);
            setSuspendedAt(bidder, suspendTime);

            // Lần 1 — restore thành công
            ratingService.checkAndRestoreSuspended(bidder, LocalDateTime.now());
            double ratingAfterFirst = bidder.getRating();

            // Lần 2 — phải bị bỏ qua
            ratingService.checkAndRestoreSuspended(bidder, LocalDateTime.now());

            assertThat(bidder.getRating())
                    .as("Rating không được tăng lần 2")
                    .isEqualTo(ratingAfterFirst);
        }

        @Test
        @Order(3)
        @DisplayName("TC-09c: Restore khi chưa đủ 3 tháng — không có side-effect")
        void restore_beforeThreeMonths_noSideEffect() throws Exception {
            NormalUser bidder = givenUser("restore_b3", 2.0);
            ratingService.penalizeLatePayment(bidder); // SUSPENDED, rating = 1.0

            double ratingBefore = bidder.getRating();
            // suspendedAt = 1 tháng trước — chưa đủ
            setSuspendedAt(bidder, LocalDateTime.now().minusMonths(1));

            ratingService.checkAndRestoreSuspended(bidder, LocalDateTime.now());

            assertAll("Không restore khi chưa đủ thời gian",
                () -> assertThat(bidder.getRating()).isEqualTo(ratingBefore),
                () -> assertThat(bidder.getAccountStatus())
                        .isEqualTo(User.AccountStatus.SUSPENDED),
                () -> assertThat(bidder.isHasEverBeenRestored()).isFalse()
            );
        }

        @Test
        @Order(4)
        @DisplayName("TC-09d: User ACTIVE (không bị suspend) — checkAndRestore không làm gì")
        void restore_activeUser_noOp() {
            NormalUser bidder = givenUser("restore_b4", 3.5);
            double ratingBefore = bidder.getRating();

            ratingService.checkAndRestoreSuspended(bidder, LocalDateTime.now());

            assertThat(bidder.getRating()).isEqualTo(ratingBefore);
            assertThat(bidder.isHasEverBeenRestored()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC-10 [HIGH] — penalizeSeller() với DB persist
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @Order(3)
    @DisplayName("TC-10 [HIGH] penalizeSeller() — Rating trừ, DB persist")
    class PenalizeSellerTests {

        @Test
        @Order(1)
        @DisplayName("TC-10a: penalizeSeller() trừ rating 1.0, DB cập nhật đúng")
        void penalizeSeller_reducesRating_dbUpdated() {
            NormalUser seller = givenUser("pen_seller1", 4.0);

            ratingService.penalizeSeller(seller);

            assertThat(seller.getRating()).isEqualTo(3.0, within(0.001));

            NormalUser fromDB = userDAO.findNormalUserById(seller.getId());
            assertThat(fromDB.getRating()).isEqualTo(3.0, within(0.001));
        }

        @Test
        @Order(2)
        @DisplayName("TC-10b: rewardSeller() cộng rating 0.2 và DB cập nhật")
        void rewardSeller_increasesRating_dbUpdated() {
            NormalUser seller = givenUser("pen_seller2", 3.5);

            ratingService.rewardSeller(seller);

            assertThat(seller.getRating()).isEqualTo(3.7, within(0.001));

            NormalUser fromDB = userDAO.findNormalUserById(seller.getId());
            assertThat(fromDB.getRating()).isEqualTo(3.7, within(0.001));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Tạo NormalUser với rating cụ thể bằng cách override sau khi tạo.
     */
    private NormalUser givenUser(String username, double rating) {
        NormalUser user = buildUserWithBalance(username, 10_000_000L, userDAO);
        // Override rating qua adjustRating (vì constructor mặc định = 3.0)
        double delta = rating - user.getRating();
        if (Math.abs(delta) > 0.001) {
            user.adjustRating(delta);
            userDAO.updateRating(user.getId(), user.getRating());
        }
        return user;
    }

    /** Dùng reflection để set suspendedAt (field private, không có public setter). */
    private static void setSuspendedAt(User user, LocalDateTime time) throws Exception {
        java.lang.reflect.Field f = User.class.getDeclaredField("suspendedAt");
        f.setAccessible(true);
        f.set(user, time);
    }
}
