package com.group13.auction.load;

import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * ============================================================================
 * LOAD TEST — UserDAORatingServiceLoadIT (DB thật)
 * ============================================================================
 *
 * Kiểm tra UserDAO + RatingService dưới tải đồng thời với MySQL thật:
 *   - registerUser() song song — unique constraint không bị bypass
 *   - updateRating() / penalizeLatePayment() nhiều thread — không lost update
 *   - findNormalUserById() + updateBalances() đồng thời — không deadlock
 *   - existsByUsername() / existsByEmail() dưới tải đọc cao
 *   - addBalance() song song cùng user — tổng balance đúng
 */
@RequiresDocker
@Testcontainers
@DisplayName("UserDAORatingServiceLoadIT — UserDAO + RatingService dưới tải (DB thật)")
class UserDAORatingServiceLoadIT extends IntegrationTestBase {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("integration/schema.sql");

    private UserDAO              userDAO;
    private FinancialTransactionDAO financialTransactionDAO;
    private RatingService        ratingService;
    private WalletService        walletService;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() {
        userDAO                 = new UserDAO();
        financialTransactionDAO = new FinancialTransactionDAO();
        ratingService           = new RatingService(userDAO);
        walletService           = new WalletService(financialTransactionDAO, userDAO, ratingService);
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
    }

    // =========================================================================
    // Group 1 – registerUser() song song — unique constraint
    // =========================================================================

    @Nested
    @DisplayName("Group 1 – registerUser() song song — unique constraint enforcement")
    class RegisterUserLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD1: 20 thread cùng đăng ký username khác nhau — tất cả thành công, không conflict")
        void concurrent_registerDifferentUsers_allSucceed() throws Exception {
            int threads = 20;
            CountDownLatch gate      = new CountDownLatch(1);
            CountDownLatch done      = new CountDownLatch(threads);
            AtomicInteger  successes = new AtomicInteger();
            AtomicInteger  failures  = new AtomicInteger();
            List<String>   userIds   = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int idx = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        String username = "ud1_u" + idx + "_" + UUID.randomUUID().toString().substring(0, 6);
                        String email    = username + "@load.test";
                        String userId   = userDAO.registerUser(username,
                                User.hashPassword("pass"), email);
                        if (userId != null) {
                            successes.incrementAndGet();
                            userIds.add(userId);
                        } else {
                            failures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            // Track tất cả user để cleanup
            userIds.forEach(UserDAORatingServiceLoadIT.this::trackUser);

            assertThat(failures.get())
                    .as("Tất cả %d registerUser với username khác nhau phải thành công", threads)
                    .isZero();
            assertThat(successes.get()).isEqualTo(threads);
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD2: 16 thread cùng đăng ký CÙNG username — chỉ 1 thành công, còn lại null (unique constraint)")
        void concurrent_registerSameUsername_onlyOneSucceeds() throws Exception {
            int threads    = 16;
            String sharedUsername = "ud2_shared_" + UUID.randomUUID().toString().substring(0, 6);
            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  successes = new AtomicInteger();
            List<String>   userIds  = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int idx = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        String email  = "ud2_" + idx + "@load.test";
                        String userId = userDAO.registerUser(sharedUsername,
                                User.hashPassword("pass"), email);
                        if (userId != null) {
                            successes.incrementAndGet();
                            userIds.add(userId);
                        }
                    } catch (Exception ignored) {
                        // DB exception cũng chấp nhận được (UNIQUE constraint)
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            userIds.forEach(UserDAORatingServiceLoadIT.this::trackUser);

            assertThat(successes.get())
                    .as("Chỉ đúng 1 thread được registerUser với username trùng lặp")
                    .isEqualTo(1);
        }
    }

    // =========================================================================
    // Group 2 – addBalance() song song cùng user
    // =========================================================================

    @Nested
    @DisplayName("Group 2 – addBalance() song song cùng user — tổng đúng")
    class AddBalanceLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD3: 24 thread addBalance đồng thời cùng 1 user — tổng DB balance = 24 × amount")
        void concurrent_addBalance_sameUser_totalCorrect() throws Exception {
            int threads     = 24;
            long addAmount  = 500_000L;
            NormalUser user = buildUserWithBalance("ud3_shared", 0L, userDAO);

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  failures = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                new Thread(() -> {
                    try {
                        gate.await();
                        boolean result = userDAO.addBalance(user.getId(), addAmount);
                        if (!result) failures.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(failures.get())
                    .as("addBalance không được fail dưới %d thread đồng thời", threads)
                    .isZero();

            // Tổng balance trong DB phải = threads × addAmount (không có lost update)
            NormalUser fromDB = userDAO.findNormalUserById(user.getId());
            assertThat(fromDB.getBalance())
                    .as("DB balance phải = %d × %d = %d (không lost update)",
                            threads, addAmount, (long) threads * addAmount)
                    .isEqualTo((long) threads * addAmount);
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD4: 16 thread findNormalUserById + addBalance xen kẽ — không deadlock")
        void concurrent_readAndWrite_noDeadlock() throws Exception {
            int threads = 16;
            int opsPerThread = 20;
            NormalUser user = buildUserWithBalance("ud4_rw", 1_000_000L, userDAO);

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  failures = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                final int seed = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        for (int op = 0; op < opsPerThread; op++) {
                            try {
                                if (seed % 2 == 0) {
                                    userDAO.findNormalUserById(user.getId());
                                } else {
                                    userDAO.addBalance(user.getId(), 1_000L);
                                }
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
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
            assertThat(failures.get()).isZero();
        }
    }

    // =========================================================================
    // Group 3 – RatingService: penalizeLatePayment song song nhiều user
    // =========================================================================

    @Nested
    @DisplayName("Group 3 – RatingService.penalizeLatePayment song song (DB thật)")
    class RatingPenaltyLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD5: 16 user × penalizeLatePayment đồng thời — rating DB giảm đúng, không lost update")
        void concurrent_penalizeLatePayment_16Users_ratingConsistent() throws Exception {
            int userCount = 16;
            double initialRating = 4.0;

            List<NormalUser> users = new ArrayList<>(userCount);
            for (int i = 0; i < userCount; i++) {
                NormalUser u = buildUserWithBalance("ud5_u" + i, 1_000_000L, userDAO);
                // Set rating = 4.0
                u.adjustRating(initialRating - u.getRating());
                userDAO.updateRating(u.getId(), u.getRating());
                users.add(u);
            }

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(userCount);
            AtomicInteger  failures = new AtomicInteger();

            for (NormalUser u : users) {
                new Thread(() -> {
                    try {
                        gate.await();
                        ratingService.penalizeLatePayment(u);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(failures.get()).isZero();

            // Mỗi user: rating DB = initialRating - 1.0
            for (NormalUser u : users) {
                NormalUser fromDB = userDAO.findNormalUserById(u.getId());
                assertThat(fromDB.getRating())
                        .as("Rating DB của %s phải = %.1f - 1.0", u.getUsername(), initialRating)
                        .isEqualTo(initialRating - 1.0, within(0.001));
                assertThat(fromDB.isHasEverBeenPenalized())
                        .as("hasEverBeenPenalized phải = true sau penalize")
                        .isTrue();
            }
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD6: 8 thread rewardBidder + 8 thread penalizeLatePayment trên cùng 1 user — không NPE, rating hợp lệ [0.0, 5.0]")
        void concurrent_rewardAndPenalize_sameUser_ratingInValidRange() throws Exception {
            int rewardThreads  = 8;
            int penaltyThreads = 8;
            int threads        = rewardThreads + penaltyThreads;
            int opsPerThread   = 5;

            // Bắt đầu rating = 3.0
            NormalUser sharedUser = buildUserWithBalance("ud6_shared", 1_000_000L, userDAO);

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  failures = new AtomicInteger();

            for (int t = 0; t < rewardThreads; t++) {
                new Thread(() -> {
                    try {
                        gate.await();
                        for (int op = 0; op < opsPerThread; op++) {
                            try {
                                ratingService.rewardBidder(sharedUser);
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

            for (int t = 0; t < penaltyThreads; t++) {
                new Thread(() -> {
                    try {
                        gate.await();
                        for (int op = 0; op < opsPerThread; op++) {
                            try {
                                ratingService.penalizeLatePayment(sharedUser);
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
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
            assertThat(failures.get()).isZero();

            // Rating phải nằm trong khoảng hợp lệ [RATING_MIN=0.0, RATING_MAX=5.0]
            // (không phải 1.0 — production RATING_MIN = 0.0, SUSPEND_THRESHOLD = 1.5 là khác nhau)
            NormalUser fromDB = userDAO.findNormalUserById(sharedUser.getId());
            assertThat(fromDB.getRating())
                    .as("Rating phải nằm trong khoảng [0.0, 5.0] sau reward/penalize đồng thời")
                    .isBetween(0.0, 5.0);
        }
    }

    // =========================================================================
    // Group 4 – existsByUsername / existsByEmail dưới tải đọc cao
    // =========================================================================

    @Nested
    @DisplayName("Group 4 – existsByUsername / existsByEmail dưới tải đọc cao")
    class ExistenceCheckLoadTest {

        @Test
        @Timeout(value = 30)
        @DisplayName("L-UD7: 32 thread × 200 lần existsByUsername — không exception, kết quả nhất quán")
        void concurrent_existsByUsername_noException_consistentResult() throws Exception {
            // Tạo user trước để biết chắc tồn tại
            NormalUser existing = buildUserWithBalance("ud7_existing", 0L, userDAO);
            int threads      = 32;
            int opsPerThread = 200;

            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  wrongResult = new AtomicInteger();
            AtomicInteger  exceptions  = new AtomicInteger();

            for (int t = 0; t < threads; t++) {
                final int seed = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        for (int op = 0; op < opsPerThread; op++) {
                            try {
                                // Kiểm tra cả user tồn tại và không tồn tại
                                boolean shouldExist = userDAO.existsByUsername(existing.getUsername());
                                boolean shouldNotExist = userDAO.existsByUsername("definitely_not_exist_xyz_" + seed);
                                if (!shouldExist) wrongResult.incrementAndGet();
                                if (shouldNotExist) wrongResult.incrementAndGet();
                            } catch (Exception e) {
                                exceptions.incrementAndGet();
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

            assertThat(exceptions.get())
                    .as("existsByUsername không được throw exception dưới tải")
                    .isZero();
            assertThat(wrongResult.get())
                    .as("existsByUsername phải trả về kết quả đúng cho cả tồn tại và không tồn tại")
                    .isZero();
        }

        @Test
        @Timeout(value = 30)
        @DisplayName("L-UD8: 16 thread register rồi check existsByEmail ngay — không race condition")
        void concurrent_registerThenCheckEmail_noRaceCondition() throws Exception {
            int threads = 16;
            CountDownLatch gate       = new CountDownLatch(1);
            CountDownLatch done       = new CountDownLatch(threads);
            AtomicInteger  failures   = new AtomicInteger();
            List<String>   registeredIds = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int idx = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        String email  = "ud8_" + idx + "_" + UUID.randomUUID().toString().substring(0, 6) + "@test.vn";
                        String username = "ud8_u" + idx + "_" + UUID.randomUUID().toString().substring(0, 6);
                        String userId = userDAO.registerUser(username, User.hashPassword("pass"), email);
                        if (userId != null) {
                            registeredIds.add(userId);
                            // Ngay sau register phải existsByEmail = true
                            boolean exists = userDAO.existsByEmail(email);
                            if (!exists) failures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            registeredIds.forEach(UserDAORatingServiceLoadIT.this::trackUser);

            assertThat(failures.get())
                    .as("existsByEmail phải trả về true ngay sau khi register thành công")
                    .isZero();
        }
    }

    // =========================================================================
    // Group 5 – WalletService.deposit song song nhiều user (riêng lẻ, không dùng DB load test khác)
    // =========================================================================

    @Nested
    @DisplayName("Group 5 – Tổng hợp: register + deposit + ratingCheck đồng thời")
    class ComprehensiveMixedLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-UD9: Register + deposit + isEligible song song 20 thread — không exception nào là fatal")
        void comprehensive_registerDepositRating_noFatalError() throws Exception {
            int threads      = 20;
            int opsPerThread = 10;
            AtomicLong totalOps = new AtomicLong();
            AtomicInteger fatalErrors = new AtomicInteger();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();
            List<String> createdUserIds = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int idx = t;
                futures.add(pool.submit(() -> {
                    for (int op = 0; op < opsPerThread; op++) {
                        try {
                            // 1. Register
                            String uname = "ud9_t" + idx + "_op" + op + "_" + UUID.randomUUID().toString().substring(0, 4);
                            String email = uname + "@test.vn";
                            String userId = userDAO.registerUser(uname, User.hashPassword("p"), email);
                            if (userId == null) continue;
                            createdUserIds.add(userId);

                            // 2. Deposit
                            NormalUser user = userDAO.findNormalUserById(userId);
                            if (user == null) continue;
                            walletService.deposit(user, 1_000_000L);

                            // 3. isEligible
                            ratingService.isEligible(user);

                            totalOps.incrementAndGet();
                        } catch (Error e) {
                            // JVM Error = fatal
                            fatalErrors.incrementAndGet();
                        } catch (Exception ignored) {
                            // Java Exception chấp nhận được
                        }
                    }
                }));
            }

            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            createdUserIds.forEach(UserDAORatingServiceLoadIT.this::trackUser);

            assertThat(fatalErrors.get())
                    .as("Không được có JVM Error trong mixed load test")
                    .isZero();
            assertThat(totalOps.get())
                    .as("Phải có ít nhất 1 full operation (register+deposit+isEligible) thành công")
                    .isPositive();
        }
    }
}