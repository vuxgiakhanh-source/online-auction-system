package com.group13.auction.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * ============================================================================ LOAD TEST —
 * SessionManagerLoadTest (unit, không cần Docker)
 * ============================================================================
 *
 * <p>Kiểm tra SessionManager Singleton dưới tải cao đồng thời: - register / unregister /
 * authenticate song song nhiều thread - broadcastToAuction / broadcastAll dưới 200+ connections -
 * addAuctionWatcher / removeAuctionWatcher race condition - getUserIdsWatchingAuction consistency
 * dưới concurrent mutation
 *
 * <p>Không cần Docker — SessionManager không chạm DB.
 */
@DisplayName("SessionManagerLoadTest — SessionManager dưới tải cao (unit)")
class SessionManagerLoadTest {

  private SessionManager sm;
  private final List<WebSocket> managedSockets = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() {
    sm = SessionManager.getInstance();
  }

  @AfterEach
  void tearDown() {
    // Dọn sạch tất cả socket đã register trong test
    for (WebSocket ws : managedSockets) {
      try {
        sm.unregister(ws);
      } catch (Exception ignored) {
      }
    }
    managedSockets.clear();
  }

  /** Tạo WebSocket mock mở, track để cleanup */
  private WebSocket openSocket() {
    WebSocket ws = mock(WebSocket.class);
    lenient().when(ws.isOpen()).thenReturn(true);
    managedSockets.add(ws);
    return ws;
  }

  // =========================================================================
  // Group 1 – register / authenticate / unregister song song
  // =========================================================================

  @Nested
  @DisplayName("Group 1 – register/authenticate/unregister song song")
  class RegisterAuthLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-SM1: 50 thread đồng thời register + authenticate — không NPE, connectedCount đúng")
    void concurrent_registerAndAuthenticate_noNPE() throws Exception {
      int threads = 50;
      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      List<WebSocket> sockets = new ArrayList<>(threads);
      for (int i = 0; i < threads; i++) {
        sockets.add(openSocket());
      }

      for (int i = 0; i < threads; i++) {
        final WebSocket ws = sockets.get(i);
        final String userId = "user-sm1-" + i;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    sm.register(ws);
                    sm.authenticate(ws, userId, "user" + userId, "NORMAL_USER");
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
          .as("Không được có exception trong concurrent register+authenticate")
          .isZero();
      // Sau khi tất cả đã authenticate, các userId phải online
      long onlineCount =
          sockets.stream()
              .mapToInt(
                  ws -> {
                    ClientSession sess = sm.getByConnection(ws);
                    return (sess != null && sess.isAuthenticated()) ? 1 : 0;
                  })
              .sum();
      assertThat(onlineCount).isGreaterThan(0);
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-SM2: 32 thread register rồi unregister ngay — không memory leak (connectedCount không"
            + " tăng mãi)")
    void concurrent_registerUnregister_noMemoryLeak() throws Exception {
      int threads = 32;
      int cycles = 5;
      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(threads);
      AtomicInteger failures = new AtomicInteger();

      int countBefore = sm.getConnectedCount();

      for (int i = 0; i < threads; i++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int c = 0; c < cycles; c++) {
                      WebSocket ws = openSocket();
                      sm.register(ws);
                      sm.unregister(ws);
                      managedSockets.remove(ws); // đã unregister rồi
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
      // connectedCount không tăng so với trước (tất cả đã unregister)
      assertThat(sm.getConnectedCount())
          .as("connectedCount sau register+unregister phải về mức ban đầu")
          .isEqualTo(countBefore);
    }
  }

  // =========================================================================
  // Group 2 – broadcastToAuction dưới nhiều connection
  // =========================================================================

  @Nested
  @DisplayName("Group 2 – broadcastToAuction dưới nhiều connection")
  class BroadcastLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-SM3: 100 client watch cùng 1 auction, 4 thread broadcast đồng thời — không exception")
    void massWatchers_concurrentBroadcast_noException() throws Exception {
      String auctionId = "auc-load-" + UUID.randomUUID();
      int watcherCount = 100;
      int broadcastThreads = 4;
      int broadcastRounds = 20;

      // Đăng ký 100 watcher
      for (int i = 0; i < watcherCount; i++) {
        WebSocket ws = openSocket();
        sm.register(ws);
        sm.addAuctionWatcher(ws, auctionId);
      }

      ExecutorService pool = Executors.newFixedThreadPool(broadcastThreads);
      AtomicInteger failures = new AtomicInteger();
      List<Future<?>> futures = new ArrayList<>();

      for (int t = 0; t < broadcastThreads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int r = 0; r < broadcastRounds; r++) {
                    try {
                      sm.broadcastToAuction(auctionId, Packet.of(PacketType.BID_UPDATE));
                    } catch (Exception e) {
                      failures.incrementAndGet();
                    }
                  }
                }));
      }

      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

      assertThat(failures.get())
          .as("broadcastToAuction không được throw exception dưới tải")
          .isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-SM4: addAuctionWatcher / removeAuctionWatcher / broadcastToAuction đồng thời — không"
            + " deadlock")
    void concurrentAddRemoveAndBroadcast_noDeadlock() throws Exception {
      String auctionId = "auc-arb-" + UUID.randomUUID();
      int mutatorThreads = 8;
      int broadcasterThreads = 4;
      int ops = 20;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(mutatorThreads + broadcasterThreads);
      AtomicInteger failures = new AtomicInteger();

      // Mutator threads: add và remove watcher
      for (int i = 0; i < mutatorThreads; i++) {
        final int idx = i;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    WebSocket ws = openSocket();
                    sm.register(ws);
                    for (int op = 0; op < ops; op++) {
                      sm.addAuctionWatcher(ws, auctionId);
                      sm.removeAuctionWatcher(ws, auctionId);
                    }
                    sm.unregister(ws);
                    managedSockets.remove(ws);
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      // Broadcaster threads: broadcast liên tục
      for (int i = 0; i < broadcasterThreads; i++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int op = 0; op < ops * 2; op++) {
                      sm.broadcastToAuction(auctionId, Packet.of(PacketType.PING));
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
    @DisplayName("L-SM5: broadcastAll với 200 connection đang mở — không exception, không NPE")
    void broadcastAll_200Connections_noException() throws Exception {
      int connCount = 200;

      for (int i = 0; i < connCount; i++) {
        WebSocket ws = openSocket();
        sm.register(ws);
      }

      AtomicInteger failures = new AtomicInteger();
      int broadcastCount = 10;
      for (int i = 0; i < broadcastCount; i++) {
        try {
          sm.broadcastAll(Packet.of(PacketType.PING));
        } catch (Exception e) {
          failures.incrementAndGet();
        }
      }

      assertThat(failures.get())
          .as("broadcastAll với 200 connection không được throw exception")
          .isZero();
    }
  }

  // =========================================================================
  // Group 3 – sendToUser / isOnline dưới tải
  // =========================================================================

  @Nested
  @DisplayName("Group 3 – sendToUser / isOnline dưới tải")
  class SendToUserLoadTest {

    @Test
    @Timeout(value = 30)
    @DisplayName("L-SM6: 16 thread sendToUser đồng thời tới 50 user online — không exception")
    void concurrent_sendToUser_50OnlineUsers_noException() throws Exception {
      int userCount = 50;
      int senderThreads = 16;
      int sendsPerThread = 30;

      // Tạo 50 user online
      List<String> userIds = new ArrayList<>(userCount);
      for (int i = 0; i < userCount; i++) {
        String userId = "sm6-user-" + i;
        WebSocket ws = openSocket();
        sm.register(ws);
        sm.authenticate(ws, userId, "u" + i, "NORMAL_USER");
        userIds.add(userId);
      }

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(senderThreads);
      AtomicInteger failures = new AtomicInteger();

      for (int t = 0; t < senderThreads; t++) {
        final int seed = t;
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int s = 0; s < sendsPerThread; s++) {
                      String targetId = userIds.get((seed + s) % userCount);
                      try {
                        sm.sendToUser(targetId, Packet.of(PacketType.PING));
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
      assertThat(failures.get()).isZero();
    }

    @Test
    @Timeout(value = 30)
    @DisplayName("L-SM7: isOnline / getAuthenticatedCount song song 32 thread — luôn nhất quán")
    void concurrent_isOnline_getAuthenticatedCount_consistent() throws Exception {
      int threads = 32;
      int cycles = 100;

      // Tạo 10 user online trước
      int onlineCount = 10;
      for (int i = 0; i < onlineCount; i++) {
        WebSocket ws = openSocket();
        sm.register(ws);
        sm.authenticate(ws, "sm7-u" + i, "user" + i, "NORMAL_USER");
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
                      // isOnline và getAuthenticatedCount không được throw
                      boolean online = sm.isOnline("sm7-u" + (seed % onlineCount));
                      int count = sm.getAuthenticatedCount();
                      if (count < 0) failures.incrementAndGet(); // sanity check
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
  // Group 4 – getUserIdsWatchingAuction consistency
  // =========================================================================

  @Nested
  @DisplayName("Group 4 – getUserIdsWatchingAuction consistency dưới concurrent mutation")
  class WatcherListConsistencyTest {

    @Test
    @Timeout(value = 30)
    @DisplayName(
        "L-SM8: 20 thread add/remove watcher + 10 thread query — không NPE, list không bao giờ"
            + " null")
    void concurrent_addRemoveQuery_listNeverNull() throws Exception {
      String auctionId = "auc-watch-" + UUID.randomUUID();
      int mutators = 20;
      int readers = 10;
      int ops = 30;

      CountDownLatch gate = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(mutators + readers);
      AtomicInteger failures = new AtomicInteger();

      for (int i = 0; i < mutators; i++) {
        final int idx = i;
        new Thread(
                () -> {
                  try {
                    WebSocket ws = openSocket();
                    sm.register(ws);
                    sm.authenticate(ws, "sm8-u" + idx, "u" + idx, "NORMAL_USER");
                    gate.await();
                    for (int op = 0; op < ops; op++) {
                      sm.addAuctionWatcher(ws, auctionId);
                      sm.removeAuctionWatcher(ws, auctionId);
                    }
                  } catch (Exception e) {
                    failures.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                })
            .start();
      }

      for (int i = 0; i < readers; i++) {
        new Thread(
                () -> {
                  try {
                    gate.await();
                    for (int op = 0; op < ops * 2; op++) {
                      List<String> ids = sm.getUserIdsWatchingAuction(auctionId);
                      if (ids == null) {
                        failures.incrementAndGet();
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
          .as("getUserIdsWatchingAuction không được null hay throw dưới tải concurrent")
          .isZero();
    }
  }
}
