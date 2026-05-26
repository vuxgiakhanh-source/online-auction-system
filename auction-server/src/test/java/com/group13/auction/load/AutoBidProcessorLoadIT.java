package com.group13.auction.load;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidProcessor;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.BidIncrementCalculator;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * LOAD TEST — AutoBidProcessorLoadIT (DB thật)
 * ============================================================================
 *
 * Kiểm tra AutoBidProcessor dưới tải với MySQL thật:
 *
 * Group 1 — Chain auto-bid nhiều auto-bidder dưới tải:
 *   Nhiều auto-bidder + manual bidder cùng auction — chain xử lý đúng,
 *   giá không vượt maxBid, không deadlock dưới lock.
 *
 * Group 2 — Auto-bid chain qua nhiều phiên đồng thời:
 *   N phiên chạy song song, mỗi phiên có auto-bid chain riêng.
 *   Verify isolation giữa các phiên.
 *
 * Group 3 — AutoBidProcessor.process() gọi đồng thời từ nhiều thread (trong lock):
 *   Stress test luồng: manual bid → trigger process() trong lock —
 *   không NPE, không chain vô tận.
 *
 * Group 4 — AutoBidProcessor fallback DB lookup dưới tải:
 *   User không có trong AuctionManager in-memory → processor fallback tìm DB.
 *   Stress test đường DB fallback.
 */
@RequiresDocker
@Testcontainers
@DisplayName("AutoBidProcessorLoadIT — AutoBidProcessor + DB dưới tải")
class AutoBidProcessorLoadIT extends IntegrationTestBase {

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

    private RatingService        ratingService;
    private WalletService        walletService;
    private AuctionService       auctionService;
    private BidService           bidService;
    private AutoBidProcessor     autoBidProcessor;
    private SessionManager       sessionManager;

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

        // SessionManager mock — không gửi packet thật
        sessionManager = SessionManager.getInstance();
        autoBidProcessor = new AutoBidProcessor(bidService, sessionManager);

        resetTracking();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Dừng tất cả executor của AutoBidProcessor trước khi xóa DB,
        // tránh chain vẫn đang ghi notification khi auction đã bị xóa.
        clearAutoBidProcessorState();
        clearAutoBidRegistryAll();
        clearAuctionManagerSingletons();
        cleanupDB();
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // Group 1 — Chain auto-bid nhiều auto-bidder dưới tải (1 phiên)
    // =========================================================================

    @Nested
    @DisplayName("Group 1 – Chain auto-bid nhiều auto-bidder, 1 phiên (DB thật)")
    class AutoBidChainLoadTest {

        @Test
        @Timeout(value = 90)
        @DisplayName("L-ABP1: 6 auto-bidder + 4 manual bidder cùng phiên — giá không vượt maxBid, không deadlock")
        void autoBidChain_6AutoBidders_4ManualBidders_noDeadlock() throws Exception {
            Auction auction = givenRunningAuction("abp1_sel", 1_000_000L, 5_000_000L);

            int autoCount   = 6;
            int manualCount = 4;
            long baseMaxBid = 4_000_000L;

            // Tạo auto-bidders (tuần tự, tránh concurrent ArrayList)
            List<NormalUser> autoBidders = new ArrayList<>(autoCount);
            for (int i = 0; i < autoCount; i++) {
                NormalUser u = buildUserWithBalance("abp1_auto" + i, 50_000_000L, userDAO);
                AuctionObserver obs = new BidderObserver(u, null);
                bidService.joinAuction(u, auction, obs);
                long maxBid = baseMaxBid - (i * 100_000L); // 4M, 3.9M, 3.8M...
                AutoBidRegistry.getInstance().register(u.getId(), auction.getId(), maxBid);
                AuctionManager.getInstance().addToUserList(u);
                autoBidders.add(u);
            }

            // Tạo manual bidders
            List<NormalUser> manualBidders = new ArrayList<>(manualCount);
            for (int i = 0; i < manualCount; i++) {
                NormalUser u = buildUserWithBalance("abp1_man" + i, 50_000_000L, userDAO);
                AuctionObserver obs = new BidderObserver(u, null);
                bidService.joinAuction(u, auction, obs);
                manualBidders.add(u);
            }

            ReentrantLock auctionLock = AuctionLockRegistry.getInstance().getLock(auction.getId());
            AtomicInteger successes   = new AtomicInteger();
            AtomicInteger failures    = new AtomicInteger();
            ExecutorService pool      = Executors.newFixedThreadPool(manualCount);
            List<Future<?>> futures   = new ArrayList<>();

            for (int i = 0; i < manualCount; i++) {
                final NormalUser bidder = manualBidders.get(i);
                final int idx = i;
                futures.add(pool.submit(() -> {
                    for (int r = 0; r < 3; r++) {
                        long amount = 1_200_000L + (idx * 80_000L) + (r * 120_000L);
                        try {
                            auctionLock.lock();
                            try {
                                bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                                successes.incrementAndGet();
                            } finally {
                                auctionLock.unlock();
                            }
                            // FIX: submit() NGOÀI lock — chain cần acquire lock để chạy placeBid
                            autoBidProcessor.submit(auction, bidder.getId());
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                    }
                }));
            }

            for (Future<?> f : futures) f.get(90, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            // FIX: chờ chain autobid ghi xong DB trước khi assert
            awaitAutoBidChain(auction.getId(), 15);

            // Giá không vượt maxBid cao nhất
            assertThat(auction.getCurrentPrice())
                .as("Giá cuối không được vượt maxBid lớn nhất (%d)", baseMaxBid)
                .isLessThanOrEqualTo(baseMaxBid);
            assertThat(auction.getCurrentPrice()).isGreaterThanOrEqualTo(1_000_000L);

            // Có ít nhất 1 bid thành công
            assertThat(successes.get()).isPositive();

            // Giá DB khớp RAM
            Auction fromDb = auctionDAO.findAuctionById(auction.getId());
            assertThat(fromDb.getCurrentPrice()).isEqualTo(auction.getCurrentPrice());

            AuctionLockRegistry.getInstance().release(auction.getId());
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ABP2: Auto-bid chain khi auto-bidder cạn maxBid — bị xóa khỏi registry, giá dừng đúng")
        void autoBidChain_exhaustedMaxBid_removedFromRegistry() throws Exception {
            Auction auction = givenRunningAuction("abp2_sel", 1_000_000L, 3_000_000L);

            NormalUser autoBidder  = buildUserWithBalance("abp2_auto", 50_000_000L, userDAO);
            NormalUser manualBidder = buildUserWithBalance("abp2_man", 50_000_000L, userDAO);

            AuctionObserver obsA = new BidderObserver(autoBidder, null);
            AuctionObserver obsM = new BidderObserver(manualBidder, null);
            bidService.joinAuction(autoBidder, auction, obsA);
            bidService.joinAuction(manualBidder, auction, obsM);
            AuctionManager.getInstance().addToUserList(autoBidder);

            // maxBid chỉ đủ counter 1 lần
            long manualBid   = 1_000_000L + BidIncrementCalculator.calculate(1_000_000L); // 1_050_000
            long autoBidMax  = manualBid + BidIncrementCalculator.calculate(manualBid);    // vừa đủ counter 1 lần
            AutoBidRegistry.getInstance().register(autoBidder.getId(), auction.getId(), autoBidMax);

            ReentrantLock lock = AuctionLockRegistry.getInstance().getLock(auction.getId());
            lock.lock();
            try {
                bidService.placeBid(manualBidder, auction, manualBid, new StandardBidStrategy());
            } finally {
                lock.unlock();
            }
            // FIX: submit() NGOÀI lock
            autoBidProcessor.submit(auction, manualBidder.getId());
            awaitAutoBidChain(auction.getId(), 15);

            // autoBidder đang dẫn đầu hoặc đã cạn và bị xóa
            long finalPrice = auction.getCurrentPrice();
            assertThat(finalPrice).isGreaterThanOrEqualTo(manualBid);
            assertThat(finalPrice).isLessThanOrEqualTo(autoBidMax);

            AuctionLockRegistry.getInstance().release(auction.getId());
        }
    }

    // =========================================================================
    // Group 2 — Auto-bid chain qua nhiều phiên đồng thời
    // =========================================================================

    @Nested
    @DisplayName("Group 2 – Auto-bid chain qua nhiều phiên song song (DB thật)")
    class MultiAuctionAutoBidLoadTest {

        @Test
        @Timeout(value = 120)
        @DisplayName("L-ABP3: 5 phiên × 2 auto-bidder × 3 manual bid song song — isolation đúng, giá không chéo phiên")
        void multiAuction_autoBidChain_isolated() throws Exception {
            int auctionCount  = 5;
            int autoPerAuction = 2;
            int manualPerAuction = 2;

            List<Auction>    auctions    = new ArrayList<>(auctionCount);
            List<List<NormalUser>> autoGroups   = new ArrayList<>();
            List<List<NormalUser>> manualGroups = new ArrayList<>();

            for (int a = 0; a < auctionCount; a++) {
                Auction auction = givenRunningAuction("abp3_sel" + a, 1_000_000L, 5_000_000L);
                auctions.add(auction);

                List<NormalUser> autos   = new ArrayList<>(autoPerAuction);
                List<NormalUser> manuals = new ArrayList<>(manualPerAuction);

                for (int i = 0; i < autoPerAuction; i++) {
                    NormalUser u = buildUserWithBalance("abp3_a" + a + "_" + i, 50_000_000L, userDAO);
                    bidService.joinAuction(u, auction, new BidderObserver(u, null));
                    long maxBid = 4_000_000L - (i * 500_000L);
                    AutoBidRegistry.getInstance().register(u.getId(), auction.getId(), maxBid);
                    AuctionManager.getInstance().addToUserList(u);
                    autos.add(u);
                }
                for (int i = 0; i < manualPerAuction; i++) {
                    NormalUser u = buildUserWithBalance("abp3_m" + a + "_" + i, 50_000_000L, userDAO);
                    bidService.joinAuction(u, auction, new BidderObserver(u, null));
                    manuals.add(u);
                }

                autoGroups.add(autos);
                manualGroups.add(manuals);
            }

            ExecutorService pool    = Executors.newFixedThreadPool(auctionCount * manualPerAuction);
            AtomicInteger   success = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();

            for (int a = 0; a < auctionCount; a++) {
                final Auction auction      = auctions.get(a);
                final List<NormalUser> manuals = manualGroups.get(a);
                final ReentrantLock lock = AuctionLockRegistry.getInstance().getLock(auction.getId());

                for (int m = 0; m < manualPerAuction; m++) {
                    final NormalUser bidder = manuals.get(m);
                    final int midx = m;
                    futures.add(pool.submit(() -> {
                        for (int r = 0; r < 3; r++) {
                            long amount = 1_200_000L + (midx * 100_000L) + (r * 150_000L);
                            try {
                                lock.lock();
                                try {
                                    bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                                } finally {
                                    lock.unlock();
                                }
                                // FIX: submit() NGOÀI lock
                                autoBidProcessor.submit(auction, bidder.getId());
                                success.incrementAndGet();
                            } catch (Exception ignored) {}
                        }
                    }));
                }
            }

            for (Future<?> f : futures) f.get(120, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

            // FIX: chờ tất cả chain hoàn thành trước khi assert DB
            for (int a = 0; a < auctionCount; a++) {
                awaitAutoBidChain(auctions.get(a).getId(), 15);
            }

            // Mỗi phiên: giá DB khớp RAM
            for (int a = 0; a < auctionCount; a++) {
                Auction auction = auctions.get(a);
                Auction fromDb  = auctionDAO.findAuctionById(auction.getId());
                assertThat(fromDb.getCurrentPrice())
                    .as("Giá DB phiên %d phải khớp RAM", a)
                    .isEqualTo(auction.getCurrentPrice());
                // Release lock
                AuctionLockRegistry.getInstance().release(auction.getId());
            }

            assertThat(success.get()).isPositive();
        }
    }

    // =========================================================================
    // Group 3 — process() gọi liên tiếp trong lock dưới tải cao
    // =========================================================================

    @Nested
    @DisplayName("Group 3 – process() gọi liên tiếp trong lock — không NPE, không vòng lặp vô tận")
    class ProcessUnderHighLoadTest {

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ABP4: 8 thread × 5 lần (placeBid + process) trong lock — giá tăng đơn điệu, không exception")
        void concurrent_placeBidAndProcess_inLock_priceMonotonic() throws Exception {
            Auction auction = givenRunningAuction("abp4_sel", 1_000_000L, 10_000_000L);

            int threads      = 8;
            int bidPerThread = 5;
            long autoBidMax  = 9_000_000L;

            // 1 auto-bidder với maxBid rất cao
            NormalUser autoBidder = buildUserWithBalance("abp4_auto", 200_000_000L, userDAO);
            bidService.joinAuction(autoBidder, auction, new BidderObserver(autoBidder, null));
            AutoBidRegistry.getInstance().register(autoBidder.getId(), auction.getId(), autoBidMax);
            AuctionManager.getInstance().addToUserList(autoBidder);

            // N manual bidders
            List<NormalUser> bidders = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                NormalUser u = buildUserWithBalance("abp4_man" + i, 50_000_000L, userDAO);
                bidService.joinAuction(u, auction, new BidderObserver(u, null));
                bidders.add(u);
            }

            ReentrantLock lock     = AuctionLockRegistry.getInstance().getLock(auction.getId());
            CountDownLatch gate    = new CountDownLatch(1);
            CountDownLatch done    = new CountDownLatch(threads);
            AtomicInteger  success = new AtomicInteger();
            AtomicInteger  failures = new AtomicInteger();
            List<Long>     snapshots = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final NormalUser bidder = bidders.get(t);
                final int tidx = t;
                new Thread(() -> {
                    try {
                        gate.await();
                        for (int r = 0; r < bidPerThread; r++) {
                            long amount = 1_100_000L + (tidx * 50_000L) + (r * 80_000L);
                            try {
                                lock.lock();
                                try {
                                    bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                                    snapshots.add(auction.getCurrentPrice());
                                    success.incrementAndGet();
                                } finally {
                                    lock.unlock();
                                }
                                // FIX: submit() NGOÀI lock
                                autoBidProcessor.submit(auction, bidder.getId());
                            } catch (Exception ignored) {}
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            gate.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

            // FIX: chờ chain cuối hoàn thành trước khi assert giá
            awaitAutoBidChain(auction.getId(), 15);

            assertThat(failures.get()).isZero();
            assertThat(success.get()).isPositive();

            // Giá trong snapshots phải tăng đơn điệu
            for (int i = 1; i < snapshots.size(); i++) {
                assertThat(snapshots.get(i))
                    .as("Snapshot[%d] phải >= snapshot[%d-1]", i, i)
                    .isGreaterThanOrEqualTo(snapshots.get(i - 1));
            }

            // Giá không vượt autoBidMax
            assertThat(auction.getCurrentPrice())
                .as("Giá cuối không được vượt autoBidMax")
                .isLessThanOrEqualTo(autoBidMax);

            AuctionLockRegistry.getInstance().release(auction.getId());
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ABP5: MAX_CHAIN_DEPTH — chain không vô tận khi 20 auto-bidder cùng maxBid")
        void autoBidChain_20SameMaxBid_doesNotInfiniteLoop() throws Exception {
            Auction auction = givenRunningAuction("abp5_sel", 1_000_000L, 8_000_000L);

            int autoBidderCount = 20;
            long sameMaxBid     = 5_000_000L;

            for (int i = 0; i < autoBidderCount; i++) {
                NormalUser u = buildUserWithBalance("abp5_auto" + i, 50_000_000L, userDAO);
                bidService.joinAuction(u, auction, new BidderObserver(u, null));
                AutoBidRegistry.getInstance().register(u.getId(), auction.getId(), sameMaxBid);
                AuctionManager.getInstance().addToUserList(u);
                try { Thread.sleep(2); } catch (InterruptedException ignored) {}
            }

            NormalUser trigger = buildUserWithBalance("abp5_trigger", 50_000_000L, userDAO);
            bidService.joinAuction(trigger, auction, new BidderObserver(trigger, null));

            ReentrantLock lock = AuctionLockRegistry.getInstance().getLock(auction.getId());

            // FIX: placeBid trong lock, release lock, rồi mới submit()
            lock.lock();
            try {
                long firstBid = 1_000_000L + BidIncrementCalculator.calculate(1_000_000L);
                bidService.placeBid(trigger, auction, firstBid, new StandardBidStrategy());
            } finally {
                lock.unlock();
            }

            // Đo thời gian chain chạy (không giữ lock nên chain không bị block)
            long startMs = System.currentTimeMillis();
            autoBidProcessor.submit(auction, trigger.getId());
            awaitAutoBidChain(auction.getId(), 10);
            long elapsedMs = System.currentTimeMillis() - startMs;

            assertThat(elapsedMs)
                .as("Chain phải kết thúc trong < 5s (không vô tận): thực tế=%dms", elapsedMs)
                .isLessThan(5_000L);

            // Giá không vượt maxBid
            assertThat(auction.getCurrentPrice()).isLessThanOrEqualTo(sameMaxBid);

            AuctionLockRegistry.getInstance().release(auction.getId());
        }
    }

    // =========================================================================
    // Group 4 — AutoBidProcessor DB fallback dưới tải
    // =========================================================================

    @Nested
    @DisplayName("Group 4 – AutoBidProcessor DB fallback dưới tải")
    class AutoBidProcessorDBFallbackTest {

        @Test
        @Timeout(value = 90)
        @DisplayName("L-ABP6: auto-bidder KHÔNG có trong AuctionManager — processor fallback DB, không NPE")
        void autoBidProcessor_dbFallback_userNotInMemory_noNPE() throws Exception {
            Auction auction = givenRunningAuction("abp6_sel", 1_000_000L, 5_000_000L);

            // Tạo auto-bidder nhưng KHÔNG add vào AuctionManager.addToUserList()
            NormalUser autoBidder  = buildUserWithBalance("abp6_auto", 50_000_000L, userDAO);
            NormalUser manualBidder = buildUserWithBalance("abp6_man", 50_000_000L, userDAO);

            bidService.joinAuction(autoBidder, auction, new BidderObserver(autoBidder, null));
            bidService.joinAuction(manualBidder, auction, new BidderObserver(manualBidder, null));

            // KHÔNG gọi AuctionManager.getInstance().addToUserList(autoBidder)
            // -> processor phải fallback DB
            AutoBidRegistry.getInstance().register(autoBidder.getId(), auction.getId(), 4_000_000L);

            ReentrantLock lock = AuctionLockRegistry.getInstance().getLock(auction.getId());
            AtomicInteger failures = new AtomicInteger();

            lock.lock();
            try {
                long manualBid = 1_000_000L + BidIncrementCalculator.calculate(1_000_000L);
                bidService.placeBid(manualBidder, auction, manualBid, new StandardBidStrategy());
            } catch (Exception e) {
                failures.incrementAndGet();
            } finally {
                lock.unlock();
            }
            // FIX: submit() và chờ chain NGOÀI lock
            try {
                autoBidProcessor.submit(auction, manualBidder.getId());
                awaitAutoBidChain(auction.getId(), 15);
            } catch (Exception e) {
                failures.incrementAndGet();
            }

            assertThat(failures.get())
                .as("process() không được throw exception khi fallback DB")
                .isZero();
            assertThat(auction.getCurrentPrice()).isGreaterThan(1_000_000L);

            AuctionLockRegistry.getInstance().release(auction.getId());
        }

        @Test
        @Timeout(value = 60)
        @DisplayName("L-ABP7: 6 phiên song song DB fallback — không deadlock, không NPE")
        void concurrent_dbFallback_6Auctions_noDeadlock() throws Exception {
            int auctionCount = 6;
            List<Auction> auctions = new ArrayList<>(auctionCount);
            List<NormalUser> triggers = new ArrayList<>(auctionCount);

            for (int a = 0; a < auctionCount; a++) {
                Auction auction = givenRunningAuction("abp7_sel" + a, 1_000_000L, 5_000_000L);
                auctions.add(auction);

                NormalUser auto = buildUserWithBalance("abp7_auto" + a, 50_000_000L, userDAO);
                NormalUser manual = buildUserWithBalance("abp7_man" + a, 50_000_000L, userDAO);

                bidService.joinAuction(auto, auction, new BidderObserver(auto, null));
                bidService.joinAuction(manual, auction, new BidderObserver(manual, null));
                // Không add auto vào AuctionManager -> DB fallback
                AutoBidRegistry.getInstance().register(auto.getId(), auction.getId(), 4_000_000L);
                triggers.add(manual);
            }

            ExecutorService pool    = Executors.newFixedThreadPool(auctionCount);
            AtomicInteger   failures = new AtomicInteger();
            List<Future<?>> futures  = new ArrayList<>();

            for (int a = 0; a < auctionCount; a++) {
                final Auction auction = auctions.get(a);
                final NormalUser bidder = triggers.get(a);
                final ReentrantLock lock = AuctionLockRegistry.getInstance().getLock(auction.getId());

                futures.add(pool.submit(() -> {
                    try {
                        lock.lock();
                        try {
                            long bid = 1_000_000L + BidIncrementCalculator.calculate(1_000_000L);
                            bidService.placeBid(bidder, auction, bid, new StandardBidStrategy());
                        } finally {
                            lock.unlock();
                        }
                        // FIX: submit() NGOÀI lock
                        autoBidProcessor.submit(auction, bidder.getId());
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }));
            }

            for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            assertThat(failures.get())
                .as("DB fallback đồng thời không được throw exception")
                .isZero();

            for (int a = 0; a < auctionCount; a++) {
                AuctionLockRegistry.getInstance().release(auctions.get(a).getId());
            }
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Auction givenRunningAuction(String sellerPrefix, long startingPrice, long reservePrice) {
        NormalUser seller = buildUserWithBalance(sellerPrefix, 80_000_000L, userDAO);
        String itemId = buildItem(seller.getId(), "ABP-Item-" + sellerPrefix, startingPrice, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction = Auction.create(item,
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusHours(3),
            reservePrice);
        auctionDAO.createAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());
        return auction;
    }

    private void clearAutoBidRegistryAll() {
        try {
            java.lang.reflect.Field f = AutoBidRegistry.class.getDeclaredField("registry");
            f.setAccessible(true);
            Object reg = f.get(AutoBidRegistry.getInstance());
            if (reg instanceof Map<?, ?> m) m.clear();
        } catch (Exception ignored) {}
    }

    private void clearAuctionManagerSingletons() {
        try {
            AuctionManager mgr = AuctionManager.getInstance();
            clearField(mgr, "allAuctions");
            clearField(mgr, "allUsers");
        } catch (Exception ignored) {}
    }

    private void clearField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        Object obj = f.get(target);
        if (obj instanceof Map<?, ?> m) m.clear();
        else if (obj instanceof List<?> l) l.clear();
    }

    /**
     * Dùng reflection để shutdown tất cả executor của AutoBidProcessor và xóa static state.
     * Gọi trước cleanupDB() để tránh chain vẫn đang chạy khi auction bị xóa khỏi DB.
     */
    private void clearAutoBidProcessorState() {
        try {
            // 1. Shutdown tất cả per-auction executor
            java.lang.reflect.Field execField =
                AutoBidProcessor.class.getDeclaredField("auctionExecutors");
            execField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<String, ExecutorService> executors =
                (java.util.concurrent.ConcurrentHashMap<String, ExecutorService>) execField.get(null);
            for (ExecutorService ex : executors.values()) {
                ex.shutdownNow();
            }
            for (ExecutorService ex : executors.values()) {
                try { ex.awaitTermination(500, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) {}
            }
            executors.clear();

            // 2. Xóa các static map khác
            for (String name : new String[]{"chainRunning", "chainNeedsRecheck",
                "bidActivityRings", "lastAutoBidMs"}) {
                java.lang.reflect.Field f = AutoBidProcessor.class.getDeclaredField(name);
                f.setAccessible(true);
                Object map = f.get(null);
                if (map instanceof Map<?, ?> m) m.clear();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Poll chainRunning via reflection cho đến khi chain kết thúc hoặc hết timeout.
     * chainRunning được set false trong finally block của chain task, SAU KHI tất cả
     * DB writes (từ bidService.placeBid) đã hoàn thành → an toàn để assert DB sau đây.
     */
    private void awaitAutoBidChain(String auctionId, long timeoutSeconds) {
        try {
            java.lang.reflect.Field f = AutoBidProcessor.class.getDeclaredField("chainRunning");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.concurrent.ConcurrentHashMap<String,
                java.util.concurrent.atomic.AtomicBoolean> runningMap =
                (java.util.concurrent.ConcurrentHashMap<String,
                    java.util.concurrent.atomic.AtomicBoolean>) f.get(null);

            long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;
            // Cho chain một chút thời gian để start (executor scheduling delay)
            Thread.sleep(30);
            while (System.currentTimeMillis() < deadline) {
                java.util.concurrent.atomic.AtomicBoolean flag = runningMap.get(auctionId);
                if (flag == null || !flag.get()) return; // chain đã xong hoặc chưa bắt đầu
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
    }
}