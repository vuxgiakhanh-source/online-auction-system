package com.group13.auction.common.protocol;

import com.group13.auction.common.dto.core.ErrorDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Packet factory")
class PacketTest {

    @Test
    @DisplayName("reply() đặt requestId trên packet")
    void reply_setsRequestIdOnPacket() {
        Packet<ErrorDTO> packet = Packet.reply(
                PacketType.LOGIN_FAILED,
                ErrorDTO.of(ErrorDTO.WRONG_PASSWORD, "msg", "rid-1"),
                "rid-1");

        assertThat(packet.getRequestId()).isEqualTo("rid-1");
        assertThat(packet.getType()).isEqualTo(PacketType.LOGIN_FAILED);
    }
}
