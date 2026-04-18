package com.group13.auction.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Type;
import java.time.LocalDateTime;

/**
 * Codec chịu trách nhiệm serialize/deserialize {@link Packet} sang JSON và ngược lại.
 *
 * <p>Sử dụng Gson. LocalDateTime được serialize dưới dạng ISO-8601 string.
 *
 * <p>Cách dùng:
 * <pre>
 *   // Encode
 *   String json = PacketCodec.encode(Packet.of(PacketType.PING, null));
 *
 *   // Decode (biết trước kiểu payload)
 *   Packet&lt;BidRequestDTO&gt; packet = PacketCodec.decode(json, BidRequestDTO.class);
 *
 *   // Decode raw (không biết trước kiểu)
 *   PacketType type = PacketCodec.peekType(json);
 *   JsonElement payload = PacketCodec.peekPayload(json);
 * </pre>
 */
public final class PacketCodec {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .serializeNulls()
            .create();

    private PacketCodec() {}

    /**
     * Serialize Packet thành JSON string.
     *
     * @param packet packet cần encode
     * @return JSON string
     */
    public static String encode(Packet<?> packet) {
        return GSON.toJson(packet);
    }

    /**
     * Deserialize JSON string thành Packet với payload kiểu {@code T}.
     *
     * @param json   JSON string nhận được từ WebSocket
     * @param payloadClass kiểu class của payload
     * @param <T>    kiểu payload
     * @return Packet đã giải mã
     */
    public static <T> Packet<T> decode(String json, Class<T> payloadClass) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        PacketType type = PacketType.valueOf(obj.get("type").getAsString());
        String requestId = obj.has("requestId") && !obj.get("requestId").isJsonNull()
                ? obj.get("requestId").getAsString() : null;
        long timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong()
                : System.currentTimeMillis();

        T payload = null;
        if (obj.has("payload") && !obj.get("payload").isJsonNull() && payloadClass != Void.class) {
            payload = GSON.fromJson(obj.get("payload"), payloadClass);
        }

        Packet<T> packet = new Packet<>(type, payload, requestId);
        packet.setTimestamp(timestamp);
        return packet;
    }

    /**
     * Deserialize JSON string thành Packet với payload kiểu {@link Type} (dùng cho generic).
     *
     * @param json JSON string
     * @param type kiểu {@link Type} của payload (ví dụ: {@code new TypeToken<List<UserDTO>>(){}.getType()})
     * @param <T>  kiểu payload
     * @return Packet đã giải mã
     */
    public static <T> Packet<T> decode(String json, Type type) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        PacketType packetType = PacketType.valueOf(obj.get("type").getAsString());
        String requestId = obj.has("requestId") && !obj.get("requestId").isJsonNull()
                ? obj.get("requestId").getAsString() : null;
        long timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong()
                : System.currentTimeMillis();

        T payload = null;
        if (obj.has("payload") && !obj.get("payload").isJsonNull()) {
            payload = GSON.fromJson(obj.get("payload"), type);
        }

        Packet<T> packet = new Packet<>(packetType, payload, requestId);
        packet.setTimestamp(timestamp);
        return packet;
    }

    /**
     * Lấy nhanh {@link PacketType} từ JSON mà không cần deserialize toàn bộ.
     *
     * @param json JSON string
     * @return PacketType
     */
    public static PacketType peekType(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return PacketType.valueOf(obj.get("type").getAsString());
    }

    /**
     * Lấy raw payload element để xử lý tự chọn kiểu sau.
     *
     * @param json JSON string
     * @return JsonElement payload (có thể null/JsonNull)
     */
    public static JsonElement peekPayload(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return obj.has("payload") ? obj.get("payload") : null;
    }

    /**
     * Deserialize JsonElement thành kiểu cụ thể (dùng sau {@link #peekPayload}).
     *
     * @param element JsonElement payload
     * @param clazz   kiểu target
     * @param <T>     kiểu
     * @return object đã deserialize
     */
    public static <T> T fromElement(JsonElement element, Class<T> clazz) {
        return GSON.fromJson(element, clazz);
    }

    /** Expose GSON instance nếu cần dùng ngoài. */
    public static Gson gson() {
        return GSON;
    }
}