package com.group13.auction.integration.service.bid;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.unit.TestFixture;

import com.group13.auction.dao.*;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.*;
import com.group13.auction.strategy.StandardBidStrategy;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ════════════════════════════════════════════════════════════════════
 *  BidServiceIntegrationIT — Integration Tests cho BidService
 *  Kỹ thuật: Bottom-up (BidService + tất cả DAO thật + DB thật)
 * ════════════════════════════════════════════════════════════════════
 *
 *  Scope: Kiểm tra luồng joinAuction + placeBid với DB thực.
 *
 *  TC-04 [CRITICAL]: joinAuction — deposit lock + DB persist + seller guard
 *    BUG RISK: lockDeposit RAM và DB mất đồng bộ; seller tự bid;
 *    double-join không chặn (duplicate deposit).
 *
 *  TC-05 [CRITICAL]: placeBid — anti-sniping, reserve tracking, concurrent bids
 *    BUG RISK: 10 thread cùng bid → race condition → currentPrice sai;
 *    ACCEPTED_RESERVE_NOT_MET ghi sai DB.
 */
@RequiresDocker
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("BidServiceIntegrationIT — BidService × DAO × DB (Bottom-up)")
class BidServiceIntegrationIT extends IntegrationTestBase {

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

    private RatingService  ratingService;
    private WalletService  walletService;
    private AuctionService auctionService;
    private BidService     bidService;

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

        ratingService  = new RatingService(userDAO);
        TestFixture.bootstrapSystemAdmin();
        walletService  = new WalletService(financialTransactionDAO, userDAO, ratingService);
        auctionService = new AuctionService(ratingService, auctionDAO);
        bidService     = new BidService(auctionService, ratingService, walletService,
                                        bidTransactionDAO, auctionDAO, userDAO);
        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanupDB();
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // TC-04 — joinAuction()
    // =========================================================================

    @Nested
    @Order(1)
    @DisplayName("TC-04 [CRITICAL] joinAuction() — Deposit lock + DB consistency + Guard conditions")
    class JoinAuctionTests {

        @Test
        @Order(1)
        @DisplayName("TC-04a: joinAuction() happy path — deposit locked, DB balance khớp RAM")
        void joinAuction_happyPath_depositLockedAndDbConsistent() {
            // balance = 10M, deposit = 30% * 5M = 1.5M
            NormalUser bidder = givenUserWithBalance("join_b1", 10_000_000L);
            Auction auction   = givenRunningAuction("join_s1", 5_000_000L, 8_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);

            long balanceBefore = bidder.getBalance();
            long depositExpected = auction.getItem().getStartingPrice() * 3 / 10; // 1_500_000

            bidService.joinAuction(bidder, auction, obs);

            // RAM
            assertAll("RAM sau joinAuction",
                () -> assertThat(bidder.getLockedDeposit())
                        .as("lockedDeposit phải = 30% startingPrice")
                        .isEqualTo(depositExpected),
                () -> assertThat(bidder.getAvailableBalance())
                        .as("số dư khả dụng phải giảm đi bằng với deposit")
                        .isEqualTo(balanceBefore - depositExpected),
                () -> assertThat(bidder.hasJoined(auction.getId()))
                        .as("bidder phải được đánh dấu đã joined")
                        .isTrue()
            );

            // DB
            NormalUser fromDB = userDAO.findNormalUserById(bidder.getId());
            assertAll("DB khớp RAM sau joinAuction",
                () -> assertThat(fromDB.getBalance())
                        .isEqualTo(bidder.getBalance()),
                () -> assertThat(fromDB.getLockedDeposit())
                        .isEqualTo(bidder.getLockedDeposit())
            );

            // FinancialTransaction DEPOSIT_LOCK phải được ghi
            long lockedInDB = financialTransactionDAO.findLockedDepositAmount(
                    bidder.getId(), auction.getId());
            assertThat(lockedInDB)
                    .as("FinancialTransaction DEPOSIT_LOCK phải ghi đúng số tiền")
                    .isEqualTo(depositExpected);
        }

        @Test
        @Order(2)
        @DisplayName("TC-04b: Double-join cùng auction — idempotent, chỉ lock deposit 1 lần")
        void joinAuction_doubleJoin_idempotent_noDoubleDeposit() {
            NormalUser bidder = givenUserWithBalance("join_b2", 10_000_000L);
            Auction auction   = givenRunningAuction("join_s2", 5_000_000L, 8_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);

            bidService.joinAuction(bidder, auction, obs);
            long lockedAfterFirst = bidder.getLockedDeposit();

            // Gọi lần 2 — phải bị bỏ qua (idempotent)
            bidService.joinAuction(bidder, auction, obs);

            assertThat(bidder.getLockedDeposit())
                    .as("Không được lock deposit lần 2")
                    .isEqualTo(lockedAfterFirst);
        }

        @Test
        @Order(3)
        @DisplayName("TC-04c: Seller cố join phiên của chính mình — bị từ chối AuctionBusinessException")
        void joinAuction_sellerBidOwnAuction_rejected() {
            NormalUser seller = givenUserWithBalance("join_s3", 50_000_000L);
            // Gán role SELLER cho seller
            seller.addRole(User.UserRole.SELLER);
            Auction auction = givenRunningAuctionForSeller(seller, 5_000_000L, 8_000_000L);

            // Thêm auctionId vào allAuctionIds của seller để hệ thống nhận ra
            seller.addAuctionId(auction.getId());

            AuctionObserver obs = new BidderObserver(seller, null);

            assertThatThrownBy(() -> bidService.joinAuction(seller, auction, obs))
                    .isInstanceOf(AuctionBusinessException.class);

            // Không lock deposit
            assertThat(seller.getLockedDeposit()).isZero();
        }

        @Test
        @Order(4)
        @DisplayName("TC-04d: Bidder không đủ số dư để đặt cọc — bị từ chối, balance không thay đổi")
        void joinAuction_insufficientBalance_rejected_noSideEffect() {
            // startingPrice = 5M, deposit = 1.5M, nhưng bidder chỉ có 1M
            NormalUser bidder = givenUserWithBalance("join_b4", 1_000_000L);
            Auction auction   = givenRunningAuction("join_s4", 5_000_000L, 8_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);

            long balanceBefore = bidder.getBalance();

            assertThatThrownBy(() -> bidService.joinAuction(bidder, auction, obs))
                    .isInstanceOf(AuctionBusinessException.class);

            assertAll("Không có side-effect khi join thất bại",
                () -> assertThat(bidder.getBalance()).isEqualTo(balanceBefore),
                () -> assertThat(bidder.getLockedDeposit()).isZero(),
                () -> assertThat(bidder.hasJoined(auction.getId())).isFalse()
            );
        }
    }

    // =========================================================================
    // TC-05 — placeBid()
    // =========================================================================

    @Nested
    @Order(2)
    @DisplayName("TC-05 [CRITICAL] placeBid() — BidTransaction, currentPrice, concurrent safety")
    class PlaceBidTests {

        @Test
        @Order(1)
        @DisplayName("TC-05a: Happy path — placeBid() ghi BidTransaction ACCEPTED, DB currentPrice cập nhật")
        void placeBid_happyPath_transactionSavedAndPriceUpdated() {
            NormalUser bidder = givenUserWithBalance("bid_b1", 20_000_000L);
            Auction auction   = givenRunningAuction("bid_s1", 5_000_000L, 8_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);
            bidService.joinAuction(bidder, auction, obs);

            long bidAmount = 9_000_000L; // > startingPrice, < reserve
            bidService.placeBid(bidder, auction, bidAmount, new StandardBidStrategy());

            // currentPrice RAM phải cập nhật
            assertThat(auction.getCurrentPrice()).isEqualTo(bidAmount);
            assertThat(auction.getCurrentLeader()).isEqualTo(bidder);

            // DB currentPrice phải cập nhật
            Auction fromDB = auctionDAO.findAuctionById(auction.getId());
            assertThat(fromDB.getCurrentPrice()).isEqualTo(bidAmount);

            // BidTransaction ACCEPTED phải được ghi
            List<BidTransaction> history = bidTransactionDAO.findByAuctionId(auction.getId());
            assertThat(history).isNotEmpty();
            assertThat(history.get(history.size() - 1).getResult())
                    .isEqualTo(BidTransaction.BidResult.ACCEPTED);
        }

        @Test
        @Order(2)
        @DisplayName("TC-05b: Bid dưới giá tối thiểu — REJECTED, currentPrice không thay đổi")
        void placeBid_belowMinIncrement_rejected_priceUnchanged() {
            NormalUser bidder = givenUserWithBalance("bid_b2", 20_000_000L);
            Auction auction   = givenRunningAuction("bid_s2", 5_000_000L, 8_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);
            bidService.joinAuction(bidder, auction, obs);

            long priceBeforeBid = auction.getCurrentPrice();
            // Bid không đủ increment (dưới startingPrice)
            assertThatThrownBy(() ->
                bidService.placeBid(bidder, auction, 100L, new StandardBidStrategy()))
                .isInstanceOf(InvalidBidException.class);

            assertThat(auction.getCurrentPrice()).isEqualTo(priceBeforeBid);
        }

        @Test
        @Order(3)
        @DisplayName("TC-05c: Bid >= reservePrice → BidResult.ACCEPTED (không phải ACCEPTED_RESERVE_NOT_MET)")
        void placeBid_aboveReserve_resultIsAccepted() {
            NormalUser bidder = givenUserWithBalance("bid_b3", 20_000_000L);
            Auction auction   = givenRunningAuction("bid_s3", 5_000_000L, 7_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);
            bidService.joinAuction(bidder, auction, obs);

            // Bid vượt reserve
            bidService.placeBid(bidder, auction, 8_000_000L, new StandardBidStrategy());

            assertThat(auction.isReserveMet()).isTrue();

            List<BidTransaction> txs = bidTransactionDAO.findByAuctionId(auction.getId());
            assertThat(txs).isNotEmpty();
            assertThat(txs.get(txs.size() - 1).getResult())
                    .as("Bid vượt reserve phải là ACCEPTED")
                    .isEqualTo(BidTransaction.BidResult.ACCEPTED);
        }

        @Test
        @Order(4)
        @DisplayName("TC-05d: Bid < reservePrice → BidResult.ACCEPTED_RESERVE_NOT_MET được ghi vào DB")
        void placeBid_belowReserve_resultIsReserveNotMet() {
            NormalUser bidder = givenUserWithBalance("bid_b4", 20_000_000L);
            // reserve = 10M, bid sẽ < reserve
            Auction auction   = givenRunningAuction("bid_s4", 5_000_000L, 10_000_000L);
            AuctionObserver obs = new BidderObserver(bidder, null);
            bidService.joinAuction(bidder, auction, obs);

            bidService.placeBid(bidder, auction, 6_000_000L, new StandardBidStrategy());

            assertThat(auction.isReserveMet()).isFalse();

            List<BidTransaction> txs = bidTransactionDAO.findByAuctionId(auction.getId());
            assertThat(txs).isNotEmpty();
            assertThat(txs.get(txs.size() - 1).getResult())
                    .isEqualTo(BidTransaction.BidResult.ACCEPTED_RESERVE_NOT_MET);
        }

        @Test
        @Order(5)
        @DisplayName("TC-05e: [CONCURRENT] 10 thread cùng placeBid — chỉ 1 winner, price tăng monotonic")
        void placeBid_concurrent10Threads_onlyOneLeader_priceMonotonic()
                throws InterruptedException {
            Auction auction  = givenRunningAuction("bid_s5", 1_000_000L, 2_000_000L);
            int threadCount  = 10;
            long baseBalance = 50_000_000L;

            // Tạo 10 bidder và join
            NormalUser[] bidders = new NormalUser[threadCount];
            for (int i = 0; i < threadCount; i++) {
                bidders[i] = givenUserWithBalance("bid_b5_" + i, baseBalance);
                bidService.joinAuction(bidders[i], auction,
                        new BidderObserver(bidders[i], null));
            }

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);
            CountDownLatch startGun    = new CountDownLatch(1);
            CountDownLatch done        = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final NormalUser bidder = bidders[i];
                final long amount = 2_000_000L + (i * 100_000L); // khác nhau để tránh tie
                new Thread(() -> {
                    try {
                        startGun.await();
                        bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            startGun.countDown();
            done.await(10, TimeUnit.SECONDS);

            // currentPrice phải >= startingPrice và currentLeader không null
            assertAll("Concurrent bid — price monotonic, leader valid",
                () -> assertThat(auction.getCurrentPrice())
                        .as("currentPrice phải >= startingPrice")
                        .isGreaterThanOrEqualTo(1_000_000L),
                () -> assertThat(auction.getCurrentLeader())
                        .as("Phải có 1 currentLeader sau khi bid")
                        .isNotNull(),
                () -> assertThat(successCount.get() + failCount.get())
                        .as("Tổng thành công + thất bại = threadCount")
                        .isEqualTo(threadCount)
            );
        }

        @Test
        @Order(6)
        @DisplayName("TC-05f: Anti-sniping — bid trong 30s cuối phải gia hạn end_time")
        void placeBid_withinAntiSnipingWindow_extendsEndTime() {
            NormalUser bidder = givenUserWithBalance("bid_b6", 20_000_000L);
            // end_time = 20 giây từ bây giờ → sẽ kích hoạt anti-sniping (< 30s)
            NormalUser seller = givenUserWithBalance("bid_s6", 50_000_000L);
            String itemId = buildItem(seller.getId(), "Anti-snipe Item", 5_000_000L, itemDAO);
            Item item = itemDAO.findItemById(itemId);
            Auction auction = Auction.create(item,
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusSeconds(20), // 20s còn lại
                    8_000_000L);
            auctionDAO.createAuction(auction);
            auctionService.startAuction(auction);
            trackAuction(auction.getId());

            AuctionObserver obs = new BidderObserver(bidder, null);
            bidService.joinAuction(bidder, auction, obs);

            LocalDateTime originalEnd = auction.getEndTime();
            bidService.placeBid(bidder, auction, 6_000_000L, new StandardBidStrategy());

            assertThat(auction.getEndTime())
                    .as("Anti-sniping phải gia hạn end_time")
                    .isAfter(originalEnd);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NormalUser givenUserWithBalance(String username, long balance) {
        return buildUserWithBalance(username, balance, userDAO);
    }

    private Auction givenRunningAuction(String sellerUsername,
                                         long startingPrice, long reservePrice) {
        NormalUser seller = buildUserWithBalance(sellerUsername, 50_000_000L, userDAO);
        String itemId = buildItem(seller.getId(), "Item-" + sellerUsername,
                startingPrice, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction = Auction.create(item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                reservePrice);
        auctionDAO.createAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());
        return auction;
    }

    private Auction givenRunningAuctionForSeller(NormalUser seller,
                                                   long startingPrice, long reservePrice) {
        String itemId = buildItem(seller.getId(), "SellerItem", startingPrice, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction = Auction.create(item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                reservePrice);
        auctionDAO.createAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());
        return auction;
    }
}
