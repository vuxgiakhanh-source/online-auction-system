package com.group13.auction.common.protocol;

/**
 * Gói tin cơ bản truyền qua WebSocket, được serialize/deserialize bằng Gson.
 *
 * <p>Mọi message đều có dạng:
 * <pre>
 * {
 *   "type": "PLACE_BID",
 *   "payload": { ... object cụ thể ... },
 *   "requestId": "uuid-optional"
 * }
 * </pre>
 *
 * <p>{@code requestId} được Client sinh ra cho mỗi request — Server echo lại trong response
 * để Client có thể match async (hữu ích khi nhiều request cùng bay).
 *
 * @param <T> Kiểu dữ liệu của payload (DTO cụ thể).
 */
public class Packet<T> {

    /** Loại gói tin. Bắt buộc. */
    private PacketType type;

    /** Dữ liệu đính kèm. Có thể null nếu gói tin không cần payload. */
    private T payload;

    /**
     * ID tùy chọn để Client track request ↔ response.
     * Server echo lại giá trị này trong packet response tương ứng.
     */
    private String requestId;

    /** Timestamp milliseconds khi packet được tạo (UTC). */
    private long timestamp;

    public Packet() {
        this.timestamp = System.currentTimeMillis();
    }

    public Packet(PacketType type, T payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public Packet(PacketType type, T payload, String requestId) {
        this.type = type;
        this.payload = payload;
        this.requestId = requestId;
        this.timestamp = System.currentTimeMillis();
    }

    // ── Static factory helpers ────────────────────────────────────────────────

    public static <T> Packet<T> of(PacketType type, T payload) {
        return new Packet<>(type, payload);
    }

    public static Packet<Void> of(PacketType type) {
        return new Packet<>(type, null);
    }

    public static <T> Packet<T> of(PacketType type, T payload, String requestId) {
        return new Packet<>(type, payload, requestId);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public PacketType getType() { return type; }
    public void setType(PacketType type) { this.type = type; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "Packet{type=" + type + ", requestId=" + requestId + ", timestamp=" + timestamp + "}";
    }
}