package com.group13.auction.integration.user;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.integration.base.IntegrationTestBase;

import com.group13.auction.dao.*;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;

import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ════════════════════════════════════════════════════════════════════
 *  UserRegistrationIntegrationIT — Integration Tests cho User Registration
 *  Kỹ thuật: Bottom-up (UserDAO + DB thật)
 * ════════════════════════════════════════════════════════════════════
 *
 *  TC-26 [HIGH]: registerUser() — unique constraint username/email
 *    BUG RISK: registerUser() không validate trùng email, chỉ validate username.
 *    Hai account cùng email → security issue.
 *
 *  TC-27 [HIGH]: deposit() + withdraw() end-to-end — balance floor = 0
 *    BUG RISK: withdraw() không check balance ≥ 0 → balance âm.
 *    Hoặc deposit() không đồng bộ với locked_balance → sai available.
 *
 *  TC-28 [MEDIUM]: findNormalUserById() và findUserByUsername() — reconstitute đúng
 *    BUG RISK: reconstitute() không inject joinedAuctionIds → hasJoined() sai.
 *    lockedDeposit không được đọc đúng từ locked_balance → availableBalance sai.
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("UserRegistrationIntegrationIT — User Registration & Reconstitution")
class UserRegistrationIntegrationIT extends IntegrationTestBase {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql");

    private UserDAO              userDAO;
    private FinancialTransactionDAO financialTransactionDAO;
    private RatingService ratingService;
    private WalletService walletService;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() {
        userDAO                 = new UserDAO();
        financialTransactionDAO = new FinancialTransactionDAO();
        ratingService           = new com.group13.auction.service.RatingService(userDAO);
        walletService           = new com.group13.auction.service.WalletService(
                financialTransactionDAO, userDAO, ratingService);
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
    }

    // =========================================================================
    // TC-26 — Unique constraints
    // =========================================================================

    @Nested
    @Order(1)
    @DisplayName("TC-26 [HIGH] registerUser() — Unique constraints enforcement")
    class RegisterUserConstraintTests {

        @Test
        @Order(1)
        @DisplayName("TC-26a: registerUser() thành công — userId là UUID hợp lệ, existsByUsername = true")
        void registerUser_success_returnsValidUuidAndExistsInDb() {
            String userId = userDAO.registerUser("reg_user1",
                    User.hashPassword("pass123"), "reg1@test.vn");
            trackUser(userId);

            assertThat(userId).isNotNull();
            assertDoesNotThrow(() -> UUID.fromString(userId));
            assertThat(userDAO.existsByUsername("reg_user1")).isTrue();
        }

        @Test
        @Order(2)
        @DisplayName("TC-26b: Duplicate username → registerUser() trả về null (unique constraint)")
        void registerUser_duplicateUsername_returnsNull() {
            String userId1 = userDAO.registerUser("dup_user",
                    User.hashPassword("pass"), "dup1@test.vn");
            trackUser(userId1);

            String userId2 = userDAO.registerUser("dup_user",
                    User.hashPassword("pass2"), "dup2@test.vn");

            assertThat(userId2).isNull();
        }

        @Test
        @Order(3)
        @DisplayName("TC-26c: Duplicate email → registerUser() trả về null (unique email constraint)")
        void registerUser_duplicateEmail_returnsNull() {
            String userId1 = userDAO.registerUser("email_user1",
                    User.hashPassword("pass"), "shared@test.vn");
            trackUser(userId1);

            String userId2 = userDAO.registerUser("email_user2",
                    User.hashPassword("pass"), "shared@test.vn");

            assertThat(userId2).isNull();
        }

        @Test
        @Order(4)
        @DisplayName("TC-26d: existsByEmail() trả về true sau khi đăng ký")
        void existsByEmail_afterRegister_returnsTrue() {
            String userId = userDAO.registerUser("email_check1",
                    User.hashPassword("pass"), "unique_email@test.vn");
            trackUser(userId);

            assertThat(userDAO.existsByEmail("unique_email@test.vn")).isTrue();
            assertThat(userDAO.existsByEmail("nonexistent@test.vn")).isFalse();
        }
    }

    // =========================================================================
    // TC-27 — deposit() + withdraw() end-to-end
    // =========================================================================

    @Nested
    @Order(2)
    @DisplayName("TC-27 [HIGH] deposit()/withdraw() — Balance floor + DB consistency")
    class DepositWithdrawTests {

        @Test
        @Order(1)
        @DisplayName("TC-27a: deposit() nhiều lần — balance tích lũy đúng trong DB")
        void deposit_multipleTimes_balanceAccumulates() {
            NormalUser user = givenUser("dw_user1");

            walletService.deposit(user, 5_000_000L);
            walletService.deposit(user, 3_000_000L);
            walletService.deposit(user, 2_000_000L);

            NormalUser fromDB = userDAO.findNormalUserById(user.getId());
            assertThat(fromDB.getBalance())
                    .as("DB balance = sum(deposits)")
                    .isEqualTo(10_000_000L);
        }

        @Test
        @Order(2)
        @DisplayName("TC-27b: withdraw() vượt available → IllegalArgumentException, balance không đổi")
        void withdraw_exceedsAvailableBalance_throwsException_balanceUnchanged() {
            NormalUser user = givenUser("dw_user2");
            walletService.deposit(user, 5_000_000L);
            // Lock 2M deposit để available = 3M
            String auctionId = UUID.randomUUID().toString();
            walletService.lockDeposit(user, 2_000_000L, auctionId);

            long balanceBefore = user.getBalance();

            assertThatThrownBy(() -> walletService.withdraw(user, 4_000_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Số dư khả dụng không đủ");

            assertThat(user.getBalance()).isEqualTo(balanceBefore);
        }

        @Test
        @Order(3)
        @DisplayName("TC-27c: withdraw(0) → IllegalArgumentException (amount <= 0 guard)")
        void withdraw_zeroAmount_throwsIllegalArgument() {
            NormalUser user = givenUser("dw_user3");
            walletService.deposit(user, 5_000_000L);

            assertThatThrownBy(() -> walletService.withdraw(user, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Order(4)
        @DisplayName("TC-27d: deposit(-1) → IllegalArgumentException (negative amount)")
        void deposit_negativeAmount_throwsIllegalArgument() {
            NormalUser user = givenUser("dw_user4");

            assertThatThrownBy(() -> walletService.deposit(user, -1_000_000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // TC-28 — reconstitute từ DB
    // =========================================================================

    @Nested
    @Order(3)
    @DisplayName("TC-28 [MEDIUM] findNormalUserById() — reconstitute đúng tất cả fields")
    class ReconstituteTests {

        @Test
        @Order(1)
        @DisplayName("TC-28a: findNormalUserById() — balance, lockedDeposit, rating, status đúng")
        void findNormalUserById_reconstitutesAllFieldsCorrectly() {
            NormalUser user = givenUser("reconstitute1");
            walletService.deposit(user, 8_000_000L);
            String auctionId = UUID.randomUUID().toString();
            walletService.lockDeposit(user, 2_000_000L, auctionId);

            NormalUser fromDB = userDAO.findNormalUserById(user.getId());

            assertAll("Reconstitute đúng tất cả fields",
                    () -> assertThat(fromDB.getBalance())
                            .as("balance phải đúng")
                            .isEqualTo(user.getBalance()),
                    () -> assertThat(fromDB.getLockedDeposit())
                            .as("lockedDeposit phải đúng từ locked_balance")
                            .isEqualTo(2_000_000L),
                    () -> assertThat(fromDB.getAvailableBalance())
                            .as("availableBalance = balance - lockedDeposit")
                            .isEqualTo(fromDB.getBalance() - 2_000_000L),
                    () -> assertThat(fromDB.getAccountStatus())
                            .isEqualTo(User.AccountStatus.ACTIVE),
                    () -> assertThat(fromDB.getRating())
                            .isGreaterThan(0.0)
            );
        }

        @Test
        @Order(2)
        @DisplayName("TC-28b: findUserByUsername() — trả về đúng user với đúng username")
        void findUserByUsername_returnsCorrectUser() {
            NormalUser user = givenUser("findByUsername1");

            NormalUser fromDB = userDAO.findUserByUsername(user.getUsername());

            assertThat(fromDB).isNotNull();
            assertThat(fromDB.getId()).isEqualTo(user.getId());
            assertThat(fromDB.getUsername()).isEqualTo(user.getUsername());
        }

        @Test
        @Order(3)
        @DisplayName("TC-28c: findNormalUserById() với ID không tồn tại → null")
        void findNormalUserById_nonExistent_returnsNull() {
            assertThat(userDAO.findNormalUserById(UUID.randomUUID().toString())).isNull();
        }

        @Test
        @Order(4)
        @DisplayName("TC-28d: joinedAuctionIds được inject đúng khi findNormalUserById")
        void findNormalUserById_joinedAuctionIdsInjected() {
            NormalUser user = givenUser("reconstitute2");
            String auctionId = UUID.randomUUID().toString();
            userDAO.saveUserAuctionActivity(user.getId(), auctionId, "JOINED");

            NormalUser fromDB = userDAO.findNormalUserById(user.getId());

            assertThat(fromDB.getJoinedAuctionIds())
                    .as("joinedAuctionIds phải được inject từ user_auction_activity")
                    .contains(auctionId);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NormalUser givenUser(String username) {
        return buildUserWithBalance(username, 0L, userDAO);
    }
}