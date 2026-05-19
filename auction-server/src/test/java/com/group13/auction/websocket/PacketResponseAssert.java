package com.group13.auction.websocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.assertj.core.api.Assertions.assertThat;

/** Assert {@code requestId} trên header packet JSON (không chỉ trong payload ErrorDTO). */
final class PacketResponseAssert {

    private PacketResponseAssert() {}

    static void assertEchoesRequestId(String encodedJson, String expectedRequestId) {
        assertThat(encodedJson).isNotBlank();
        JsonObject root = JsonParser.parseString(encodedJson).getAsJsonObject();
        assertThat(root.has("requestId") && !root.get("requestId").isJsonNull())
                .as("Packet phải có field requestId ở root (echo từ client)")
                .isTrue();
        assertThat(root.get("requestId").getAsString())
                .as("packet.requestId")
                .isEqualTo(expectedRequestId);
    }
}
