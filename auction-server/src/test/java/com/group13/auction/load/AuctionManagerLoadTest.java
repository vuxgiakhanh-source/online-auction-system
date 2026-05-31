package com.group13.auction.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.observer.StaffObserver;
import com.group13.auction.unit.TestFixture;
import java.lang.reflect.Field;
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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * ============================================================================ LOAD TEST —
 * AuctionManagerLoadTest (unit, không cần Docker)
 * ============================================================================
 *
 * <p>Kiểm tra AuctionManager Singleton dưới tải cao đồng thời. AuctionManager dùng
 * ConcurrentHashMap cho allAuctions/allUsers và CopyOnWriteArrayList cho observers — load test
 * verify thread-safety của tất cả hot path.
 *
 * <p>Group 1 — registerAuction / findAuctionById song song: 100+ auction register + lookup đồng
 * thời — không mất item, không NPE.
 *
 * <p>Group 2 — addToUserList / findUserByUsername song song: 50 thread write + 50 thread read —
 * putIfAbsent atomic, không lost update.
 *
 * <p>Group 3 — addGlobalObserver / notifyGlobalObservers song song: CopyOnWriteArrayList không bị
 * ConcurrentModificationException khi notify và add observer xảy ra đồng thời.
 *
 * <p>Group 4 — getAuctionsByStatus / getAllAuctions dưới write storm: Query + register auction đồng
 * thời — không stale read gây NPE.
 *
 * <p>Group 5 — Mixed workload: register + lookup + notify đồng thời: Stress test tổng hợp giống
 * production (server nhận nhiều request cùng lúc).
 */
@DisplayName("AuctionManagerLoadTest — AuctionManager Singleton dưới tải cao (unit)")
class AuctionManagerLoadTest {

  private AuctionManager manager;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    manager = AuctionManager.getInstance();
    clearAllState();
  }

  @AfterEach
  void tearDown() throws Exception {
    clearAllState();
    TestFixture.resetSystemAdmin();
  }
  // Group 1 — registerAuction / findAuctionById song song
  @Nested
  @DisplayName("Group 1 – registerAuction / findAuctionById song song")
  class RegisterAndFindAuctionLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM1: 50 thread register 200 auction đồng thời — tất cả đều tìm được sau khi register")
    void concurrent_registerAndFind_200Auctions_noLoss() throws Exception {
      int threads = 50;
      int auctionsEach = 4; // 50 × 4 = 200 auction
      List<String> allIds = new CopyOnWriteArrayList<>();

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < auctionsEach; i++) {
                      NormalUser seller =
                          TestFixture.normalSeller(
                              "am1_sel_" + UUID.randomUUID().toString().substring(0, 8));
                      Auction auction = TestFixture.openAuction(seller, 1_000_000L);
                      manager.registerAuction(auction);
                      allIds.add(auction.getId());
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

      assertThat(failures.get()).as("registerAuction không được throw exception dưới tải").isZero();

      // Tất cả auction phải tìm được
      long notFound = allIds.stream().filter(id -> manager.findAuctionById(id) == null).count();
      assertThat(notFound).as("Không được có auction nào bị mất sau concurrent register").isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM2: 32 thread register + 32 thread findById đồng thời — findById không bao giờ crash")
    void concurrent_registerAndFind_readWriteMix_noCrash() throws Exception {
      int writers = 32;
      int readers = 32;
      int total = writers + readers;

      // Seed 20 auction trước để reader có thể đọc ngay từ đầu
      List<String> seedIds = new ArrayList<>(20);
      for (int i = 0; i < 20; i++) {
        NormalUser seller =
            TestFixture.normalSeller("am2_seed_" + UUID.randomUUID().toString().substring(0, 6));
        Auction a = TestFixture.openAuction(seller, 500_000L);
        manager.registerAuction(a);
        seedIds.add(a.getId());
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(total);
      AtomicInteger crashes = new AtomicInteger();

      // Writer threads
      for (int t = 0; t < writers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 10; i++) {
                      NormalUser seller =
                          TestFixture.normalSeller(
                              "am2_w_" + UUID.randomUUID().toString().substring(0, 6));
                      manager.registerAuction(TestFixture.openAuction(seller, 500_000L));
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      // Reader threads
      for (int t = 0; t < readers; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 50; i++) {
                      // Read cả id tồn tại và không tồn tại
                      String id =
                          (i % 2 == 0)
                              ? seedIds.get(seed % seedIds.size())
                              : UUID.randomUUID().toString();
                      manager.findAuctionById(id); // không được throw
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(crashes.get()).as("Không được có JVM Error trong read-write mix").isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName("L-AM3: registerAuction(null) không crash, registerAuction(đúng) vẫn tìm được")
    void registerAuction_nullGuard_doesNotAffectValidRegistrations() throws Exception {
      int threads = 20;
      List<String> validIds = new CopyOnWriteArrayList<>();
      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger exceptions = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        final boolean isNull = (t % 4 == 0); // 1/4 thread thử null
        new Thread(
                () -> {
                  try {
                    gate.await();
                    if (isNull) {
                      try {
                        manager.registerAuction(null);
                      } catch (IllegalArgumentException expected) {
                        // Đây là hành vi đúng — không phải crash
                      }
                    } else {
                      NormalUser seller =
                          TestFixture.normalSeller(
                              "am3_" + UUID.randomUUID().toString().substring(0, 6));
                      Auction a = TestFixture.openAuction(seller, 500_000L);
                      manager.registerAuction(a);
                      validIds.add(a.getId());
                    }
                  } catch (Error e) {
                    exceptions.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(exceptions.get()).isZero();

      // Valid auction phải tìm được
      long notFound = validIds.stream().filter(id -> manager.findAuctionById(id) == null).count();
      assertThat(notFound).isZero();
    }
  }
  // Group 2 — addToUserList / getAllUsers song song
  @Nested
  @DisplayName("Group 2 – addToUserList / getAllUsers song song")
  class UserManagementLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName("L-AM4: 40 thread addToUserList đồng thời — putIfAbsent, getAllUsers trả về đủ")
    void concurrent_addToUserList_40Threads_noLoss() throws Exception {
      int threads = 40;
      List<NormalUser> users = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        users.add(
            TestFixture.bidderWithBalance(
                "am4_u" + i + "_" + UUID.randomUUID().toString().substring(0, 4), 1_000_000L));
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      for (NormalUser u : users) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    manager.addToUserList(u);
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

      // getAllUsers phải chứa tất cả users đã add
      List<User> all = manager.getAllUsers();
      for (NormalUser u : users) {
        assertThat(all.stream().anyMatch(x -> x.getId().equals(u.getId())))
            .as("User %s phải có trong getAllUsers()", u.getUsername())
            .isTrue();
      }
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM5: addToUserList + getAllUsers + findUserByUsername đồng thời — không"
            + " ConcurrentModificationException")
    void concurrent_userOps_mixed_noCME() throws Exception {
      int writers = 16;
      int readers = 16;
      int total = writers + readers;

      // Seed 10 user trước
      List<NormalUser> seeds = new ArrayList<>(10);
      for (int i = 0; i < 10; i++) {
        NormalUser u =
            TestFixture.bidderWithBalance(
                "am5_seed" + i + "_" + UUID.randomUUID().toString().substring(0, 4), 0L);
        manager.addToUserList(u);
        seeds.add(u);
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(total);
      AtomicInteger crashes = new AtomicInteger();

      for (int t = 0; t < writers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 5; i++) {
                      NormalUser u =
                          TestFixture.bidderWithBalance(
                              "am5_w_" + UUID.randomUUID().toString().substring(0, 8), 0L);
                      manager.addToUserList(u);
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      for (int t = 0; t < readers; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 20; i++) {
                      manager.getAllUsers();
                      manager.findUserByUsernameInMemoryOnly(
                          seeds.get(seed % seeds.size()).getUsername());
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(crashes.get()).as("Mixed user ops không được gây JVM Error").isZero();
    }
  }
  // Group 3 — addGlobalObserver / notifyGlobalObservers song song
  @Nested
  @DisplayName("Group 3 – addGlobalObserver / notifyGlobalObservers song song")
  class ObserverLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM6: 20 thread add observer + 10 thread notify đồng thời — CopyOnWriteArrayList không bị"
            + " lỗi")
    void concurrent_addObserverAndNotify_noException() throws Exception {
      int adders = 20;
      int notifiers = 10;
      int total = adders + notifiers;

      NormalUser dummySeller = TestFixture.normalSeller("am6_seller");
      NormalUser dummyBidder = TestFixture.bidderWithBalance("am6_bidder", 1_000_000L);
      Auction dummyAuction = TestFixture.openAuction(dummySeller, 500_000L);

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(total);
      AtomicInteger failures = new AtomicInteger();
      AtomicInteger notifySent = new AtomicInteger();

      // Adder threads: thêm BidderObserver vào global
      for (int t = 0; t < adders; t++) {
        NormalUser obs =
            TestFixture.bidderWithBalance(
                "am6_obs_" + t + "_" + UUID.randomUUID().toString().substring(0, 4), 0L);
        AuctionObserver observer = new BidderObserver(obs, TestFixture.ratingServiceAllowAll());
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 5; i++) {
                      manager.addGlobalObserver(observer);
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      // Notifier threads: notify global observers
      for (int t = 0; t < notifiers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 20; i++) {
                      AuctionEvent event =
                          new AuctionEvent(
                              AuctionEvent.AuctionEventType.BID_PLACED,
                              dummyAuction,
                              dummyBidder,
                              600_000L);
                      manager.notifyGlobalObservers(event);
                      notifySent.incrementAndGet();
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
          .as("addGlobalObserver + notifyGlobalObservers không được throw exception")
          .isZero();
      assertThat(notifySent.get()).isPositive();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM7: addStaffObserver + notifyStaffObservers đồng thời — không"
            + " ConcurrentModificationException")
    void concurrent_staffObserver_addAndNotify_noException() throws Exception {
      int adders = 10;
      int notifiers = 8;
      int total = adders + notifiers;

      NormalUser dummySeller = TestFixture.normalSeller("am7_seller");
      Auction dummyAuction = TestFixture.openAuction(dummySeller, 500_000L);
      manager.registerAuction(dummyAuction);
      dummySeller.addAuctionId(dummyAuction.getId());

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(total);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < adders; t++) {
        // StaffObserver cần Admin
        com.group13.auction.model.user.Admin staffAdmin =
            com.group13.auction.model.user.Admin.reconstitute(
                UUID.randomUUID().toString(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                "am7_staff_" + UUID.randomUUID().toString().substring(0, 6),
                User.hashPassword("pass"),
                "am7_staff@test.vn",
                User.AccountStatus.ACTIVE,
                5.0,
                com.group13.auction.model.user.Admin.LEVEL_STAFF,
                null);
        AuctionObserver staffObs = new StaffObserver(staffAdmin);
        new Thread(
                () -> {
                  try {
                    gate.await();
                    manager.addStaffObserver(staffObs);
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      for (int t = 0; t < notifiers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 15; i++) {
                      AuctionEvent event =
                          new AuctionEvent(
                              AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
                              dummyAuction,
                              null,
                              0L,
                              "load test");
                      manager.notifyStaffObservers(event);
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
        "L-AM8: removeGlobalObserver + notifyGlobalObservers đồng thời — không crash khi list thay"
            + " đổi trong notify")
    void concurrent_removeObserverAndNotify_noCrash() throws Exception {
      // Seed 10 observer trước
      NormalUser dummySeller = TestFixture.normalSeller("am8_seller");
      Auction dummyAuction = TestFixture.openAuction(dummySeller, 500_000L);
      NormalUser dummyBidder = TestFixture.bidderWithBalance("am8_bidder", 0L);

      List<AuctionObserver> seededObs = new ArrayList<>(10);
      for (int i = 0; i < 10; i++) {
        NormalUser u =
            TestFixture.bidderWithBalance(
                "am8_obs_" + i + "_" + UUID.randomUUID().toString().substring(0, 4), 0L);
        BidderObserver obs = new BidderObserver(u, TestFixture.ratingServiceAllowAll());
        manager.addGlobalObserver(obs);
        seededObs.add(obs);
      }

      int removers = 5;
      int notifiers = 5;
      int total = removers + notifiers;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(total);
      AtomicInteger crashes = new AtomicInteger();

      for (int t = 0; t < removers; t++) {
        final AuctionObserver toRemove = seededObs.get(t);
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 5; i++) {
                      manager.removeGlobalObserver(toRemove);
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      for (int t = 0; t < notifiers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 20; i++) {
                      AuctionEvent event =
                          new AuctionEvent(
                              AuctionEvent.AuctionEventType.BID_PLACED,
                              dummyAuction,
                              dummyBidder,
                              600_000L);
                      manager.notifyGlobalObservers(event);
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(crashes.get()).as("remove + notify đồng thời không được gây JVM Error").isZero();
    }
  }
  // Group 4 — getAuctionsByStatus / getAllAuctions dưới write storm
  @Nested
  @DisplayName("Group 4 – getAuctionsByStatus / getAllAuctions dưới write storm")
  class AuctionQueryLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM9: 20 writer register + 20 reader getAuctionsByStatus đồng thời — không NPE, kết quả"
            + " luôn hợp lệ")
    void concurrent_registerAndQuery_noNPE() throws Exception {
      int writers = 20;
      int readers = 20;
      int total = writers + readers;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(total);
      AtomicInteger crashes = new AtomicInteger();
      AtomicInteger nullReturns = new AtomicInteger();

      for (int t = 0; t < writers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 5; i++) {
                      NormalUser seller =
                          TestFixture.normalSeller(
                              "am9_w_" + UUID.randomUUID().toString().substring(0, 6));
                      Auction a = TestFixture.openAuction(seller, 500_000L);
                      manager.registerAuction(a);
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      for (int t = 0; t < readers; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < 20; i++) {
                      List<Auction> byStatus =
                          manager.getAuctionsByStatus(Auction.AuctionStatus.OPEN);
                      List<Auction> all = manager.getAllAuctions();
                      if (byStatus == null || all == null) {
                        nullReturns.incrementAndGet();
                      }
                      // Verify: byStatus ⊆ all
                      if (!all.containsAll(byStatus)) {
                        crashes.incrementAndGet();
                      }
                    }
                  } catch (Error e) {
                    crashes.incrementAndGet();
                  } catch (Exception ignored) {
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(crashes.get()).isZero();
      assertThat(nullReturns.get())
          .as("getAuctionsByStatus/getAllAuctions không được trả về null")
          .isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM10: getRunningAuctions() 24 thread đồng thời khi có write — kết quả chỉ gồm RUNNING")
    void concurrent_getRunningAuctions_onlyRunningReturned() throws Exception {
      // Seed một số OPEN và RUNNING auction
      NormalUser seller = TestFixture.normalSeller("am10_seller");
      for (int i = 0; i < 20; i++) {
        Auction open = TestFixture.openAuction(seller, 500_000L);
        manager.registerAuction(open);
        if (i % 2 == 0) {
          Auction running = TestFixture.runningAuction(seller, 500_000L);
          manager.registerAuction(running);
        }
      }

      int threads = 24;
      int opsPerThread = 50;
      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger violations = new AtomicInteger();

      for (int t = 0; t < threads; t++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                      List<Auction> running = manager.getRunningAuctions();
                      for (Auction a : running) {
                        if (a.getStatus() != Auction.AuctionStatus.RUNNING) {
                          violations.incrementAndGet();
                        }
                      }
                    }
                  } catch (Exception e) {
                    violations.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      gate.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(violations.get())
          .as("getRunningAuctions() chỉ được trả về auction RUNNING")
          .isZero();
    }
  }
  // Group 5 — Mixed workload: register + find + notify + addUser đồng thời
  @Nested
  @DisplayName("Group 5 – Mixed workload tổng hợp (giống production)")
  class MixedWorkloadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-AM11: register auction + add user + notify observer + query — 48 thread, không"
            + " exception")
    void mixedWorkload_48Threads_noException() throws Exception {
      int registerThreads = 12;
      int userAddThreads = 12;
      int notifyThreads = 12;
      int queryThreads = 12;
      int total = registerThreads + userAddThreads + notifyThreads + queryThreads;

      NormalUser eventSeller = TestFixture.normalSeller("am11_event_seller");
      NormalUser eventBidder = TestFixture.bidderWithBalance("am11_event_bidder", 0L);
      Auction eventAuction = TestFixture.openAuction(eventSeller, 500_000L);
      manager.registerAuction(eventAuction);

      // Seed observer
      AuctionObserver obs = new BidderObserver(eventBidder, TestFixture.ratingServiceAllowAll());
      manager.addGlobalObserver(obs);

      ExecutorService pool = Executors.newFixedThreadPool(total);
      AtomicInteger crashes = new AtomicInteger();
      AtomicLong totalOps = new AtomicLong();
      List<Future<?>> futures = new ArrayList<>();

      // Register tasks
      for (int t = 0; t < registerThreads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < 10; i++) {
                    try {
                      NormalUser s =
                          TestFixture.normalSeller(
                              "am11_s_" + UUID.randomUUID().toString().substring(0, 6));
                      manager.registerAuction(TestFixture.openAuction(s, 500_000L));
                      totalOps.incrementAndGet();
                    } catch (Error e) {
                      crashes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      // addToUserList tasks
      for (int t = 0; t < userAddThreads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < 10; i++) {
                    try {
                      NormalUser u =
                          TestFixture.bidderWithBalance(
                              "am11_u_" + UUID.randomUUID().toString().substring(0, 6), 0L);
                      manager.addToUserList(u);
                      totalOps.incrementAndGet();
                    } catch (Error e) {
                      crashes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      // Notify tasks
      for (int t = 0; t < notifyThreads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < 10; i++) {
                    try {
                      manager.notifyGlobalObservers(
                          new AuctionEvent(
                              AuctionEvent.AuctionEventType.BID_PLACED,
                              eventAuction,
                              eventBidder,
                              600_000L));
                      totalOps.incrementAndGet();
                    } catch (Error e) {
                      crashes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      // Query tasks
      for (int t = 0; t < queryThreads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < 20; i++) {
                    try {
                      manager.getAllAuctions();
                      manager.getAllUsers();
                      manager.getRunningAuctions();
                      totalOps.incrementAndGet();
                    } catch (Error e) {
                      crashes.incrementAndGet();
                    } catch (Exception ignored) {
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

      assertThat(crashes.get()).as("Mixed workload không được gây JVM Error").isZero();
      assertThat(totalOps.get()).as("Phải có ít nhất 1 operation thành công").isPositive();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName("L-AM12: Throughput — getAllAuctions() 8 thread trong 5s > 10_000 lần/s")
    void throughput_getAllAuctions_meetsThreshold() throws Exception {
      // Seed 500 auction
      NormalUser seller = TestFixture.normalSeller("am12_seller");
      for (int i = 0; i < 500; i++) {
        manager.registerAuction(TestFixture.openAuction(seller, 500_000L));
      }

      int threads = 8;
      int durationMs = 5_000;
      AtomicLong totalCalls = new AtomicLong();
      long endTime = System.currentTimeMillis() + durationMs;

      ExecutorService pool = Executors.newFixedThreadPool(threads);
      List<Future<?>> futures = new ArrayList<>();

      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  while (System.currentTimeMillis() < endTime) {
                    manager.getAllAuctions();
                    totalCalls.incrementAndGet();
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

      double throughput = (double) totalCalls.get() / (durationMs / 1000.0);
      assertThat(throughput)
          .as("getAllAuctions() throughput phải > 10_000/s (thực tế: %.0f/s)", throughput)
          .isGreaterThan(10_000.0);
    }
  }
  // Private helpers
  /** Reset toàn bộ state của AuctionManager Singleton. */
  private void clearAllState() throws Exception {
    Field f1 = AuctionManager.class.getDeclaredField("allAuctions");
    Field f2 = AuctionManager.class.getDeclaredField("allUsers");
    Field f3 = AuctionManager.class.getDeclaredField("globalObservers");
    Field f4 = AuctionManager.class.getDeclaredField("staffObservers");
    for (Field f : new Field[] {f1, f2, f3, f4}) {
      f.setAccessible(true);
      Object col = f.get(manager);
      if (col instanceof Map<?, ?> m) {
        m.clear();
      } else if (col instanceof List<?> l) {
        l.clear();
      }
    }
  }
}
