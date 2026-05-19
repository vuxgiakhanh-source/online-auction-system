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
import java.util.concurrent.atomic.AtomicInteger;
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
 */
public class AuctionWebSocketClient extends WebSocketClient {

    private static final Logger log = Logger.getLogger(AuctionWebSocketClient.class.getName());

    /** Singleton — một ứng dụng client chỉ cần 1 kết nối. */
    private static volatile AuctionWebSocketClient instance;

    // ── Handlers ──────────────────────────────────────────────────────────────

    private final List<ServerResponseHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * One-shot callback: requestId → (successType, failedType, callback).
     * Khi server trả về packet có requestId khớp và type là success/failed → callback và xoá.
     */
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long BASE_RECONNECT_DELAY_MS = 1_000;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
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

    public static AuctionWebSocketClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Client chưa được khởi tạo. Gọi getInstance(uri) trước.");
        }
        return instance;
    }

    // ── WebSocketClient callbacks ─────────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        reconnectAttempts.set(0);
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
     * <p>Callback nhận {@code (type, payload)}:
     * <ul>
     *   <li>{@code type == successType} — thành công</li>
     *   <li>{@code type == failedType}  — lỗi nghiệp vụ (sai password, v.v.)</li>
     *   <li>{@code type == null}        — timeout hoặc không thể gửi (mất kết nối / send exception)
     *       → controller nên hiển thị "server không phản hồi" hoặc "mất kết nối"</li>
     * </ul>
     *
     * <p>FIX BUG: Trước đây khi timeout (10s) hoặc khi send() thất bại, callback KHÔNG BAO GIỜ
     * được gọi. UI treo mãi hoặc hiện "server không phản hồi" từ một cơ chế timeout khác ở
     * controller — dù server thực ra đã kịp trả về kết quả (e.g. LOGIN_FAILED sai mật khẩu).
     *
     * @param packet      packet cần gửi
     * @param successType PacketType mong đợi khi thành công
     * @param failedType  PacketType mong đợi khi thất bại
     * @param callback    (type, payload) → xử lý; type=null nếu timeout/không kết nối
     */
    public void sendAndExpect(Packet<?> packet,
                              PacketType successType,
                              PacketType failedType,
                              BiConsumer<PacketType, JsonElement> callback) {
        String requestId = java.util.UUID.randomUUID().toString();
        packet.setRequestId(requestId);

        // FIX BUG B: kiểm tra kết nối TRƯỚC khi thêm vào pendingRequests.
        // Cũ: thêm vào map → send() drop im lặng → 10s sau timeout → callback không được gọi.
        // Mới: nếu không kết nối, gọi callback ngay với type=null → controller hiển thị lỗi tức thì.
        if (!isOpen()) {
            log.warning("[CLIENT] sendAndExpect: chưa kết nối, gọi callback ngay. type=" + packet.getType());
            try {
                callback.accept(null, null);
            } catch (Exception e) {
                log.warning("[CLIENT] Callback error (not connected): " + e.getMessage());
            }
            return;
        }

        pendingRequests.put(requestId, new PendingRequest(successType, failedType, callback));

        // FIX BUG B (tiếp): nếu send() throw exception sau khi đã đăng ký pending,
        // cleanup ngay và gọi callback với type=null.
        try {
            send(PacketCodec.encode(packet));
        } catch (Exception e) {
            log.warning("[CLIENT] sendAndExpect send error: " + e.getMessage() + " | type=" + packet.getType());
            PendingRequest orphan = pendingRequests.remove(requestId);
            if (orphan != null) {
                try {
                    orphan.callback.accept(null, null);
                } catch (Exception ce) {
                    log.warning("[CLIENT] Callback error (send failed): " + ce.getMessage());
                }
            }
            return;
        }

        // Timeout: xoá pending sau 10s nếu không có response
        reconnectScheduler.schedule(() -> {
            PendingRequest removed = pendingRequests.remove(requestId);
            if (removed != null) {
                // FIX BUG A: Trước đây chỉ log warning và bỏ qua callback.
                // Hậu quả: nếu server trả về sau timeout (hoặc không trả về),
                // callback không bao giờ được gọi → UI treo ở trạng thái "đang tải"
                // hoặc hiện "server không phản hồi" từ một timeout khác của controller.
                //
                // Fix: luôn gọi callback với (null, null) để controller biết và hiện
                // thông báo lỗi phù hợp.
                log.warning("[CLIENT] Pending request timeout: " + packet.getType()
                        + " | requestId=" + requestId);
                try {
                    removed.callback.accept(null, null);
                } catch (Exception e) {
                    log.warning("[CLIENT] Timeout callback error: " + e.getMessage());
                }
            }
        }, 10, TimeUnit.SECONDS);
    }

    // ── Handler management ────────────────────────────────────────────────────

    public void addHandler(ServerResponseHandler handler) {
        if (!handlers.contains(handler)) handlers.add(handler);
    }

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
        if (reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
            log.severe("[CLIENT] Đã thử kết nối lại " + MAX_RECONNECT_ATTEMPTS
                    + " lần không thành công. Dừng.");
            return;
        }

        long delay = BASE_RECONNECT_DELAY_MS * (long) Math.pow(2, reconnectAttempts.get());
        reconnectAttempts.incrementAndGet();
        log.info("[CLIENT] Thử kết nối lại lần " + reconnectAttempts.get()
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