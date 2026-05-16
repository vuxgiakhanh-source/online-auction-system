package com.group13.auction.network.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.client.handler.ServerResponseHandler;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * WebSocket Client của hệ thống đấu giá.
 *
 * <p>Tính năng:
 * <ul>
 *   <li>Gửi packet tới server với optional {@code requestId}.</li>
 *   <li>Dispatch response tới {@link ServerResponseHandler} đã đăng ký.</li>
 *   <li>Hỗ trợ one-shot callback: {@code sendAndExpect(packet, type, callback)}.</li>
 *   <li>Auto-reconnect khi mất kết nối (exponential backoff).</li>
 *   <li>Heartbeat PING/PONG để giữ kết nối sống.</li>
 * </ul>
 *
 * <p>Cách dùng từ JavaFX Controller:
 * <pre>
 *   AuctionWebSocketClient client = AuctionWebSocketClient.getInstance();
 *   client.addHandler(myController);          // đăng ký nhận tất cả packet
 *   client.send(Packet.of(PacketType.LOGIN, loginReq));
 *
 *   // Hoặc dùng callback một lần:
 *   client.sendAndExpect(
 *       Packet.of(PacketType.LOGIN, loginReq),
 *       PacketType.LOGIN_SUCCESS,
 *       PacketType.LOGIN_FAILED,
 *       (type, payload) -> Platform.runLater(() -> handleLoginResult(type, payload))
 *   );
 * </pre>
 */
public class AuctionWebSocketClient extends WebSocketClient {

    private static final Logger log = Logger.getLogger(AuctionWebSocketClient.class.getName());

    /** Singleton — một ứng dụng client chỉ cần 1 kết nối. */
    private static volatile AuctionWebSocketClient instance;

    // ── Handlers ──────────────────────────────────────────────────────────────

    /** Danh sách handler nhận TẤT CẢ packet (Controller chính đăng ký ở đây). */
    private final List<ServerResponseHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * One-shot callback: requestId → (successType, failedType, callback).
     * Khi server trả về packet có requestId khớp và type là success/failed → callback và xoá.
     */
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long BASE_RECONNECT_DELAY_MS = 1_000;
    private volatile int reconnectAttempts = 0;
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private static final long PING_INTERVAL_MS = 30_000;
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> heartbeatFuture;

    // ── Auth state ────────────────────────────────────────────────────────────

    private volatile String authToken;
    private volatile String currentUserId;
    private volatile String currentUsername;

    // ── Constructor ───────────────────────────────────────────────────────────

    private AuctionWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    /**
     * Tạo hoặc lấy singleton instance.
     *
     * @param serverUri URI của WebSocket server (ví dụ: {@code ws://localhost:8080})
     * @return singleton instance
     */
    public static AuctionWebSocketClient getInstance(URI serverUri) {
        if (instance == null) {
            synchronized (AuctionWebSocketClient.class) {
                if (instance == null) {
                    instance = new AuctionWebSocketClient(serverUri);
                }
            }
        }
        return instance;
    }

    /** Lấy instance đã tạo sẵn (gọi sau getInstance(uri) lần đầu). */
    public static AuctionWebSocketClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Client chưa được khởi tạo. Gọi getInstance(uri) trước.");
        }
        return instance;
    }

    // ── WebSocketClient callbacks ─────────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        reconnectAttempts = 0;
        log.info("[CLIENT] ✅ Kết nối thành công tới server: " + getURI());
        startHeartbeat();
    }

    @Override
    public void onMessage(String message) {
        PacketType type;
        JsonElement payload;
        String requestId;

        try {
            JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
            type = PacketType.valueOf(obj.get("type").getAsString());
            payload = obj.has("payload") && !obj.get("payload").isJsonNull()
                    ? obj.get("payload") : null;
            requestId = obj.has("requestId") && !obj.get("requestId").isJsonNull()
                    ? obj.get("requestId").getAsString() : null;
        } catch (Exception e) {
            log.warning("[CLIENT] Malformed message từ server: " + e.getMessage());
            return;
        }

        // 1. Xử lý PONG (heartbeat response)
        if (type == PacketType.PONG) {
            return;
        }

        // 2. One-shot pending request callback
        if (requestId != null) {
            PendingRequest pending = pendingRequests.get(requestId);
            if (pending != null && (type == pending.successType || type == pending.failedType)) {
                pendingRequests.remove(requestId);
                try {
                    pending.callback.accept(type, payload);
                } catch (Exception e) {
                    log.warning("[CLIENT] Pending callback error: " + e.getMessage());
                }
            }
        }

        // 3. Broadcast tới tất cả handlers (controllers)
        final PacketType finalType = type;
        final JsonElement finalPayload = payload;
        final String finalRequestId = requestId;
        for (ServerResponseHandler handler : handlers) {
            try {
                handler.onPacketReceived(finalType, finalPayload, finalRequestId);
            } catch (Exception e) {
                log.warning("[CLIENT] Handler error: " + e.getMessage());
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        stopHeartbeat();
        log.warning("[CLIENT] ❌ Mất kết nối | code=" + code + " | reason=" + reason
                + " | remote=" + remote);
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        log.severe("[CLIENT] WebSocket error: " + ex.getMessage());
    }

    // ── Send API ──────────────────────────────────────────────────────────────

    /**
     * Gửi packet tới server (fire-and-forget).
     *
     * @param packet packet cần gửi
     */
    public void send(Packet<?> packet) {
        if (!isOpen()) {
            log.warning("[CLIENT] Không thể gửi — chưa kết nối: " + packet.getType());
            return;
        }
        try {
            send(PacketCodec.encode(packet));
        } catch (Exception e) {
            log.warning("[CLIENT] Send error: " + e.getMessage());
        }
    }

    /**
     * Gửi packet và đăng ký callback một lần cho response (success hoặc failed).
     *
     * <p>Callback được gọi trên thread WebSocket — nếu update UI JavaFX thì dùng
     * {@code Platform.runLater()}.
     *
     * @param packet      packet cần gửi
     * @param successType PacketType mong đợi khi thành công
     * @param failedType  PacketType mong đợi khi thất bại
     * @param callback    (type, payload) → xử lý
     */
    public void sendAndExpect(Packet<?> packet,
                              PacketType successType,
                              PacketType failedType,
                              BiConsumer<PacketType, JsonElement> callback) {
        String requestId = java.util.UUID.randomUUID().toString();
        packet.setRequestId(requestId);
        pendingRequests.put(requestId, new PendingRequest(successType, failedType, callback));
        send(packet);

        // Timeout: xoá pending sau 10s nếu không có response
        reconnectScheduler.schedule(() -> {
            PendingRequest removed = pendingRequests.remove(requestId);
            if (removed != null) {
                log.warning("[CLIENT] Pending request timeout: " + packet.getType()
                        + " | requestId=" + requestId);
            }
        }, 10, TimeUnit.SECONDS);
    }

    // ── Handler management ────────────────────────────────────────────────────

    /**
     * Đăng ký handler nhận tất cả packet từ server.
     * Thường gọi trong Controller khi scene/view được khởi tạo.
     *
     * @param handler handler cần đăng ký
     */
    public void addHandler(ServerResponseHandler handler) {
        if (!handlers.contains(handler)) handlers.add(handler);
    }

    /**
     * Huỷ đăng ký handler.
     * Thường gọi khi Controller bị destroy hoặc scene thay đổi.
     *
     * @param handler handler cần huỷ
     */
    public void removeHandler(ServerResponseHandler handler) {
        handlers.remove(handler);
    }

    // ── Auth state ────────────────────────────────────────────────────────────

    public void setAuthState(String token, String userId, String username) {
        this.authToken = token;
        this.currentUserId = userId;
        this.currentUsername = username;
    }

    public void clearAuthState() {
        this.authToken = null;
        this.currentUserId = null;
        this.currentUsername = null;
    }

    public boolean isLoggedIn() { return authToken != null; }
    public String getAuthToken() { return authToken; }
    public String getCurrentUserId() { return currentUserId; }
    public String getCurrentUsername() { return currentUsername; }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isOpen()) {
                send(Packet.of(PacketType.PING, System.currentTimeMillis()));
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.fine("[CLIENT] Heartbeat started.");
    }

    private void stopHeartbeat() {
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private void scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.severe("[CLIENT] Đã thử kết nối lại " + MAX_RECONNECT_ATTEMPTS
                    + " lần không thành công. Dừng.");
            return;
        }

        long delay = BASE_RECONNECT_DELAY_MS * (long) Math.pow(2, reconnectAttempts);
        reconnectAttempts++;
        log.info("[CLIENT] Thử kết nối lại lần " + reconnectAttempts
                + " sau " + delay + "ms...");

        reconnectScheduler.schedule(() -> {
            try {
                reconnect();
            } catch (Exception e) {
                log.warning("[CLIENT] Reconnect failed: " + e.getMessage());
                scheduleReconnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Dừng client và giải phóng tài nguyên.
     * Gọi khi ứng dụng tắt.
     */
    public void shutdown() {
        stopHeartbeat();
        reconnectScheduler.shutdownNow();
        heartbeatScheduler.shutdownNow();
        try {
            closeBlocking();
        } catch (Exception e) {
            log.warning("[CLIENT] Shutdown error: " + e.getMessage());
        }
        instance = null;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private static class PendingRequest {
        final PacketType successType;
        final PacketType failedType;
        final BiConsumer<PacketType, JsonElement> callback;

        PendingRequest(PacketType successType, PacketType failedType,
                       BiConsumer<PacketType, JsonElement> callback) {
            this.successType = successType;
            this.failedType = failedType;
            this.callback = callback;
        }
    }
}