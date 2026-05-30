package com.group13.auction.network.server.session;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton quản lý toàn bộ {@link ClientSession} đang kết nối.
 *
 * <p>Chịu trách nhiệm:
 *
 * <ul>
 *   <li>Đăng ký / huỷ đăng ký session khi client connect / disconnect.
 *   <li>Map WebSocket handle → ClientSession.
 *   <li>Map userId → ClientSession (sau khi xác thực).
 *   <li>Broadcast có chọn lọc: toàn bộ / theo auctionId / theo userId / theo role.
 * </ul>
 *
 * <p>Thread-safety: ConcurrentHashMap trên tất cả map.
 */
public class SessionManager {

  /**
   * FIX BROADCAST STALENESS v2 — Parallel within event, FIFO between events.
   *
   * <p>┌─────────────────────────────────────────────────────────────────────┐ │ Vấn đề với
   * sequential broadcast (forEach): │ │ │ │ Bid A → loop gửi user 1, 2, ..., 1000 tuần tự │ │ user
   * #1000 nhận trễ ~100ms so với user #1 │ │ Trong 100ms đó, Bid B đã xảy ra → user #1000 thấy
   * stale │ │ │ │ Fix: │ │ • broadcastOrderingExecutor (single-thread): đảm bảo thứ tự EVENT │ │ →
   * Bid A LUÔN được xử lý trước Bid B (FIFO queue) │ │ • Trong mỗi event: snapshot danh sách target
   * → parallelStream() │ │ → 1000 users nhận gần như ĐỒNG THỜI (~cùng ms) │ │ • sendPool (I/O
   * thread pool): thực hiện actual send song song │ │ │ │ Kết quả: │ │ User #1 và User #1000 nhận
   * BID_UPDATE(A) gần như cùng lúc │ │ → không còn stale window trong cùng 1 event │
   * └─────────────────────────────────────────────────────────────────────┘
   */

  /** Single-thread: đảm bảo thứ tự giữa các bid event (FIFO). */
  private final java.util.concurrent.ExecutorService broadcastOrderingExecutor =
      java.util.concurrent.Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "broadcast-ordering-thread");
            t.setDaemon(true);
            return t;
          });

  /**
   * Thread pool I/O để gửi song song trong cùng 1 event. Số thread = 2 × CPU cores, phù hợp với
   * I/O-bound WebSocket sends. Daemon thread để JVM không bị block khi shutdown.
   */
  private final java.util.concurrent.ExecutorService sendPool =
      java.util.concurrent.Executors.newFixedThreadPool(
          Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
          r -> {
            Thread t = new Thread(r, "broadcast-send-thread");
            t.setDaemon(true);
            return t;
          });

  private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

  private static final SessionManager INSTANCE = new SessionManager();

  /** WebSocket connection → ClientSession */
  private final ConcurrentHashMap<WebSocket, ClientSession> byConnection =
      new ConcurrentHashMap<>();

  /** userId → ClientSession (chỉ có sau khi authenticate) */
  private final ConcurrentHashMap<String, ClientSession> byUserId = new ConcurrentHashMap<>();

  private SessionManager() {}

  public static SessionManager getInstance() {
    return INSTANCE;
  }

  // ── Session lifecycle ─────────────────────────────────────────────────────

  /**
   * Đăng ký session mới khi WebSocket onOpen.
   *
   * @param connection WebSocket handle
   * @return ClientSession mới tạo
   */
  public ClientSession register(WebSocket connection) {
    ClientSession session = new ClientSession(connection);
    byConnection.put(connection, session);
    log.info(
        "New connection: remoteAddress={}, totalConnected={}",
        connection.getRemoteSocketAddress(),
        byConnection.size());
    return session;
  }

  /**
   * Xoá session khi WebSocket onClose.
   *
   * @param connection WebSocket handle
   */
  public void unregister(WebSocket connection) {
    ClientSession session = byConnection.remove(connection);
    if (session != null && session.getUserId() != null) {
      byUserId.remove(session.getUserId());
      log.info(
          "Session disconnected: userId={}, username={}",
          session.getUserId(),
          session.getUsername());
    }
  }

  /**
   * Gọi sau khi LOGIN_SUCCESS: liên kết userId với session để push sau này.
   *
   * @param connection WebSocket handle
   * @param userId userId từ DB
   * @param username username
   * @param role role string
   */
  public void authenticate(WebSocket connection, String userId, String username, String role) {
    authenticate(connection, userId, username, role, false);
  }

  public void authenticate(
      WebSocket connection, String userId, String username, String role, boolean restricted) {
    ClientSession session = byConnection.get(connection);
    if (session == null) {
      return;
    }

    // Nếu userId này đang có session cũ (ví dụ login từ tab khác) → đóng session cũ
    ClientSession oldSession = byUserId.get(userId);
    if (oldSession != null && oldSession != session) {
      oldSession.close(1000, "Logged in from another location");
      byConnection.remove(oldSession.getConnection());
      log.info("Replaced stale session: userId={}, username={}", userId, username);
    }

    session.authenticate(userId, username, role, restricted);
    byUserId.put(userId, session);
    log.info(
        "Session authenticated: userId={}, username={}, role={}, restricted={}",
        userId,
        username,
        role,
        restricted);
  }

  /**
   * Gọi khi LOGOUT hoặc session bị kick: xoá userId mapping.
   *
   * @param connection WebSocket handle
   */
  public void deauthenticate(WebSocket connection) {
    ClientSession session = byConnection.get(connection);
    if (session == null) {
      return;
    }

    if (session.getUserId() != null) {
      byUserId.remove(session.getUserId());
    }
    session.deauthenticate();
  }

  // ── Lookup ────────────────────────────────────────────────────────────────

  public ClientSession getByConnection(WebSocket connection) {
    return byConnection.get(connection);
  }

  public ClientSession getByUserId(String userId) {
    return byUserId.get(userId);
  }

  public boolean isOnline(String userId) {
    return byUserId.containsKey(userId);
  }

  public Collection<ClientSession> getAllSessions() {
    return Collections.unmodifiableCollection(byConnection.values());
  }

  public int getConnectedCount() {
    return byConnection.size();
  }

  public int getAuthenticatedCount() {
    return byUserId.size();
  }

  // ── Broadcast ─────────────────────────────────────────────────────────────

  /**
   * Gửi packet tới MỘT user cụ thể (nếu đang online).
   *
   * @param userId ID người nhận
   * @param packet packet cần gửi
   */
  public void sendToUser(String userId, Packet<?> packet) {
    ClientSession session = byUserId.get(userId);
    if (session != null) {
      session.send(packet);
    }
  }

  /**
   * Phát packet kết thúc phiên: user đã JOINED (và seller) nhận qua kênh user; watcher không JOINED
   * nhận qua room. Mỗi session dedupe theo auctionId + packetType.
   */
  public void deliverAuctionLifecyclePacket(
      String auctionId, Packet<?> packet, Set<String> primaryUserIds) {
    if (auctionId == null || packet == null || packet.getType() == null) {
      return;
    }
    PacketType type = packet.getType();
    Set<String> primary =
        primaryUserIds != null ? primaryUserIds : Collections.emptySet();

    for (String userId : primary) {
      ClientSession session = byUserId.get(userId);
      if (session != null) {
        session.deliverLifecyclePacketOnce(auctionId, type, packet);
      }
    }

    for (ClientSession session : byConnection.values()) {
      if (!session.isWatchingAuction(auctionId)) {
        continue;
      }
      String uid = session.getUserId();
      if (uid != null && primary.contains(uid)) {
        continue;
      }
      session.deliverLifecyclePacketOnce(auctionId, type, packet);
    }
  }

  /**
   * Gửi packet tới tất cả client đang xem một phiên đấu giá cụ thể. Dùng cho BID_UPDATE,
   * AUCTION_ENDED_UPDATE, v.v.
   *
   * @param auctionId ID phiên
   * @param packet packet cần broadcast
   */
  public void broadcastToAuction(String auctionId, Packet<?> packet) {
    // Encode 1 lần, gửi nhiều lần
    String json = PacketCodec.encode(packet);
    byConnection
        .values()
        .forEach(
            session -> {
              if (session.isWatchingAuction(auctionId)) {
                session.sendRaw(json);
              }
            });
  }

  /**
   * FIX BROADCAST STALENESS: gửi bất đồng bộ qua thread pool riêng.
   *
   * <p>Dùng sau khi đã release bid lock (trong BidHandler) để: 1. Không block luồng xử lý bid tiếp
   * theo. 2. Đảm bảo thứ tự: các bid xảy ra trước → broadcast trước (FIFO queue). 3. Encode JSON 1
   * lần, gửi N clients song song.
   *
   * <p>Kết quả: người cuối hàng nhận được thông báo delay tối đa = 1 network RTT, KHÔNG phải (N-1)
   * × RTT như cách sync cũ.
   */
  /**
   * Broadcast bất đồng bộ tới tất cả watcher của auction.
   *
   * <p>Cơ chế 2 tầng: 1. broadcastOrderingExecutor (single-thread FIFO): đảm bảo event A xử lý
   * trước event B. 2. sendPool (parallel): trong event, snapshot danh sách target → gửi song song.
   *
   * <p>User #1 và user #1000 nhận packet trong cùng một "lần gửi" song song, không còn bị user cuối
   * nhận trễ hơn user đầu cả trăm millisecond.
   */
  public void broadcastToAuctionAsync(String auctionId, Packet<?> packet) {
    // Encode 1 lần trên calling thread (không phải trong executor)
    final String json = PacketCodec.encode(packet);

    // Bước 1: submit vào ordering executor để giữ thứ tự event
    broadcastOrderingExecutor.submit(
        () -> {
          // Bước 2: snapshot danh sách target tại thời điểm event được xử lý
          java.util.List<ClientSession> targets = new java.util.ArrayList<>();
          for (ClientSession session : byConnection.values()) {
            if (session.isWatchingAuction(auctionId)) {
              targets.add(session);
            }
          }

          if (targets.isEmpty()) {
            return;
          }

          if (targets.size() == 1) {
            // Tối ưu: 1 watcher thì không cần pool overhead
            targets.get(0).sendRaw(json);
            return;
          }

          // Bước 3: gửi song song tới tất cả target
          // CountDownLatch đảm bảo ordering executor chờ đến khi TẤT CẢ gửi xong
          // trước khi xử lý event tiếp theo → không có event sau chen vào giữa
          java.util.concurrent.CountDownLatch latch =
              new java.util.concurrent.CountDownLatch(targets.size());
          for (ClientSession target : targets) {
            sendPool.submit(
                () -> {
                  try {
                    target.sendRaw(json);
                  } finally {
                    latch.countDown();
                  }
                });
          }
          try {
            // Timeout 5s: tránh treo nếu sendPool bị quá tải
            boolean completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
              log.warn(
                  "broadcastToAuction timed out waiting for {} sends on auction {}",
                  targets.size(),
                  auctionId);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  /** Async variant cho broadcastToAuctionExcept — cùng cơ chế 2 tầng. */
  public void broadcastToAuctionExceptAsync(
      String auctionId, Packet<?> packet, String excludeUserId) {
    final String json = PacketCodec.encode(packet);
    broadcastOrderingExecutor.submit(
        () -> {
          java.util.List<ClientSession> targets = new java.util.ArrayList<>();
          for (ClientSession session : byConnection.values()) {
            if (session.isWatchingAuction(auctionId)
                && (session.getUserId() == null || !session.getUserId().equals(excludeUserId))) {
              targets.add(session);
            }
          }

          if (targets.isEmpty()) {
            return;
          }

          java.util.concurrent.CountDownLatch latch =
              new java.util.concurrent.CountDownLatch(targets.size());
          for (ClientSession target : targets) {
            sendPool.submit(
                () -> {
                  try {
                    target.sendRaw(json);
                  } finally {
                    latch.countDown();
                  }
                });
          }
          try {
            boolean completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
              log.warn(
                  "broadcastToAuctionExceptAsync timed out waiting for {} sends on auction {}",
                  targets.size(),
                  auctionId);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  /**
   * Gửi packet tới tất cả client đang xem phiên, TRỪ một userId (thường là người vừa bid).
   *
   * @param auctionId ID phiên
   * @param packet packet cần broadcast
   * @param excludeUserId userId loại trừ (có thể null)
   */
  public void broadcastToAuctionExcept(String auctionId, Packet<?> packet, String excludeUserId) {
    String json = PacketCodec.encode(packet);
    byConnection
        .values()
        .forEach(
            session -> {
              // FIX: kiểm tra getUserId() != null trước khi gọi equals()
              // để tránh NPE khi session chưa authenticate
              if (session.isWatchingAuction(auctionId)
                  && (session.getUserId() == null || !session.getUserId().equals(excludeUserId))) {
                session.sendRaw(json);
              }
            });
  }

  /**
   * Gửi packet tới tất cả Admin (Staff + Master) đang online. Dùng cho FRAUD_DETECTED_NOTIFY,
   * SELLER_CANCEL_REQUEST_NOTIFY.
   *
   * @param packet packet cần gửi
   */
  public void broadcastToAdmins(Packet<?> packet) {
    String json = PacketCodec.encode(packet);
    byConnection
        .values()
        .forEach(
            session -> {
              if (session.isAuthenticated() && session.isAdmin()) {
                session.sendRaw(json);
              }
            });
  }

  /**
   * Gửi packet tới tất cả client đang kết nối (broadcast toàn hệ thống). Dùng cho
   * SYSTEM_ANNOUNCEMENT, SERVER_SHUTDOWN_NOTIFY.
   *
   * @param packet packet cần broadcast
   */
  public void broadcastAll(Packet<?> packet) {
    String json = PacketCodec.encode(packet);
    byConnection.values().forEach(session -> session.sendRaw(json));
  }

  /**
   * Gửi packet tới tất cả client đã xác thực.
   *
   * @param packet packet cần gửi
   */
  public void broadcastAuthenticated(Packet<?> packet) {
    String json = PacketCodec.encode(packet);
    byUserId.values().forEach(session -> session.sendRaw(json));
  }

  /**
   * Lấy danh sách userId đang watching auction (để refund deposit sau khi phiên kết thúc).
   *
   * @param auctionId ID phiên
   * @return danh sách userId đang watch
   */
  public List<String> getUserIdsWatchingAuction(String auctionId) {
    return byConnection.values().stream()
        .filter(s -> s.isAuthenticated() && s.isWatchingAuction(auctionId))
        .map(ClientSession::getUserId)
        .collect(Collectors.toList());
  }

  /**
   * Đếm số live WebSocket connection đang watching auction này.
   *
   * <p>Đây là "số người đang xem tại thời điểm này" — dùng để broadcast {@code
   * VIEWER_COUNT_UPDATE}. Khác với {@code auction.getViewerCount()} là tổng lịch sử (chỉ tăng,
   * không giảm).
   *
   * @param auctionId ID phiên
   * @return số connections đang {@code isWatchingAuction(auctionId) == true}
   */
  public int getActiveViewerCount(String auctionId) {
    int count = 0;
    for (ClientSession session : byConnection.values()) {
      if (session.isWatchingAuction(auctionId)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Đăng ký session đang watch một auction (gọi khi JOIN_AUCTION hoặc WATCH_AUCTION thành công).
   *
   * @param connection WebSocket handle
   * @param auctionId ID phiên
   */
  public void addAuctionWatcher(WebSocket connection, String auctionId) {
    ClientSession session = byConnection.get(connection);
    if (session != null) {
      session.addWatchingAuction(auctionId);
    }
  }

  /**
   * Huỷ đăng ký watching auction (gọi khi LEAVE_AUCTION hoặc disconnect).
   *
   * @param connection WebSocket handle
   * @param auctionId ID phiên
   */
  public void removeAuctionWatcher(WebSocket connection, String auctionId) {
    ClientSession session = byConnection.get(connection);
    if (session != null) {
      session.removeWatchingAuction(auctionId);
    }
  }
}
