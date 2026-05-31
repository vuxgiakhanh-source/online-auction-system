package com.group13.auction.integration.service.report;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.group13.auction.dao.*;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.*;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** QualityReportIntegrationIT — ĐÃ SỬA */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("QualityReportIntegrationIT — QualityReport flow (Sandwich)")
class QualityReportIntegrationIT extends IntegrationTestBase {

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
  private QualityReportDAO qualityReportDAO;

  private RatingService ratingService;
  private WalletService walletService;
  private AuctionService auctionService;
  private BidService bidService;
  private PaymentService paymentService;
  private QualityReportService qualityReportService;

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
    qualityReportDAO = new QualityReportDAO();

    ratingService = new RatingService(userDAO);
    TestFixture.bootstrapSystemAdmin();
    walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
    auctionService = new AuctionService(ratingService, auctionDAO);
    bidService =
        new BidService(
            auctionService, ratingService, walletService, bidTransactionDAO, auctionDAO, userDAO);
    paymentService =
        new PaymentService(
            auctionService,
            ratingService,
            walletService,
            auctionWinnerDAO,
            secondChanceOfferDAO,
            bidTransactionDAO,
            userDAO);
    qualityReportService =
        new QualityReportService(ratingService, paymentService, qualityReportDAO, userDAO);
    resetTracking();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanupDB();
    TestFixture.resetSystemAdmin();
  }
  // TC-20 — approveReport()
  @Nested
  @Order(1)
  @DisplayName("TC-20 [HIGH] approveReport() — Seller penalized, winner refunded")
  class ApproveReportTests {

    @Test
    @Order(1)
    @DisplayName("TC-20a: submitReport() lưu report ở PENDING")
    void submitReport_persistsAsPending() {
      NormalUser winner = givenUserWithBalance("qr_winner1", 20_000_000L);
      Auction auction = givenPaidAuction("qr_seller1", winner, 5_000_000L, 7_000_000L, 8_000_000L);

      QualityReport report =
          QualityReport.create(
              winner,
              auction.getId(),
              "Test quality issue",
              java.util.List.of("http://img.test/1.jpg"));
      qualityReportService.submitReport(report);
      trackQualityReport(report.getId());

      assertThat(report.getStatus())
          .as("Report phải ở PENDING sau khi submit")
          .isEqualTo(QualityReport.ReportStatus.PENDING);
    }

    @Test
    @Order(2)
    @DisplayName("TC-20b: approveReport() → seller rating giảm, DB cập nhật")
    void approveReport_sellerRatingDecreased_dbUpdated() {
      NormalUser winner = givenUserWithBalance("qr_winner2", 20_000_000L);
      NormalUser seller = givenUserWithBalance("qr_seller2_user", 0L);
      Auction auction =
          givenPaidAuctionWithSeller(seller, winner, 5_000_000L, 7_000_000L, 8_000_000L);

      double sellerRatingBefore = seller.getRating();
      Admin admin = buildAdmin("qr_admin2");

      QualityReport report =
          QualityReport.create(
              winner,
              auction.getId(),
              "Test quality issue",
              java.util.List.of("http://img.test/1.jpg"));
      qualityReportService.submitReport(report);
      trackQualityReport(report.getId());

      qualityReportService.approveReport(admin, report, auction);

      // === PHẦN ĐÃ SỬA ===
      // Fetch lại từ DB vì approveReport() dùng object seller khác (từ ItemDAO)
      NormalUser sellerAfter = userDAO.findNormalUserById(seller.getId());

      assertThat(sellerAfter.getRating())
          .as("Seller rating phải giảm sau approve")
          .isLessThan(sellerRatingBefore)
          .isGreaterThanOrEqualTo(1.0);

      // Kiểm tra DB (đã có sẵn)
      assertThat(sellerAfter.getRating())
          .as("DB seller rating phải được cập nhật")
          .isLessThan(sellerRatingBefore);
    }

    @Test
    @Order(3)
    @DisplayName("TC-20c: approveReport() với report không ở PENDING — IllegalStateException")
    void approveReport_notPending_throwsIllegalState() {
      NormalUser winner = givenUserWithBalance("qr_winner3", 20_000_000L);
      Auction auction = givenPaidAuction("qr_seller3", winner, 5_000_000L, 7_000_000L, 8_000_000L);

      Admin admin = buildAdmin("qr_admin3");
      QualityReport report =
          QualityReport.create(
              winner,
              auction.getId(),
              "Test quality issue",
              java.util.List.of("http://img.test/1.jpg"));
      qualityReportService.submitReport(report);
      trackQualityReport(report.getId());

      qualityReportService.approveReport(admin, report, auction);

      assertThatThrownBy(() -> qualityReportService.approveReport(admin, report, auction))
          .isInstanceOf(IllegalStateException.class);
    }
  }
  // TC-21 — rejectReport()
  @Nested
  @Order(2)
  @DisplayName("TC-21 [MEDIUM] rejectReport() — Tiền không hoàn, report REJECTED")
  class RejectReportTests {

    @Test
    @Order(1)
    @DisplayName("TC-21a: rejectReport() → report status = REJECTED, seller không bị penalize")
    void rejectReport_reportRejected_sellerNotPenalized() {
      NormalUser winner = givenUserWithBalance("qr_rj_winner1", 20_000_000L);
      NormalUser seller = givenUserWithBalance("qr_rj_seller1", 0L);
      Auction auction =
          givenPaidAuctionWithSeller(seller, winner, 5_000_000L, 7_000_000L, 8_000_000L);

      double sellerRatingBefore = seller.getRating();
      Admin admin = buildAdmin("qr_rj_admin1");

      QualityReport report =
          QualityReport.create(
              winner,
              auction.getId(),
              "Test quality issue",
              java.util.List.of("http://img.test/1.jpg"));
      qualityReportService.submitReport(report);
      trackQualityReport(report.getId());

      qualityReportService.rejectReport(admin, report);

      assertAll(
          "Sau rejectReport()",
          () ->
              assertThat(report.getStatus())
                  .as("Report status phải = REJECTED")
                  .isEqualTo(QualityReport.ReportStatus.REJECTED),
          () ->
              assertThat(seller.getRating())
                  .as("Seller rating không thay đổi khi report bị từ chối")
                  .isEqualTo(sellerRatingBefore));
    }

    @Test
    @Order(2)
    @DisplayName("TC-21b: rejectReport() với report không ở PENDING — IllegalStateException")
    void rejectReport_notPending_throwsIllegalState() {
      NormalUser winner = givenUserWithBalance("qr_rj_winner2", 20_000_000L);
      Auction auction =
          givenPaidAuction("qr_rj_seller2", winner, 5_000_000L, 7_000_000L, 8_000_000L);

      Admin admin = buildAdmin("qr_rj_admin2");
      QualityReport report =
          QualityReport.create(
              winner,
              auction.getId(),
              "Test quality issue",
              java.util.List.of("http://img.test/1.jpg"));
      qualityReportService.submitReport(report);
      trackQualityReport(report.getId());

      qualityReportService.rejectReport(admin, report);

      assertThatThrownBy(() -> qualityReportService.rejectReport(admin, report))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // Helpers (giữ nguyên)
  private NormalUser givenUserWithBalance(String username, long balance) {
    return buildUserWithBalance(username, balance, userDAO);
  }

  private Auction givenPaidAuction(
      String sellerUsername,
      NormalUser winner,
      long startingPrice,
      long bidAmount,
      long reservePrice) {
    NormalUser seller = buildUserWithBalance(sellerUsername, 50_000_000L, userDAO);
    return givenPaidAuctionWithSeller(seller, winner, startingPrice, bidAmount, reservePrice);
  }

  private Auction givenPaidAuctionWithSeller(
      NormalUser seller, NormalUser winner, long startingPrice, long bidAmount, long reservePrice) {
    String itemId =
        buildItem(seller.getId(), "QRItem-" + seller.getUsername(), startingPrice, itemDAO);
    Item item = itemDAO.findItemById(itemId);
    Auction auction =
        Auction.create(
            item,
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusHours(2),
            reservePrice);
    auctionDAO.createAuction(auction);
    auctionService.startAuction(auction);
    trackAuction(auction.getId());

    bidService.joinAuction(winner, auction, new BidderObserver(winner, null));
    bidService.placeBid(winner, auction, bidAmount, new StandardBidStrategy());
    auctionService.closeAuction(auction);

    if (auction.getWinner() != null) {
      paymentService.completePayment(auction);
    }
    return auction;
  }

  private Admin buildAdmin(String username) {
    com.group13.auction.model.user.AdminFactory factory =
        new com.group13.auction.model.user.AdminFactory();
    return (Admin) factory.createUser(username, "admin_pass", username + "@admin.vn");
  }
}
