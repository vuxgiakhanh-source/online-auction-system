package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.ClientSession;

/**
 * Interface mà mỗi handler domain phải implement.
 *
 * <p>Router sẽ dispatch packet tới đúng handler dựa trên {@link PacketType}.
 * Mỗi handler xử lý một nhóm PacketType (AUTH, BID, PAYMENT, v.v.).
 */
public interface PacketHandler {

    /**
     * Xử lý packet nhận được từ client.
     *
     * @param session     session của client gửi
     * @param type        loại packet
     * @param payload     raw JsonElement payload (có thể null)
     * @param requestId   requestId để echo về (có thể null)
     */
    void handle(ClientSession session, PacketType type, JsonElement payload, String requestId);

    /**
     * Kiểm tra handler này có xử lý được loại packet này không.
     *
     * @param type loại packet
     * @return true nếu handler này xử lý
     */
    boolean supports(PacketType type);
}
