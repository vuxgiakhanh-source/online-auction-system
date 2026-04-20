package com.group13.auction.network.server.session;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import org.java_websocket.WebSocket;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Đại diện cho một kết nối WebSocket từ phía Server.
 *
 * <p>Mỗi lần client kết nối → tạo 1 {@code ClientSession} mới.
 * Session lưu:
 * <ul>
 *   <li>WebSocket connection handle.</li>
 *   <li>userId và role sau khi xác thực.</li>
 *   <li>Tập auctionId đang được watch/join (để broadcast có chọn lọc).</li>
 * </ul>
 *
 * <p>Thread-safety: các field mutable dùng volatile + ConcurrentHashSet.
 */
public class ClientSession {

    private final WebSocket connection;

    /** null cho đến khi LOGIN_SUCCESS. */
    private volatile String userId;
    private volatile String username;
    /** "NORMAL_USER" | "ADMIN_STAFF" | "ADMIN_MASTER" */
    private volatile String userRole;
    private volatile boolean authenticated;

    /**
     * Tập auctionId mà session này đang theo dõi (join hoặc watch).
     * Dùng để broadcast có chọn lọc: chỉ gửi BID_UPDATE tới đúng client.
     */
    private final Set<String> watchingAuctionIds = ConcurrentHashMap.newKeySet();

    public ClientSession(WebSocket connection) {
        this.connection = connection;
        this.authenticated = false;
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

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

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Đánh dấu session đã xác thực thành công.
     *
     * @param userId   ID user từ DB
     * @param username username
     * @param userRole role string
     */
    public void authenticate(String userId, String username, String userRole) {
        this.userId = userId;
        this.username = username;
        this.userRole = userRole;
        this.authenticated = true;
    }

    /** Reset trạng thái xác thực khi logout. */
    public void deauthenticate() {
        this.userId = null;
        this.username = null;
        this.userRole = null;
        this.authenticated = false;
        this.watchingAuctionIds.clear();
    }

    // ── Auction watch management ──────────────────────────────────────────────

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

    // ── Getters ───────────────────────────────────────────────────────────────

    public WebSocket getConnection() { return connection; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getUserRole() { return userRole; }
    public boolean isAuthenticated() { return authenticated; }
    public boolean isOpen() { return connection != null && connection.isOpen(); }

    public boolean isAdmin() {
        return "ADMIN_STAFF".equals(userRole) || "ADMIN_MASTER".equals(userRole);
    }

    public boolean isMasterAdmin() {
        return "ADMIN_MASTER".equals(userRole);
    }

    @Override
    public String toString() {
        return "ClientSession{userId='" + userId + "', username='" + username
                + "', role='" + userRole + "', authenticated=" + authenticated + "}";
    }
}