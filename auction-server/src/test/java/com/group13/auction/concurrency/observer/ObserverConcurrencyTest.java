package com.group13.auction.concurrency.observer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.strategy.AuctionLockRegistry;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/**
 * ============================================================================
 * ObserverConcurrencyTest — GAP 4 + GAP 8
 *
 * <p>GAP 4: AuctionService.addObserver() + notify() concurrent → CopyOnWriteArrayList.contains() +
 * add() không atomic → duplicate risk. → Verify: không lost observer; duplicate chỉ khi cùng
 * instance.
 *
 * <p>GAP 8: Stress test observer add + notify nhiều rounds.
 * ============================================================================
 */
@DisplayName("Observer: addObserver() + notify() concurrent (GAP 4, GAP 8)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ObserverConcurrencyTest extends ConcurrencyTestBase {

  private AuctionLockRegistry lockRegistry;
  private Auction auction;
  private IRatingService mockRatingService;
  private AuctionDAO mockAuctionDAO;

  /**
   * Bootstrap SystemAdmin bằng reflection để tránh gọi DB thật. AuctionService có field `private
   * final SystemAdmin system = SystemAdmin.getInstance()` được khởi tạo tại field-level (trước
   * constructor body) → cần INSTANCE tồn tại trước khi `new AuctionService(...)` được gọi.
   */
  private static void bootstrapSystemAdminForTest() throws Exception {
    Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
    instanceField.setAccessible(true);
    if (instanceField.get(null) == null) {
      // Tạo mock SystemAdmin qua reflection để tránh gọi DB
      SystemAdmin mockAdmin = mock(SystemAdmin.class);
      instanceField.set(null, mockAdmin);
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    bootstrapSystemAdminForTest();

    mockRatingService = mock(IRatingService.class);
    mockAuctionDAO = mock(AuctionDAO.class);
    lockRegistry = AuctionLockRegistry.getInstance();
    auction = buildRunningAuction();
    resetAuctionManagerUsers();
  }

  @AfterEach
  void tearDown() {
    lockRegistry.release(auction.getId());
    resetAuctionManagerUsers();
  }

  // ── G4-1 ─────────────────────────────────────────────────────────────────

  @Test
  @Order(1)
  @DisplayName(
      "G4-1: 20 threads addObserver() với instance khác nhau — tất cả observers nhận notify()")
  @Timeout(value = 10)
  void concurrentAddObserver_allObserversReceiveNotify() throws InterruptedException {
    AuctionService as = new AuctionService(mockRatingService, mockAuctionDAO);

    int N = 20;
    AtomicInteger notifyCount = new AtomicInteger(0);
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(N);

    for (int i = 0; i < N; i++) {
      // Mỗi observer là instance riêng biệt — đếm số lần notify
      AuctionObserver obs = countingObserver(notifyCount);
      new Thread(
              () -> {
                try {
                  gate.await();
                  as.addObserver(auction.getId(), obs);
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

    // Notify tất cả với BID_PLACED → gọi onBidPlaced() trên mỗi observer
    as.notify(
        auction, AuctionEvent.AuctionEventType.BID_PLACED, buildUser("notifier", 0L), 600_000L);

    assertThat(notifyCount.get()).as("Tất cả %d observers phải nhận notify()", N).isEqualTo(N);
  }

  // ── G4-2 ─────────────────────────────────────────────────────────────────

  @Test
  @Order(2)
  @DisplayName(
      "G4-2: [KNOWN RISK] Cùng observer instance add từ 5 threads — duplicate detection không"
          + " atomic")
  @Timeout(value = 5)
  void sameObserver_concurrentAdd_duplicateRisk_documentedBehavior() throws InterruptedException {
    AuctionService as = new AuctionService(mockRatingService, mockAuctionDAO);
    AtomicInteger notifyCount = new AtomicInteger(0);
    // Cùng 1 instance — contains() + add() không atomic → có thể duplicate
    AuctionObserver singleObs = countingObserver(notifyCount);

    int N = 5;
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(N);

    for (int i = 0; i < N; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  as.addObserver(auction.getId(), singleObs);
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

    as.notify(
        auction, AuctionEvent.AuctionEventType.BID_PLACED, buildUser("notifier2", 0L), 600_000L);

    if (notifyCount.get() > 1) {
      log.warn(
          "[G4-2 KNOWN RISK] Observer duplicate: notifyCount={} "
              + "(contains+add not atomic → fix bằng synchronized block)",
          notifyCount.get());
    }

    // Observer phải nhận ít nhất 1 lần — không bao giờ 0
    assertThat(notifyCount.get())
        .as("Observer phải nhận ít nhất 1 notify")
        .isGreaterThanOrEqualTo(1);
  }

  // ── G8-1 ─────────────────────────────────────────────────────────────────

  @Test
  @Order(3)
  @DisplayName("G8-1: 20 unique observers concurrent add — không lost observer")
  @Timeout(value = 5)
  void uniqueObservers_concurrentAdd_noneAreLost() throws InterruptedException {
    AuctionService as = new AuctionService(mockRatingService, mockAuctionDAO);

    int N = 20;
    AtomicInteger total = new AtomicInteger(0);
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(N);

    for (int i = 0; i < N; i++) {
      AuctionObserver obs = countingObserver(total);
      new Thread(
              () -> {
                try {
                  gate.await();
                  as.addObserver(auction.getId(), obs);
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

    as.notify(auction, AuctionEvent.AuctionEventType.BID_PLACED, buildUser("x", 0L), 600_000L);

    assertThat(total.get())
        .as("Phải có đúng %d notifications, không lost observer", N)
        .isEqualTo(N);
  }

  // ── G8-2 ─────────────────────────────────────────────────────────────────

  @Test
  @Order(4)
  @DisplayName("G8-2: 5 rounds stress — mỗi round 10 observers add + notify đúng count")
  @Timeout(value = 15)
  void stressRounds_addAndNotify_countCorrect() throws InterruptedException {
    for (int round = 0; round < 5; round++) {
      Auction freshAuction = buildRunningAuction();
      AuctionService as = new AuctionService(mockRatingService, mockAuctionDAO);

      int N = 10;
      AtomicInteger count = new AtomicInteger(0);
      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(N);

      for (int i = 0; i < N; i++) {
        AuctionObserver obs = countingObserver(count);
        new Thread(
                () -> {
                  try {
                    gate.await();
                    as.addObserver(freshAuction.getId(), obs);
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

      as.notify(
          freshAuction,
          AuctionEvent.AuctionEventType.BID_PLACED,
          buildUser("n" + round, 0L),
          600_000L);

      assertThat(count.get()).as("[Round %d] Phải có đúng %d notifications", round, N).isEqualTo(N);

      lockRegistry.release(freshAuction.getId());
    }
  }
}
