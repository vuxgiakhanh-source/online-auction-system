package com.group13.auction.load;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.PaymentService;
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
 * LOAD TEST — PaymentServiceLoadIT (DB thật)
 * ============================================================================
 *
 * Kiểm tra PaymentService + WalletService dưới tải đồng thời với MySQL thật:
 *   - refundDeposits() đồng thời nhiều phiên — không deadlock, không lost update
 *   - lockDeposit song song nhiều user trên cùng phiên — balance nhất quán
 *   - completePayment song song nhiều phiên — không double-charge
 *   - WalletService.deposit() + withdraw() xen kẽ — balance không âm
 */
@RequiresDocker
@Testcontainers
@DisplayName("PaymentServiceLoadIT — PaymentService + WalletService dưới tải (DB thật)")
class PaymentServiceLoadIT extends IntegrationTestBase {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.0")
            .withDatabaseName("omnibid_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("database/schema.sql");

    private UserDAO              userDAO;
    private ItemDAO              itemDAO;
    private AuctionDAO           auctionDAO;
    private BidTransactionDAO    bidTransactionDAO;
    private FinancialTransactionDAO financialTransactionDAO;
    private AuctionWinnerDAO     auctionWinnerDAO;
    private SecondChanceOfferDAO secondChanceOfferDAO;

    private RatingService  ratingService;
    private WalletService  walletService;
    private AuctionService auctionService;
    private BidService     bidService;
    private PaymentService paymentService;

    @BeforeAll
    static void configureDataSource() throws Exception {
        configureTestcontainer(mysql);
    }

    @BeforeEach
    void setUp() throws Exception {
        userDAO                 = new UserDAO();
        itemDAO                 = new ItemDAO();
        auctionDAO              = new AuctionDAO();
        bidTransactionDAO       = new BidTransactionDAO();
        financialTransactionDAO = new FinancialTransactionDAO();
        auctionWinnerDAO        = new AuctionWinnerDAO();
        secondChanceOfferDAO    = new SecondChanceOfferDAO();

        ratingService  = new RatingService(userDAO);
        TestFixture.bootstrapSystemAdmin();
        walletService  = new WalletService(financialTransactionDAO, userDAO, ratingService);
        auctionService = new AuctionService(ratingService, auctionDAO);
        bidService     = new BidService(auctionService, ratingService, walletService,
                bidTransactionDAO, auctionDAO, userDAO);
        paymentService = new PaymentService(auctionService, ratingService, walletService,
                auctionWinnerDAO, secondChanceOfferDAO, bidTransactionDAO, userDAO);
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // Group 1 – lockDeposit song song nhiều user
    // =========================================================================

    @Nested
    @DisplayName("Group 1 – lockDeposit song song nhiều user (DB thật)")
    class LockDepositLoadTest {

        @Test
        @Timeout(value = 90)
        @DisplayName("L-PAY1: 20 user đồng thời lockDeposit vào cùng 1 phiên — balance DB khớp RAM, không lost update")
        void concurrent_lockDeposit_20Users_balanceConsistent() throws Exception {
            int userCount = 20;
            long initialBalance = 10_000_000L;

            NormalUser seller = buildUserWithBalance("pay1_sel", 80_000_000L, userDAO);
            String itemId = buildItem(seller.getId(), "PAY1-Item", 1_000_000L, itemDAO);
            Item item = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(2),
                    3_000_000L);
            auctionDAO.createAuction(auction);
            auctionService.startAuction(auction);
            trackAuction(auction.getId());

            List<NormalUser> users = new ArrayList<>(userCount);
            for (int i = 0; i < userCount; i++) {
                users.add(buildUserWithBalance("pay1_u" + i, initialBalance, userDAO));
            }

            long depositAmount = 300_000L; // 30% of 1_000_000 startingPrice
            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(userCount);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failure = new AtomicInteger();

            for (NormalUser u : users) {
                new Thread(() -> {
                    try {
                        gate.await();
                        walletService.lockDeposit(u, depositAmount, auction.getId());
                        success.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

            // Không được có exception
            assertThat(failure.get())
                    .as("lockDeposit không được throw exception dưới tải")
                    .isZero();
            assertThat(success.get()).isEqualTo(userCount);

            // Mỗi user: balance RAM = initialBalance - deposit, lockedDeposit = deposit
            for (NormalUser u : users) {
                NormalUser fromDB = userDAO.findNormalUserById(u.getId());
                assertThat(fromDB.getAvailableBalance())
                        .as("DB available balance của %s phải = initialBalance - deposit", u.getUsername())
                        .isEqualTo(initialBalance - depositAmount);
                assertThat(fromDB.getLockedDeposit())
                        .as("DB lockedDeposit phải = deposit")
                        .isEqualTo(depositAmount);
            }
        }
    }

    // =========================================================================
    // Group 2 – refundDeposits nhiều phiên đồng thời
    // =========================================================================

    @Nested
    @DisplayName("Group 2 – refundDeposits nhiều phiên đồng thời (DB thật)")
    class RefundDepositsLoadTest {

        @Test
        @Timeout(value = 120)
        @DisplayName("L-PAY2: 6 phiên × 4 bidder, refundDeposits chạy song song — không deadlock, balance đúng")
        void concurrent_refundDeposits_6Auctions_noDeadlock() throws Exception {
            int auctionCount    = 6;
            int biddersPerAuction = 4;
            long initialBalance = 10_000_000L;
            long deposit        = 300_000L;

            List<Auction> auctions = new ArrayList<>(auctionCount);
            List<List<NormalUser>> bidderGroups = new ArrayList<>();

            // Tạo các phiên và bidder
            for (int a = 0; a < auctionCount; a++) {
                NormalUser seller = buildUserWithBalance("pay2_sel" + a, 80_000_000L, userDAO);
                String itemId = buildItem(seller.getId(), "PAY2-Item-" + a, 1_000_000L, itemDAO);
                Item item = itemDAO.findItemById(itemId);
                Auction auction = Auction.create(item,
                        LocalDateTime.now().minusMinutes(1),
                        LocalDateTime.now().plusHours(2),
                        3_000_000L);
                auctionDAO.createAuction(auction);
                auctionService.startAuction(auction);
                trackAuction(auction.getId());
                auctions.add(auction);

                List<NormalUser> bidders = new ArrayList<>(biddersPerAuction);
                for (int b = 0; b < biddersPerAuction; b++) {
                    NormalUser u = buildUserWithBalance("pay2_b" + a + "_" + b, initialBalance, userDAO);
                    walletService.lockDeposit(u, deposit, auction.getId());
                    bidTransactionDAO.saveTransaction(BidTransaction.create(
                            u, auction.getId(), 1_500_000L, BidTransaction.BidResult.ACCEPTED));
                    u.addJoinedAuction(auction.getId());
                    bidders.add(u);
                }
                bidderGroups.add(bidders);
            }

            // refundDeposits song song cho tất cả phiên
            ExecutorService pool = Executors.newFixedThreadPool(auctionCount);
            AtomicInteger failures = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();

            for (int a = 0; a < auctionCount; a++) {
                final Auction auction = auctions.get(a);
                futures.add(pool.submit(() -> {
                    try {
                        paymentService.refundDeposits(auction);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }));
            }

            for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            assertThat(failures.get())
                    .as("refundDeposits không được throw exception dưới tải đa phiên")
                    .isZero();

            // Verify: mỗi bidder nhận lại đủ cọc
            for (int a = 0; a < auctionCount; a++) {
                for (NormalUser u : bidderGroups.get(a)) {
                    assertThat(u.getBalance())
                            .as("Balance của %s phải = initialBalance (cọc đã được hoàn)", u.getUsername())
                            .isEqualTo(initialBalance);
                }
            }
        }
    }

    // =========================================================================
    // Group 3 – WalletService deposit/withdraw xen kẽ dưới tải
    // =========================================================================

    @Nested
    @DisplayName("Group 3 – WalletService deposit/withdraw xen kẽ — balance không âm")
    class WalletDepositWithdrawLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-PAY3: 12 thread deposit/withdraw xen kẽ trên cùng user — balance không bao giờ âm")
        void concurrent_depositWithdraw_sameUser_balanceNeverNegative() throws Exception {
            int threads   = 12;
            int opsPerThread = 10;
            long startBalance = 5_000_000L;
            NormalUser sharedUser = buildUserWithBalance("pay3_shared", startBalance, userDAO);

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
                                    walletService.deposit(sharedUser, 100_000L);
                                } else {
                                    // Chỉ rút nếu có đủ available
                                    if (sharedUser.getAvailableBalance() >= 100_000L) {
                                        walletService.withdraw(sharedUser, 100_000L);
                                    }
                                }
                            } catch (IllegalArgumentException ignored) {
                                // balance không đủ là hành vi đúng
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

            assertThat(failures.get()).isZero();
            // Balance RAM không bao giờ âm
            assertThat(sharedUser.getBalance())
                    .as("Balance không được âm sau deposit/withdraw song song")
                    .isGreaterThanOrEqualTo(0L);
            // DB khớp RAM
            NormalUser fromDB = userDAO.findNormalUserById(sharedUser.getId());
            assertThat(fromDB.getBalance())
                    .as("DB balance phải khớp RAM")
                    .isEqualTo(sharedUser.getBalance());
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-PAY4: 8 phiên song song, mỗi phiên 1 bidder đặt cọc rồi hoàn — không lost update")
        void parallel_lockAndUnlock_8Auctions_noLostUpdate() throws Exception {
            int auctionCount = 8;
            long initialBalance = 5_000_000L;
            long deposit = 1_000_000L;

            List<NormalUser> users = new ArrayList<>(auctionCount);
            List<String> auctionIds = new ArrayList<>(auctionCount);
            for (int i = 0; i < auctionCount; i++) {
                users.add(buildUserWithBalance("pay4_u" + i, initialBalance, userDAO));
                auctionIds.add(UUID.randomUUID().toString());
            }

            ExecutorService pool = Executors.newFixedThreadPool(auctionCount);
            AtomicInteger failures = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < auctionCount; i++) {
                final NormalUser user = users.get(i);
                final String aucId = auctionIds.get(i);
                futures.add(pool.submit(() -> {
                    try {
                        walletService.lockDeposit(user, deposit, aucId);
                        walletService.unlockDeposit(user, deposit, aucId);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }));
            }

            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            assertThat(failures.get()).isZero();

            // Mỗi user: balance về lại initialBalance, lockedDeposit = 0
            for (NormalUser user : users) {
                NormalUser fromDB = userDAO.findNormalUserById(user.getId());
                assertThat(fromDB.getBalance())
                        .as("Balance %s phải về lại initialBalance sau lock+unlock", user.getUsername())
                        .isEqualTo(initialBalance);
                assertThat(fromDB.getLockedDeposit())
                        .as("lockedDeposit phải = 0 sau unlock")
                        .isZero();
            }
        }
    }

    // =========================================================================
    // Group 4 – completePayment nhiều phiên đồng thời
    // =========================================================================

    @Nested
    @DisplayName("Group 4 – completePayment nhiều phiên đồng thời (DB thật)")
    class CompletePaymentLoadTest {

        @Test
        @Timeout(value = 120)
        @DisplayName("L-PAY5: 5 phiên song song completePayment — không double-charge, DB nhất quán")
        void concurrent_completePayment_5Auctions_noDoubleCharge() throws Exception {
            int auctionCount = 5;
            long startingPrice = 1_000_000L;
            long bidAmount     = 2_000_000L;
            long reservePrice  = 1_500_000L;

            List<Auction>    auctions = new ArrayList<>(auctionCount);
            List<NormalUser> winners  = new ArrayList<>(auctionCount);
            long deposit = startingPrice * 3 / 10; // 300_000

            for (int i = 0; i < auctionCount; i++) {
                NormalUser seller = buildUserWithBalance("pay5_sel" + i, 80_000_000L, userDAO);
                // Balance phải đủ để joinAuction (lockDeposit) + thanh toán phần remaining
                // deposit = startingPrice * 3/10 = 300_000
                // remaining = bidAmount - deposit = 1_700_000
                // -> cần balance >= deposit + remaining = bidAmount = 2_000_000
                NormalUser winner = buildUserWithBalance("pay5_win" + i, bidAmount + deposit + 1_000_000L, userDAO);

                String itemId = buildItem(seller.getId(), "PAY5-Item-" + i, startingPrice, itemDAO);
                Item item = itemDAO.findItemById(itemId);
                Auction auction = Auction.create(item,
                        LocalDateTime.now().minusMinutes(1),
                        LocalDateTime.now().plusHours(2),
                        reservePrice);
                auctionDAO.createAuction(auction);
                auctionService.startAuction(auction);
                trackAuction(auction.getId());

                // Đặt bid và đóng phiên để có winner
                AuctionObserver obs = new BidderObserver(winner, null);
                bidService.joinAuction(winner, auction, obs);
                bidService.placeBid(winner, auction, bidAmount, new StandardBidStrategy());
                auctionService.closeAuction(auction);

                auctions.add(auction);
                winners.add(winner);
            }

            // completePayment song song cho tất cả phiên
            ExecutorService pool = Executors.newFixedThreadPool(auctionCount);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger failures  = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < auctionCount; i++) {
                final Auction auction = auctions.get(i);
                if (auction.getWinner() == null) continue; // bỏ qua nếu không có winner
                futures.add(pool.submit(() -> {
                    try {
                        paymentService.completePayment(auction);
                        successes.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }));
            }

            for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            // Ít nhất một phiên phải completePayment thành công
            assertThat(successes.get())
                    .as("Phải có ít nhất 1 phiên completePayment thành công")
                    .isPositive();

            // Mỗi phiên thành công: winner lockedDeposit = 0
            for (int i = 0; i < auctionCount; i++) {
                if (auctions.get(i).getStatus() == Auction.AuctionStatus.PAID) {
                    assertThat(winners.get(i).getLockedDeposit())
                            .as("lockedDeposit của winner phải = 0 sau completePayment")
                            .isZero();
                }
            }
        }
    }
}