package com.group13.auction.integration.service.payment;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.dao.*;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Integration test WalletService: payment, withdraw, available balance (DAO + DB thật). */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("WalletPaymentIT — WalletService × DAO × DB (Bottom-up)")
class WalletPaymentIT extends IntegrationTestBase {

  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.0")
          .withDatabaseName("omnibid_test")
          .withUsername("test_user")
          .withPassword("test_pass")
          .withInitScript("database/schema.sql");

  private UserDAO userDAO;
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
    itemDAO = new ItemDAO();
    auctionDAO = new AuctionDAO();
    financialTransactionDAO = new FinancialTransactionDAO();
    ratingService = new RatingService(userDAO);
    walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
    resetTracking();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanupDB();
  }
  // TC-01 — executePaymentToBank() Rollback Consistency
  @Nested
  @Order(1)
  @DisplayName("TC-01 [CRITICAL] executePaymentToBank() — RAM/DB Rollback Consistency")
  class ExecutePaymentToBankRollbackTests {

    @Test
    @Order(1)
    @DisplayName(
        "TC-01a: Happy path — balance, lockedDeposit, DB nhất quán sau thanh toán thành công")
    void happyPath_balanceAndDB_consistentAfterPayment() {
      NormalUser winner = givenUserWithBalance("pay_w1", 20_000_000L);
      String auctionId = createDummyAuction(winner.getId());
      long finalPrice = 10_000_000L;
      long depositPaid = 3_000_000L;

      walletService.lockDeposit(winner, depositPaid, auctionId);
      long balanceBefore = winner.getBalance(); // 20M (chưa trừ)

      // Act
      walletService.executePaymentToBank(winner, finalPrice, depositPaid, auctionId);

      long remaining = finalPrice - depositPaid; // 7M

      // Assert RAM
      assertAll(
          "RAM nhất quán sau thanh toán thành công",
          () ->
              assertThat(winner.getBalance())
                  .as("balance = balanceBefore - remaining - depositPaid")
                  .isEqualTo(balanceBefore - remaining - depositPaid),
          () ->
              assertThat(winner.getLockedDeposit())
                  .as("lockedDeposit phải = 0 sau khi giải phóng cọc")
                  .isZero());

      // Assert DB — phải khớp RAM
      NormalUser fromDB = userDAO.findNormalUserById(winner.getId());
      assertAll(
          "DB khớp RAM sau thanh toán thành công",
          () ->
              assertThat(fromDB.getBalance())
                  .as("DB balance phải bằng RAM balance")
                  .isEqualTo(winner.getBalance()),
          () ->
              assertThat(fromDB.getLockedDeposit())
                  .as("DB lockedDeposit phải bằng RAM lockedDeposit")
                  .isEqualTo(winner.getLockedDeposit()));
    }

    @Test
    @Order(2)
    @DisplayName("TC-01b: Insufficient balance → PaymentException, RAM rollback, DB không thay đổi")
    void insufficientBalance_throwsPaymentException_ramAndDbRolledBack() {
      // winner chỉ có 5M, remaining = 10M - 3M = 7M > available(5M - 3M = 2M)
      NormalUser winner = givenUserWithBalance("pay_w2", 5_000_000L);
      String auctionId = createDummyAuction(winner.getId());
      long finalPrice = 10_000_000L;
      long depositPaid = 3_000_000L;

      walletService.lockDeposit(winner, depositPaid, auctionId);
      long balanceBefore = winner.getBalance();
      long lockedBefore = winner.getLockedDeposit();

      // Act — phải ném PaymentException
      assertThatThrownBy(
              () -> walletService.executePaymentToBank(winner, finalPrice, depositPaid, auctionId))
          .isInstanceOf(PaymentException.class);

      // RAM phải rollback
      assertAll(
          "RAM rollback sau thất bại",
          () ->
              assertThat(winner.getBalance())
                  .as("RAM balance phải rollback về trước khi payment")
                  .isEqualTo(balanceBefore),
          () ->
              assertThat(winner.getLockedDeposit())
                  .as("RAM lockedDeposit phải rollback")
                  .isEqualTo(lockedBefore));

      // DB phải khớp RAM đã rollback
      NormalUser fromDB = userDAO.findNormalUserById(winner.getId());
      assertAll(
          "DB phải phản ánh trạng thái rollback",
          () ->
              assertThat(fromDB.getBalance())
                  .as("DB balance phải bằng RAM balance đã rollback")
                  .isEqualTo(winner.getBalance()),
          () ->
              assertThat(fromDB.getLockedDeposit())
                  .as("DB lockedDeposit phải bằng RAM lockedDeposit đã rollback")
                  .isEqualTo(winner.getLockedDeposit()));
    }

    @Test
    @Order(3)
    @DisplayName(
        "TC-01c: Concurrent executePaymentToBank — chỉ 1 trong 2 thread thành công, không"
            + " double-payment")
    void concurrentPayment_onlyOneSucceeds_noDoubleCharge() throws InterruptedException {
      // winner có đúng đủ tiền cho 1 lần thanh toán
      NormalUser winner = givenUserWithBalance("pay_w3", 15_000_000L);
      String auctionId = createDummyAuction(winner.getId());
      long finalPrice = 10_000_000L;
      long depositPaid = 3_000_000L;
      walletService.lockDeposit(winner, depositPaid, auctionId);

      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger failCount = new AtomicInteger(0);
      CountDownLatch startGun = new CountDownLatch(1);

      Runnable attempt =
          () -> {
            try {
              startGun.await();
              walletService.executePaymentToBank(winner, finalPrice, depositPaid, auctionId);
              successCount.incrementAndGet();
            } catch (PaymentException | InterruptedException e) {
              failCount.incrementAndGet();
            }
          };

      Thread t1 = new Thread(attempt);
      Thread t2 = new Thread(attempt);
      t1.start();
      t2.start();
      startGun.countDown();
      t1.join(5_000);
      t2.join(5_000);

      assertAll(
          "Concurrent payment — không double charge",
          () ->
              assertThat(successCount.get())
                  .as("Chỉ đúng 1 thread được thanh toán thành công")
                  .isEqualTo(1),
          () -> assertThat(failCount.get()).as("1 thread phải thất bại").isEqualTo(1),
          () ->
              assertThat(winner.getBalance())
                  .as("balance giảm đúng 1 lần finalPrice, không bị double-deduct")
                  .isEqualTo(15_000_000L - finalPrice));
    }
  }
  // TC-06 — withdraw() không rút vào tiền đang lock
  @Nested
  @Order(2)
  @DisplayName("TC-06 [HIGH] withdraw() — Không rút vào tiền đang lock (availableBalance boundary)")
  class WithdrawBoundaryTests {

    @Test
    @Order(1)
    @DisplayName("TC-06a: Rút đúng phần available (không chạm locked) → thành công, DB nhất quán")
    void withdraw_exactAvailableAmount_succeeds() {
      // balance = 10M, locked = 3M, available = 7M
      NormalUser user = givenUserWithBalance("wd_u1", 10_000_000L);
      String auctionId = createDummyAuction(user.getId());
      walletService.lockDeposit(user, 3_000_000L, auctionId);

      // Rút đúng 7M available
      assertDoesNotThrow(() -> walletService.withdraw(user, 7_000_000L));

      assertAll(
          "Sau khi rút đúng available",
          () ->
              assertThat(user.getBalance())
                  .as("balance còn lại = phần locked = 3M")
                  .isEqualTo(3_000_000L),
          () ->
              assertThat(user.getLockedDeposit())
                  .as("lockedDeposit KHÔNG thay đổi khi rút")
                  .isEqualTo(3_000_000L));

      NormalUser fromDB = userDAO.findNormalUserById(user.getId());
      assertThat(fromDB.getBalance()).as("DB balance phải = 3M").isEqualTo(3_000_000L);
    }

    @Test
    @Order(2)
    @DisplayName(
        "TC-06b: Rút vượt available (cố rút vào locked) → IllegalArgumentException, không thay đổi")
    void withdraw_exceedsAvailableBalance_throws_noSideEffect() {
      // balance = 10M, locked = 3M, available = 7M — cố rút 8M
      NormalUser user = givenUserWithBalance("wd_u2", 10_000_000L);
      String auctionId = createDummyAuction(user.getId());
      walletService.lockDeposit(user, 3_000_000L, auctionId);

      long balanceBefore = user.getBalance();
      long lockedBefore = user.getLockedDeposit();

      assertThatThrownBy(() -> walletService.withdraw(user, 8_000_000L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Số dư khả dụng không đủ");

      // Không có side-effect
      assertAll(
          "Không thay đổi sau withdraw thất bại",
          () -> assertThat(user.getBalance()).isEqualTo(balanceBefore),
          () -> assertThat(user.getLockedDeposit()).isEqualTo(lockedBefore));
    }

    @Test
    @Order(3)
    @DisplayName("TC-06c: Rút balance = 0 → IllegalArgumentException (amount <= 0 guard)")
    void withdraw_zeroAmount_throws() {
      NormalUser user = givenUserWithBalance("wd_u3", 5_000_000L);

      assertThatThrownBy(() -> walletService.withdraw(user, 0L))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Order(4)
    @DisplayName(
        "TC-06d: deposit() → withdraw() → deposit() nhiều lần — DB balance nhất quán end-to-end")
    void multipleDepositWithdraw_dbConsistent() {
      NormalUser user = givenUserWithBalance("wd_u4", 5_000_000L);

      walletService.deposit(user, 3_000_000L); // 8M
      walletService.withdraw(user, 2_000_000L); // 6M
      walletService.deposit(user, 1_000_000L); // 7M
      walletService.withdraw(user, 500_000L); // 6.5M

      NormalUser fromDB = userDAO.findNormalUserById(user.getId());
      assertThat(fromDB.getBalance())
          .as("DB balance phải nhất quán sau chuỗi deposit/withdraw")
          .isEqualTo(6_500_000L);
    }
  }

  // Helpers

  private NormalUser givenUserWithBalance(String username, long balance) {
    return buildUserWithBalance(username, balance, userDAO);
  }

  private String createDummyAuction(String sellerId) {
    ensureSellerRecord(sellerId);
    String itemId = UUID.randomUUID().toString();
    itemDAO.addItem(
        itemId,
        sellerId,
        "WalletIT item " + itemId.substring(0, 6),
        "test",
        1_000_000L,
        "ELECTRONICS");
    trackItem(itemId);

    var item = itemDAO.findItemById(itemId);
    var auction =
        com.group13.auction.model.auction.Auction.create(
            item,
            LocalDateTime.now().plusSeconds(1),
            LocalDateTime.now().plusHours(1),
            1_200_000L);
    auctionDAO.createAuction(auction);
    trackAuction(auction.getId());
    return auction.getId();
  }
}
