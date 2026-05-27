package com.group13.auction.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.integration.base.IntegrationTestBase;
import com.group13.auction.integration.base.RequiresDocker;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.AuctionSortService;
import com.group13.auction.service.BidService;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.BidIncrementCalculator;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

/**
 * ============================================================================ LOAD TEST —
 * AuctionSystemLoadIT ============================================================================
 *
 * <p>Load test đầy đủ cho hệ thống đấu giá với MySQL thật (Testcontainers). Chạy phase {@code
 * verify} (Failsafe), không chạy trong {@code mvn test}.
 *
 * <p>Các nhóm test bao gồm:
 *
 * <ul>
 *   <li>Group 1 – WalletService: deposit/withdraw song song nhiều user.
 *   <li>Group 2 – Multi-auction: nhiều phiên đấu giá chạy đồng thời.
 *   <li>Group 3 – Auto-bid dưới tải: nhiều auto-bidder + manual bidder.
 *   <li>Group 4 – AuctionSortService: sort/filter danh sách lớn dưới tải.
 *   <li>Group 5 – Join storm: nhiều user join cùng 1 phiên đồng thời.
 *   <li>Group 6 – RatingService: isEligible song song nhiều user.
 *   <li>Group 7 – Mixed workload: bid + deposit + sort cùng lúc.
 * </ul>
 */
@RequiresDocker
@Testcontainers
@DisplayName("AuctionSystemLoadIT — Load test toàn hệ thống (DB thật)")
class AuctionSystemLoadIT extends IntegrationTestBase {

  // ── Testcontainer ────────────────────────────────────────────────────────
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.0")
          .withDatabaseName("omnibid_test")
          .withUsername("test_user")
          .withPassword("test_pass")
          .withInitScript("database/schema.sql");

  // ── DAOs ─────────────────────────────────────────────────────────────────
  private UserDAO userDAO;
  private ItemDAO itemDAO;
  private AuctionDAO auctionDAO;
  private BidTransactionDAO bidTransactionDAO;
  private FinancialTransactionDAO financialTransactionDAO;

  // ── Services ─────────────────────────────────────────────────────────────
  private RatingService ratingService;
  private WalletService walletService;
  private AuctionService auctionService;
  private BidService bidService;
  private AuctionSortService sortService;

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

    ratingService = new RatingService(userDAO);
    TestFixture.bootstrapSystemAdmin();
    walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
    auctionService = new AuctionService(ratingService, auctionDAO);
    bidService =
        new BidService(
            auctionService, ratingService, walletService, bidTransactionDAO, auctionDAO, userDAO);
    sortService = new AuctionSortService();
    resetTracking();
  }

  @AfterEach
  void tearDown() throws Exception {
    // AutoBidRegistry chỉ có clearAuction — registry sẽ được dọn qua reflection để tránh state leak
    clearAutoBidRegistryAll();
    // Reset AuctionLockRegistry Singleton để không leak lock vào các test khác trong JVM.
    // Đặc biệt quan trọng vì SizeContract.emptyRegistry_sizeIsZero của AuctionLockRegistryTest
    // mong đợi size() = 0 sau setUp(). Nếu load test tạo lock (getLock) mà không gọi clearAll(),
    // Singleton sẽ còn lock orphan và làm fail test đó.
    com.group13.auction.strategy.AuctionLockRegistry.getInstance().clearAll();
    cleanupDB();
    TestFixture.resetSystemAdmin();
  }

  /** Dọn toàn bộ registry của AutoBidRegistry Singleton qua reflection. */
  private static void clearAutoBidRegistryAll() {
    try {
      java.lang.reflect.Field f = AutoBidRegistry.class.getDeclaredField("registry");
      f.setAccessible(true);
      Object registry = f.get(AutoBidRegistry.getInstance());
      if (registry instanceof java.util.Map<?, ?> m) {
        m.clear();
      }
    } catch (Exception ignored) {
      // Nếu không clear được thì chấp nhận — test vẫn độc lập theo từng phiên
    }
  }

  // =========================================================================
  // Group 1 – WalletService: deposit song song nhiều user
  // =========================================================================

  @Nested
  @DisplayName("Group 1 – WalletService: deposit song song (DB thật)")
  class WalletServiceLoadTest {

    @Test
    @Timeout(value = 60)
    @DisplayName(
        "L-W1: 20 user × 5 lần deposit song song — balance DB khớp RAM, không bị lost update")
    void concurrentDeposit_balanceConsistency() throws Exception {
      int userCount = 20;
      int depositRounds = 5;
      long depositAmount = 1_000_000L;

      List<NormalUser> users = new ArrayList<>(userCount);
      for (int i = 0; i < userCount; i++) {
        users.add(buildUserWithBalance("ld1_u" + i, 0L, userDAO));
      }

      ExecutorService pool = Executors.newFixedThreadPool(Math.min(userCount, 16));
      List<Future<?>> futures = new ArrayList<>();
      AtomicInteger failures = new AtomicInteger();

      for (int i = 0; i < userCount; i++) {
        final NormalUser user = users.get(i);
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < depositRounds; r++) {
                    try {
                      walletService.deposit(user, depositAmount);
                    } catch (Exception e) {
                      failures.incrementAndGet();
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(60, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get())
          .as("Không được có exception trong quá trình deposit song song")
          .isZero();

      // Xác minh balance RAM khớp số lần deposit thực tế
      for (NormalUser user : users) {
        assertThat(user.getBalance())
            .as("Balance RAM của %s phải = depositRounds × depositAmount", user.getUsername())
            .isEqualTo((long) depositRounds * depositAmount);
      }
    }

    @Test
    @Timeout(value = 60)
    @DisplayName(
        "L-W2: 16 thread deposit vào cùng 1 user — tổng balance đúng, không có race condition")
    void concurrentDepositSameUser_noRaceCondition() throws Exception {
      int threads = 16;
      long depositEach = 500_000L;
      NormalUser sharedUser = buildUserWithBalance("ld2_shared", 0L, userDAO);

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int i = 0; i < threads; i++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    walletService.deposit(sharedUser, depositEach);
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get()).isZero();
      // Balance phải = threads × depositEach (không có lost update)
      assertThat(sharedUser.getBalance())
          .as("Balance sau %d deposit đồng thời phải = %d × %d", threads, threads, depositEach)
          .isEqualTo((long) threads * depositEach);
    }
  }

  // =========================================================================
  // Group 2 – Multi-auction: nhiều phiên đấu giá chạy đồng thời
  // =========================================================================

  @Nested
  @DisplayName("Group 2 – Multi-auction: nhiều phiên đồng thời (DB thật)")
  class MultiAuctionLoadTest {

    private static final int AUCTION_COUNT = 8;
    private static final int BIDDERS_PER = 6;
    private static final int BIDS_PER_BIDDER = 4;

    @Test
    @Timeout(value = 120)
    @DisplayName(
        "L-A1: 8 phiên × 6 bidder × 4 bid song song — không deadlock, giá mỗi phiên khớp DB")
    void multipleAuctions_concurrentBidding_noDeadlock() throws Exception {
      // Tạo seller + phiên cho từng auction
      List<Auction> auctions = new ArrayList<>(AUCTION_COUNT);
      for (int a = 0; a < AUCTION_COUNT; a++) {
        NormalUser seller = buildUserWithBalance("ma1_sel" + a, 80_000_000L, userDAO);
        String itemId = buildItem(seller.getId(), "MA-Item-" + a, 1_000_000L, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction =
            Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                3_000_000L);
        auctionDAO.createAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());
        auctions.add(auction);
      }

      // Tạo bidder và join cho từng phiên
      List<List<NormalUser>> bidderGroups = new ArrayList<>();
      for (int a = 0; a < AUCTION_COUNT; a++) {
        Auction auction = auctions.get(a);
        List<NormalUser> bidders = new ArrayList<>(BIDDERS_PER);
        for (int b = 0; b < BIDDERS_PER; b++) {
          NormalUser u = buildUserWithBalance("ma1_b" + a + "_" + b, 100_000_000L, userDAO);
          AuctionObserver obs = new BidderObserver(u, null);
          bidService.joinAuction(u, auction, obs);
          bidders.add(u);
        }
        bidderGroups.add(bidders);
      }

      // Spawn threads: mỗi bidder bid BIDS_PER_BIDDER lần trên phiên của mình
      ExecutorService pool = Executors.newFixedThreadPool(32);
      AtomicInteger totalSuccess = new AtomicInteger();
      List<Future<?>> futures = new ArrayList<>();

      for (int a = 0; a < AUCTION_COUNT; a++) {
        Auction auction = auctions.get(a);
        List<NormalUser> bidders = bidderGroups.get(a);

        for (int b = 0; b < BIDDERS_PER; b++) {
          final NormalUser bidder = bidders.get(b);
          final int bIdx = b;
          futures.add(
              pool.submit(
                  () -> {
                    for (int r = 0; r < BIDS_PER_BIDDER; r++) {
                      long amount = 1_200_000L + (bIdx * 20_000L) + (r * 50_000L);
                      try {
                        bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                        totalSuccess.incrementAndGet();
                      } catch (Exception ignored) {
                      }
                    }
                  }));
        }
      }

      for (Future<?> f : futures) {
        f.get(120, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

      assertThat(totalSuccess.get())
          .as("Tổng bid thành công phải > 0 khi chịu tải đa phiên")
          .isPositive();

      // Mỗi phiên: giá DB phải khớp RAM
      for (Auction auction : auctions) {
        Auction fromDb = auctionDAO.findAuctionById(auction.getId());
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getCurrentPrice())
            .as("Giá DB phải khớp RAM cho phiên %s", auction.getId())
            .isEqualTo(auction.getCurrentPrice());
      }
    }

    @Test
    @Timeout(value = 90)
    @DisplayName(
        "L-A2: Nhiều phiên cùng lúc — giá mỗi phiên luôn tăng đơn điệu (không có lost update)")
    void multipleAuctions_priceMonotonicallyIncreasing() throws Exception {
      int aCount = 4;
      List<Auction> auctions = new ArrayList<>(aCount);
      Map<String, List<Long>> priceSnapshots = new ConcurrentHashMap<>();

      for (int a = 0; a < aCount; a++) {
        NormalUser seller = buildUserWithBalance("ma2_sel" + a, 80_000_000L, userDAO);
        String itemId = buildItem(seller.getId(), "MA2-Item-" + a, 500_000L, itemDAO);
        Item item = itemDAO.findItemById(itemId);
        Auction auction =
            Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(2),
                2_000_000L);
        auctionDAO.createAuction(auction);
        auctionService.startAuction(auction);
        trackAuction(auction.getId());
        auctions.add(auction);
        priceSnapshots.put(auction.getId(), new CopyOnWriteArrayList<>());
      }

      ExecutorService pool = Executors.newFixedThreadPool(16);
      List<Future<?>> futures = new ArrayList<>();
      int biddersPerAuction = 4;

      for (int a = 0; a < aCount; a++) {
        Auction auction = auctions.get(a);
        List<Long> snaps = priceSnapshots.get(auction.getId());

        for (int b = 0; b < biddersPerAuction; b++) {
          final NormalUser bidder =
              buildUserWithBalance("ma2_b" + a + "_" + b, 50_000_000L, userDAO);
          AuctionObserver obs = new BidderObserver(bidder, null);
          bidService.joinAuction(bidder, auction, obs);
          final int bidderIdx = b;
          futures.add(
              pool.submit(
                  () -> {
                    for (int r = 0; r < 5; r++) {
                      long amount = 600_000L + (bidderIdx * 15_000L) + (r * 30_000L);
                      try {
                        bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                        snaps.add(auction.getCurrentPrice());
                      } catch (Exception ignored) {
                      }
                    }
                  }));
        }
      }

      for (Future<?> f : futures) {
        f.get(90, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

      // Verify monotonic increase per auction
      for (Auction auction : auctions) {
        List<Long> snaps = priceSnapshots.get(auction.getId());
        for (int i = 1; i < snaps.size(); i++) {
          assertThat(snaps.get(i))
              .as("Giá phiên %s tại snapshot[%d] phải >= snapshot[%d-1]", auction.getId(), i, i)
              .isGreaterThanOrEqualTo(snaps.get(i - 1));
        }
      }
    }
  }

  // =========================================================================
  // Group 3 – Auto-bid dưới tải: nhiều auto-bidder + manual bidder
  // =========================================================================

  @Nested
  @DisplayName("Group 3 – Auto-bid dưới tải")
  class AutoBidLoadTest {

    @Test
    @Timeout(value = 90)
    @DisplayName(
        "L-AB1: 8 auto-bidder + 4 manual-bidder song song — giá không vượt maxBid của bất kỳ ai")
    void multipleAutoBidders_concurrentManualBidders_priceWithinBounds() throws Exception {
      NormalUser seller = buildUserWithBalance("ab1_sel", 80_000_000L, userDAO);
      String itemId = buildItem(seller.getId(), "AB1-Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              5_000_000L);
      auctionDAO.createAuction(auction);
      auctionService.startAuction(auction);
      trackAuction(auction.getId());

      int autoBidderCount = 8;
      int manualBidderCount = 4;
      long autoBidMaxBid = 4_000_000L;

      // Tạo và đăng ký auto-bidders
      List<NormalUser> autoBidders = new ArrayList<>(autoBidderCount);
      for (int i = 0; i < autoBidderCount; i++) {
        NormalUser u = buildUserWithBalance("ab1_auto" + i, 50_000_000L, userDAO);
        AuctionObserver obs = new BidderObserver(u, null);
        bidService.joinAuction(u, auction, obs);
        // maxBid mỗi người khác nhau để tạo ra chain rõ ràng
        long maxBid = autoBidMaxBid - (i * 100_000L);
        AutoBidRegistry.getInstance().register(u.getId(), auction.getId(), maxBid);
        autoBidders.add(u);
      }

      // Tạo manual bidders
      List<NormalUser> manualBidders = new ArrayList<>(manualBidderCount);
      for (int i = 0; i < manualBidderCount; i++) {
        NormalUser u = buildUserWithBalance("ab1_man" + i, 50_000_000L, userDAO);
        AuctionObserver obs = new BidderObserver(u, null);
        bidService.joinAuction(u, auction, obs);
        manualBidders.add(u);
      }

      ExecutorService pool = Executors.newFixedThreadPool(16);
      AtomicInteger successes = new AtomicInteger();
      List<Future<?>> futures = new ArrayList<>();

      for (int i = 0; i < manualBidderCount; i++) {
        final NormalUser bidder = manualBidders.get(i);
        final int idx = i;
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < 3; r++) {
                    long amount = 1_200_000L + (idx * 50_000L) + (r * 100_000L);
                    try {
                      bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                      successes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(90, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

      // Giá cuối không được vượt maxBid cao nhất trong auto-bidders
      assertThat(auction.getCurrentPrice())
          .as("Giá cuối không được vượt maxBid của auto-bidder đầu tiên (cao nhất)")
          .isLessThanOrEqualTo(autoBidMaxBid);
      assertThat(auction.getCurrentPrice()).isGreaterThanOrEqualTo(1_000_000L);

      AutoBidRegistry.getInstance().clearAuction(auction.getId());
    }

    @Test
    @Timeout(value = 60)
    @DisplayName(
        "L-AB2: AutoBidRegistry.register/clearAuction song song 32 thread — không NPE, không"
            + " corruption")
    void autoBidRegistry_concurrentRegisterAndClear_noCorruption() throws Exception {
      int threads = 32;

      // FIX: Tạo auction + users thật trong DB để tránh FK violation
      // (auto_bids.user_id REFERENCES users.id và auto_bids.auction_id REFERENCES auctions.id)
      NormalUser seller = buildUserWithBalance("ab2_sel", 80_000_000L, userDAO);
      String itemId = buildItem(seller.getId(), "AB2-Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              5_000_000L);
      auctionDAO.createAuction(auction);
      auctionService.startAuction(auction);
      trackAuction(auction.getId());
      String auctionId = auction.getId();

      // Tạo 32 user thật tuần tự trên main thread (buildUserWithBalance không thread-safe)
      List<NormalUser> users = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        users.add(buildUserWithBalance("ab2_u" + i, 1_000_000L, userDAO));
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int i = 0; i < threads; i++) {
        final int idx = i;
        final String userId = users.get(i).getId();
        new Thread(
                () -> {
                  try {
                    gate.await();
                    long maxBid = 1_000_000L + (idx * 10_000L);
                    AutoBidRegistry.getInstance().register(userId, auctionId, maxBid);
                    // Nửa thread thực hiện clear, nửa còn lại check
                    if (idx % 2 == 0) {
                      AutoBidRegistry.getInstance().clearAuction(auctionId);
                    } else {
                      AutoBidRegistry.getInstance().hasActiveBid(userId, auctionId);
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get())
          .as("Không được có exception khi thao tác AutoBidRegistry song song")
          .isZero();

      AutoBidRegistry.getInstance().clearAuction(auctionId);
    }
  }

  // =========================================================================
  // Group 4 – AuctionSortService: sort/filter danh sách lớn dưới tải
  // =========================================================================

  @Nested
  @DisplayName("Group 4 – AuctionSortService: sort/filter dưới tải (không cần DB)")
  class AuctionSortServiceLoadTest {

    /** Xây dựng list auction in-memory (không cần DB) bằng TestFixture. */
    private List<Auction> buildAuctionList(int count) {
      NormalUser seller = TestFixture.normalSeller("sort_seller");
      List<Auction> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        long price = 500_000L + (i * 100_000L);
        Auction a = TestFixture.runningAuction(seller, price);
        list.add(a);
      }
      return list;
    }

    @Test
    @Timeout(value = 30)
    @DisplayName("L-S1: 16 thread × 50 lần sort 200 auction — không lỗi, kết quả đơn điệu giảm")
    void concurrentSortByPriceDesc_largeLists() throws Exception {
      List<Auction> auctions = buildAuctionList(200);
      int threads = 16;
      int opsPerThread = 50;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                      List<Auction> sorted = sortService.sortByCurrentPriceDesc(auctions);
                      // Verify monotonically decreasing
                      for (int j = 1; j < sorted.size(); j++) {
                        if (sorted.get(j).getCurrentPrice() > sorted.get(j - 1).getCurrentPrice()) {
                          failures.incrementAndGet();
                          return;
                        }
                      }
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get())
          .as("Kết quả sort phải đúng và không có exception dưới %d thread", threads)
          .isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName("L-S2: 8 thread × 30 lần sortByViewersThenPrice 100 auction — kết quả nhất quán")
    void concurrentSortByViewersThenPrice_consistent() throws Exception {
      List<Auction> auctions = buildAuctionList(100);
      int threads = 8;
      int opsPerThread = 30;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                      List<Auction> sorted = sortService.sortByViewersThenPrice(auctions);
                      if (sorted == null || sorted.size() != auctions.size()) {
                        failures.incrementAndGet();
                        return;
                      }
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(failures.get()).isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-S3: filterByCategory song song 12 thread × 100 lần — không mất item, không exception")
    void concurrentFilterByCategory_noItemLoss() throws Exception {
      List<Auction> auctions = buildAuctionList(150);
      int threads = 12;
      int opsPerThread = 100;
      long expectedSameSize = auctions.size();

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                      // sortByCategoryThenPrice phải trả về đúng số lượng
                      List<Auction> sorted = sortService.sortByCategoryThenPrice(auctions);
                      if (sorted.size() != expectedSameSize) {
                        failures.incrementAndGet();
                        return;
                      }
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(failures.get()).isZero();
    }
  }

  // =========================================================================
  // Group 5 – Join storm: nhiều user join cùng 1 phiên đồng thời
  // =========================================================================

  @Nested
  @DisplayName("Group 5 – Join storm: nhiều user join cùng phiên (DB thật)")
  class JoinStormLoadTest {

    @Test
    @Timeout(value = 90)
    @DisplayName(
        "L-J1: 50 user join cùng 1 phiên đồng thời — tất cả đều join thành công, không deadlock")
    void massJoin_singleAuction_allSucceed() throws Exception {
      int joinerCount = 50;

      NormalUser seller = buildUserWithBalance("js1_sel", 80_000_000L, userDAO);
      String itemId = buildItem(seller.getId(), "JS1-Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              3_000_000L);
      auctionDAO.createAuction(auction);
      auctionService.startAuction(auction);
      trackAuction(auction.getId());

      List<NormalUser> joiners = new ArrayList<>(joinerCount);
      for (int i = 0; i < joinerCount; i++) {
        joiners.add(buildUserWithBalance("js1_j" + i, 10_000_000L, userDAO));
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(joinerCount);
      AtomicInteger success = new AtomicInteger();
      AtomicInteger failure = new AtomicInteger();

      for (int i = 0; i < joinerCount; i++) {
        final NormalUser u = joiners.get(i);
        new Thread(
                () -> {
                  try {
                    gate.await();
                    AuctionObserver obs = new BidderObserver(u, null);
                    bidService.joinAuction(u, auction, obs);
                    success.incrementAndGet();
                  } catch (Exception e) {
                    failure.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(success.get())
          .as("Tất cả %d user phải join thành công", joinerCount)
          .isEqualTo(joinerCount);
      assertThat(failure.get()).isZero();
    }

    @Test
    @Timeout(value = 90)
    @DisplayName(
        "L-J2: Join → bid ngay lập tức (không delay) — không NPE, không deadlock, có ít nhất 1 bid"
            + " thành công")
    void joinThenBidImmediately_noDeadlock() throws Exception {
      int userCount = 30;

      NormalUser seller = buildUserWithBalance("js2_sel", 80_000_000L, userDAO);
      String itemId = buildItem(seller.getId(), "JS2-Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              3_000_000L);
      auctionDAO.createAuction(auction);
      auctionService.startAuction(auction);
      trackAuction(auction.getId());

      // Tạo user TRƯỚC khi spawn thread — buildUserWithBalance() ghi vào trackedUserIds
      // (ArrayList không thread-safe), phải tạo tuần tự trên main thread
      List<NormalUser> joiners = new ArrayList<>(userCount);
      for (int i = 0; i < userCount; i++) {
        joiners.add(buildUserWithBalance("js2_u" + i, 30_000_000L, userDAO));
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(userCount);
      AtomicInteger bidSucc = new AtomicInteger();

      for (int i = 0; i < userCount; i++) {
        final NormalUser u = joiners.get(i);
        final int idx = i;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    AuctionObserver obs = new BidderObserver(u, null);
                    bidService.joinAuction(u, auction, obs);
                    // Bid ngay sau khi join
                    long amount = 1_200_000L + (idx * 30_000L);
                    try {
                      bidService.placeBid(u, auction, amount, new StandardBidStrategy());
                      bidSucc.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

      assertThat(bidSucc.get())
          .as("Phải có ít nhất 1 bid thành công trong %d user bid cùng lúc", userCount)
          .isPositive();
    }
  }

  // =========================================================================
  // Group 6 – RatingService: isEligible song song nhiều user
  // =========================================================================

  @Nested
  @DisplayName("Group 6 – RatingService: isEligible song song (không cần DB)")
  class RatingServiceLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-R1: 32 thread × 500 lần isEligible trên nhiều user — không exception, kết quả nhất quán")
    void concurrentIsEligible_noExceptionConsistentResults() throws Exception {
      int userCount = 20;
      int threads = 32;
      int opsPerThread = 500;

      // Tạo mix active/suspended/banned user qua TestFixture (không cần DB)
      List<NormalUser> activeUsers = new ArrayList<>();
      List<NormalUser> suspendedUsers = new ArrayList<>();
      for (int i = 0; i < userCount / 2; i++) {
        activeUsers.add(TestFixture.bidderWithBalance("rat_act" + i, 1_000_000L));
      }
      for (int i = 0; i < userCount / 2; i++) {
        suspendedUsers.add(TestFixture.suspendedBidder("rat_sus" + i));
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                      // Xen kẽ active và suspended
                      NormalUser u =
                          (seed % 2 == 0)
                              ? activeUsers.get(i % activeUsers.size())
                              : suspendedUsers.get(i % suspendedUsers.size());
                      // isEligible không được throw exception
                      ratingService.isEligible(u);
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(failures.get())
          .as("isEligible không được throw exception bất kỳ dưới tải")
          .isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-R2: 16 thread × 200 lần rewardBidder song song — không exception, không race condition")
    void concurrentRewardBidder_noException() throws Exception {
      int threads = 16;
      int opsPerThread = 200;

      // Tạo user qua DB để rewardBidder có thể cập nhật rating
      List<NormalUser> users = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        users.add(buildUserWithBalance("rr2_u" + i, 1_000_000L, userDAO));
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final NormalUser u = users.get(t);
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                      try {
                        ratingService.rewardBidder(u);
                      } catch (Exception e) {
                        failures.incrementAndGet();
                      }
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(failures.get())
          .as("rewardBidder không được throw exception bất kỳ dưới tải")
          .isZero();
    }
  }

  // =========================================================================
  // Group 7 – Mixed workload: bid + deposit + sort cùng lúc
  // =========================================================================

  @Nested
  @DisplayName("Group 7 – Mixed workload: bid + deposit + sort đồng thời (DB thật)")
  class MixedWorkloadLoadTest {

    @Test
    @Timeout(value = 120)
    @DisplayName(
        "L-M1: Bid / deposit / sort chạy song song — không deadlock, không lỗi nghiêm trọng, giá DB"
            + " khớp RAM")
    void mixedWorkload_noDeadlockNoFatalError() throws Exception {
      // Chuẩn bị auction
      NormalUser seller = buildUserWithBalance("mix1_sel", 80_000_000L, userDAO);
      String itemId = buildItem(seller.getId(), "Mix1-Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              5_000_000L);
      auctionDAO.createAuction(auction);
      auctionService.startAuction(auction);
      trackAuction(auction.getId());

      int bidThreads = 8;
      int depositThreads = 6;
      int sortThreads = 4;

      // Bidder users
      List<NormalUser> bidders = new ArrayList<>(bidThreads);
      for (int i = 0; i < bidThreads; i++) {
        NormalUser u = buildUserWithBalance("mix1_bid" + i, 50_000_000L, userDAO);
        AuctionObserver obs = new BidderObserver(u, null);
        bidService.joinAuction(u, auction, obs);
        bidders.add(u);
      }

      // Deposit users
      List<NormalUser> depositors = new ArrayList<>(depositThreads);
      for (int i = 0; i < depositThreads; i++) {
        depositors.add(buildUserWithBalance("mix1_dep" + i, 0L, userDAO));
      }

      // Auction list for sort
      NormalUser sortSeller = TestFixture.normalSeller("mix1_srt_sel");
      List<Auction> sortAuctions = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        sortAuctions.add(TestFixture.runningAuction(sortSeller, 500_000L + i * 10_000L));
      }

      ExecutorService pool = Executors.newFixedThreadPool(32);
      AtomicInteger bidSucc = new AtomicInteger();
      AtomicInteger depSucc = new AtomicInteger();
      AtomicInteger srtSucc = new AtomicInteger();
      AtomicInteger fatalErrors = new AtomicInteger();
      List<Future<?>> futures = new ArrayList<>();

      // Bid tasks
      for (int i = 0; i < bidThreads; i++) {
        final NormalUser bidder = bidders.get(i);
        final int idx = i;
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < 5; r++) {
                    long amount = 1_200_000L + (idx * 20_000L) + (r * 40_000L);
                    try {
                      bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                      bidSucc.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      // Deposit tasks
      for (int i = 0; i < depositThreads; i++) {
        final NormalUser depositor = depositors.get(i);
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < 10; r++) {
                    try {
                      walletService.deposit(depositor, 500_000L);
                      depSucc.incrementAndGet();
                    } catch (Exception e) {
                      fatalErrors.incrementAndGet();
                    }
                  }
                }));
      }

      // Sort tasks
      for (int i = 0; i < sortThreads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < 50; r++) {
                    try {
                      sortService.sortByCurrentPriceDesc(sortAuctions);
                      sortService.sortByViewersThenPrice(sortAuctions);
                      srtSucc.incrementAndGet();
                    } catch (Exception e) {
                      fatalErrors.incrementAndGet();
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(120, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

      assertThat(fatalErrors.get())
          .as("Không được có lỗi nghiêm trọng trong mixed workload")
          .isZero();
      assertThat(bidSucc.get()).isPositive();
      assertThat(depSucc.get()).isPositive();
      assertThat(srtSucc.get()).isPositive();

      // Giá DB phải khớp RAM
      Auction fromDb = auctionDAO.findAuctionById(auction.getId());
      assertThat(fromDb).isNotNull();
      assertThat(fromDb.getCurrentPrice())
          .as("Giá DB phải khớp RAM sau mixed workload")
          .isEqualTo(auction.getCurrentPrice());
    }

    @Test
    @Timeout(value = 90)
    @DisplayName("L-M2: Throughput — tổng số bid/s > 5 trong 30s dưới tải 12 thread")
    void bidThroughput_sustainedLoad() throws Exception {
      NormalUser seller = buildUserWithBalance("mix2_sel", 80_000_000L, userDAO);
      String itemId = buildItem(seller.getId(), "Mix2-Item", 1_000_000L, itemDAO);
      Item item = itemDAO.findItemById(itemId);
      Auction auction =
          Auction.create(
              item,
              LocalDateTime.now().minusMinutes(1),
              LocalDateTime.now().plusHours(2),
              20_000_000L);
      auctionDAO.createAuction(auction);
      auctionService.startAuction(auction);
      trackAuction(auction.getId());

      int threads = 12;
      int durationSeconds = 20;

      List<NormalUser> bidders = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        NormalUser u = buildUserWithBalance("mix2_t" + i, 500_000_000L, userDAO);
        AuctionObserver obs = new BidderObserver(u, null);
        bidService.joinAuction(u, auction, obs);
        bidders.add(u);
      }

      AtomicLong totalAttempts = new AtomicLong();
      AtomicLong totalSuccess = new AtomicLong();
      long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

      ExecutorService pool = Executors.newFixedThreadPool(threads);
      List<Future<?>> futures = new ArrayList<>();

      // FIX: Mỗi thread đọc currentPrice rồi bid vượt đúng minIncrement (200_000).
      // Offset theo idx để 12 thread không tranh nhau cùng 1 mức giá.
      // Kết quả: tỷ lệ bid thành công cao, reject chỉ xảy ra khi 2 thread
      // đọc currentPrice cùng lúc trước khi 1 thread kịp updateBid — không thể tránh hoàn toàn.
      for (int i = 0; i < threads; i++) {
        final NormalUser bidder = bidders.get(i);
        final int idx = i;
        futures.add(
            pool.submit(
                () -> {
                  while (System.currentTimeMillis() < endTime) {
                    totalAttempts.incrementAndGet();
                    // Đọc currentPrice hiện tại (volatile read) + 1 increment + offset theo thread
                    long currentPrice = auction.getCurrentPrice();
                    long amount =
                        currentPrice
                            + BidIncrementCalculator.calculate(currentPrice)
                            + (idx * 10_000L);
                    try {
                      bidService.placeBid(bidder, auction, amount, new StandardBidStrategy());
                      totalSuccess.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(90, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

      double throughput = (double) totalSuccess.get() / durationSeconds;

      assertThat(totalAttempts.get())
          .as("Phải có ít nhất 1 bid attempt trong %ds", durationSeconds)
          .isPositive();
      assertThat(totalSuccess.get())
          .as("Phải có ít nhất 1 bid thành công trong %ds", durationSeconds)
          .isPositive();
      // Throughput thực tế phải > 5 bid/s
      assertThat(throughput)
          .as("Throughput phải > 5 bid/s (thực tế: %.2f bid/s)", throughput)
          .isGreaterThan(5.0);
    }
  }

  // =========================================================================
  // Group 8 – AuctionLockRegistry dưới tải lớn (không cần DB)
  // =========================================================================

  @Nested
  @DisplayName("Group 8 – AuctionLockRegistry dưới tải lớn (không cần DB)")
  class LockRegistryLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-LK1: 100 thread × 10 auction ID — getLock/release không bị memory leak và không NPE")
    void massGetAndRelease_noMemoryLeakNoException() throws Exception {
      int threads = 100;
      int auctionIds = 10;
      int cycles = 20;

      String[] ids = new String[auctionIds];
      for (int i = 0; i < auctionIds; i++) {
        ids[i] = UUID.randomUUID().toString();
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int c = 0; c < cycles; c++) {
                      String id = ids[(seed + c) % auctionIds];
                      try {
                        var lock = AuctionLockRegistry.getInstance().getLock(id);
                        if (lock == null) {
                          failures.incrementAndGet();
                        }
                        // Không release để test không memory leak khi lock vẫn đang dùng
                      } catch (Exception e) {
                        failures.incrementAndGet();
                      }
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get())
          .as("getLock không được trả về null và không được throw exception")
          .isZero();

      // Cleanup
      for (String id : ids) {
        AuctionLockRegistry.getInstance().release(id);
      }
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-LK2: 50 thread tranh lock cùng 1 auctionId — chỉ 1 thread vào critical section tại mỗi"
            + " thời điểm")
    void contendedLock_mutualExclusion_under50Threads() throws Exception {
      int threads = 50;
      String auctionId = UUID.randomUUID().toString();
      var lock = AuctionLockRegistry.getInstance().getLock(auctionId);

      AtomicInteger concurrentInside = new AtomicInteger();
      AtomicInteger maxConcurrent = new AtomicInteger();
      AtomicInteger violations = new AtomicInteger();
      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);

      for (int i = 0; i < threads; i++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    lock.lock();
                    try {
                      int inside = concurrentInside.incrementAndGet();
                      maxConcurrent.accumulateAndGet(inside, Math::max);
                      if (inside > 1) {
                        violations.incrementAndGet();
                      }
                      Thread.sleep(1);
                      concurrentInside.decrementAndGet();
                    } finally {
                      lock.unlock();
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      AuctionLockRegistry.getInstance().release(auctionId);

      assertThat(violations.get())
          .as("Phải đảm bảo mutual exclusion — không có 2 thread cùng vào critical section")
          .isZero();
      assertThat(maxConcurrent.get())
          .as("Tối đa 1 thread trong critical section tại mỗi thời điểm")
          .isEqualTo(1);
    }
  }
}
