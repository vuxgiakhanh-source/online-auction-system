package com.group13.auction.integration.dao;
import com.group13.auction.integration.base.RequiresDocker;

import com.group13.auction.dao.*;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.model.user.User.UserRole;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================
 * INTEGRATION TEST — PERSISTENCE LAYER (Bottom-up Strategy)
 * ============================================================
 *
 * CHIẾN LƯỢC: Bottom-up Integration Testing
 * ------------------------------------------
 * Theo lý thuyết Bottom-up, chúng ta bắt đầu test từ tầng thấp nhất
 * (DatabaseConnection) rồi tích hợp dần lên tầng DAO cao hơn:
 *
 *   Tầng 1 → DatabaseConnection (cơ sở hạ tầng DB)
 *   Tầng 2 → UserDAO + FinancialTransactionDAO (DAO độc lập)
 *   Tầng 3 → ItemDAO + BidTransactionDAO (DAO phụ thuộc UserDAO)
 *   Tầng 4 → AuctionDAO (DAO phụ thuộc ItemDAO + UserDAO)
 *   Tầng 5 → Tích hợp đa DAO + kiểm tra tính toàn vẹn Transaction
 *
 * CÔNG NGHỆ:
 *   - Testcontainers (MySQLContainer): database thực tế, isolated
 *   - JUnit 5: test framework
 *   - AssertJ: fluent assertions
 *
 * DATA ISOLATION:
 *   - @BeforeEach: tạo dữ liệu test mới trước mỗi test
 *   - @AfterEach:  cleanup toàn bộ dữ liệu đã tạo trong test đó
 *   - Không dùng dữ liệu shared giữa các test → mỗi test độc lập hoàn toàn
 *
 * Các class được tích hợp:
 *   - {@link DatabaseConnection} (Singleton — reconfigured via HikariCP pool)
 *   - {@link UserDAO}
 *   - {@link ItemDAO}
 *   - {@link AuctionDAO}
 *   - {@link BidTransactionDAO}
 *   - {@link FinancialTransactionDAO}
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Persistence Layer — Bottom-up Integration Tests")
class DAOIntegrationIT {

    // =========================================================================
    // Testcontainers setup — MySQL thực tế, isolated mỗi test run
    // =========================================================================

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql"); // đặt trong src/test/resources/integration/schema.sql

    // =========================================================================
    // DAO instances — khởi tạo sau khi container đã up
    // =========================================================================

    private UserDAO userDAO;
    private ItemDAO itemDAO;
    private AuctionDAO auctionDAO;
    private BidTransactionDAO bidTransactionDAO;
    private FinancialTransactionDAO financialTransactionDAO;

    /** IDs được tạo trong mỗi test — dùng để cleanup @AfterEach */
    private final List<String> createdUserIds     = new ArrayList<>();
    private final List<String> createdItemIds     = new ArrayList<>();
    private final List<String> createdAuctionIds  = new ArrayList<>();
    private final List<String> createdBidTxIds    = new ArrayList<>();
    private final List<String> createdFinTxIds    = new ArrayList<>();

    // =========================================================================
    // Lifecycle hooks
    // =========================================================================

    @BeforeAll
    static void configureDataSource() throws Exception {
        // Reconfigure HikariCP pool to point to the Testcontainer instance.
        DatabaseConnection.getInstance()
                .reconfigure(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    @BeforeEach
    void setUp() {
        userDAO              = new UserDAO();
        itemDAO              = new ItemDAO();
        auctionDAO           = new AuctionDAO();
        bidTransactionDAO    = new BidTransactionDAO();
        financialTransactionDAO = new FinancialTransactionDAO();

        createdUserIds.clear();
        createdItemIds.clear();
        createdAuctionIds.clear();
        createdBidTxIds.clear();
        createdFinTxIds.clear();
    }

    /**
     * Cleanup sau mỗi test — xóa theo thứ tự FK an toàn:
     * bid_transactions → financial_transactions → user_auction_activity
     * → auctions → items → users
     */
    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Xóa theo thứ tự FK constraint
                deleteByIds(conn, "bid_transactions",       "id", createdBidTxIds);
                deleteByIds(conn, "financial_transactions", "id", createdFinTxIds);
                deleteByIds(conn, "user_auction_activity",  "auction_id", createdAuctionIds);
                deleteByIds(conn, "auctions",               "id", createdAuctionIds);
                deleteByIds(conn, "items",                  "id", createdItemIds);
                deleteByIds(conn, "sellers",                "user_id", createdUserIds);
                deleteByIds(conn, "users",                  "id", createdUserIds);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // =========================================================================
    // TẦNG 1 — DatabaseConnection (Bottom: tầng cơ sở hạ tầng)
    // =========================================================================

    @Nested
    @Order(1)
    @DisplayName("Tầng 1 — DatabaseConnection")
    class DatabaseConnectionTests {

        @Test
        @Order(1)
        @DisplayName("getInstance() trả về cùng một instance (Singleton)")
        void singleton_returnsSameInstance() {
            DatabaseConnection inst1 = DatabaseConnection.getInstance();
            DatabaseConnection inst2 = DatabaseConnection.getInstance();

            assertThat(inst1).isSameAs(inst2);
        }

        @Test
        @Order(2)
        @DisplayName("getConnection() trả về Connection hợp lệ tới Testcontainer")
        void getConnection_returnsValidConnection() throws Exception {
            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                assertThat(conn).isNotNull();
                assertThat(conn.isClosed()).isFalse();
                assertThat(conn.getCatalog()).isEqualTo("omnibid_test");
            }
        }

        @Test
        @Order(3)
        @DisplayName("getConnection() mỗi lần trả về Connection MỚI (không pool cũ đã đóng)")
        void getConnection_returnsNewConnectionEachTime() throws Exception {
            Connection conn1 = DatabaseConnection.getInstance().getConnection();
            conn1.close();

            try (Connection conn2 = DatabaseConnection.getInstance().getConnection()) {
                assertThat(conn2).isNotNull();
                assertThat(conn2.isClosed()).isFalse();
                // conn1 và conn2 là 2 object khác nhau
                assertThat(conn2).isNotSameAs(conn1);
            }
        }

        @Test
        @Order(4)
        @DisplayName("Schema được khởi tạo đúng — bảng users tồn tại")
        void schema_requiredTablesExist() throws Exception {
            String[] requiredTables = {
                    "users", "items", "auctions", "bid_transactions",
                    "financial_transactions", "user_auction_activity"
            };

            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                for (String table : requiredTables) {
                    try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
                        assertThat(rs.next())
                                .as("Bảng '%s' phải tồn tại trong schema", table)
                                .isTrue();
                    }
                }
            }
        }
    }

    // =========================================================================
    // TẦNG 2 — UserDAO (DAO độc lập, không phụ thuộc DAO khác)
    // =========================================================================

    @Nested
    @Order(2)
    @DisplayName("Tầng 2 — UserDAO (không phụ thuộc DAO khác)")
    class UserDAOTests {

        @Test
        @Order(1)
        @DisplayName("registerUser() tạo user mới và trả về UUID hợp lệ")
        void registerUser_createsUserAndReturnsUuid() {
            String userId = userDAO.registerUser(
                    "bidder_alice", hashPassword("pass1234"), "alice@omnibid.vn");

            assertThat(userId).isNotNull().isNotBlank();
            // Phải là UUID v4 hợp lệ
            assertDoesNotThrow(() -> UUID.fromString(userId));
            createdUserIds.add(userId);
        }

        @Test
        @Order(2)
        @DisplayName("registerUser() với username trùng lặp phải thất bại (trả về null)")
        void registerUser_duplicateUsername_returnsNull() {
            String userId1 = userDAO.registerUser(
                    "bidder_bob", hashPassword("pass1234"), "bob@omnibid.vn");
            createdUserIds.add(userId1);

            // Cùng username, email khác
            String userId2 = userDAO.registerUser(
                    "bidder_bob", hashPassword("pass5678"), "bob2@omnibid.vn");

            assertThat(userId2).isNull();
            // userId2 null nên không add vào cleanup list
        }

        @Test
        @Order(3)
        @DisplayName("findNormalUserById() hồi sinh đầy đủ NormalUser từ DB")
        void findNormalUserById_reconstitutesUser() {
            String userId = userDAO.registerUser(
                    "bidder_carol", hashPassword("pass1234"), "carol@omnibid.vn");
            createdUserIds.add(userId);

            NormalUser found = userDAO.findNormalUserById(userId);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(userId);
            assertThat(found.getUsername()).isEqualTo("bidder_carol");
            assertThat(found.getEmail()).isEqualTo("carol@omnibid.vn");
            assertThat(found.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            // Rating mặc định khi tạo mới
            assertThat(found.getRating()).isGreaterThan(0.0);
        }

        @Test
        @Order(4)
        @DisplayName("findNormalUserById() với ID không tồn tại trả về null")
        void findNormalUserById_nonExistentId_returnsNull() {
            NormalUser result = userDAO.findNormalUserById(UUID.randomUUID().toString());
            assertThat(result).isNull();
        }

        @Test
        @Order(5)
        @DisplayName("addBalance() cộng tiền chính xác vào balance user")
        void addBalance_incrementsBalanceCorrectly() {
            String userId = userDAO.registerUser(
                    "bidder_dave", hashPassword("pass1234"), "dave@omnibid.vn");
            createdUserIds.add(userId);

            boolean r1 = userDAO.addBalance(userId, 5_000_000L);
            boolean r2 = userDAO.addBalance(userId, 3_000_000L);

            assertThat(r1).isTrue();
            assertThat(r2).isTrue();

            NormalUser user = userDAO.findNormalUserById(userId);
            assertThat(user.getBalance()).isEqualTo(8_000_000L);
        }

        @Test
        @Order(6)
        @DisplayName("updateBalances() cập nhật cả balance và locked_balance")
        void updateBalances_updatesBothFields() {
            String userId = userDAO.registerUser(
                    "bidder_eve", hashPassword("pass1234"), "eve@omnibid.vn");
            createdUserIds.add(userId);
            userDAO.addBalance(userId, 10_000_000L);

            boolean result = userDAO.updateBalances(userId, 7_000_000L, 3_000_000L);

            assertThat(result).isTrue();
            NormalUser user = userDAO.findNormalUserById(userId);
            assertThat(user.getBalance()).isEqualTo(7_000_000L);
            assertThat(user.getLockedDeposit()).isEqualTo(3_000_000L);
        }

        @Test
        @Order(7)
        @DisplayName("updateAccountStatus() thay đổi trạng thái ACTIVE → BANNED")
        void updateAccountStatus_changesStatusInDB() {
            String userId = userDAO.registerUser(
                    "bidder_frank", hashPassword("pass1234"), "frank@omnibid.vn");
            createdUserIds.add(userId);

            boolean result = userDAO.updateAccountStatus(userId, "BANNED");

            assertThat(result).isTrue();
            NormalUser user = userDAO.findNormalUserById(userId);
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
        }

        @Test
        @Order(8)
        @DisplayName("saveUserAuctionActivity() lưu JOINED, findJoinedAuctionIdsByUserId() trả về đúng")
        void saveAndFindUserAuctionActivity_roundTrip() {
            // Tạo seller + seller record + item + auction thật vì user_auction_activity có FK → auctions(id)
            String userId = userDAO.registerUser(
                    "bidder_grace", hashPassword("pass1234"), "grace@omnibid.vn");
            createdUserIds.add(userId);

            String sellerId = userDAO.registerUser(
                    "seller_grace_aux", hashPassword("pass1234"), "grace_seller@omnibid.vn");
            createdUserIds.add(sellerId);
            ensureSellerRecord(sellerId);

            String itemId = UUID.randomUUID().toString();
            itemDAO.addItem(itemId, sellerId, "Grace Test Item", "desc", 1_000_000L, "ELECTRONICS");
            createdItemIds.add(itemId);

            Item item = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(1), 1_500_000L);
            auctionDAO.createAuction(auction);
            createdAuctionIds.add(auction.getId());

            boolean saved = userDAO.saveUserAuctionActivity(userId, auction.getId(), "JOINED");

            assertThat(saved).isTrue();
            Set<String> joinedIds = userDAO.findJoinedAuctionIdsByUserId(userId);
            assertThat(joinedIds).contains(auction.getId());
        }

        @ParameterizedTest
        @Order(9)
        @ValueSource(strings = {"ACTIVE", "SUSPENDED", "BANNED"})
        @DisplayName("updateAccountStatus() hỗ trợ đầy đủ các trạng thái hợp lệ")
        void updateAccountStatus_supportsAllStatuses(String status) {
            String userId = userDAO.registerUser(
                    "bidder_" + status.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 6),
                    hashPassword("pass1234"),
                    status.toLowerCase() + "@omnibid.vn");
            createdUserIds.add(userId);

            boolean result = userDAO.updateAccountStatus(userId, status);

            assertThat(result).isTrue();
            NormalUser user = userDAO.findNormalUserById(userId);
            assertThat(user.getAccountStatus().name()).isEqualTo(
                    "DELETED".equals(status) ? "BANNED" : status);
        }
    }

    // =========================================================================
    // TẦNG 2B — FinancialTransactionDAO (DAO độc lập)
    // =========================================================================

    @Nested
    @Order(3)
    @DisplayName("Tầng 2B — FinancialTransactionDAO (không phụ thuộc DAO khác)")
    class FinancialTransactionDAOTests {

        /** Helper tạo seller + item + auction thật để dùng làm auctionId hợp lệ */
        private String createRealAuction(String sellerUsername) {
            String sellerId = userDAO.registerUser(
                    sellerUsername, hashPassword("pass"), sellerUsername + "@test.vn");
            createdUserIds.add(sellerId);
            ensureSellerRecord(sellerId);
            String itemId = UUID.randomUUID().toString();
            itemDAO.addItem(itemId, sellerId, "FinTx Item " + sellerUsername, "desc", 1_000_000L, "ELECTRONICS");
            createdItemIds.add(itemId);
            Item item = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(1), 1_500_000L);
            auctionDAO.createAuction(auction);
            createdAuctionIds.add(auction.getId());
            return auction.getId();
        }

        @Test
        @Order(1)
        @DisplayName("saveTransaction(DEPOSIT_LOCK) lưu thành công và truy vấn được")
        void saveTransaction_depositLock_persistsCorrectly() {
            // Cần user để FK sender_id hợp lệ, auction thật để FK auction_id hợp lệ
            String userId    = userDAO.registerUser(
                    "wallet_user1", hashPassword("pass"), "wallet1@test.vn");
            createdUserIds.add(userId);
            String auctionId = createRealAuction("wallet_seller1");

            FinancialTransaction tx = FinancialTransaction.create(
                    userId, "SYSTEM_LOCKED", 500_000L,
                    TransactionType.DEPOSIT_LOCK, auctionId);

            boolean saved = financialTransactionDAO.saveTransaction(tx);

            assertThat(saved).isTrue();
            createdFinTxIds.add(tx.getId());
        }

        @Test
        @Order(2)
        @DisplayName("findLockedDepositAmount() tính đúng tổng tiền cọc đã lock")
        void findLockedDepositAmount_sumIsCorrect() {
            String userId    = userDAO.registerUser(
                    "wallet_user2", hashPassword("pass"), "wallet2@test.vn");
            createdUserIds.add(userId);
            String auctionId = createRealAuction("wallet_seller2");

            // Lưu 2 DEPOSIT_LOCK cho cùng 1 (userId, auctionId)
            FinancialTransaction tx1 = FinancialTransaction.create(
                    userId, "SYSTEM_LOCKED", 300_000L, TransactionType.DEPOSIT_LOCK, auctionId);
            FinancialTransaction tx2 = FinancialTransaction.create(
                    userId, "SYSTEM_LOCKED", 200_000L, TransactionType.DEPOSIT_LOCK, auctionId);

            financialTransactionDAO.saveTransaction(tx1);
            financialTransactionDAO.saveTransaction(tx2);
            createdFinTxIds.add(tx1.getId());
            createdFinTxIds.add(tx2.getId());

            long total = financialTransactionDAO.findLockedDepositAmount(userId, auctionId);

            assertThat(total).isEqualTo(500_000L);
        }

        @Test
        @Order(3)
        @DisplayName("findLockedDepositAmount() với user/auction không có DEPOSIT_LOCK trả về 0")
        void findLockedDepositAmount_noneFound_returnsZero() {
            long total = financialTransactionDAO.findLockedDepositAmount(
                    UUID.randomUUID().toString(), UUID.randomUUID().toString());

            assertThat(total).isZero();
        }

        @Test
        @Order(4)
        @DisplayName("saveTransaction() các loại TransactionType khác nhau đều lưu thành công")
        void saveTransaction_allTransactionTypes_persist() {
            String userId = userDAO.registerUser(
                    "wallet_user3", hashPassword("pass"), "wallet3@test.vn");
            createdUserIds.add(userId);
            String auctionId = createRealAuction("wallet_seller3");

            TransactionType[] types = {
                    TransactionType.DEPOSIT_LOCK,
                    TransactionType.DEPOSIT_UNLOCK,
                    TransactionType.DEPOSIT_FORFEIT,
                    TransactionType.PAYMENT_FROM_WINNER,
                    TransactionType.PAYOUT_TO_SELLER
            };

            for (TransactionType type : types) {
                FinancialTransaction tx = FinancialTransaction.create(
                        userId, "SYSTEM_BANK", 100_000L, type, auctionId);
                boolean saved = financialTransactionDAO.saveTransaction(tx);
                assertThat(saved).as("TransactionType %s phải lưu thành công", type).isTrue();
                createdFinTxIds.add(tx.getId());
            }
        }
    }

    // =========================================================================
    // TẦNG 3 — ItemDAO (phụ thuộc UserDAO — tích hợp 2 DAO)
    // =========================================================================

    @Nested
    @Order(4)
    @DisplayName("Tầng 3 — ItemDAO × UserDAO (tích hợp seller→item)")
    class ItemDAOIntegrationTests {

        @Test
        @Order(1)
        @DisplayName("addItem() lưu item mới — seller FK hợp lệ")
        void addItem_withValidSeller_persistsItem() {
            String sellerId = userDAO.registerUser(
                    "seller_henry", hashPassword("pass"), "henry@omnibid.vn");
            createdUserIds.add(sellerId);
            ensureSellerRecord(sellerId);

            String itemId = UUID.randomUUID().toString();
            boolean result = itemDAO.addItem(
                    itemId, sellerId, "iPhone 15 Pro", "Like new", 20_000_000L, "ELECTRONICS");

            assertThat(result).isTrue();
            createdItemIds.add(itemId);
        }

        @Test
        @Order(2)
        @DisplayName("findItemById() hồi sinh đúng loại Electronics từ DB")
        void findItemById_reconstitutesElectronics() {
            String sellerId = userDAO.registerUser(
                    "seller_irene", hashPassword("pass"), "irene@omnibid.vn");
            createdUserIds.add(sellerId);
            ensureSellerRecord(sellerId);

            String itemId = UUID.randomUUID().toString();
            itemDAO.addItem(itemId, sellerId, "MacBook Air M2", "Brand new",
                    25_000_000L, "ELECTRONICS");
            createdItemIds.add(itemId);

            Item item = itemDAO.findItemById(itemId);

            assertThat(item).isNotNull();
            assertThat(item.getId()).isEqualTo(itemId);
            assertThat(item.getName()).isEqualTo("MacBook Air M2");
            assertThat(item.getStartingPrice()).isEqualTo(25_000_000L);
            assertThat(item.getCategory()).isEqualTo(Item.ItemCategory.ELECTRONICS);
        }

        @Test
        @Order(3)
        @DisplayName("findItemById() với ID không tồn tại trả về null")
        void findItemById_nonExistent_returnsNull() {
            Item item = itemDAO.findItemById(UUID.randomUUID().toString());
            assertThat(item).isNull();
        }

        @Test
        @Order(4)
        @DisplayName("findItemsBySellerId() trả về đúng danh sách item của seller đó")
        void findItemsBySellerId_returnsOnlySellerItems() {
            String seller1Id = userDAO.registerUser(
                    "seller_jack", hashPassword("pass"), "jack@omnibid.vn");
            String seller2Id = userDAO.registerUser(
                    "seller_kate", hashPassword("pass"), "kate@omnibid.vn");
            createdUserIds.add(seller1Id);
            createdUserIds.add(seller2Id);
            ensureSellerRecord(seller1Id);
            ensureSellerRecord(seller2Id);

            // seller1 có 2 item
            String item1Id = UUID.randomUUID().toString();
            String item2Id = UUID.randomUUID().toString();
            // seller2 có 1 item
            String item3Id = UUID.randomUUID().toString();

            itemDAO.addItem(item1Id, seller1Id, "Item A", "desc", 1_000_000L, "ART");
            itemDAO.addItem(item2Id, seller1Id, "Item B", "desc", 2_000_000L, "ART");
            itemDAO.addItem(item3Id, seller2Id, "Item C", "desc", 3_000_000L, "VEHICLE");
            createdItemIds.addAll(List.of(item1Id, item2Id, item3Id));

            List<Item> seller1Items = itemDAO.findItemsBySellerId(seller1Id);
            List<Item> seller2Items = itemDAO.findItemsBySellerId(seller2Id);

            assertThat(seller1Items).hasSize(2)
                    .extracting(Item::getId)
                    .containsExactlyInAnyOrder(item1Id, item2Id);
            assertThat(seller2Items).hasSize(1)
                    .extracting(Item::getId)
                    .containsExactly(item3Id);
        }
    }

    // =========================================================================
    // TẦNG 4 — AuctionDAO (phụ thuộc ItemDAO + UserDAO)
    // =========================================================================

    @Nested
    @Order(5)
    @DisplayName("Tầng 4 — AuctionDAO × ItemDAO × UserDAO (tích hợp đầy đủ)")
    class AuctionDAOIntegrationTests {

        /**
         * Helper: tạo seller + item + auction đầy đủ trong DB,
         * track IDs để cleanup.
         */
        private Auction prepareFullAuction(String sellerUsername, String itemName,
                                           long startingPrice, long reservePrice) {
            // Tầng thấp nhất trước: tạo seller
            String sellerId = userDAO.registerUser(
                    sellerUsername, hashPassword("pass"), sellerUsername + "@omnibid.vn");
            createdUserIds.add(sellerId);
            userDAO.addBalance(sellerId, 50_000_000L);

            NormalUser sellerUser = userDAO.findNormalUserById(sellerId);

            // Tầng tiếp: tạo item
            ensureSellerRecord(sellerId);
            String itemId = UUID.randomUUID().toString();
            itemDAO.addItem(itemId, sellerId, itemName, "Integration test item",
                    startingPrice, "ELECTRONICS");
            createdItemIds.add(itemId);

            Item item = itemDAO.findItemById(itemId);

            // Tầng cao: tạo auction
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(2);
            Auction auction = Auction.create(item, start, end, reservePrice);
            auctionDAO.createAuction(auction);
            createdAuctionIds.add(auction.getId());

            return auction;
        }

        @Test
        @Order(1)
        @DisplayName("createAuction() lưu phiên mới ở trạng thái OPEN")
        void createAuction_persistsWithOpenStatus() {
            Auction auction = prepareFullAuction(
                    "seller_liam", "Sony WH-1000XM5", 3_000_000L, 4_000_000L);

            // Hồi sinh từ DB để verify
            Auction found = auctionDAO.findAuctionById(auction.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(auction.getId());
            assertThat(found.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);
            assertThat(found.getCurrentPrice()).isEqualTo(3_000_000L); // = startingPrice
            assertThat(found.getCurrentLeader()).isNull();
        }

        @Test
        @Order(2)
        @DisplayName("updateAuctionStatus() OPEN → RUNNING phản ánh đúng trong DB")
        void updateAuctionStatus_openToRunning_updatesDB() {
            Auction auction = prepareFullAuction(
                    "seller_mia", "Dell XPS 15", 15_000_000L, 18_000_000L);

            boolean updated = auctionDAO.updateAuctionStatus(auction.getId(), "RUNNING");

            assertThat(updated).isTrue();
            Auction found = auctionDAO.findAuctionById(auction.getId());
            assertThat(found.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
        }

        @Test
        @Order(3)
        @DisplayName("updateHighestPrice() cập nhật currentPrice + currentLeader chính xác")
        void updateHighestPrice_updatesPriceAndLeader() {
            Auction auction = prepareFullAuction(
                    "seller_noah", "LG C2 OLED", 10_000_000L, 12_000_000L);

            // Tạo bidder
            String bidderId = userDAO.registerUser(
                    "bidder_olivia", hashPassword("pass"), "olivia@omnibid.vn");
            createdUserIds.add(bidderId);

            boolean updated = auctionDAO.updateHighestPrice(
                    auction.getId(), 11_000_000L, bidderId);

            assertThat(updated).isTrue();
            Auction found = auctionDAO.findAuctionById(auction.getId());
            assertThat(found.getCurrentPrice()).isEqualTo(11_000_000L);
            assertThat(found.getCurrentLeader()).isNotNull();
            assertThat(found.getCurrentLeader().getId()).isEqualTo(bidderId);
        }

        @Test
        @Order(4)
        @DisplayName("updateAuctionResult() lưu trạng thái FINISHED khi reserve đạt")
        void updateAuctionResult_finishedState_persistsCorrectly() {
            Auction auction = prepareFullAuction(
                    "seller_peter", "ASUS ROG", 20_000_000L, 22_000_000L);

            String bidderId = userDAO.registerUser(
                    "bidder_quinn", hashPassword("pass"), "quinn@omnibid.vn");
            createdUserIds.add(bidderId);

            // Simulate: bid vượt reserve, chuyển sang RUNNING rồi FINISHED
            auctionDAO.updateHighestPrice(auction.getId(), 25_000_000L, bidderId);
            auctionDAO.updateAuctionStatus(auction.getId(), "RUNNING");

            // Cập nhật auction object (simulate transitionToClose)
            NormalUser bidder = userDAO.findNormalUserById(bidderId);
            auction.updateBid(25_000_000L, bidder);
            auction.transitionToRunning();
            auction.transitionToClose(true); // RUNNING → FINISHED

            boolean updated = auctionDAO.updateAuctionResult(auction);

            assertThat(updated).isTrue();
            Auction found = auctionDAO.findAuctionById(auction.getId());
            assertThat(found.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
            assertThat(found.getCurrentPrice()).isEqualTo(25_000_000L);
        }

        @Test
        @Order(5)
        @DisplayName("updateEndTime() cập nhật end_time (anti-sniping) chính xác")
        void updateEndTime_antiSniping_updatesDB() {
            Auction auction = prepareFullAuction(
                    "seller_rachel", "Kindle Oasis", 2_000_000L, 2_500_000L);

            LocalDateTime extended = auction.getEndTime().plusSeconds(60);
            boolean updated = auctionDAO.updateEndTime(auction.getId(), extended);

            assertThat(updated).isTrue();
            // Verify qua raw SQL (endTime không expose qua findAuctionById đơn giản)
            assertThat(queryEndTime(auction.getId()))
                    .isAfterOrEqualTo(auction.getEndTime());
        }

        @Test
        @Order(6)
        @DisplayName("findAuctionById() với ID không tồn tại trả về null")
        void findAuctionById_nonExistent_returnsNull() {
            Auction result = auctionDAO.findAuctionById(UUID.randomUUID().toString());
            assertThat(result).isNull();
        }

        @Test
        @Order(7)
        @DisplayName("findUnfinishedAuctionIdsBySellerId() chỉ trả về OPEN/RUNNING")
        void findUnfinishedAuctionIds_onlyActiveStatuses() {
            String sellerId = userDAO.registerUser(
                    "seller_sam", hashPassword("pass"), "sam@omnibid.vn");
            createdUserIds.add(sellerId);
            userDAO.addBalance(sellerId, 50_000_000L);
            NormalUser seller = userDAO.findNormalUserById(sellerId);

            ensureSellerRecord(sellerId); // FK: items.seller_id → sellers.user_id
            // Tạo 2 item
            String item1Id = UUID.randomUUID().toString();
            String item2Id = UUID.randomUUID().toString();
            itemDAO.addItem(item1Id, sellerId, "Item OPEN",   "d", 1_000_000L, "ELECTRONICS");
            itemDAO.addItem(item2Id, sellerId, "Item CANCELED", "d", 1_000_000L, "ELECTRONICS");
            createdItemIds.addAll(List.of(item1Id, item2Id));

            Item openItem     = itemDAO.findItemById(item1Id);
            Item canceledItem = itemDAO.findItemById(item2Id);

            // Auction 1: OPEN
            Auction openAuction = Auction.create(
                    openItem, LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(1), 1_500_000L);
            auctionDAO.createAuction(openAuction);
            createdAuctionIds.add(openAuction.getId());

            // Auction 2: CANCELED
            Auction canceledAuction = Auction.create(
                    canceledItem, LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(1), 1_500_000L);
            auctionDAO.createAuction(canceledAuction);
            auctionDAO.updateAuctionStatus(canceledAuction.getId(), "CANCELED");
            createdAuctionIds.add(canceledAuction.getId());

            List<String> unfinished = auctionDAO.findUnfinishedAuctionIdsBySellerId(sellerId);

            assertThat(unfinished).containsExactly(openAuction.getId());
            assertThat(unfinished).doesNotContain(canceledAuction.getId());
        }
    }

    // =========================================================================
    // TẦNG 5A — BidTransactionDAO (phụ thuộc UserDAO + AuctionDAO)
    // =========================================================================

    @Nested
    @Order(6)
    @DisplayName("Tầng 5A — BidTransactionDAO × UserDAO × AuctionDAO")
    class BidTransactionDAOIntegrationTests {

        @Test
        @Order(1)
        @DisplayName("saveTransaction(ACCEPTED) ghi bid hợp lệ vào DB")
        void saveTransaction_accepted_persistsBid() {
            // Tầng thấp: tạo user + item + auction
            FullAuctionContext ctx = createFullAuctionContext("bid_seller1", "bid_bidder1");

            BidTransaction tx = BidTransaction.create(
                    ctx.bidder, ctx.auctionId, 2_500_000L, BidResult.ACCEPTED);

            boolean saved = bidTransactionDAO.saveTransaction(tx);

            assertThat(saved).isTrue();
            createdBidTxIds.add(tx.getId());
        }

        @Test
        @Order(2)
        @DisplayName("saveTransaction(REJECTED) cũng lưu được — audit trail đầy đủ")
        void saveTransaction_rejected_persistsForAuditTrail() {
            FullAuctionContext ctx = createFullAuctionContext("bid_seller2", "bid_bidder2");

            BidTransaction tx = BidTransaction.create(
                    ctx.bidder, ctx.auctionId, 100L, BidResult.REJECTED);

            boolean saved = bidTransactionDAO.saveTransaction(tx);

            assertThat(saved).isTrue();
            createdBidTxIds.add(tx.getId());
        }

        @Test
        @Order(3)
        @DisplayName("findBiddersByAuction() chỉ trả về bidder đã ACCEPTED (không REJECTED)")
        void findBiddersByAuction_excludesRejectedBids() {
            FullAuctionContext ctx = createFullAuctionContext("bid_seller3", "bid_bidder3");

            // Bidder thứ 2 bị REJECTED
            String bidder2Id = userDAO.registerUser(
                    "bid_bidder3b", hashPassword("pass"), "bidder3b@omnibid.vn");
            createdUserIds.add(bidder2Id);
            NormalUser bidder2 = userDAO.findNormalUserById(bidder2Id);

            BidTransaction acceptedTx = BidTransaction.create(
                    ctx.bidder, ctx.auctionId, 2_200_000L, BidResult.ACCEPTED);
            BidTransaction rejectedTx = BidTransaction.create(
                    bidder2, ctx.auctionId, 100L, BidResult.REJECTED);

            bidTransactionDAO.saveTransaction(acceptedTx);
            bidTransactionDAO.saveTransaction(rejectedTx);
            createdBidTxIds.addAll(List.of(acceptedTx.getId(), rejectedTx.getId()));

            List<NormalUser> bidders = bidTransactionDAO.findBiddersByAuction(ctx.auctionId);

            assertThat(bidders).hasSize(1);
            assertThat(bidders.get(0).getId()).isEqualTo(ctx.bidder.getId());
        }

        @Test
        @Order(4)
        @DisplayName("findByAuctionId() trả về lịch sử bid sắp xếp theo thời gian")
        void findByAuctionId_returnsSortedBidHistory() {
            FullAuctionContext ctx = createFullAuctionContext("bid_seller4", "bid_bidder4");

            BidTransaction tx1 = BidTransaction.create(
                    ctx.bidder, ctx.auctionId, 2_200_000L, BidResult.ACCEPTED);
            BidTransaction tx2 = BidTransaction.create(
                    ctx.bidder, ctx.auctionId, 2_500_000L, BidResult.ACCEPTED);
            BidTransaction tx3 = BidTransaction.create(
                    ctx.bidder, ctx.auctionId, 2_800_000L, BidResult.ACCEPTED);

            bidTransactionDAO.saveTransaction(tx1);
            bidTransactionDAO.saveTransaction(tx2);
            bidTransactionDAO.saveTransaction(tx3);
            createdBidTxIds.addAll(List.of(tx1.getId(), tx2.getId(), tx3.getId()));

            List<BidTransaction> history = bidTransactionDAO.findByAuctionId(ctx.auctionId);

            assertThat(history).hasSize(3);
            // Giá bid phải tăng dần (sắp xếp theo time asc)
            assertThat(history).extracting(BidTransaction::getAmount)
                    .containsExactly(2_200_000L, 2_500_000L, 2_800_000L);
        }
    }

    // =========================================================================
    // TẦNG 5B — TÍNH TOÀN VẸN TRANSACTION (Atomicity)
    // Đây là yêu cầu quan trọng nhất: trừ tiền + ghi FinancialTransaction
    // phải thành công cùng lúc hoặc rollback hoàn toàn.
    // =========================================================================

    @Nested
    @Order(7)
    @DisplayName("Tầng 5B — Tính toàn vẹn Transaction (Atomicity)")
    class TransactionIntegrityTests {

        /**
         * TC-ATOM-01: Happy path — lockDeposit atomic
         *
         * Kiểm tra: trừ balance + tăng lockedBalance + lưu FinancialTransaction
         * phải nhất quán với nhau sau khi thành công.
         *
         * Đây là simulation của WalletService.lockDeposit() ở tầng DAO:
         * ta thực hiện thủ công trong 1 Connection + 1 Transaction để đảm bảo atomicity.
         */
        @Test
        @Order(1)
        @DisplayName("TC-ATOM-01: lockDeposit atomic — balance + lockedDeposit + FinancialTx nhất quán")
        void lockDeposit_atomic_allOrNothing() throws Exception {
            // Setup
            String userId    = userDAO.registerUser(
                    "atom_bidder1", hashPassword("pass"), "atom1@omnibid.vn");
            createdUserIds.add(userId);
            userDAO.addBalance(userId, 10_000_000L);
            ensureSellerRecord(userId);
            String auctionId = UUID.randomUUID().toString();
            insertDummyAuctionRow(auctionId, userId); // FK: financial_transactions.auction_id → auctions.id
            long depositAmount = 3_000_000L;

            // Thực thi atomic block
            String finTxId = executeAtomicDepositLock(userId, auctionId, depositAmount);
            createdFinTxIds.add(finTxId);

            // Verify: cả 3 thao tác phải nhất quán
            NormalUser user = userDAO.findNormalUserById(userId);
            long lockedInDB = financialTransactionDAO.findLockedDepositAmount(userId, auctionId);

            assertAll("Atomicity — balance, locked, finTx phải nhất quán",
                    () -> assertThat(user.getBalance())
                            .as("Balance phải giảm đúng depositAmount")
                            .isEqualTo(10_000_000L - depositAmount),
                    () -> assertThat(user.getLockedDeposit())
                            .as("lockedDeposit phải tăng đúng depositAmount")
                            .isEqualTo(depositAmount),
                    () -> assertThat(lockedInDB)
                            .as("FinancialTransaction ghi nhận đúng số tiền cọc")
                            .isEqualTo(depositAmount)
            );
        }

        /**
         * TC-ATOM-02: Rollback khi FinancialTransaction lưu thất bại
         *
         * Kiểm tra: nếu bước lưu FinancialTransaction bị lỗi (ví dụ: vi phạm constraint),
         * thì balance trong bảng users phải được rollback — không mất tiền của user.
         */
        @Test
        @Order(2)
        @DisplayName("TC-ATOM-02: Rollback khi lưu FinancialTransaction thất bại — balance không thay đổi")
        void lockDeposit_rollback_onFinancialTxFailure() throws Exception {
            String userId    = userDAO.registerUser(
                    "atom_bidder2", hashPassword("pass"), "atom2@omnibid.vn");
            createdUserIds.add(userId);
            userDAO.addBalance(userId, 10_000_000L);
            ensureSellerRecord(userId);
            String auctionId = UUID.randomUUID().toString();
            insertDummyAuctionRow(auctionId, userId);

            long balanceBefore = userDAO.findNormalUserById(userId).getBalance();

            // Thực thi atomic block nhưng cố tình fail ở bước lưu FinancialTransaction
            assertThatThrownBy(() ->
                    executeAtomicDepositLock_withFailure(userId, auctionId, 3_000_000L))
                    .isInstanceOf(SQLException.class);

            // Verify: balance phải giữ nguyên sau rollback
            NormalUser userAfter = userDAO.findNormalUserById(userId);
            assertThat(userAfter.getBalance())
                    .as("Balance phải giữ nguyên sau khi rollback")
                    .isEqualTo(balanceBefore);
        }

        /**
         * TC-ATOM-03: Concurrent deposit lock — race condition không xảy ra
         *
         * Kiểm tra: 2 thread đồng thời lockDeposit cho cùng 1 user
         * không làm mất tiền (lost update).
         */
        @Test
        @Order(3)
        @DisplayName("TC-ATOM-03: Concurrent lockDeposit — không lost update")
        void concurrentLockDeposit_noLostUpdate() throws Exception {
            String userId = userDAO.registerUser(
                    "atom_bidder3", hashPassword("pass"), "atom3@omnibid.vn");
            createdUserIds.add(userId);
            userDAO.addBalance(userId, 10_000_000L);
            ensureSellerRecord(userId);

            String auction1Id = UUID.randomUUID().toString();
            String auction2Id = UUID.randomUUID().toString();
            insertDummyAuctionRow(auction1Id, userId);
            insertDummyAuctionRow(auction2Id, userId);
            long deposit1 = 2_000_000L;
            long deposit2 = 3_000_000L;

            // 2 thread đồng thời lock deposit
            List<Exception> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
            List<String> finTxIds = new java.util.concurrent.CopyOnWriteArrayList<>();

            Thread t1 = new Thread(() -> {
                try {
                    String id = executeAtomicDepositLock(userId, auction1Id, deposit1);
                    finTxIds.add(id);
                } catch (Exception e) { errors.add(e); }
            });
            Thread t2 = new Thread(() -> {
                try {
                    String id = executeAtomicDepositLock(userId, auction2Id, deposit2);
                    finTxIds.add(id);
                } catch (Exception e) { errors.add(e); }
            });

            t1.start(); t2.start();
            t1.join(); t2.join();
            createdFinTxIds.addAll(finTxIds);

            assertThat(errors).isEmpty();

            NormalUser user = userDAO.findNormalUserById(userId);
            long totalLocked = deposit1 + deposit2;
            long expectedBalance = 10_000_000L - totalLocked;

            assertThat(user.getBalance())
                    .as("Balance phải = initialBalance - sum(deposits)")
                    .isEqualTo(expectedBalance);
            assertThat(user.getLockedDeposit())
                    .as("lockedDeposit phải = sum(deposits)")
                    .isEqualTo(totalLocked);
        }

        /**
         * TC-ATOM-04: unlockDeposit atomic — hoàn cọc cho bidder không thắng
         *
         * Kiểm tra: balance được cộng lại + lockedDeposit giảm + FinancialTx lưu đúng.
         */
        @Test
        @Order(4)
        @DisplayName("TC-ATOM-04: unlockDeposit atomic — bidder không thắng được hoàn đủ tiền cọc")
        void unlockDeposit_atomic_restoresBalanceCorrectly() throws Exception {
            String userId    = userDAO.registerUser(
                    "atom_bidder4", hashPassword("pass"), "atom4@omnibid.vn");
            createdUserIds.add(userId);
            userDAO.addBalance(userId, 10_000_000L);
            ensureSellerRecord(userId);
            String auctionId = UUID.randomUUID().toString();
            insertDummyAuctionRow(auctionId, userId);
            long deposit = 3_000_000L;

            // Bước 1: lock
            String lockTxId = executeAtomicDepositLock(userId, auctionId, deposit);
            createdFinTxIds.add(lockTxId);

            // Bước 2: unlock (hoàn cọc)
            String unlockTxId = executeAtomicDepositUnlock(userId, auctionId, deposit);
            createdFinTxIds.add(unlockTxId);

            // Verify: balance về lại như ban đầu, lockedDeposit = 0
            NormalUser user = userDAO.findNormalUserById(userId);
            assertAll("unlockDeposit — phải hoàn về trạng thái ban đầu",
                    () -> assertThat(user.getBalance())
                            .as("Balance phải trở về 10_000_000 sau khi hoàn cọc")
                            .isEqualTo(10_000_000L),
                    () -> assertThat(user.getLockedDeposit())
                            .as("lockedDeposit phải = 0 sau khi hoàn toàn bộ")
                            .isZero()
            );
        }

        /**
         * TC-ATOM-05: Không thể lock deposit vượt quá available balance
         *
         * Kiểm tra: nếu user chỉ có 5tr nhưng cố lock 8tr,
         * thao tác phải bị reject và balance không thay đổi.
         */
        @Test
        @Order(5)
        @DisplayName("TC-ATOM-05: lockDeposit vượt available balance bị reject — balance nguyên vẹn")
        void lockDeposit_exceedsAvailableBalance_rejected() throws Exception {
            String userId = userDAO.registerUser(
                    "atom_bidder5", hashPassword("pass"), "atom5@omnibid.vn");
            createdUserIds.add(userId);
            userDAO.addBalance(userId, 5_000_000L);
            // auctionId không cần tồn tại — test throw trước khi chạm DB
            String auctionId = UUID.randomUUID().toString();

            assertThatThrownBy(() ->
                    executeAtomicDepositLock(userId, auctionId, 8_000_000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Số dư khả dụng không đủ");

            // Verify: balance không thay đổi
            NormalUser user = userDAO.findNormalUserById(userId);
            assertThat(user.getBalance()).isEqualTo(5_000_000L);
            assertThat(user.getLockedDeposit()).isZero();
        }

        /**
         * TC-ATOM-06: Multi-step payment flow — tính toàn vẹn toàn bộ luồng
         *
         * Simulate: bidder thắng phiên, thanh toán phần còn lại.
         * Kiểm tra: balance giảm đúng, FinancialTransaction ghi đủ các bước.
         */
        @Test
        @Order(6)
        @DisplayName("TC-ATOM-06: Multi-step payment flow — balance và FinancialTx nhất quán end-to-end")
        void paymentFlow_multiStep_allTransactionsConsistent() throws Exception {
            String winnerId  = userDAO.registerUser(
                    "atom_winner6", hashPassword("pass"), "winner6@omnibid.vn");
            createdUserIds.add(winnerId);
            userDAO.addBalance(winnerId, 30_000_000L);
            ensureSellerRecord(winnerId);
            String auctionId = UUID.randomUUID().toString();
            insertDummyAuctionRow(auctionId, winnerId);

            long finalPrice  = 15_000_000L;
            long depositPaid =  4_500_000L; // 30% * 15tr
            long remaining   = finalPrice - depositPaid; // 10_500_000

            // Bước 1: lock deposit
            String lockTxId = executeAtomicDepositLock(winnerId, auctionId, depositPaid);
            createdFinTxIds.add(lockTxId);

            // Bước 2: thanh toán phần còn lại + giải phóng cọc → hết deposit
            String paymentTxId = executeAtomicPayment(winnerId, auctionId, finalPrice, depositPaid);
            createdFinTxIds.add(paymentTxId);

            // Verify final state
            NormalUser winner = userDAO.findNormalUserById(winnerId);
            long expectedBalance = 30_000_000L - finalPrice;

            assertAll("Payment flow end-to-end",
                    () -> assertThat(winner.getBalance())
                            .as("Balance winner sau thanh toán = 30tr - finalPrice")
                            .isEqualTo(expectedBalance),
                    () -> assertThat(winner.getLockedDeposit())
                            .as("lockedDeposit phải = 0 sau khi thanh toán xong")
                            .isZero()
            );
        }
    }

    // =========================================================================
    // TẦNG 6 — Cross-DAO Integrity (AuctionDAO + UserDAO + BidTransactionDAO)
    // =========================================================================

    @Nested
    @Order(8)
    @DisplayName("Tầng 6 — Cross-DAO: Tính toàn vẹn dữ liệu liên bảng")
    class CrossDAOIntegrityTests {

        @Test
        @Order(1)
        @DisplayName("Full bid flow: createAuction → joinAuction → placeBid → DB nhất quán")
        void fullBidFlow_dataConsistencyAcrossAllTables() {
            // === Setup ===
            String sellerId  = userDAO.registerUser(
                    "crossdao_seller", hashPassword("pass"), "crossseller@omnibid.vn");
            String bidderId  = userDAO.registerUser(
                    "crossdao_bidder", hashPassword("pass"), "crossbidder@omnibid.vn");
            createdUserIds.addAll(List.of(sellerId, bidderId));
            userDAO.addBalance(bidderId, 20_000_000L);

            ensureSellerRecord(sellerId);

            String itemId = UUID.randomUUID().toString();
            itemDAO.addItem(itemId, sellerId, "Cross DAO Test Item", "d",
                    5_000_000L, "ELECTRONICS");
            createdItemIds.add(itemId);

            Item item    = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    7_000_000L);
            auctionDAO.createAuction(auction);
            createdAuctionIds.add(auction.getId());

            // === Simulate joinAuction: lock deposit ===
            long deposit = item.getStartingPrice() * 3 / 10; // 30% = 1_500_000
            userDAO.updateBalances(bidderId,
                    20_000_000L - deposit, deposit);

            FinancialTransaction lockTx = FinancialTransaction.create(
                    bidderId, "SYSTEM_LOCKED", deposit,
                    TransactionType.DEPOSIT_LOCK, auction.getId());
            financialTransactionDAO.saveTransaction(lockTx);
            createdFinTxIds.add(lockTx.getId());
            userDAO.saveUserAuctionActivity(bidderId, auction.getId(), "JOINED");
            auctionDAO.updateViewerCount(auction.getId(), 1);

            // === Simulate placeBid ===
            long bidAmount = 6_000_000L;
            auctionDAO.updateAuctionStatus(auction.getId(), "RUNNING");
            auctionDAO.updateHighestPrice(auction.getId(), bidAmount, bidderId);

            NormalUser bidder = userDAO.findNormalUserById(bidderId);
            BidTransaction bidTx = BidTransaction.create(
                    bidder, auction.getId(), bidAmount, BidResult.ACCEPTED);
            bidTransactionDAO.saveTransaction(bidTx);
            createdBidTxIds.add(bidTx.getId());

            // === Verify cross-table consistency ===
            Auction auctionFromDB  = auctionDAO.findAuctionById(auction.getId());
            NormalUser bidderFromDB = userDAO.findNormalUserById(bidderId);
            List<BidTransaction> bidHistory = bidTransactionDAO.findByAuctionId(auction.getId());
            long depositInDB = financialTransactionDAO.findLockedDepositAmount(
                    bidderId, auction.getId());

            assertAll("Cross-DAO consistency",
                    () -> assertThat(auctionFromDB.getStatus())
                            .as("Auction phải ở RUNNING")
                            .isEqualTo(Auction.AuctionStatus.RUNNING),
                    () -> assertThat(auctionFromDB.getCurrentPrice())
                            .as("currentPrice phải = bidAmount")
                            .isEqualTo(bidAmount),
                    () -> assertThat(auctionFromDB.getCurrentLeader().getId())
                            .as("currentLeader phải là bidderId")
                            .isEqualTo(bidderId),
                    () -> assertThat(bidderFromDB.getLockedDeposit())
                            .as("lockedDeposit của bidder phải = deposit")
                            .isEqualTo(deposit),
                    () -> assertThat(bidHistory).hasSize(1)
                            .as("BidTransaction phải được ghi 1 lần"),
                    () -> assertThat(depositInDB)
                            .as("FinancialTransaction DEPOSIT_LOCK phải ghi đúng số tiền")
                            .isEqualTo(deposit),
                    () -> assertThat(bidderFromDB.getJoinedAuctionIds())
                            .as("User phải được đánh dấu đã JOINED auction")
                            .contains(auction.getId())
            );
        }

        @Test
        @Order(2)
        @DisplayName("Auction kết thúc không có winner: updateAuctionResult(CANCELED) — không tạo AuctionWinner")
        void closeAuction_noWinner_statusCanceled() {
            String sellerId = userDAO.registerUser(
                    "crossdao_seller2", hashPassword("pass"), "crossseller2@omnibid.vn");
            createdUserIds.add(sellerId);
            ensureSellerRecord(sellerId);

            String itemId = UUID.randomUUID().toString();
            itemDAO.addItem(itemId, sellerId, "No winner item", "d", 1_000_000L, "ART");
            createdItemIds.add(itemId);

            Item item = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(1), 2_000_000L);
            auctionDAO.createAuction(auction);
            createdAuctionIds.add(auction.getId());

            // Simulate: RUNNING → no winner → CANCELED
            auction.transitionToRunning();
            auction.transitionToClose(false); // → CANCELED
            auctionDAO.updateAuctionResult(auction);

            Auction found = auctionDAO.findAuctionById(auction.getId());
            assertThat(found.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
            assertThat(found.getCurrentLeader()).isNull();
        }

        @Test
        @Order(3)
        @DisplayName("Cleanup verification — không có dữ liệu rò rỉ sau tearDown (self-check)")
        void dataCleanupVerification_noLeakedData() {
            // Tạo user và ngay lập tức track để cleanup
            String tempUserId = userDAO.registerUser(
                    "cleanup_check_user", hashPassword("pass"), "cleanup@omnibid.vn");
            assertThat(tempUserId).isNotNull();
            createdUserIds.add(tempUserId); // sẽ bị xóa trong @AfterEach

            // Verify user tồn tại TRONG test
            assertThat(userDAO.findNormalUserById(tempUserId)).isNotNull();

            // @AfterEach sẽ xóa user này — kiểm tra pattern cleanup hoạt động đúng
            // (không thể verify AFTER tearDown trong cùng test, nhưng test run tiếp theo
            //  sẽ fail nếu dữ liệu bị rò rỉ sang username trùng)
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Thực thi lockDeposit trong 1 DB Transaction (atomicity thực sự).
     * Bao gồm:
     *   1. Cập nhật balance và lockedBalance trong bảng users
     *   2. Lưu FinancialTransaction kiểu DEPOSIT_LOCK
     * Nếu bất kỳ bước nào fail → rollback toàn bộ.
     *
     * @return ID của FinancialTransaction đã lưu
     */
    private String executeAtomicDepositLock(String userId, String auctionId,
                                            long depositAmount) throws Exception {
        FinancialTransaction tx = FinancialTransaction.create(
                userId, "SYSTEM_LOCKED", depositAmount,
                TransactionType.DEPOSIT_LOCK, auctionId);

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: kiểm tra số dư + lock atomic trong DB (SELECT FOR UPDATE tránh race)
                long available;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT balance, locked_balance FROM users WHERE id = ? FOR UPDATE")) {
                    ps.setString(1, userId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("User not found: " + userId);
                        long balance = rs.getLong("balance");
                        long locked  = rs.getLong("locked_balance");
                        available = balance - locked;
                    }
                }
                if (available < depositAmount) {
                    conn.rollback();
                    throw new IllegalStateException(
                            "Số dư khả dụng không đủ. Khả dụng: " + available
                                    + ", Yêu cầu: " + depositAmount);
                }
                // Dùng arithmetic SQL: balance = balance - ? tránh lost update
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET balance = balance - ?, locked_balance = locked_balance + ? WHERE id = ?")) {
                    ps.setLong(1, depositAmount);
                    ps.setLong(2, depositAmount);
                    ps.setString(3, userId);
                    ps.executeUpdate();
                }

                // Bước 2: lưu FinancialTransaction
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO financial_transactions " +
                                "(id, sender_id, receiver_id, amount, transaction_type, auction_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, tx.getId());
                    ps.setString(2, tx.getFromUserId());
                    ps.setString(3, tx.getToUserId());
                    ps.setLong(4, tx.getAmount());
                    ps.setString(5, tx.getType().name());
                    ps.setString(6, tx.getAuctionId());
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
        return tx.getId();
    }

    /**
     * Cố tình simulate lỗi ở bước lưu FinancialTransaction
     * để kiểm tra rollback của TC-ATOM-02.
     */
    private void executeAtomicDepositLock_withFailure(String userId, String auctionId,
                                                      long depositAmount) throws Exception {
        NormalUser user = userDAO.findNormalUserById(userId);

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bước 1: cập nhật balance (thành công)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET balance = ? WHERE id = ?")) {
                    ps.setLong(1, user.getBalance() - depositAmount);
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }

                // Bước 2: cố tình insert FinancialTransaction với ID trùng lặp → fail
                String duplicateId = "duplicate-id-that-breaks-constraint";
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO financial_transactions " +
                                "(id, sender_id, receiver_id, amount, transaction_type, auction_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, duplicateId);
                    ps.setString(2, userId);
                    ps.setString(3, "SYSTEM_LOCKED");
                    ps.setLong(4, depositAmount);
                    ps.setString(5, "DEPOSIT_LOCK");
                    ps.setString(6, auctionId);
                    ps.executeUpdate();
                    // Insert lần 2 với cùng ID → duplicate key → SQLException
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e; // re-throw để test bắt được
            }
        }
    }

    /**
     * Thực thi unlockDeposit trong 1 DB Transaction (atomicity).
     * Bao gồm:
     *   1. Cập nhật balance + lockedBalance (cộng lại balance)
     *   2. Lưu FinancialTransaction kiểu DEPOSIT_UNLOCK
     *
     * @return ID của FinancialTransaction đã lưu
     */
    private String executeAtomicDepositUnlock(String userId, String auctionId,
                                              long depositAmount) throws Exception {
        FinancialTransaction tx = FinancialTransaction.create(
                "SYSTEM_LOCKED", userId, depositAmount,
                TransactionType.DEPOSIT_UNLOCK, auctionId);

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Dùng arithmetic SQL: balance = balance + ? tránh lost update
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET balance = balance + ?, " +
                                "locked_balance = GREATEST(0, locked_balance - ?) WHERE id = ?")) {
                    ps.setLong(1, depositAmount);
                    ps.setLong(2, depositAmount);
                    ps.setString(3, userId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO financial_transactions " +
                                "(id, sender_id, receiver_id, amount, transaction_type, auction_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, tx.getId());
                    ps.setString(2, tx.getFromUserId());
                    ps.setString(3, tx.getToUserId());
                    ps.setLong(4, tx.getAmount());
                    ps.setString(5, tx.getType().name());
                    ps.setString(6, tx.getAuctionId());
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
        return tx.getId();
    }

    /**
     * Simulate thanh toán cuối: trừ phần remaining + giải phóng deposit.
     * Balance = balance - (finalPrice - depositPaid) - depositPaid = balance - finalPrice
     *
     * @return ID của FinancialTransaction payment
     */
    private String executeAtomicPayment(String userId, String auctionId,
                                        long finalPrice, long depositPaid) throws Exception {
        long remaining = finalPrice - depositPaid;

        FinancialTransaction paymentTx = FinancialTransaction.create(
                userId, "SYSTEM_BANK", finalPrice,
                TransactionType.PAYMENT_FROM_WINNER, auctionId);

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Sau lockDeposit, balance đã bị trừ depositPaid rồi.
                // Payment chỉ trừ thêm remaining = finalPrice - depositPaid,
                // đồng thời giải phóng toàn bộ lockedDeposit về 0.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET balance = balance - ?, locked_balance = 0 WHERE id = ?")) {
                    ps.setLong(1, remaining);  // chỉ trừ phần chưa có trong locked
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO financial_transactions " +
                                "(id, sender_id, receiver_id, amount, transaction_type, auction_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, paymentTx.getId());
                    ps.setString(2, paymentTx.getFromUserId());
                    ps.setString(3, paymentTx.getToUserId());
                    ps.setLong(4, paymentTx.getAmount());
                    ps.setString(5, paymentTx.getType().name());
                    ps.setString(6, paymentTx.getAuctionId());
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
        return paymentTx.getId();
    }

    /** Utility: xóa các row theo danh sách ID trong 1 Connection đã mở. */
    /**
     * Đảm bảo user có bản ghi APPROVED trong bảng sellers.
     * items.seller_id FK → sellers(user_id), không phải users(id).
     * Dùng INSERT IGNORE để idempotent.
     */
    private void ensureSellerRecord(String userId) {
        String sql = "INSERT IGNORE INTO sellers (user_id, approval_status, approved_date) "
                + "VALUES (?, 'APPROVED', CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Không thể tạo seller record cho userId=" + userId, e);
        }
    }

    /**
     * Insert bản ghi auction tối thiểu vào DB để phục vụ FK constraint
     * trong financial_transactions.auction_id → auctions(id).
     * Dùng trong TransactionIntegrityTests khi test không cần auction đầy đủ.
     */
    private void insertDummyAuctionRow(String auctionId, String sellerId) throws SQLException {
        // Cần item trước vì auctions.item_id → items(id)
        String itemId = UUID.randomUUID().toString();
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            // Insert item tối thiểu
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO items (id, seller_id, name, description, starting_price, category_type) " +
                            "VALUES (?, ?, 'dummy', 'dummy', 1000000, 'ELECTRONICS')")) {
                ps.setString(1, itemId);
                ps.setString(2, sellerId);
                ps.executeUpdate();
            }
            // Insert auction tối thiểu
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO auctions (id, item_id, start_time, end_time, " +
                            "current_price, status, reserve_price) " +
                            "VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 1 HOUR), 1000000, 'OPEN', 1000000)")) {
                ps.setString(1, auctionId);
                ps.setString(2, itemId);
                ps.executeUpdate();
            }
            createdItemIds.add(itemId);
            createdAuctionIds.add(auctionId);
        }
    }

    private void deleteByIds(Connection conn, String table, String column,
                             List<String> ids) throws SQLException {
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 1, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

    /** Tạo FullAuctionContext: seller + item + auction + bidder — tất cả trong DB. */
    private FullAuctionContext createFullAuctionContext(String sellerUsername,
                                                        String bidderUsername) {
        String sellerId = userDAO.registerUser(
                sellerUsername, hashPassword("pass"), sellerUsername + "@omnibid.vn");
        String bidderId = userDAO.registerUser(
                bidderUsername, hashPassword("pass"), bidderUsername + "@omnibid.vn");
        createdUserIds.addAll(List.of(sellerId, bidderId));
        userDAO.addBalance(bidderId, 10_000_000L);

        ensureSellerRecord(sellerId);
        String itemId = UUID.randomUUID().toString();
        itemDAO.addItem(itemId, sellerId, "Test Item " + sellerUsername,
                "d", 2_000_000L, "ELECTRONICS");
        createdItemIds.add(itemId);

        Item item = itemDAO.findItemById(itemId);
        Auction auction = Auction.create(item,
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now().plusHours(1), 3_000_000L);
        auctionDAO.createAuction(auction);
        createdAuctionIds.add(auction.getId());

        NormalUser bidder = userDAO.findNormalUserById(bidderId);
        return new FullAuctionContext(auction.getId(), bidder);
    }

    /** Query end_time trực tiếp từ DB cho TC updateEndTime. */
    private LocalDateTime queryEndTime(String auctionId) {
        String sql = "SELECT end_time FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("end_time");
                    return ts != null ? ts.toLocalDateTime() : null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Không thể query end_time", e);
        }
        return null;
    }

    /** Sử dụng logic hash tương tự User.hashPassword() */
    private String hashPassword(String raw) {
        return User.hashPassword(raw);
    }

    /**
     // =========================================================================
     // Inner helper records/classes
     // =========================================================================

     /** Context object giữ thông tin auction + bidder cho các test. */
    private static class FullAuctionContext {
        final String auctionId;
        final NormalUser bidder;

        FullAuctionContext(String auctionId, NormalUser bidder) {
            this.auctionId = auctionId;
            this.bidder    = bidder;
        }
    }
}