package com.group13.auction.integration.service.payment;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.dao.*;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.*;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * ════════════════════════════════════════════════════════════════════ DepositRefundIT —
 * Integration Tests cho luồng hoàn cọc Kỹ thuật: Bottom-up (PaymentService + WalletService + DAO
 * thực) ════════════════════════════════════════════════════════════════════
 *
 * <p>Scope: Kiểm tra PaymentService.refundDeposits() — phương thức hoàn cọc cho tất cả bidder khi
 * phiên bị CANCELED hoặc kết thúc.
 *
 * <p>TC-02 [CRITICAL]: BUG RISK: refundDeposits() gọi bidTransactionDAO.findBiddersByAuction() để
 * lấy danh sách, rồi walletService.unlockDeposit() cho từng người. depositAmount tính bằng
 * startingPrice * 3/10 thay vì query từ financial_transactions → số tiền hoàn có thể không khớp số
 * tiền đã lock. Winner không được hoàn (cọc tính vào finalPrice) — nếu logic sai → mất tiền.
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("DepositRefundIT — PaymentService.refundDeposits() (Bottom-up)")
class DepositRefundIT extends IntegrationTestBase {

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
  private BidTransactionDAO bidTransactionDAO;
  private FinancialTransactionDAO financialTransactionDAO;
  private AuctionWinnerDAO auctionWinnerDAO;
  private SecondChanceOfferDAO secondChanceOfferDAO;

  private RatingService ratingService;
  private WalletService walletService;
  private AuctionService auctionService;
  private PaymentService paymentService;

  @BeforeAll
  static void configureDataSource() throws Exception {
    configureTestcontainer(mysql);
  }

  @BeforeEach
  void setUp() throws Exception {
    userDAO = new UserDAO();
    itemDAO = new ItemDAO();
    auctionDAO = new AuctionDAO();
    bidTransactionDAO = new BidTransactionDAO();
    financialTransactionDAO = new FinancialTransactionDAO();
    auctionWinnerDAO = new AuctionWinnerDAO();
    secondChanceOfferDAO = new SecondChanceOfferDAO();

    ratingService = new RatingService(userDAO);
    TestFixture.bootstrapSystemAdmin();
    walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
    auctionService = new AuctionService(ratingService, auctionDAO);
    paymentService =
        new PaymentService(
            auctionService,
            ratingService,
            walletService,
            auctionWinnerDAO,
            secondChanceOfferDAO,
            bidTransactionDAO,
            userDAO);
    resetTracking();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanupDB();
    TestFixture.resetSystemAdmin();
  }

  // =========================================================================
  // TC-02 — refundDeposits() khi Auction CANCELED
  // =========================================================================

  @Test
  @Order(1)
  @DisplayName("TC-02a: 3 bidders đều nhận lại đúng số tiền cọc sau khi phiên bị CANCELED")
  void threeBidders_allReceiveFullDepositRefund_afterCancel() {
    // Arrange — phiên với 3 bidder, không có winner
    Auction auction = givenRunningAuction("ref_seller1", 5_000_000L, 8_000_000L);
    NormalUser b1 = givenUserWithBalance("ref_b1", 10_000_000L);
    NormalUser b2 = givenUserWithBalance("ref_b2", 10_000_000L);
    NormalUser b3 = givenUserWithBalance("ref_b3", 10_000_000L);
    long deposit = auction.getItem().getStartingPrice() * 3 / 10; // 1_500_000

    for (NormalUser b : List.of(b1, b2, b3)) {
      b.addJoinedAuction(auction.getId());
      walletService.lockDeposit(b, deposit, auction.getId());
      // Cần có BidTransaction ACCEPTED để findBiddersByAuction() trả về
      BidTransaction tx =
          BidTransaction.create(b, auction.getId(), 5_500_000L, BidTransaction.BidResult.ACCEPTED);
      bidTransactionDAO.saveTransaction(tx);
    }

    long b1Before = b1.getAvailableBalance();
    long b2Before = b2.getAvailableBalance();
    long b3Before = b3.getAvailableBalance();

    // Act — hoàn cọc cho tất cả (không có winner)
    paymentService.refundDeposits(auction);

    // Assert RAM — mỗi người nhận lại đúng số tiền cọc
    assertAll(
        "Tất cả bidder nhận lại đủ cọc",
        () ->
            assertThat(b1.getBalance())
                .as("b1 nhận lại deposit = %d", deposit)
                .isEqualTo(b1Before + deposit),
        () ->
            assertThat(b2.getBalance())
                .as("b2 nhận lại deposit = %d", deposit)
                .isEqualTo(b2Before + deposit),
        () ->
            assertThat(b3.getBalance())
                .as("b3 nhận lại deposit = %d", deposit)
                .isEqualTo(b3Before + deposit));

    // Assert DB — lockedDeposit phải = 0 sau khi hoàn
    NormalUser b1DB = userDAO.findNormalUserById(b1.getId());
    assertThat(b1DB.getLockedDeposit())
        .as("DB lockedDeposit của b1 phải = 0 sau hoàn cọc")
        .isZero();
  }

  @Test
  @Order(2)
  @DisplayName("TC-02b: Winner KHÔNG được hoàn cọc — chỉ bidder thua mới được hoàn")
  void winner_doesNotReceiveRefund_onlyLosersRefunded() {
    // Arrange
    Auction auction = givenRunningAuction("ref_seller2", 3_000_000L, 4_000_000L);
    NormalUser winner = givenUserWithBalance("ref_winner2", 15_000_000L);
    NormalUser loser = givenUserWithBalance("ref_loser2", 10_000_000L);
    long deposit = auction.getItem().getStartingPrice() * 3 / 10; // 900_000

    for (NormalUser b : List.of(winner, loser)) {
      b.addJoinedAuction(auction.getId());
      walletService.lockDeposit(b, deposit, auction.getId());
    }

    bidTransactionDAO.saveTransaction(
        BidTransaction.create(
            winner, auction.getId(), 5_000_000L, BidTransaction.BidResult.ACCEPTED));
    bidTransactionDAO.saveTransaction(
        BidTransaction.create(
            loser, auction.getId(), 4_000_000L, BidTransaction.BidResult.ACCEPTED));

    // Đặt winner — refundDeposits() sẽ bỏ qua winner
    AuctionWinner aw = AuctionWinner.create(winner, auction.getId(), 5_000_000L, deposit, false);
    auction.setWinner(aw);

    long winnerLockedBefore = winner.getLockedDeposit();
    long loserAvailableBalanceBefore = loser.getAvailableBalance();

    // Act
    paymentService.refundDeposits(auction);

    assertAll(
        "Chỉ loser được hoàn, winner giữ nguyên cọc",
        () ->
            assertThat(loser.getBalance())
                .as("loser nhận lại đúng deposit")
                .isEqualTo(loserAvailableBalanceBefore + deposit),
        () ->
            assertThat(winner.getLockedDeposit())
                .as("winner KHÔNG được hoàn cọc (cọc đã tính vào finalPrice)")
                .isEqualTo(winnerLockedBefore));
  }

  @Test
  @Order(3)
  @DisplayName("TC-02c: refundDeposits() khi không có bidder nào — không throw exception")
  void zeroBidders_refundDeposits_doesNotThrow() {
    Auction auction = givenRunningAuction("ref_seller3", 2_000_000L, 3_000_000L);

    assertDoesNotThrow(() -> paymentService.refundDeposits(auction));
  }

  @Test
  @Order(4)
  @DisplayName(
      "TC-02d: refundDeposits() gọi 2 lần — lần 2 không double-refund (lockedDeposit đã = 0)")
  void calledTwice_secondCallIsNoOp_noDoubleRefund() {
    // Arrange
    Auction auction = givenRunningAuction("ref_seller4", 4_000_000L, 6_000_000L);
    NormalUser bidder = givenUserWithBalance("ref_bidder4", 10_000_000L);
    long deposit = auction.getItem().getStartingPrice() * 3 / 10;

    bidder.addJoinedAuction(auction.getId());
    walletService.lockDeposit(bidder, deposit, auction.getId());
    bidTransactionDAO.saveTransaction(
        BidTransaction.create(
            bidder, auction.getId(), 5_000_000L, BidTransaction.BidResult.ACCEPTED));

    // Act — gọi 2 lần
    paymentService.refundDeposits(auction);
    long balanceAfterFirst = bidder.getBalance();

    paymentService.refundDeposits(auction); // lần 2 — lockedDeposit đã = 0

    assertThat(bidder.getBalance())
        .as("Lần 2 không được cộng thêm tiền (lockedDeposit đã = 0)")
        .isEqualTo(balanceAfterFirst);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private NormalUser givenUserWithBalance(String username, long balance) {
    return buildUserWithBalance(username, balance, userDAO);
  }

  private Auction givenRunningAuction(
      String sellerUsername, long startingPrice, long reservePrice) {
    NormalUser seller = buildUserWithBalance(sellerUsername, 50_000_000L, userDAO);
    ensureSellerRecord(seller.getId());
    String itemId = UUID.randomUUID().toString();
    itemDAO.addItem(
        itemId, seller.getId(), "Item " + sellerUsername, "test", startingPrice, "ELECTRONICS");
    trackItem(itemId);

    var item = itemDAO.findItemById(itemId);
    var auction =
        Auction.create(
            item,
            LocalDateTime.now().plusSeconds(1),
            LocalDateTime.now().plusHours(2),
            reservePrice);
    auctionDAO.createAuction(auction);
    auctionService.startAuction(auction);
    trackAuction(auction.getId());

    return auction;
  }
}
