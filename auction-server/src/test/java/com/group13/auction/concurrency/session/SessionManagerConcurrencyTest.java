package com.group13.auction.concurrency.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.network.server.session.SessionManager;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

/**
 * ============================================================================
 * SessionManagerConcurrencyTest — Group C (SANDWICH)
 *
 * <p>SessionManager Singleton dùng ConcurrentHashMap → thread-safe theo Javadoc. Test này verify
 * không có state corruption khi: - register/unregister/authenticate chạy đồng thời (sandwich real
 * vs mock) - broadcast khi session đang bị remove đồng thời (lost update check) - watcher list
 * đọc/ghi đồng thời không NPE
 *
 * <p>GAP-SESSION-1: register + authenticate đồng thời → byUserId nhất quán GAP-SESSION-2:
 * unregister giữa chừng broadcast → không NPE/exception GAP-SESSION-3: addAuctionWatcher +
 * removeAuctionWatcher đồng thời → không ConcurrentModificationException
 * ============================================================================
 */
@DisplayName("SessionManager: register/authenticate/broadcast concurrency (SANDWICH)")
@TestMethodOrder(OrderAnnotation.class)
class SessionManagerConcurrencyTest extends ConcurrencyTestBase {

  private static final int THREAD_COUNT = 20;
  private static final int TIMEOUT_SECONDS = 15;

  private SessionManager sessionManager;

  @BeforeEach
  void setUp() throws Exception {
    sessionManager = SessionManager.getInstance();
    clearMaps();
  }

  @AfterEach
  void tearDown() throws Exception {
    clearMaps();
  }

  private void clearMaps() {
    for (String field : new String[] {"byConnection", "byUserId"}) {
      try {
        Field f = SessionManager.class.getDeclaredField(field);
        f.setAccessible(true);
        ((ConcurrentHashMap<?, ?>) f.get(sessionManager)).clear();
      } catch (Exception e) {
        log.warn("Cannot clear SessionManager.{}: {}", field, e.getMessage());
      }
    }
  }

  private WebSocket mockWs(String id) {
    WebSocket ws = mock(WebSocket.class, withSettings().name("ws-" + id));
    lenient().when(ws.isOpen()).thenReturn(true);
    lenient()
        .when(ws.getRemoteSocketAddress())
        .thenReturn(new InetSocketAddress("127.0.0.1", 8080));
    return ws;
  }

  // ── GAP-SESSION-1 ─────────────────────────────────────────────────────────

  @Nested
  @Order(1)
  @DisplayName("GAP-SESSION-1 — register + authenticate đồng thời, byUserId nhất quán")
  class RegisterAuthenticateConcurrencyTest {

    @Test
    @Order(1)
    @Timeout(TIMEOUT_SECONDS)
    @DisplayName("C1a: 20 thread register rồi authenticate đồng thời — getConnectedCount đúng")
    void registerThenAuthenticate_concurrent_connectedCountCorrect() throws Exception {
      List<WebSocket> wsList = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        wsList.add(mockWs("c1a-" + i));
      }

      ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
      CountDownLatch startGate = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        final int idx = i;
        futures.add(
            pool.submit(
                () -> {
                  try {
                    startGate.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  try {
                    sessionManager.register(wsList.get(idx));
                    sessionManager.authenticate(
                        wsList.get(idx), "userId-c1a-" + idx, "user-c1a-" + idx, "NORMAL_USER");
                  } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[CONC SESSION] C1a error: {}", e.getMessage());
                  }
                }));
      }

      startGate.countDown();
      for (Future<?> f : futures) {
        f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      pool.shutdown();

      assertThat(errors.get())
          .as("Không có exception khi register+authenticate đồng thời")
          .isZero();
      assertThat(sessionManager.getConnectedCount()).isEqualTo(THREAD_COUNT);
      assertThat(sessionManager.getAuthenticatedCount()).isEqualTo(THREAD_COUNT);
    }

    @Test
    @Order(2)
    @Timeout(TIMEOUT_SECONDS)
    @DisplayName("C1b: authenticate sau đó deauthenticate đồng thời — không NPE, byUserId sạch")
    void authenticateDeauthenticate_concurrent_noNPE() throws Exception {
      List<WebSocket> wsList = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        WebSocket ws = mockWs("c1b-" + i);
        wsList.add(ws);
        sessionManager.register(ws);
      }

      ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
      CountDownLatch startGate = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        final int idx = i;
        futures.add(
            pool.submit(
                () -> {
                  try {
                    startGate.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  try {
                    sessionManager.authenticate(
                        wsList.get(idx), "userId-c1b-" + idx, "user-c1b-" + idx, "NORMAL_USER");
                    sessionManager.deauthenticate(wsList.get(idx));
                  } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[CONC SESSION] C1b error: {}", e.getMessage());
                  }
                }));
      }

      startGate.countDown();
      for (Future<?> f : futures) {
        f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      pool.shutdown();

      assertThat(errors.get()).as("Không có exception khi auth+deauth đồng thời").isZero();
      // Sau deauthenticate, byUserId phải trống
      assertThat(sessionManager.getAuthenticatedCount())
          .as("Tất cả đã deauthenticate → byUserId = 0")
          .isZero();
    }
  }

  // ── GAP-SESSION-2 ─────────────────────────────────────────────────────────

  @Nested
  @Order(2)
  @DisplayName("GAP-SESSION-2 — unregister giữa chừng không NPE")
  class UnregisterDuringOperationTest {

    @Test
    @Order(1)
    @Timeout(TIMEOUT_SECONDS)
    @DisplayName("C2a: register rồi unregister đồng thời — byConnection nhất quán")
    void registerUnregister_concurrent_byConnectionConsistent() throws Exception {
      List<WebSocket> wsList = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        wsList.add(mockWs("c2a-" + i));
      }

      ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
      CountDownLatch startGate = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      List<Future<?>> futures = new ArrayList<>();
      for (WebSocket ws : wsList) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    startGate.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  try {
                    sessionManager.register(ws);
                    sessionManager.unregister(ws);
                  } catch (Exception e) {
                    errors.incrementAndGet();
                  }
                }));
      }

      startGate.countDown();
      for (Future<?> f : futures) {
        f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      pool.shutdown();

      assertThat(errors.get()).isZero();
      assertThat(sessionManager.getConnectedCount())
          .as("Tất cả đã unregister → byConnection = 0")
          .isZero();
    }
  }

  // ── GAP-SESSION-3 ─────────────────────────────────────────────────────────

  @Nested
  @Order(3)
  @DisplayName("GAP-SESSION-3 — addAuctionWatcher + removeAuctionWatcher đồng thời")
  class WatcherConcurrencyTest {

    @Test
    @Order(1)
    @Timeout(TIMEOUT_SECONDS)
    @DisplayName(
        "C3a: nhiều session cùng watch/unwatch một auction — không ConcurrentModificationException")
    void watchUnwatch_sameAuction_noConcurrentModification() throws Exception {
      int WATCHERS = 20;
      String auctionId = "auction-conc-test";

      List<WebSocket> wsList = new ArrayList<>();
      for (int i = 0; i < WATCHERS; i++) {
        WebSocket ws = mockWs("c3a-" + i);
        wsList.add(ws);
        sessionManager.register(ws);
      }

      ExecutorService pool = Executors.newFixedThreadPool(WATCHERS);
      CountDownLatch startGate = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < WATCHERS; i++) {
        final WebSocket ws = wsList.get(i);
        futures.add(
            pool.submit(
                () -> {
                  try {
                    startGate.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  try {
                    sessionManager.addAuctionWatcher(ws, auctionId);
                    sessionManager.removeAuctionWatcher(ws, auctionId);
                  } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[CONC SESSION] C3a watcher error: {}", e.getMessage());
                  }
                }));
      }

      startGate.countDown();
      for (Future<?> f : futures) {
        f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      pool.shutdown();

      assertThat(errors.get()).as("Không có exception khi watch/unwatch đồng thời").isZero();

      // Sau khi tất cả removeAuctionWatcher, danh sách watcher phải trống
      List<String> watchers = sessionManager.getUserIdsWatchingAuction(auctionId);
      assertThat(watchers).as("Danh sách watcher phải trống sau remove hết").isEmpty();
    }

    @Test
    @Order(2)
    @Timeout(TIMEOUT_SECONDS)
    @DisplayName(
        "C3b: isOnline() đọc đồng thời khi authenticate/deauthenticate đang xảy ra — không NPE")
    void isOnline_concurrentRead_noNPE() throws Exception {
      List<WebSocket> wsList = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        WebSocket ws = mockWs("c3b-" + i);
        wsList.add(ws);
        sessionManager.register(ws);
        sessionManager.authenticate(ws, "userId-c3b-" + i, "user-c3b-" + i, "NORMAL_USER");
      }

      ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
      CountDownLatch startGate = new CountDownLatch(1);
      AtomicInteger errors = new AtomicInteger();

      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        final int idx = i;
        final WebSocket ws = wsList.get(i);
        futures.add(
            pool.submit(
                () -> {
                  try {
                    startGate.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                  try {
                    // Đọc isOnline đồng thời với deauthenticate trên thread khác
                    if (idx % 2 == 0) {
                      sessionManager.isOnline("userId-c3b-" + idx);
                    } else {
                      sessionManager.deauthenticate(ws);
                    }
                  } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("[CONC SESSION] C3b error: {}", e.getMessage());
                  }
                }));
      }

      startGate.countDown();
      for (Future<?> f : futures) {
        f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      pool.shutdown();

      assertThat(errors.get())
          .as("Không NPE khi đọc isOnline đồng thời với deauthenticate")
          .isZero();
    }
  }
}
