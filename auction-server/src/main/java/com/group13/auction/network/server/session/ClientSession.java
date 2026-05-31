package com.group13.auction.network.server.session;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Đại diện cho một kết nối WebSocket từ phía Server.
 *
 * <p>Mỗi lần client kết nối → tạo 1 {@code ClientSession} mới. Session lưu:
 *
 * <ul>
 *   <li>WebSocket connection handle.
 *   <li>userId và role sau khi xác thực.
 *   <li>Tập auctionId đang được watch/join (để broadcast có chọn lọc).
 * </ul>
 *
 * <p>Thread-safety: các field mutable dùng volatile + ConcurrentHashSet.
 */
public class ClientSession {

  private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

  private final WebSocket connection;

  /**
   * FIX: Gom 4 volatile field thành 1 AtomicReference<AuthState> — tránh race condition khi đọc
   * nhiều field cùng lúc (ví dụ isAuthenticated()=true nhưng userId đã null do thread khác đang
   * deauthenticate()). AuthState là immutable record → publish-by-reference an toàn.
   */
  private static final class AuthState {
    final String userId;
    final String username;
    final String userRole;
    final boolean authenticated;
    final boolean restricted;

    AuthState(
        String userId,
        String username,
        String userRole,
        boolean authenticated,
        boolean restricted) {
      this.userId = userId;
      this.username = username;
      this.userRole = userRole;
      this.authenticated = authenticated;
      this.restricted = restricted;
    }

    static final AuthState ANONYMOUS = new AuthState(null, null, null, false, false);
  }

  private final java.util.concurrent.atomic.AtomicReference<AuthState> authState =
      new java.util.concurrent.atomic.AtomicReference<>(AuthState.ANONYMOUS);

  /**
   * Tập auctionId mà session này đang theo dõi (join hoặc watch). Dùng để broadcast có chọn lọc:
   * chỉ gửi BID_UPDATE tới đúng client.
   */
  private final Set<String> watchingAuctionIds = ConcurrentHashMap.newKeySet();

  /** Mỗi phiên + loại packet lifecycle chỉ gửi một lần trên session này. */
  private final Set<String> deliveredLifecycleKeys = ConcurrentHashMap.newKeySet();

  /**
   * FIX PERFORMANCE: Cache NormalUser object để tránh DB round-trip mỗi bid.
   *
   * <p>Vấn đề cũ: requireNormalUser() gọi AuctionManager.findUserByUsername() →
   * userDAO.findUserByUsername() → SELECT * FROM users mỗi lần có bid. 500 bid/s = 500+ DB
   * queries/s chỉ để look up user.
   *
   * <p>Fix: cache user object trong session sau lần load đầu tiên. Cache an toàn vì: - join/leave
   * gọi addJoinedAuction/removeJoinedAuction trực tiếp trên object này → in-place update ✓ -
   * balance chỉ được kiểm tra lúc join (lockDeposit), không phải lúc bid ✓ - accountStatus ít thay
   * đổi; nếu bị ban, RatingService.isEligible() vẫn reject ✓
   *
   * <p>Invalidate: gọi invalidateCachedUser() khi deauthenticate hoặc khi cần load state mới nhất
   * từ DB (ví dụ: sau khi admin thay đổi trạng thái).
   *
   * <p>volatile đảm bảo visibility giữa các thread (dù 1 session chỉ serve từ 1 thread,
   * invalidateCachedUser() có thể được gọi từ thread khác).
   */
  private volatile com.group13.auction.model.user.NormalUser cachedUser;

  public com.group13.auction.model.user.NormalUser getCachedUser() {
    return cachedUser;
  }

  public void setCachedUser(com.group13.auction.model.user.NormalUser user) {
    this.cachedUser = user;
  }

  /** Xóa cache để lần dùng tiếp sẽ reload từ DB. */
  public void invalidateCachedUser() {
    this.cachedUser = null;
  }

  public ClientSession(WebSocket connection) {
    this.connection = connection;
  }

  // Send helpers

  /**
   * Gửi packet tới client này (thread-safe, kiểm tra connection open).
   *
   * @param packet packet cần gửi
   */
  public void send(Packet<?> packet) {
    if (connection != null && connection.isOpen()) {
      connection.send(PacketCodec.encode(packet));
    }
  }

  /**
   * Gửi raw JSON string (dùng khi đã encode sẵn để tái sử dụng).
   *
   * @param json JSON string đã encode
   */
  public void sendRaw(String json) {
    if (connection != null && connection.isOpen()) {
      connection.send(json);
    }
  }

  /** Đóng kết nối với lý do cụ thể. */
  public void close(int code, String reason) {
    if (connection != null && connection.isOpen()) {
      connection.close(code, reason);
    }
  }

  // Authentication

  /**
   * Đánh dấu session đã xác thực thành công.
   *
   * @param userId ID user từ DB
   * @param username username
   * @param userRole role string
   */
  public void authenticate(String userId, String username, String userRole) {
    authenticate(userId, username, userRole, false);
  }

  public void authenticate(String userId, String username, String userRole, boolean restricted) {
    authState.set(new AuthState(userId, username, userRole, true, restricted));
    this.cachedUser =
        null; // FIX: xóa cache của user cũ khi có user mới đăng nhập trên cùng connection
    log.info(
        "Session authenticated: userId={}, username={}, role={}, restricted={}",
        userId,
        username,
        userRole,
        restricted);
  }

  /** Reset trạng thái xác thực khi logout. */
  public void deauthenticate() {
    AuthState prev = authState.getAndSet(AuthState.ANONYMOUS);
    this.watchingAuctionIds.clear();
    this.deliveredLifecycleKeys.clear();
    this.cachedUser = null; // FIX: xóa cache khi logout
    log.info("Session deauthenticated: username={}", prev.username);
  }

  // Auction watch management

  public void addWatchingAuction(String auctionId) {
    watchingAuctionIds.add(auctionId);
  }

  public void removeWatchingAuction(String auctionId) {
    watchingAuctionIds.remove(auctionId);
  }

  public boolean isWatchingAuction(String auctionId) {
    return watchingAuctionIds.contains(auctionId);
  }

  public Set<String> getWatchingAuctionIds() {
    return Collections.unmodifiableSet(watchingAuctionIds);
  }

  /**
   * Gửi packet lifecycle (ended / no-winner / reserve / canceled) tối đa một lần cho cặp
   * auctionId + packetType trên session này.
   *
   * @return true nếu packet được gửi
   */
  public boolean deliverLifecyclePacketOnce(
      String auctionId, com.group13.auction.common.protocol.PacketType type, Packet<?> packet) {
    if (auctionId == null || type == null || packet == null) {
      return false;
    }
    if (!deliveredLifecycleKeys.add(auctionId + ":" + type.name())) {
      return false;
    }
    send(packet);
    return true;
  }

  // Getters

  public WebSocket getConnection() {
    return connection;
  }

  public String getUserId() {
    return authState.get().userId;
  }

  public String getUsername() {
    return authState.get().username;
  }

  public String getUserRole() {
    return authState.get().userRole;
  }

  public boolean isAuthenticated() {
    return authState.get().authenticated;
  }

  public boolean isOpen() {
    return connection != null && connection.isOpen();
  }

  public boolean isAdmin() {
    String r = authState.get().userRole;
    return "ADMIN_STAFF".equals(r) || "ADMIN_MASTER".equals(r);
  }

  public boolean isMasterAdmin() {
    return "ADMIN_MASTER".equals(authState.get().userRole);
  }

  /** true khi user BANNED/SUSPENDED đăng nhập ở chế độ chỉ ví. */
  public boolean isRestricted() {
    return authState.get().restricted;
  }

  @Override
  public String toString() {
    AuthState s = authState.get();
    return "ClientSession{userId='"
        + s.userId
        + "', username='"
        + s.username
        + "', role='"
        + s.userRole
        + "', authenticated="
        + s.authenticated
        + ", restricted="
        + s.restricted
        + "}";
  }
}
