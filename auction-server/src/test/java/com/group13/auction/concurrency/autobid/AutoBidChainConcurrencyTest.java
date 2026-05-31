package com.group13.auction.concurrency.autobid;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;

/**
 * ============================================================================
 * AutoBidChainConcurrencyTest — Group D (BIG BANG) Tất cả thành phần thực: BidService,
 * AuctionLockRegistry, AutoBidRegistry, AutoBidProcessor, StandardBidStrategy, AutoBidStrategy. Chỉ
 * mock: DAO, SessionManager, IAuctionService.
 * ============================================================================
 */
@DisplayName("AutoBid: Chain Integration (BIG BANG)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AutoBidChainConcurrencyTest extends ConcurrencyTestBase {

  private static final int THREAD_COUNT = 10;

  private BidService bidService;
  private AutoBidProcessor autoBidProcessor;
  private AuctionLockRegistry lockRegistry;
  private AutoBidRegistry autoBidRegistry;
  private Auction auction;
  /** Giả lập JOINED trong DB cho AutoBidProcessor (UserDAO thật không có Testcontainers ở đây). */
  private final java.util.Map<String, java.util.Set<String>> joinedInDbSimulation =
      new java.util.concurrent.ConcurrentHashMap<>();

  @BeforeEach
  void setUp() {
    IAuctionService mockAuctionService = mock(IAuctionService.class);
    IRatingService mockRatingService = mock(IRatingService.class);
    IWalletService mockWalletService = mock(IWalletService.class);
    BidTransactionDAO mockBidTransactionDAO = mock(BidTransactionDAO.class);
    AuctionDAO mockAuctionDAO = mock(AuctionDAO.class);
    UserDAO mockUserDAO = mock(UserDAO.class);
    SessionManager mockSessionManager = mock(SessionManager.class);

    when(mockRatingService.isEligible(any())).thenReturn(true);
    when(mockBidTransactionDAO.saveTransactionAndUpdatePrice(
            any(), anyString(), anyLong(), anyString()))
        .thenReturn(true);
    when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
    when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
    doNothing().when(mockWalletService).lockDeposit(any(), anyLong(), any());

    resetAuctionManagerUsers();

    bidService =
        new BidService(
            mockAuctionService,
            mockRatingService,
            mockWalletService,
            mockBidTransactionDAO,
            mockAuctionDAO,
            mockUserDAO);
    autoBidProcessor = new AutoBidProcessor(bidService, mockSessionManager);
    injectAutoBidUserDaoMock(autoBidProcessor);
    lockRegistry = AuctionLockRegistry.getInstance();
    autoBidRegistry = AutoBidRegistry.getInstance();
    joinedInDbSimulation.clear();
    auction = buildRunningAuction();
  }

  @AfterEach
  void tearDown() {
    joinedInDbSimulation.clear();
    clearAutoBidProcessorState();
    autoBidRegistry.clearAuction(auction.getId());
    lockRegistry.release(auction.getId());
    resetAuctionManagerUsers();
  }

  // D1

  @Test
  @Order(1)
  @DisplayName("D1: Auto-bid counter-trigger sau khi manual bidder vượt qua")
  void autoBid_triggersCounterBid_afterManualBidderSurpasses() {
    NormalUser autoBidder = buildUser("autoBidderD1", USER_BALANCE);
    NormalUser manualBidder = buildUser("manualBidderD1", USER_BALANCE);
    autoBidder.addJoinedAuction(auction.getId());
    manualBidder.addJoinedAuction(auction.getId());

    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

    long maxBid = 1_000_000L;
    autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

    long manualBidAmount = 600_000L;
    ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      bidService.placeBid(manualBidder, auction, manualBidAmount, new StandardBidStrategy());
    } finally {
      lock.unlock();
    }
    // FIX: submit() NGOÀI lock — chain cần lock để chạy placeBid
    autoBidProcessor.submit(auction, manualBidder.getId());
    awaitAutoBidChain(auction.getId(), 4);

    long expectedAutoPrice = manualBidAmount + BidIncrementCalculator.calculate(manualBidAmount);

    assertThat(auction.getCurrentPrice())
        .as("Auto-bid phải counter lên manualBid + increment")
        .isEqualTo(expectedAutoPrice);
    assertThat(auction.getCurrentLeader())
        .as("AutoBidder phải là leader sau counter")
        .isSameAs(autoBidder);
    assertThat(auction.getCurrentPrice())
        .as("Giá không được vượt maxBid")
        .isLessThanOrEqualTo(maxBid);
  }

  // D2

  @Test
  @Order(2)
  @DisplayName("D2: Auto-bid không trigger khi nextBid vượt maxBid — entry bị xóa khỏi registry")
  void autoBid_doesNotTrigger_whenNextBidExceedsMaxBid() {
    NormalUser autoBidder = buildUser("autoBidderD2", USER_BALANCE);
    NormalUser manualBidder = buildUser("manualBidderD2", USER_BALANCE);
    autoBidder.addJoinedAuction(auction.getId());
    manualBidder.addJoinedAuction(auction.getId());

    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

    long maxBid = 560_000L;
    long manualAmount = 550_000L;
    autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

    ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      bidService.placeBid(manualBidder, auction, manualAmount, new StandardBidStrategy());
    } finally {
      lock.unlock();
    }
    autoBidProcessor.submit(auction, manualBidder.getId());
    awaitAutoBidChain(auction.getId(), 4);

    assertThat(auction.getCurrentPrice())
        .as("Giá phải là manualAmount vì auto-bid exhausted")
        .isEqualTo(manualAmount);
    assertThat(auction.getCurrentLeader()).as("Leader phải là manualBidder").isSameAs(manualBidder);
    assertThat(autoBidRegistry.hasActiveBid(autoBidder.getId(), auction.getId()))
        .as("AutoBidder phải bị xóa khỏi registry khi maxBid cạn")
        .isFalse();
  }

  // D3

  @Test
  @Order(3)
  @DisplayName("D3: Chuỗi auto-bid giữa 2 auto-bidders — giá cuối không vượt maxBid của ai")
  void autoBidChain_twoAutoBidders_finalPriceWithinBothMaxBids() {
    NormalUser autoBidderA = buildUser("autoBidderA_D3", USER_BALANCE);
    NormalUser autoBidderB = buildUser("autoBidderB_D3", USER_BALANCE);
    NormalUser manualBidder = buildUser("manualBidder_D3", USER_BALANCE);

    autoBidderA.addJoinedAuction(auction.getId());
    autoBidderB.addJoinedAuction(auction.getId());
    manualBidder.addJoinedAuction(auction.getId());

    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderA);
    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderB);

    long maxBidA = 800_000L;
    long maxBidB = 900_000L;
    autoBidRegistry.register(autoBidderA.getId(), auction.getId(), maxBidA);
    autoBidRegistry.register(autoBidderB.getId(), auction.getId(), maxBidB);

    long manualBid = 600_000L;
    ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      bidService.placeBid(manualBidder, auction, manualBid, new StandardBidStrategy());
    } finally {
      lock.unlock();
    }
    autoBidProcessor.submit(auction, manualBidder.getId());
    awaitAutoBidChain(auction.getId(), 4);

    long finalPrice = auction.getCurrentPrice();

    assertThat(finalPrice)
        .as("Giá cuối phải <= maxBidB (người thắng)")
        .isLessThanOrEqualTo(Math.max(maxBidA, maxBidB));
    assertThat(finalPrice).isGreaterThanOrEqualTo(manualBid);

    NormalUser leader = auction.getCurrentLeader();
    assertThat(leader).isIn(autoBidderA, autoBidderB);

    long winnerMaxBid = leader.equals(autoBidderA) ? maxBidA : maxBidB;
    assertThat(finalPrice)
        .as("Giá cuối không được vượt maxBid của người thắng: %d", winnerMaxBid)
        .isLessThanOrEqualTo(winnerMaxBid);
  }

  // D4

  @Test
  @Order(4)
  @DisplayName(
      "D4: 10 threads concurrent với AutoBidProcessor — giá tăng monotonically, không race"
          + " condition")
  @Timeout(value = 15)
  void concurrentManualBids_withAutoBidProcessor_noConcurrencyBug() throws InterruptedException {
    NormalUser autoBidder = buildUser("autoBidder_D4", USER_BALANCE * 10);
    autoBidder.addJoinedAuction(auction.getId());
    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

    List<NormalUser> manualBidders = buildBidders(THREAD_COUNT);
    manualBidders.forEach(
        b -> {
          b.addJoinedAuction(auction.getId());
          b.setBalance(USER_BALANCE);
        });

    long maxBid = 5_000_000L;
    autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

    ReentrantLock lock = lockRegistry.getLock(auction.getId());
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);
    List<Long> snapshots = new CopyOnWriteArrayList<>();

    for (int i = 0; i < THREAD_COUNT; i++) {
      NormalUser mb = manualBidders.get(i);
      final long bid = STARTING_PRICE + 50_000L * (i + 1);
      new Thread(
              () -> {
                try {
                  gate.await();
                  lock.lock();
                  try {
                    bidService.placeBid(mb, auction, bid, new StandardBidStrategy());
                    snapshots.add(auction.getCurrentPrice());
                  } catch (Exception ignored) {
                  } finally {
                    lock.unlock();
                  }
                  // FIX: submit() NGOÀI lock
                  autoBidProcessor.submit(auction, mb.getId());
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    awaitAutoBidChain(auction.getId(), 5);

    for (int i = 1; i < snapshots.size(); i++) {
      assertThat(snapshots.get(i))
          .as("Giá tại snapshot[%d] >= snapshot[%d-1]", i, i)
          .isGreaterThanOrEqualTo(snapshots.get(i - 1));
    }

    assertThat(auction.getCurrentPrice())
        .as("Giá cuối không được vượt maxBid")
        .isLessThanOrEqualTo(maxBid);
  }

  @Test
  @Order(5)
  @DisplayName("D4b: placeBid + autoBid ngoài lock (giống BidHandler) — không vượt maxBid")
  @Timeout(15)
  void manualBidThenAutoBidOutsideLock_matchesProductionPath() throws InterruptedException {
    NormalUser autoBidder = buildUser("autoBidder_D4b", USER_BALANCE * 10);
    autoBidder.addJoinedAuction(auction.getId());
    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidder);

    List<NormalUser> manualBidders = buildBidders(THREAD_COUNT);
    manualBidders.forEach(
        b -> {
          b.addJoinedAuction(auction.getId());
          b.setBalance(USER_BALANCE);
        });

    long maxBid = 5_000_000L;
    autoBidRegistry.register(autoBidder.getId(), auction.getId(), maxBid);

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);
    List<Long> snapshots = new CopyOnWriteArrayList<>();

    for (int i = 0; i < THREAD_COUNT; i++) {
      NormalUser mb = manualBidders.get(i);
      final long bid = STARTING_PRICE + 50_000L * (i + 1);
      new Thread(
              () -> {
                try {
                  gate.await();
                  try {
                    bidService.placeBid(mb, auction, bid, new StandardBidStrategy());
                    autoBidProcessor.submit(auction, mb.getId());
                    snapshots.add(auction.getCurrentPrice());
                  } catch (Exception ignored) {
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
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    for (int i = 1; i < snapshots.size(); i++) {
      assertThat(snapshots.get(i)).isGreaterThanOrEqualTo(snapshots.get(i - 1));
    }
    assertThat(auction.getCurrentPrice()).isLessThanOrEqualTo(maxBid);
  }

  // D3b: Luồng đăng ký autobid (giống BidHandler.register)

  @Test
  @Order(35)
  @DisplayName("D3b: Hai user register autobid lần lượt — chain phải counter sau user thứ 2")
  void registerFlow_twoAutoBidders_chainContinuesAfterSecondRegister() {
    long startingPrice = 12L;
    auction =
        buildRunningAuction(
            startingPrice, 50_000_000L, java.time.LocalDateTime.now().plusHours(2));
    com.group13.auction.manager.AuctionManager.getInstance().registerAuction(auction);

    NormalUser autoBidderA = buildUser("regFlowA", USER_BALANCE);
    NormalUser autoBidderB = buildUser("regFlowB", USER_BALANCE);
    autoBidderA.addJoinedAuction(auction.getId());
    autoBidderB.addJoinedAuction(auction.getId());
    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderA);
    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderB);

    long maxBid = 10_000_000L;
    ReentrantLock lock = lockRegistry.getLock(auction.getId());

    // User A register (first bid)
    lock.lock();
    try {
      autoBidRegistry.register(autoBidderA.getId(), auction.getId(), maxBid);
      long nextA = new AutoBidStrategy(maxBid).calculateNextBid(auction);
      bidService.placeBid(autoBidderA, auction, nextA, new AutoBidStrategy(maxBid));
    } finally {
      lock.unlock();
    }
    autoBidProcessor.submit(auction, autoBidderA.getId());
    awaitAutoBidChain(auction.getId(), 4);
    long afterA = auction.getCurrentPrice();

    // User B register (first bid)
    lock.lock();
    try {
      autoBidRegistry.register(autoBidderB.getId(), auction.getId(), maxBid);
      long nextB = new AutoBidStrategy(maxBid).calculateNextBid(auction);
      bidService.placeBid(autoBidderB, auction, nextB, new AutoBidStrategy(maxBid));
    } finally {
      lock.unlock();
    }
    autoBidProcessor.submit(auction, autoBidderB.getId());
    awaitAutoBidChain(auction.getId(), 4);

    assertThat(afterA).isEqualTo(50_012L);
    assertThat(auction.getCurrentPrice())
        .as("Sau khi B register, chain phải counter (A) — giá > 100_012")
        .isGreaterThan(100_012L);
  }

  @Test
  @Order(37)
  @DisplayName(
      "D3d: 10 autobid (login RAM thiếu JOINED) — 1 manual trigger, chain escalation qua hydrate DB")
  void tenUsers_loginStaleManager_tenAutoBidders_chainEscalatesAfterManualTrigger()
      throws Exception {
    long startingPrice = 12L;
    auction =
        buildRunningAuction(
            startingPrice, 50_000_000L, java.time.LocalDateTime.now().plusHours(2));
    com.group13.auction.manager.AuctionManager.getInstance().registerAuction(auction);
    String auctionId = auction.getId();

    int totalUsers = 10;
    long maxBid = 50_000_000L;
    List<NormalUser> sessionUsers = new java.util.ArrayList<>();

    for (int i = 0; i < totalUsers; i++) {
      NormalUser sessionUser = buildUser("tenU" + i, USER_BALANCE);
      sessionUser.addJoinedAuction(auctionId);
      sessionUsers.add(sessionUser);

      NormalUser managerStale =
          NormalUser.reconstitute(
              sessionUser.getId(),
              sessionUser.getCreatedAt(),
              sessionUser.getUpdatedAt(),
              "mgr_" + i,
              "hash",
              "mgr" + i + "@test.com",
              com.group13.auction.model.user.User.AccountStatus.ACTIVE,
              3.0,
              USER_BALANCE,
              0L,
              java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER),
              false,
              0,
              null);
      com.group13.auction.manager.AuctionManager.getInstance().refreshUser(managerStale);
      joinedInDbSimulation.put(sessionUser.getId(), java.util.Set.of(auctionId));
      autoBidRegistry.register(sessionUser.getId(), auctionId, maxBid);
    }

    NormalUser trigger = sessionUsers.get(0);
    long manualBid = startingPrice + BidIncrementCalculator.calculate(startingPrice);
    ReentrantLock lock = lockRegistry.getLock(auctionId);
    lock.lock();
    try {
      bidService.placeBid(trigger, auction, manualBid, new StandardBidStrategy());
    } finally {
      lock.unlock();
    }
    autoBidProcessor.submit(auction, trigger.getId());
    awaitAutoBidChain(auctionId, 10);

    assertThat(auction.getCurrentLeader()).isNotNull();
    assertThat(auction.getCurrentPrice())
        .as("10 autobid + hydrate JOINED: chain phải vượt bid tay đầu")
        .isGreaterThan(manualBid);
    assertThat(auction.getCurrentPrice()).isLessThanOrEqualTo(maxBid);
  }

  // D5

  @Test
  @Order(6)
  @DisplayName("D5: Hai auto-bidder cùng maxBid — AutoBidProcessor không bị infinite loop")
  @Timeout(value = 15)
  void autoBidProcessor_sameMaxBid_noInfiniteLoop() throws InterruptedException {
    NormalUser autoBidderX = buildUser("autoBidderX_D5", USER_BALANCE);
    NormalUser autoBidderY = buildUser("autoBidderY_D5", USER_BALANCE);
    NormalUser manualBidder = buildUser("manualBidder_D5", USER_BALANCE);

    autoBidderX.addJoinedAuction(auction.getId());
    autoBidderY.addJoinedAuction(auction.getId());
    manualBidder.addJoinedAuction(auction.getId());

    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderX);
    com.group13.auction.manager.AuctionManager.getInstance().addToUserList(autoBidderY);

    long sameMaxBid = 700_000L;
    autoBidRegistry.register(autoBidderX.getId(), auction.getId(), sameMaxBid);
    Thread.sleep(5); // đảm bảo registeredAt của Y muộn hơn X
    autoBidRegistry.register(autoBidderY.getId(), auction.getId(), sameMaxBid);

    long manualBid = 550_000L;
    ReentrantLock lock = lockRegistry.getLock(auction.getId());

    // FIX: placeBid trong lock, submit() NGOÀI lock
    assertThatCode(
            () -> {
              lock.lock();
              try {
                bidService.placeBid(manualBidder, auction, manualBid, new StandardBidStrategy());
              } finally {
                lock.unlock();
              }
              autoBidProcessor.submit(auction, manualBidder.getId());
              awaitAutoBidChain(auction.getId(), 4);
            })
        .doesNotThrowAnyException();

    assertThat(auction.getCurrentPrice())
        .isGreaterThanOrEqualTo(manualBid)
        .isLessThanOrEqualTo(sameMaxBid);
  }

  // Helpers (reflection — không đụng production code)

  private void clearAutoBidProcessorState() {
    try {
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
        try {
          ex.awaitTermination(300, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
      }
      executors.clear();
      for (String name :
          new String[] {"chainRunning", "chainNeedsRecheck", "bidActivityRings", "lastAutoBidMs"}) {
        java.lang.reflect.Field f = AutoBidProcessor.class.getDeclaredField(name);
        f.setAccessible(true);
        Object map = f.get(null);
        if (map instanceof java.util.Map<?, ?> m) {
          m.clear();
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void injectAutoBidUserDaoMock(AutoBidProcessor processor) {
    try {
      UserDAO mockUserDao = mock(UserDAO.class);
      when(mockUserDao.findNormalUserById(anyString())).thenReturn(null);
      when(mockUserDao.findJoinedAuctionIdsByUserId(anyString()))
          .thenAnswer(
              inv ->
                  joinedInDbSimulation.getOrDefault(
                      inv.getArgument(0, String.class), java.util.Set.of()));
      java.lang.reflect.Field f = AutoBidProcessor.class.getDeclaredField("userDAO");
      f.setAccessible(true);
      f.set(processor, mockUserDao);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void awaitAutoBidChain(String auctionId, long timeoutSeconds) {
    try {
      java.lang.reflect.Field f = AutoBidProcessor.class.getDeclaredField("chainRunning");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean>
          runningMap =
              (java.util.concurrent.ConcurrentHashMap<
                      String, java.util.concurrent.atomic.AtomicBoolean>)
                  f.get(null);
      long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;
      Thread.sleep(20);
      while (System.currentTimeMillis() < deadline) {
        java.util.concurrent.atomic.AtomicBoolean flag = runningMap.get(auctionId);
        if (flag == null || !flag.get()) {
          return;
        }
        Thread.sleep(30);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception ignored) {
    }
  }
}
