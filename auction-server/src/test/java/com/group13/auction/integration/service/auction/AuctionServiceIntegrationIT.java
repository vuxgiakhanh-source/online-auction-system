package com.group13.auction.integration.service.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.RatingService;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Integration test AuctionService: lifecycle create/start/close/cancel (DAO + DB thật). */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("AuctionServiceIntegrationIT — AuctionService × AuctionDAO × DB (Bottom-up)")
class AuctionServiceIntegrationIT extends IntegrationTestBase {

  // Raw type — đúng với Testcontainers 2.x trong project này
  @Container
  @SuppressWarnings("resource")
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.0")
          .withDatabaseName("omnibid_test")
          .withUsername("test_user")
          .withPassword("test_pass")
          .withInitScript("database/schema.sql");

  private UserDAO userDAO;
  private ItemDAO itemDAO;
  private AuctionDAO auctionDAO;
  private RatingService ratingService;
  private AuctionService auctionService;

  @BeforeAll
  static void configureDataSource() throws Exception {
    configureTestcontainer(mysql);
  }

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    userDAO = new UserDAO();
    itemDAO = new ItemDAO();
    auctionDAO = new AuctionDAO();
    ratingService = new RatingService(userDAO);
    auctionService = new AuctionService(ratingService, auctionDAO);
    resetTracking();
  }

  @AfterEach
  void tearDown() throws Exception {
    cleanupDB();
    TestFixture.resetSystemAdmin();
  }

  // TC-A1: createAuction()

  @Nested
  @Order(1)
  @DisplayName("TC-A1 [CRITICAL] createAuction() — persist DB, status OPEN")
  class CreateAuctionTests {

    @Test
    @Order(1)
    @DisplayName("TC-A1a: happy path — persist DB, findById trả đúng trạng thái OPEN")
    void createAuction_happyPath_persistedWithOpenStatus() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a1a", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A1a", 500_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().plusMinutes(1),
              LocalDateTime.now().plusHours(2),
              1_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());

      Auction fromDb = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDb).isNotNull();
      assertThat(fromDb.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);
      assertThat(fromDb.getCurrentPrice()).isEqualTo(500_000L);
    }

    @Test
    @Order(2)
    @DisplayName("TC-A1b: hai auction khác nhau — id độc lập, cả hai persist thành công")
    void createAuction_twoAuctions_independentIds() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a1b", 0L, userDAO);
      String itemId1 = buildItem(seller.getId(), "Item A1b-1", 300_000L, itemDAO);
      String itemId2 = buildItem(seller.getId(), "Item A1b-2", 400_000L, itemDAO);
      Item item1 = itemDAO.findItemById(itemId1);
      Item item2 = itemDAO.findItemById(itemId2);

      Auction a1 =
          Auction.create(
              item1,
              LocalDateTime.now().plusMinutes(1),
              LocalDateTime.now().plusHours(1),
              500_000L);
      Auction a2 =
          Auction.create(
              item2,
              LocalDateTime.now().plusMinutes(1),
              LocalDateTime.now().plusHours(2),
              600_000L);
      auctionDAO.createAuction(a1);
      auctionDAO.createAuction(a2);
      trackAuction(a1.getId());
      trackAuction(a2.getId());

      assertThat(a1.getId()).isNotEqualTo(a2.getId());
      assertThat(auctionDAO.findAuctionById(a1.getId())).isNotNull();
      assertThat(auctionDAO.findAuctionById(a2.getId())).isNotNull();
    }
  }

  // TC-A2: startAuction()

  @Nested
  @Order(2)
  @DisplayName("TC-A2 [CRITICAL] startAuction() — OPEN → RUNNING, DB cập nhật")
  class StartAuctionTests {

    @Test
    @Order(1)
    @DisplayName("TC-A2a: startAuction() — RAM và DB đều RUNNING")
    void startAuction_ramAndDbBothRunning() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a2a", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A2a", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              2_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());

      auctionService.startAuction(auction);

      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
      Auction fromDb = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDb.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
    }

    @Test
    @Order(2)
    @DisplayName("TC-A2b: startAuction() không ném exception với phiên hợp lệ")
    void startAuction_validAuction_noException() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a2b", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A2b", 500_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(1),
              1_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());

      assertThatCode(() -> auctionService.startAuction(auction)).doesNotThrowAnyException();
    }
  }

  // TC-A3: closeAuction()

  @Nested
  @Order(3)
  @DisplayName("TC-A3 [HIGH] closeAuction() — RUNNING → final status, DB persist")
  class CloseAuctionTests {

    @Test
    @Order(1)
    @DisplayName("TC-A3a: closeAuction() không có bid — DB status nhất quán với RAM")
    void closeAuction_noBid_dbConsistentWithRam() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a3a", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A3a", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().minusMinutes(1),
              2_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());
      auctionService.startAuction(auction);

      auctionService.closeAuction(auction);

      // DB phải nhất quán với RAM
      Auction fromDb = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDb.getStatus()).isEqualTo(auction.getStatus());
    }

    @Test
    @Order(2)
    @DisplayName("TC-A3b: closeAuction() sau start — DB không còn RUNNING")
    void closeAuction_afterStart_dbNotRunning() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a3b", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A3b", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(10),
              LocalDateTime.now().minusMinutes(1),
              2_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());
      auctionService.startAuction(auction);
      auctionService.closeAuction(auction);

      Auction fromDb = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDb.getStatus()).isNotEqualTo(Auction.AuctionStatus.RUNNING);
    }
  }

  // TC-A4: cancelAuction()

  @Nested
  @Order(4)
  @DisplayName("TC-A4 [HIGH] cancelAuction() — hủy ở OPEN, DB CANCELED")
  class CancelAuctionTests {

    @Test
    @Order(1)
    @DisplayName("TC-A4a: cancelAuction() từ OPEN với FRAUDULENT_ITEM — DB và RAM đều CANCELED")
    void cancelAuction_fromOpen_canceled() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a4a", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A4a", 500_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), 1_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());

      Admin staff =
          Admin.reconstitute(
              UUID.randomUUID().toString(),
              LocalDateTime.now(),
              LocalDateTime.now(),
              "staff_a4a",
              User.hashPassword("pass"),
              "staff_a4a@test.com",
              User.AccountStatus.ACTIVE,
              5.0,
              Admin.LEVEL_STAFF,
              null);
      auctionService.cancelAuction(staff, auction, Admin.CancelReason.FRAUDULENT_ITEM);

      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
      Auction fromDb = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDb.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
    }

    @Test
    @Order(2)
    @DisplayName("TC-A4b: cancelAuction() với SELLER_REQUEST — status CANCELED")
    void cancelAuction_sellerRequest_canceled() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a4b", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Item A4b", 500_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      Auction auction =
          Auction.create(
              item, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), 1_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());

      Admin staff =
          Admin.reconstitute(
              UUID.randomUUID().toString(),
              LocalDateTime.now(),
              LocalDateTime.now(),
              "staff_a4b",
              User.hashPassword("pass"),
              "staff_a4b@test.com",
              User.AccountStatus.ACTIVE,
              5.0,
              Admin.LEVEL_STAFF,
              null);
      auctionService.cancelAuction(staff, auction, Admin.CancelReason.SELLER_REQUEST);

      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
    }
  }

  // TC-A5: Full lifecycle

  @Nested
  @Order(5)
  @DisplayName("TC-A5 [CRITICAL] Full lifecycle — OPEN → RUNNING → final status")
  class FullLifecycleTests {

    @Test
    @Order(1)
    @DisplayName("TC-A5a: OPEN → start → RUNNING → close → final status, DB nhất quán RAM")
    void fullLifecycle_createStartClose_dbConsistent() throws Exception {
      NormalUser seller = buildUserWithBalance("seller_a5a", 0L, userDAO);
      String itemId = buildItem(seller.getId(), "Lifecycle Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);

      // 1. Create → OPEN
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(2),
              LocalDateTime.now().minusMinutes(1),
              3_000_000L);
      auctionDAO.createAuction(auction);
      trackAuction(auction.getId());

      assertThat(auctionDAO.findAuctionById(auction.getId()).getStatus())
          .isEqualTo(Auction.AuctionStatus.OPEN);

      // 2. Start → RUNNING
      auctionService.startAuction(auction);
      assertThat(auctionDAO.findAuctionById(auction.getId()).getStatus())
          .isEqualTo(Auction.AuctionStatus.RUNNING);

      // 3. Close → final
      auctionService.closeAuction(auction);
      Auction afterClose = auctionDAO.findAuctionById(auction.getId());
      // DB == RAM, không còn RUNNING hay OPEN
      assertThat(afterClose.getStatus()).isEqualTo(auction.getStatus());
      assertThat(afterClose.getStatus())
          .isNotIn(Auction.AuctionStatus.RUNNING, Auction.AuctionStatus.OPEN);
    }
  }
}
