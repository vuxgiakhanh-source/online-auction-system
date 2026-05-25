package com.group13.auction.network.server.router;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.handler.PacketHandler;
import com.group13.auction.network.server.security.RestrictedAccessGuard;
import com.group13.auction.network.server.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

/**
 * Router nhận raw JSON từ WebSocket, deserialize, tìm đúng {@link PacketHandler} và dispatch.
 *
 * <p>Mỗi message đến đều được xử lý đồng bộ trong thread của java-websocket.
 * Handler nặng nên dùng ExecutorService riêng nếu cần async — nhưng với
 * đấu giá realtime, xử lý tuần tự per-session là đủ.
 */
public class PacketRouter {

    private static final Logger log = LoggerFactory.getLogger(PacketRouter.class);

    private final List<PacketHandler> handlers = new ArrayList<>();

    public PacketRouter() {}

    /**
     * Đăng ký handler. Thứ tự đăng ký không ảnh hưởng (dùng {@code supports()}).
     *
     * @param handler handler cần đăng ký
     */
    public void register(PacketHandler handler) {
        handlers.add(handler);
    }

    /**
     * Entry point: nhận raw JSON message từ WebSocket, route tới handler phù hợp.
     *
     * @param session ClientSession của sender
     * @param message raw JSON string
     */
    public void route(ClientSession session, String message) {
        PacketType type;
        JsonElement payload;
        String requestId;

        // Decode header
        try {
            type = PacketCodec.peekType(message);
            payload = PacketCodec.peekPayload(message);
            // Lấy requestId
            com.google.gson.JsonObject obj = com.google.gson.JsonParser
                    .parseString(message).getAsJsonObject();
            requestId = obj.has("requestId") && !obj.get("requestId").isJsonNull()
                    ? obj.get("requestId").getAsString() : null;
        } catch (Exception e) {
            log.warn("Malformed packet: username={}, messageLength={}",
                    session.getUsername(), message != null ? message.length() : 0, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                            "Packet không hợp lệ: " + e.getMessage())));
            return;
        }

        if (RestrictedAccessGuard.blockIfRestricted(session, type, requestId)) {
            return;
        }

        // Tìm handler
        for (PacketHandler handler : handlers) {
            if (handler.supports(type)) {
                MDC.put("requestId", requestId != null ? requestId : "-");
                MDC.put("username", session.getUsername() != null ? session.getUsername() : "anonymous");
                // Trích auctionId từ payload nếu có (BID, PAYMENT, AUCTION packets)
                try {
                    if (payload != null && payload.isJsonObject()) {
                        com.google.gson.JsonObject p = payload.getAsJsonObject();
                        String auctionId = p.has("auctionId") && !p.get("auctionId").isJsonNull()
                                ? p.get("auctionId").getAsString() : null;
                        if (auctionId != null) MDC.put("auctionId", auctionId);
                    }
                } catch (Exception ignored) { /* payload không phải object — không cần auctionId */ }
                try {
                    handler.handle(session, type, payload, requestId);
                } catch (Exception e) {
                    // Lỗi không xử lý được — log + gửi SYSTEM_ERROR về client
                    log.error("Unhandled exception while routing packet: type={}, username={}, requestId={}",
                            type, session.getUsername(), requestId, e);
                    session.send(Packet.of(PacketType.SYSTEM_ERROR,
                            ErrorDTO.of(ErrorDTO.INTERNAL_ERROR,
                                    "Lỗi hệ thống khi xử lý " + type, requestId), requestId));
                } finally {
                    MDC.remove("requestId");
                    MDC.remove("username");
                    MDC.remove("auctionId");
                }
                return;
            }
        }

        // Không có handler nào nhận
        log.warn("No handler registered for packet type: type={}, username={}, requestId={}",
                type, session.getUsername(), requestId);
        session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                        "PacketType không được hỗ trợ: " + type, requestId), requestId));
    }
}