package com.group13.auction.network.server.session;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Singleton quản lý toàn bộ {@link ClientSession} đang kết nối.
 *
 * <p>Chịu trách nhiệm:
 * <ul>
 *   <li>Đăng ký / huỷ đăng ký session khi client connect / disconnect.</li>
 *   <li>Map WebSocket handle → ClientSession.</li>
 *   <li>Map userId → ClientSession (sau khi xác thực).</li>
 *   <li>Broadcast có chọn lọc: toàn bộ / theo auctionId / theo userId / theo role.</li>
 * </ul>
 *
 * <p>Thread-safety: ConcurrentHashMap trên tất cả map.
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private static final SessionManager INSTANCE = new SessionManager();

    /** WebSocket connection → ClientSession */
    private final ConcurrentHashMap<WebSocket, ClientSession> byConnection = new ConcurrentHashMap<>();

    /** userId → ClientSession (chỉ có sau khi authenticate) */
    private final ConcurrentHashMap<String, ClientSession> byUserId = new ConcurrentHashMap<>();

    private SessionManager() {}

    public static SessionManager getInstance() { return INSTANCE; }

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
        log.info("Session registered: remoteAddress={}, connectedCount={}",
                connection.getRemoteSocketAddress(), byConnection.size());
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
            log.info("Session unregistered: userId={}, username={}, connectedCount={}",
                    session.getUserId(), session.getUsername(), byConnection.size());
        }
    }

    /**
     * Gọi sau khi LOGIN_SUCCESS: liên kết userId với session để push sau này.
     *
     * @param connection WebSocket handle
     * @param userId     userId từ DB
     * @param username   username
     * @param role       role string
     */
    public void authenticate(WebSocket connection, String userId, String username, String role) {
        ClientSession session = byConnection.get(connection);
        if (session == null) return;

        // Nếu userId này đang có session cũ (ví dụ login từ tab khác) → đóng session cũ
        ClientSession oldSession = byUserId.get(userId);
        if (oldSession != null && oldSession != session) {
            oldSession.close(1000, "Logged in from another location");
            byConnection.remove(oldSession.getConnection());
            log.info("Existing session replaced by new login: userId={}, username={}", userId, username);
        }

        session.authenticate(userId, username, role);
        byUserId.put(userId, session);
        log.info("Session authenticated: userId={}, username={}, role={}", userId, username, role);
    }

    /**
     * Gọi khi LOGOUT hoặc session bị kick: xoá userId mapping.
     *
     * @param connection WebSocket handle
     */
    public void deauthenticate(WebSocket connection) {
        ClientSession session = byConnection.get(connection);
        if (session == null) return;

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

    public int getConnectedCount() { return byConnection.size(); }
    public int getAuthenticatedCount() { return byUserId.size(); }

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
     * Gửi packet tới tất cả client đang xem một phiên đấu giá cụ thể.
     * Dùng cho BID_UPDATE, AUCTION_ENDED_UPDATE, v.v.
     *
     * @param auctionId ID phiên
     * @param packet    packet cần broadcast
     */
    public void broadcastToAuction(String auctionId, Packet<?> packet) {
        // Encode 1 lần, gửi nhiều lần
        String json = PacketCodec.encode(packet);
        byConnection.values().forEach(session -> {
            if (session.isWatchingAuction(auctionId)) {
                session.sendRaw(json);
            }
        });
    }

    /**
     * Gửi packet tới tất cả client đang xem phiên, TRỪ một userId (thường là người vừa bid).
     *
     * @param auctionId    ID phiên
     * @param packet       packet cần broadcast
     * @param excludeUserId userId loại trừ (có thể null)
     */
    public void broadcastToAuctionExcept(String auctionId, Packet<?> packet, String excludeUserId) {
        String json = PacketCodec.encode(packet);
        byConnection.values().forEach(session -> {
            // FIX: kiểm tra getUserId() != null trước khi gọi equals()
            // để tránh NPE khi session chưa authenticate
            if (session.isWatchingAuction(auctionId)
                    && (session.getUserId() == null || !session.getUserId().equals(excludeUserId))) {
                session.sendRaw(json);
            }
        });
    }

    /**
     * Gửi packet tới tất cả Admin (Staff + Master) đang online.
     * Dùng cho FRAUD_DETECTED_NOTIFY, SELLER_CANCEL_REQUEST_NOTIFY.
     *
     * @param packet packet cần gửi
     */
    public void broadcastToAdmins(Packet<?> packet) {
        String json = PacketCodec.encode(packet);
        byConnection.values().forEach(session -> {
            if (session.isAuthenticated() && session.isAdmin()) {
                session.sendRaw(json);
            }
        });
    }

    /**
     * Gửi packet tới tất cả client đang kết nối (broadcast toàn hệ thống).
     * Dùng cho SYSTEM_ANNOUNCEMENT, SERVER_SHUTDOWN_NOTIFY.
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
     * Đăng ký session đang watch một auction (gọi khi JOIN_AUCTION hoặc WATCH_AUCTION thành công).
     *
     * @param connection WebSocket handle
     * @param auctionId  ID phiên
     */
    public void addAuctionWatcher(WebSocket connection, String auctionId) {
        ClientSession session = byConnection.get(connection);
        if (session != null) session.addWatchingAuction(auctionId);
    }

    /**
     * Huỷ đăng ký watching auction (gọi khi LEAVE_AUCTION hoặc disconnect).
     *
     * @param connection WebSocket handle
     * @param auctionId  ID phiên
     */
    public void removeAuctionWatcher(WebSocket connection, String auctionId) {
        ClientSession session = byConnection.get(connection);
        if (session != null) session.removeWatchingAuction(auctionId);
    }
}
