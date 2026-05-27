package com.group13.auction.integration.user;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.dao.*;
import com.group13.auction.dao.DatabaseConnection;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * ════════════════════════════════════════════════════════════════════
 * UserRegistrationIntegrationIT — Integration Tests cho User Registration Kỹ thuật: Bottom-up
 * (UserDAO + DB thật) ════════════════════════════════════════════════════════════════════
 *
 * <p>TC-26 [HIGH]: registerUser() — unique constraint username/email TC-27 [HIGH]: deposit() +
 * withdraw() end-to-end — balance floor = 0 TC-28 [MEDIUM]: findNormalUserById() và
 * findUserByUsername() — reconstitute đúng TC-28e [HIGH] FIX BUG #1: SELLER role được load từ DB
 * khi có approved record TC-28f [HIGH] FIX BUG #1: findUserByUsername() cũng load SELLER role
 * TC-28g [HIGH] FIX BUG #1: findUserCoreByUsername() cũng load SELLER role TC-28h: user không có
 * seller record → chỉ có BIDDER role
 *
 * <p>TC-29 [HIGH] FIX BUG #2: SellerDAO.approveSellerRole() — UPSERT đảm bảo persist TC-29a:
 * approveSellerRole() khi user chưa có sellers record → INSERT thành công TC-29b:
 * approveSellerRole() khi user đã có PENDING → UPDATE → APPROVED TC-29c: approveSellerRole() lặp
 * lại → idempotent, không throw exception
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("UserRegistrationIntegrationIT — User Registration & Reconstitution")
class UserRegistrationIntegrationIT extends IntegrationTestBase {

  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.0")
          .withDatabaseName("omnibid_test")
          .withUsername("test_user")
          .withPassword("test_pass")
          .withInitScript("database/schema.sql");

  private UserDAO userDAO;
  private SellerDAO sellerDAO;
  private ItemDAO itemDAO;
  private AuctionDAO auctionDAO;
  private FinancialTransactionDAO financialTransactionDAO;
  private RatingService ratingService;
  private WalletService walletService;

  @BeforeAll
  static void configureDataSource() throws Exception {
    configureTestcontainer(mysql);
  }

  @BeforeEach
  void setUp() {
    userDAO = new UserDAO();
    sellerDAO = new SellerDAO();
    itemDAO = new ItemDAO();
    auctionDAO = new AuctionDAO();
    financialTransactionDAO = new FinancialTransactionDAO();
    ratingService = new com.group13.auction.service.RatingService(userDAO);
    walletService =
        new com.group13.auction.service.WalletService(
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
    @DisplayName(
        "TC-26a: registerUser() thành công — userId là UUID hợp lệ, existsByUsername = true")
    void registerUser_success_returnsValidUuidAndExistsInDb() {
      String userId =
          userDAO.registerUser("reg_user1", User.hashPassword("pass123"), "reg1@test.vn");
      trackUser(userId);

      assertThat(userId).isNotNull();
      assertDoesNotThrow(() -> UUID.fromString(userId));
      assertThat(userDAO.existsByUsername("reg_user1")).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("TC-26b: Duplicate username → registerUser() trả về null (unique constraint)")
    void registerUser_duplicateUsername_returnsNull() {
      String userId1 = userDAO.registerUser("dup_user", User.hashPassword("pass"), "dup1@test.vn");
      trackUser(userId1);

      String userId2 = userDAO.registerUser("dup_user", User.hashPassword("pass2"), "dup2@test.vn");

      assertThat(userId2).isNull();
    }

    @Test
    @Order(3)
    @DisplayName("TC-26c: Duplicate email → registerUser() trả về null (unique email constraint)")
    void registerUser_duplicateEmail_returnsNull() {
      String userId1 =
          userDAO.registerUser("email_user1", User.hashPassword("pass"), "shared@test.vn");
      trackUser(userId1);

      String userId2 =
          userDAO.registerUser("email_user2", User.hashPassword("pass"), "shared@test.vn");

      assertThat(userId2).isNull();
    }

    @Test
    @Order(4)
    @DisplayName("TC-26d: existsByEmail() trả về true sau khi đăng ký")
    void existsByEmail_afterRegister_returnsTrue() {
      String userId =
          userDAO.registerUser("email_check1", User.hashPassword("pass"), "unique_email@test.vn");
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
      assertThat(fromDB.getBalance()).as("DB balance = sum(deposits)").isEqualTo(10_000_000L);
    }

    @Test
    @Order(2)
    @DisplayName("TC-27b: withdraw() vượt available → IllegalArgumentException, balance không đổi")
    void withdraw_exceedsAvailableBalance_throwsException_balanceUnchanged() {
      NormalUser user = givenUser("dw_user2");
      walletService.deposit(user, 5_000_000L);
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

      assertAll(
          "Reconstitute đúng tất cả fields",
          () ->
              assertThat(fromDB.getBalance()).as("balance phải đúng").isEqualTo(user.getBalance()),
          () ->
              assertThat(fromDB.getLockedDeposit())
                  .as("lockedDeposit phải đúng từ locked_balance")
                  .isEqualTo(2_000_000L),
          () ->
              assertThat(fromDB.getAvailableBalance())
                  .as("availableBalance = balance - lockedDeposit")
                  .isEqualTo(fromDB.getBalance() - 2_000_000L),
          () -> assertThat(fromDB.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE),
          () -> assertThat(fromDB.getRating()).isGreaterThan(0.0));
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
    void findNormalUserById_joinedAuctionIdsInjected() throws Exception {
      NormalUser user = givenUser("reconstitute2");
      String auctionId = createDummyAuction(user.getId());
      userDAO.saveUserAuctionActivity(user.getId(), auctionId, "JOINED");

      NormalUser fromDB = userDAO.findNormalUserById(user.getId());

      assertThat(fromDB.getJoinedAuctionIds())
          .as("joinedAuctionIds phải được inject từ user_auction_activity")
          .contains(auctionId);
    }

    // ── FIX BUG #1: SELLER role phải được load từ DB ──────────────────────

    @Test
    @Order(5)
    @DisplayName(
        "TC-28e [HIGH] findNormalUserById() — user có approved seller record → có role SELLER")
    void findNormalUserById_withApprovedSeller_hasSellerRole() {
      // Arrange: tạo user, sau đó INSERT approved seller record trực tiếp vào DB
      NormalUser user = givenUser("seller_role_test1");
      ensureSellerRecord(user.getId()); // INSERT approval_status = 'APPROVED'

      // Act: reconstitute từ DB
      NormalUser fromDB = userDAO.findNormalUserById(user.getId());

      // Assert
      assertThat(fromDB).isNotNull();
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as("user có approved sellers record → phải có SELLER role sau khi reconstitute từ DB")
          .isTrue();
      assertThat(fromDB.hasRole(User.UserRole.BIDDER)).as("BIDDER role phải được giữ lại").isTrue();
    }

    @Test
    @Order(6)
    @DisplayName(
        "TC-28f [HIGH] findUserByUsername() — user có approved seller record → có role SELLER")
    void findUserByUsername_withApprovedSeller_hasSellerRole() {
      NormalUser user = givenUser("seller_role_test2");
      ensureSellerRecord(user.getId());

      NormalUser fromDB = userDAO.findUserByUsername(user.getUsername());

      assertThat(fromDB).isNotNull();
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as("findUserByUsername() cũng phải load SELLER role từ DB")
          .isTrue();
    }

    @Test
    @Order(7)
    @DisplayName(
        "TC-28g [HIGH] findUserCoreByUsername() — user có approved seller record → có role SELLER")
    void findUserCoreByUsername_withApprovedSeller_hasSellerRole() {
      // findUserCoreByUsername là path đặc biệt: được dùng khi login (UserService.login)
      // nếu user không có trong AuctionManager in-memory
      NormalUser user = givenUser("seller_role_test3");
      ensureSellerRecord(user.getId());

      NormalUser fromDB = userDAO.findUserCoreByUsername(user.getUsername());

      assertThat(fromDB).isNotNull();
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as(
              "findUserCoreByUsername() (login path) phải load SELLER role từ DB "
                  + "để LoginResponseDTO trả về đúng role sau server restart")
          .isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("TC-28h: findNormalUserById() — user chưa có sellers record → chỉ có BIDDER")
    void findNormalUserById_noSellerRecord_onlyHasBidderRole() {
      NormalUser user = givenUser("bidder_only_test");
      // Không gọi ensureSellerRecord → không có record trong bảng sellers

      NormalUser fromDB = userDAO.findNormalUserById(user.getId());

      assertThat(fromDB).isNotNull();
      assertThat(fromDB.hasRole(User.UserRole.BIDDER)).as("luôn có BIDDER role").isTrue();
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as("không có approved seller record → không có SELLER role")
          .isFalse();
    }

    @Test
    @Order(9)
    @DisplayName(
        "TC-28i: findNormalUserById() — user có PENDING sellers record → chỉ có BIDDER (chưa"
            + " APPROVED)")
    void findNormalUserById_pendingSellerRecord_onlyHasBidderRole() throws Exception {
      NormalUser user = givenUser("pending_seller_test");
      // INSERT PENDING record (chưa approved)
      insertPendingSellerRecord(user.getId());

      NormalUser fromDB = userDAO.findNormalUserById(user.getId());

      assertThat(fromDB).isNotNull();
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as("PENDING seller record → KHÔNG có SELLER role, phải đợi APPROVED")
          .isFalse();
    }
  }

  // =========================================================================
  // TC-29 — SellerDAO.approveSellerRole() UPSERT (FIX BUG #2)
  // =========================================================================

  @Nested
  @Order(4)
  @DisplayName("TC-29 [HIGH] SellerDAO.approveSellerRole() — UPSERT đảm bảo persist")
  class SellerDaoUpsertTests {

    @Test
    @Order(1)
    @DisplayName(
        "TC-29a: approveSellerRole() khi user chưa có sellers record → INSERT thành công, user có"
            + " SELLER role")
    void approveSellerRole_noExistingRecord_insertsAndUserHasSellerRole() {
      NormalUser user = givenUser("upsert_test1");
      // Không gọi requestSellerRole — user chưa có record trong bảng sellers
      // Đây là scenario của autoApproveSellerRole()

      boolean result = sellerDAO.approveSellerRole(user.getId());

      assertThat(result).as("UPSERT phải trả về true").isTrue();

      // Verify: reconstitute từ DB và kiểm tra role
      NormalUser fromDB = userDAO.findNormalUserById(user.getId());
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as("Sau approveSellerRole(), findNormalUserById() phải trả về SELLER role")
          .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName(
        "TC-29b: approveSellerRole() khi user đã có PENDING record → UPDATE → APPROVED, user có"
            + " SELLER role")
    void approveSellerRole_existingPendingRecord_updatesAndUserHasSellerRole() throws Exception {
      NormalUser user = givenUser("upsert_test2");
      insertPendingSellerRecord(user.getId());

      // Verify PENDING trước
      NormalUser beforeApprove = userDAO.findNormalUserById(user.getId());
      assertThat(beforeApprove.hasRole(User.UserRole.SELLER)).isFalse();

      // Approve
      boolean result = sellerDAO.approveSellerRole(user.getId());
      assertThat(result).isTrue();

      // Verify sau approve
      NormalUser afterApprove = userDAO.findNormalUserById(user.getId());
      assertThat(afterApprove.hasRole(User.UserRole.SELLER))
          .as("Sau approve PENDING record → SELLER role phải có")
          .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName(
        "TC-29c: approveSellerRole() lặp lại 2 lần → idempotent, không throw exception, vẫn có"
            + " SELLER role")
    void approveSellerRole_calledTwice_isIdempotent() {
      NormalUser user = givenUser("upsert_test3");

      // Gọi lần 1
      boolean first = sellerDAO.approveSellerRole(user.getId());
      // Gọi lần 2 (UPSERT phải idempotent)
      boolean second = sellerDAO.approveSellerRole(user.getId());

      assertThat(first).as("lần 1 phải thành công").isTrue();
      assertThat(second).as("lần 2 cũng phải thành công (idempotent)").isTrue();

      NormalUser fromDB = userDAO.findNormalUserById(user.getId());
      assertThat(fromDB.hasRole(User.UserRole.SELLER))
          .as("Sau 2 lần approve, vẫn có SELLER role")
          .isTrue();
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private NormalUser givenUser(String username) {
    return buildUserWithBalance(username, 0L, userDAO);
  }

  /** Insert PENDING seller record — để test các scenario chưa approved. */
  private void insertPendingSellerRecord(String userId) throws SQLException {
    String sql = "INSERT IGNORE INTO sellers (user_id, approval_status) VALUES (?, 'PENDING')";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.executeUpdate();
    }
  }

  /** Tạo auction tối thiểu để thỏa FK user_auction_activity.auction_id → auctions.id. */
  private String createDummyAuction(String sellerId) throws SQLException {
    ensureSellerRecord(sellerId);
    String itemId = UUID.randomUUID().toString();
    String auctionId = UUID.randomUUID().toString();
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      try (PreparedStatement ps =
          conn.prepareStatement(
              "INSERT INTO items (id, seller_id, name, description, starting_price, category_type) "
                  + "VALUES (?, ?, 'dummy', 'dummy', 1000000, 'ELECTRONICS')")) {
        ps.setString(1, itemId);
        ps.setString(2, sellerId);
        ps.executeUpdate();
      }
      try (PreparedStatement ps =
          conn.prepareStatement(
              "INSERT INTO auctions (id, item_id, start_time, end_time, current_price, status,"
                  + " reserve_price) VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR),"
                  + " 1000000, 'OPEN', 1000000)")) {
        ps.setString(1, auctionId);
        ps.setString(2, itemId);
        ps.executeUpdate();
      }
    }
    trackItem(itemId);
    trackAuction(auctionId);
    return auctionId;
  }
}
