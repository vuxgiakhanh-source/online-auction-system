package com.group13.auction.integration.service.payment;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.dao.*;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.*;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Integration test hết hạn thanh toán và second chance offer (DAO + DB thật). */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("PaymentExpireAndSecondChanceIT — expirePayment() + SecondChanceOffer (Sandwich)")
class PaymentExpireAndSecondChanceIT extends IntegrationTestBase {

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
  // TC-03 — expirePayment() Full Chain
  @Nested
  @Order(1)
  @DisplayName("TC-03 [CRITICAL] expirePayment() — Full chain khi winner không thanh toán")
  class ExpirePaymentFullChainTests {

    @Test
    @Order(1)
    @DisplayName("TC-03a: expirePayment() → forfeit cọc + giảm rating + EXPIRED + SecondChance tạo")
    void expirePayment_fullChain_allFourStepsConsistent() {
      // Arrange
      Auction auction = givenRunningAuction("exp_seller1", 5_000_000L, 6_000_000L);
      NormalUser winner = givenUserWithBalance("exp_winner1", 20_000_000L);
      NormalUser runner = givenUserWithBalance("exp_runner1", 15_000_000L);
      long deposit = auction.getItem().getStartingPrice() * 3 / 10;

      winner.addJoinedAuction(auction.getId());
      runner.addJoinedAuction(auction.getId());
      walletService.lockDeposit(winner, deposit, auction.getId());

      // Ghi bids để offerSecondChance() tìm được runner-up
      bidTransactionDAO.saveTransaction(
          BidTransaction.create(
              winner, auction.getId(), 7_000_000L, BidTransaction.BidResult.ACCEPTED));
      bidTransactionDAO.saveTransaction(
          BidTransaction.create(
              runner, auction.getId(), 6_500_000L, BidTransaction.BidResult.ACCEPTED));

      // AuctionWinner đã quá 24h (expired)
      AuctionWinner aw =
          AuctionWinner.reconstitute(
              java.util.UUID.randomUUID().toString(),
              java.time.LocalDateTime.now().minusHours(25),
              java.time.LocalDateTime.now().minusHours(25),
              winner,
              auction.getId(),
              7_000_000L,
              deposit,
              java.time.LocalDateTime.now().minusHours(1), // paymentDeadline đã qua
              null,
              null,
              AuctionWinner.PaymentStatus.PENDING,
              false);
      auction.setWinner(aw);

      double ratingBefore = winner.getRating();

      // Act
      paymentService.expirePayment(auction);

      // Assert — 4 thành phần phải nhất quán cùng lúc
      assertAll(
          "expirePayment — 4 bước phải nhất quán",

          // 1. Cọc tịch thu
          () ->
              assertThat(winner.getLockedDeposit())
                  .as("lockedDeposit phải = 0 sau forfeit cọc")
                  .isZero(),

          // 2. Rating giảm 1.0
          () ->
              assertThat(winner.getRating())
                  .as("rating phải giảm đúng 1.0 (penalizeLatePayment)")
                  .isEqualTo(ratingBefore - 1.0, within(0.001)),

          // 3. Status = EXPIRED
          () ->
              assertThat(aw.getPaymentStatus())
                  .as("AuctionWinner phải chuyển sang EXPIRED")
                  .isEqualTo(AuctionWinner.PaymentStatus.EXPIRED),

          // 4. DB persist rating + penalty flag
          () -> {
            NormalUser fromDB = userDAO.findNormalUserById(winner.getId());
            assertThat(fromDB.getRating())
                .as("DB rating phải khớp RAM sau penalize")
                .isEqualTo(winner.getRating(), within(0.001));
            assertThat(fromDB.isHasEverBeenPenalized())
                .as("DB hasEverBeenPenalized phải = true")
                .isTrue();
          });
    }

    @Test
    @Order(2)
    @DisplayName(
        "TC-03b: expirePayment() với isSecondOffer=true → cancelAuction ngay, không tạo"
            + " SecondChance mới")
    void expirePayment_secondOfferExpired_cancelAuctionDirectly_noNewSecondChance() {
      // Arrange — đây là second chance đã expire, isSecondOffer = true
      Auction auction = givenRunningAuction("exp_seller2", 3_000_000L, 4_000_000L);
      NormalUser runner = givenUserWithBalance("exp_runner2", 15_000_000L);
      long deposit = 900_000L;
      walletService.lockDeposit(runner, deposit, auction.getId());

      AuctionWinner aw =
          AuctionWinner.reconstitute(
              java.util.UUID.randomUUID().toString(),
              java.time.LocalDateTime.now().minusHours(25),
              java.time.LocalDateTime.now().minusHours(25),
              runner,
              auction.getId(),
              5_000_000L,
              deposit,
              java.time.LocalDateTime.now().minusHours(1), // paymentDeadline đã qua
              null,
              null,
              AuctionWinner.PaymentStatus.PENDING,
              true); // isSecondOffer = true
      auction.setWinner(aw);

      // Act
      paymentService.expirePayment(auction);

      // Phải bị CANCEL ngay, không tạo SecondChanceOffer mới
      Auction fromDB = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDB.getStatus())
          .as("Auction với SecondOffer đã expire phải bị CANCELED ngay")
          .isEqualTo(Auction.AuctionStatus.CANCELED);
    }

    @Test
    @Order(3)
    @DisplayName("TC-03c: expirePayment() khi chưa expire → guard clause, không có thay đổi")
    void expirePayment_notYetExpired_guardClauseActivates_noChanges() {
      Auction auction = givenRunningAuction("exp_seller3", 2_000_000L, 3_000_000L);
      NormalUser winner = givenUserWithBalance("exp_winner3", 10_000_000L);
      long deposit = 600_000L;
      walletService.lockDeposit(winner, deposit, auction.getId());

      // AuctionWinner chưa hết hạn (24h từ bây giờ)
      AuctionWinner aw = AuctionWinner.create(winner, auction.getId(), 5_000_000L, deposit, false);
      auction.setWinner(aw);

      double ratingBefore = winner.getRating();
      long lockedBefore = winner.getLockedDeposit();

      paymentService.expirePayment(auction);

      assertAll(
          "Chưa expire — không có thay đổi",
          () ->
              assertThat(winner.getRating())
                  .as("Rating không đổi khi chưa expire")
                  .isEqualTo(ratingBefore, within(0.001)),
          () ->
              assertThat(winner.getLockedDeposit())
                  .as("lockedDeposit không đổi khi chưa expire")
                  .isEqualTo(lockedBefore));
    }
  }
  // TC-07 — SecondChanceOffer Lifecycle
  @Nested
  @Order(2)
  @DisplayName("TC-07 [HIGH] SecondChanceOffer — PENDING → ACCEPTED / DECLINED / EXPIRED")
  class SecondChanceOfferLifecycleTests {

    @Test
    @Order(1)
    @DisplayName("TC-07a: acceptSecondChanceOffer() → lockDeposit runnerUp + AuctionWinner mới tạo")
    void accept_locksRunnerUpDeposit_setsNewWinner() {
      Auction auction = givenRunningAuction("sco_seller1", 3_000_000L, 5_000_000L);
      NormalUser runner = givenUserWithBalance("sco_runner1", 20_000_000L);
      runner.addJoinedAuction(auction.getId());

      long depositPaid = 900_000L;
      long offerPrice = 6_000_000L;
      SecondChanceOffer offer =
          SecondChanceOffer.create(runner, auction.getId(), offerPrice, depositPaid);

      // Act
      paymentService.acceptSecondChanceOffer(offer, auction);

      assertAll(
          "acceptSecondChanceOffer — side effects",
          () ->
              assertThat(offer.getStatus())
                  .as("Offer status = ACCEPTED")
                  .isEqualTo(SecondChanceOffer.OfferStatus.ACCEPTED),
          () ->
              assertThat(runner.getLockedDeposit())
                  .as("RunnerUp phải có deposit = %d bị lock", depositPaid)
                  .isEqualTo(depositPaid),
          () -> assertThat(auction.getWinner()).as("Auction phải có winner mới").isNotNull(),
          () ->
              assertThat(auction.getWinner().getWinner().getId())
                  .as("Winner mới phải là runnerUp")
                  .isEqualTo(runner.getId()),
          () ->
              assertThat(auction.getWinner().getIsSecondOffer())
                  .as("Winner mới phải có isSecondOffer = true")
                  .isTrue());
    }

    @Test
    @Order(2)
    @DisplayName("TC-07b: declineSecondChanceOffer() → offer DECLINED + auction bị CANCELED ngay")
    void decline_offerDeclined_auctionCanceled() {
      Auction auction = givenRunningAuction("sco_seller2", 3_000_000L, 5_000_000L);
      NormalUser runner = givenUserWithBalance("sco_runner2", 15_000_000L);

      SecondChanceOffer offer =
          SecondChanceOffer.create(runner, auction.getId(), 6_000_000L, 900_000L);

      paymentService.declineSecondChanceOffer(offer, auction);

      assertAll(
          "declineSecondChanceOffer",
          () ->
              assertThat(offer.getStatus())
                  .as("Offer status = DECLINED")
                  .isEqualTo(SecondChanceOffer.OfferStatus.DECLINED),
          () ->
              assertThat(auction.getStatus())
                  .as("Auction phải CANCELED sau decline")
                  .isEqualTo(Auction.AuctionStatus.CANCELED));
    }

    @Test
    @Order(3)
    @DisplayName(
        "TC-07c: acceptSecondChanceOffer() khi offer không còn PENDING → IllegalStateException")
    void accept_offerNotPending_throwsIllegalStateException() {
      Auction auction = givenRunningAuction("sco_seller3", 3_000_000L, 5_000_000L);
      NormalUser runner = givenUserWithBalance("sco_runner3", 15_000_000L);

      SecondChanceOffer offer =
          SecondChanceOffer.create(runner, auction.getId(), 6_000_000L, 900_000L);
      offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED); // không còn PENDING

      assertThatThrownBy(() -> paymentService.acceptSecondChanceOffer(offer, auction))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("PENDING");
    }

    @Test
    @Order(4)
    @DisplayName(
        "TC-07d: acceptSecondChanceOffer() khi offer đã hết hạn → status EXPIRED, auction CANCELED")
    void accept_offerExpired_statusExpired_auctionCanceled() {
      Auction auction = givenRunningAuction("sco_seller4", 3_000_000L, 5_000_000L);
      NormalUser runner = givenUserWithBalance("sco_runner4", 15_000_000L);

      // Tạo offer với deadline đã qua
      SecondChanceOffer expiredOffer =
          SecondChanceOffer.reconstitute(
              java.util.UUID.randomUUID().toString(),
              java.time.LocalDateTime.now().minusHours(25),
              java.time.LocalDateTime.now().minusHours(25),
              runner,
              auction.getId(),
              6_000_000L,
              900_000L,
              java.time.LocalDateTime.now().minusHours(1), // deadline đã qua
              SecondChanceOffer.OfferStatus.PENDING);

      paymentService.acceptSecondChanceOffer(expiredOffer, auction);

      assertAll(
          "Expired offer → EXPIRED + CANCELED",
          () ->
              assertThat(expiredOffer.getStatus())
                  .as("Offer status = EXPIRED")
                  .isEqualTo(SecondChanceOffer.OfferStatus.EXPIRED),
          () ->
              assertThat(auction.getStatus())
                  .as("Auction bị CANCELED vì offer đã hết hạn")
                  .isEqualTo(Auction.AuctionStatus.CANCELED));
    }
  }

  // Helpers

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
