package com.group13.auction.network.client.handler;

import com.group13.auction.common.protocol.PacketType;
import com.google.gson.JsonElement;

/**
 * Interface callback mà tầng UI (JavaFX Controller) implement để nhận response từ Server.
 *
 * <p>Client WebSocket gọi các method này khi nhận được packet từ Server.
 * UI cần gọi {@code Platform.runLater()} nếu cần update JavaFX node.
 *
 * <p>Mỗi method nhận {@code JsonElement payload} thô — Controller tự deserialize
 * bằng {@link com.group13.auction.common.protocol.PacketCodec#fromElement} sang DTO tương ứng.
 */
public interface ServerResponseHandler {

    /**
     * Được gọi khi server trả về bất kỳ packet nào.
     *
     * @param type    loại packet
     * @param payload payload thô (có thể null)
     * @param requestId requestId echo từ server (có thể null)
     */
    void onPacketReceived(PacketType type, JsonElement payload, String requestId);
}
