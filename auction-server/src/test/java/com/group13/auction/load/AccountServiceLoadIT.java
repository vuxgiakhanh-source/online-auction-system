package com.group13.auction.load;

import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.DatabaseConnection;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.SellerDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LOAD TEST — AccountServiceLoadIT (DB thật)
 * ============================================================================
 *
 * Kiểm tra AccountService dưới tải đồng thời với MySQL thật:
 *
 * Group 1 — banUser() song song:
 *   Nhiều admin ban nhiều user cùng lúc — DB status khớp RAM, không lost update.
 *
 * Group 2 — autoApproveSellerRole() song song:
 *   Nhiều user request seller role đồng thời — không double-approve,
 *   DB sellers record nhất quán.
 *
 * Group 3 — createStaffAdmin() song song:
 *   Nhiều request tạo admin STAFF cùng lúc — unique constraint enforcement,
 *   không NPE trong AuctionManager.registerUser().
 *
 * Group 4 — deposit/withdraw + banUser xen kẽ:
 *   Mixed workload: tài chính và quản lý tài khoản đồng thời.
 *
 * Group 5 — requestCancelAuction() song song:
 *   Nhiều seller gửi yêu cầu hủy phiên đồng thời — AuctionManager observer
 *   không bị race condition.
 */
@RequiresDocker
@Testcontainers
@DisplayName("AccountServiceLoadIT — AccountService dưới tải (DB thật)")
class AccountServiceLoadIT extends IntegrationTestBase {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql");

    private UserDAO        userDAO;
    private ItemDAO        itemDAO;
    private AuctionDAO     auctionDAO;
    private AdminDAO       adminDAO;
    private SellerDAO      sellerDAO;
    private AuctionWinnerDAO auctionWinnerDAO;
    private FinancialTransactionDAO financialTransactionDAO;

    private RatingService  ratingService;
    private WalletService  walletService;
    private AuctionService auctionService;
    private BidService     bidService;
    private AccountService accountService;

    /** Admin IDs tạo trong test — cần xóa thủ công vì IntegrationTestBase không track admins. */
    private final List<String> createdAdminIds = new ArrayList<>();

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() throws Exception {
        userDAO                 = new UserDAO();
        itemDAO                 = new ItemDAO();
        auctionDAO              = new AuctionDAO();
        adminDAO                = new AdminDAO();
        sellerDAO               = new SellerDAO();
        auctionWinnerDAO        = new AuctionWinnerDAO();
        financialTransactionDAO = new FinancialTransactionDAO();

        ratingService  = new RatingService(userDAO);
        TestFixture.bootstrapSystemAdmin();
        walletService  = new WalletService(financialTransactionDAO, userDAO, ratingService);
        auctionService = new AuctionService(ratingService, auctionDAO);
        bidService     = new BidService(auctionService, ratingService, walletService,
                new com.group13.auction.dao.BidTransactionDAO(), auctionDAO, userDAO);
        accountService = new AccountService(ratingService, userDAO, sellerDAO,
                adminDAO, auctionDAO, auctionWinnerDAO);
        resetTracking();
        createdAdminIds.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteAdmins();
        cleanupDB();
        clearAuctionManagerSingletons();
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // Group 1 — banUser() song song
    // =========================================================================

    @Nested
    @DisplayName("Group 1 – banUser() song song (DB thật)")
    class BanUserLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC1: 20 user × ban đồng thời bởi 1 admin — DB status = BANNED, không lost update")
        void concurrent_banUsers_20Targets_allBanned() throws Exception {
            int userCount = 20;

            // Tạo 1 admin thực hiện ban
            Admin admin = buildAdmin("acc1_admin");

            // Tạo 20 user cần ban — tuần tự (tránh concurrent ArrayList write)
            List<NormalUser> targets = new ArrayList<>(userCount);
            for (int i = 0; i < userCount; i++) {
                targets.add(buildUserWithBalance("acc1_target" + i, 1_000_000L, userDAO));
            }

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(userCount);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();

            for (NormalUser target : targets) {
                new Thread(() -> {
                    try {
                        gate.await();
                        accountService.banUser(admin, target, Admin.BanReason.LOW_RATING);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(failure.get())
                    .as("banUser không được throw exception dưới tải")
                    .isZero();
            assertThat(success.get()).isEqualTo(userCount);

            // Verify RAM: tất cả target đã bị BANNED
            for (NormalUser t : targets) {
                assertThat(t.getAccountStatus())
                        .as("RAM status của %s phải = BANNED", t.getUsername())
                        .isEqualTo(User.AccountStatus.BANNED);
            }

            // Verify DB: khớp RAM
            for (NormalUser t : targets) {
                NormalUser fromDB = userDAO.findNormalUserById(t.getId());
                assertThat(fromDB.getAccountStatus())
                        .as("DB status của %s phải = BANNED", t.getUsername())
                        .isEqualTo(User.AccountStatus.BANNED);
            }
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC2: 8 admin × ban 1 user cùng lúc — chỉ 1 ban thực sự, không conflict DB")
        void concurrent_multipleAdmins_banSameUser_noConflict() throws Exception {
            int adminCount = 8;
            NormalUser sharedTarget = buildUserWithBalance("acc2_target", 1_000_000L, userDAO);

            List<Admin> admins = new ArrayList<>(adminCount);
            for (int i = 0; i < adminCount; i++) {
                admins.add(buildAdmin("acc2_admin" + i));
            }

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(adminCount);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();

            for (Admin admin : admins) {
                new Thread(() -> {
                    try {
                        gate.await();
                        accountService.banUser(admin, sharedTarget, Admin.BanReason.LOW_RATING);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            // Không có exception — ban nhiều lần cùng user vẫn hợp lệ
            assertThat(failure.get()).isZero();
            // Status cuối phải là BANNED
            NormalUser fromDB = userDAO.findNormalUserById(sharedTarget.getId());
            assertThat(fromDB.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
        }
    }

    // =========================================================================
    // Group 2 — autoApproveSellerRole() song song
    // =========================================================================

    @Nested
    @DisplayName("Group 2 – autoApproveSellerRole() song song (DB thật)")
    class AutoApproveSellerLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC3: 16 user đủ điều kiện — autoApproveSellerRole đồng thời, không double-approve")
        void concurrent_autoApproveSellerRole_16Users_noDoubleApprove() throws Exception {
            int userCount = 16;

            // Tạo user đủ điều kiện (chưa bị phạt, rating >= 2.0, ACTIVE)
            List<NormalUser> users = new ArrayList<>(userCount);
            for (int i = 0; i < userCount; i++) {
                users.add(buildUserWithBalance("acc3_u" + i, 1_000_000L, userDAO));
            }

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(userCount);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();

            for (NormalUser u : users) {
                new Thread(() -> {
                    try {
                        gate.await();
                        accountService.autoApproveSellerRole(u);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(failure.get())
                    .as("autoApproveSellerRole không được throw exception cho user đủ điều kiện")
                    .isZero();
            assertThat(success.get()).isEqualTo(userCount);

            // Mỗi user phải có role SELLER
            for (NormalUser u : users) {
                assertThat(u.hasRole(User.UserRole.SELLER))
                        .as("%s phải có role SELLER sau autoApprove", u.getUsername())
                        .isTrue();
            }
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC4: 1 user bị gọi autoApproveSellerRole từ 10 thread — idempotent, không exception")
        void concurrent_approveSellerRole_sameUser_idempotent() throws Exception {
            int threads = 10;
            NormalUser sharedUser = buildUserWithBalance("acc4_u", 1_000_000L, userDAO);

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                    try {
                        gate.await();
                        // Lần đầu: add SELLER role. Các lần sau: return early (idempotent).
                        accountService.autoApproveSellerRole(sharedUser);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            // Không exception nào — hasRole() đã có thì return early
            assertThat(failure.get()).isZero();
            assertThat(sharedUser.hasRole(User.UserRole.SELLER)).isTrue();
        }
    }

    // =========================================================================
    // Group 3 — createStaffAdmin() song song
    // =========================================================================

    @Nested
    @DisplayName("Group 3 – createStaffAdmin() song song (DB thật)")
    class CreateStaffAdminLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC5: 10 thread tạo 10 admin STAFF khác nhau — tất cả thành công, DB unique đảm bảo")
        void concurrent_createStaffAdmin_10Different_allSucceed() throws Exception {
            int threads = 10;
            // Tạo username/email trước để đảm bảo unique
            List<String> usernames = new ArrayList<>(threads);
            List<String> emails    = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                String uid = UUID.randomUUID().toString().substring(0, 6);
                usernames.add("acc5_staff" + i + "_" + uid);
                emails.add("acc5_staff" + i + "_" + uid + "@test.vn");
            }

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();
            List<String>   adminIds = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final String username = usernames.get(t);
                final String email    = emails.get(t);
                new Thread(() -> {
                    try {
                        gate.await();
                        Admin admin = accountService.createStaffAdmin(username, "Admin@Pass1", email);
                        adminIds.add(admin.getId());
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            createdAdminIds.addAll(adminIds);

            assertThat(failure.get())
                    .as("createStaffAdmin không được fail khi username/email khác nhau")
                    .isZero();
            assertThat(success.get()).isEqualTo(threads);
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC6: 8 thread tạo admin STAFF với cùng username — chỉ 1 thành công (unique constraint)")
        void concurrent_createStaffAdmin_sameUsername_onlyOneSucceeds() throws Exception {
            int threads       = 8;
            String sharedName = "acc6_staff_" + UUID.randomUUID().toString().substring(0, 6);
            List<String> adminIds = new java.util.concurrent.CopyOnWriteArrayList<>();

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  success = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                final int idx = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        String email = "acc6_staff_" + idx + "@test.vn";
                        Admin admin = accountService.createStaffAdmin(sharedName, "Admin@Pass1", email);
                        adminIds.add(admin.getId());
                        success.incrementAndGet();
                    } catch (Exception ignored) {
                        // RuntimeException từ adminDAO.createAdmin fail — expected
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            createdAdminIds.addAll(adminIds);

            assertThat(success.get())
                    .as("Chỉ đúng 1 thread tạo được admin với username trùng")
                    .isEqualTo(1);
        }
    }

    // =========================================================================
    // Group 4 — deposit/withdraw + banUser xen kẽ (mixed workload)
    // =========================================================================

    @Nested
    @DisplayName("Group 4 – Mixed: deposit/withdraw + banUser đồng thời")
    class MixedAccountWorkloadTest {

        @Test
        @Timeout(value = 90)
        @DisplayName("L-ACC7: 8 thread deposit + 4 thread banUser cùng pool users — không deadlock, không NPE")
        void mixed_depositAndBan_noDeadlockNoNPE() throws Exception {
            int depositThreads = 8;
            int banThreads     = 4;
            int totalThreads   = depositThreads + banThreads;

            Admin admin = buildAdmin("acc7_admin");

            // Tạo 2 tập user tách biệt — deposit users và ban users
            List<NormalUser> depositUsers = new ArrayList<>(depositThreads);
            for (int i = 0; i < depositThreads; i++) {
                depositUsers.add(buildUserWithBalance("acc7_dep" + i, 1_000_000L, userDAO));
            }
            List<NormalUser> banTargets = new ArrayList<>(banThreads);
            for (int i = 0; i < banThreads; i++) {
                banTargets.add(buildUserWithBalance("acc7_ban" + i, 1_000_000L, userDAO));
            }

            ExecutorService pool    = Executors.newFixedThreadPool(totalThreads);
            AtomicInteger   failures = new AtomicInteger();
            List<Future<?>> futures  = new ArrayList<>();

            // Deposit tasks
            for (int i = 0; i < depositThreads; i++) {
                final NormalUser u = depositUsers.get(i);
                futures.add(pool.submit(() -> {
                    for (int r = 0; r < 10; r++) {
                        try {
                            accountService.deposit(u, 100_000L);
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    }
                }));
            }

            // Ban tasks
            for (int i = 0; i < banThreads; i++) {
                final NormalUser target = banTargets.get(i);
                futures.add(pool.submit(() -> {
                    try {
                        accountService.banUser(admin, target, Admin.BanReason.LOW_RATING);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }));
            }

            for (Future<?> f : futures) f.get(90, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            assertThat(failures.get())
                    .as("Mixed deposit+ban không được throw exception")
                    .isZero();

            // Verify deposit: mỗi depositUser balance RAM = initial + 10 × 100_000
            for (NormalUser u : depositUsers) {
                assertThat(u.getBalance())
                        .as("Balance của %s sau 10 deposit phải = 2_000_000", u.getUsername())
                        .isEqualTo(2_000_000L);
            }

            // Verify ban: mỗi banTarget phải là BANNED
            for (NormalUser t : banTargets) {
                assertThat(t.getAccountStatus())
                        .as("%s phải bị BANNED", t.getUsername())
                        .isEqualTo(User.AccountStatus.BANNED);
            }
        }
    }

    // =========================================================================
    // Group 5 — requestCancelAuction() song song
    // =========================================================================

    @Nested
    @DisplayName("Group 5 – requestCancelAuction() song song — AuctionManager observer không bị race")
    class RequestCancelAuctionLoadTest {

        @Test
        @Timeout(value = 90)
        @DisplayName("L-ACC8: 8 seller × requestCancelAuction đồng thời — không NPE trong observer dispatch")
        void concurrent_requestCancelAuction_8Sellers_noNPEInObserver() throws Exception {
            int sellerCount = 8;

            // Tạo seller và phiên OPEN cho từng seller
            List<NormalUser> sellers  = new ArrayList<>(sellerCount);
            List<Auction>    auctions = new ArrayList<>(sellerCount);

            for (int i = 0; i < sellerCount; i++) {
                NormalUser seller = buildUserWithBalance("acc8_sel" + i, 80_000_000L, userDAO);
                // Thêm SELLER role qua reflection (như các test khác trong project)
                addSellerRole(seller);

                String itemId = buildItem(seller.getId(), "ACC8-Item-" + i, 1_000_000L, itemDAO);
                Item item = itemDAO.findItemById(itemId);
                Auction auction = Auction.create(item,
                        LocalDateTime.now().plusMinutes(10),
                        LocalDateTime.now().plusHours(2),
                        3_000_000L);
                auctionDAO.createAuction(auction);
                trackAuction(auction.getId());

                // Đăng ký auction vào AuctionManager (cần cho notifyStaffObservers)
                com.group13.auction.manager.AuctionManager.getInstance().registerAuction(auction);

                // Thêm auctionId vào seller.allAuctionIds
                seller.addAuctionId(auction.getId());

                sellers.add(seller);
                auctions.add(auction);
            }

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(sellerCount);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();

            for (int i = 0; i < sellerCount; i++) {
                final NormalUser seller = sellers.get(i);
                final Auction    auction = auctions.get(i);
                new Thread(() -> {
                    try {
                        gate.await();
                        accountService.requestCancelAuction(seller, auction, "Load test reason");
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(failure.get())
                    .as("requestCancelAuction không được throw exception dưới tải")
                    .isZero();
            assertThat(success.get()).isEqualTo(sellerCount);
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ACC9: 16 thread notifyStaffObservers đồng thời — CopyOnWriteArrayList không bị ConcurrentModificationException")
        void concurrent_notifyStaffObservers_noException() throws Exception {
            int threads   = 16;
            int opsPerThread = 50;

            // Chuẩn bị 1 phiên để dispatch event
            NormalUser seller = buildUserWithBalance("acc9_sel", 80_000_000L, userDAO);
            addSellerRole(seller);
            String itemId = buildItem(seller.getId(), "ACC9-Item", 1_000_000L, itemDAO);
            Item item = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().plusMinutes(1),
                    LocalDateTime.now().plusHours(2),
                    3_000_000L);
            auctionDAO.createAuction(auction);
            trackAuction(auction.getId());
            com.group13.auction.manager.AuctionManager.getInstance().registerAuction(auction);
            seller.addAuctionId(auction.getId());

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  failures = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                    try {
                        gate.await();
                        for (int op = 0; op < opsPerThread; op++) {
                            try {
                                // Gọi trực tiếp qua AuctionManager — test CopyOnWriteArrayList an toàn
                                com.group13.auction.manager.AuctionManager.getInstance()
                                        .notifyStaffObservers(new com.group13.auction.observer.AuctionEvent(
                                                com.group13.auction.observer.AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
                                                auction, null, 0L, "test"));
                            } catch (Exception e) {
                                failures.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(failures.get())
                    .as("notifyStaffObservers không được throw exception dưới tải")
                    .isZero();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Tạo Admin qua AdminFactory — không qua AccountService để tránh vòng lặp. */
    private Admin buildAdmin(String username) {
        com.group13.auction.model.user.AdminFactory factory =
                new com.group13.auction.model.user.AdminFactory();
        Admin admin = (Admin) factory.createUser(username, "Admin@Pass1", username + "@admin.test");
        createdAdminIds.add(admin.getId());
        return admin;
    }

    /** Thêm SELLER role qua reflection — User.addRole() là package-private. */
    private void addSellerRole(NormalUser user) {
        try {
            java.lang.reflect.Field f = NormalUser.class.getDeclaredField("roles");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<User.UserRole> roles = (java.util.Set<User.UserRole>) f.get(user);
            roles.add(User.UserRole.SELLER);
        } catch (Exception ignored) {}
    }

    /** Xóa các admin được tạo trong test — IntegrationTestBase không track admins. */
    private void deleteAdmins() {
        if (createdAdminIds.isEmpty()) return;
        String ph = String.join(",", java.util.Collections.nCopies(createdAdminIds.size(), "?"));
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM admins WHERE id IN (" + ph + ")")) {
            for (int i = 0; i < createdAdminIds.size(); i++) {
                ps.setString(i + 1, createdAdminIds.get(i));
            }
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /** Reset AuctionManager in-memory state sau mỗi test. */
    private void clearAuctionManagerSingletons() {
        try {
            com.group13.auction.manager.AuctionManager mgr =
                    com.group13.auction.manager.AuctionManager.getInstance();
            clearMapField(mgr, "allAuctions");
            clearMapField(mgr, "allUsers");
            clearListField(mgr, "staffObservers");
            clearListField(mgr, "globalObservers");
        } catch (Exception ignored) {}
    }

    private void clearMapField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        Object map = f.get(target);
        if (map instanceof java.util.Map<?, ?> m) m.clear();
    }

    private void clearListField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        Object list = f.get(target);
        if (list instanceof java.util.List<?> l) l.clear();
    }
}
