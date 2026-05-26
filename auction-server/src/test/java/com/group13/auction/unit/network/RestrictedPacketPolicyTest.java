package com.group13.auction.unit.network;

import com.group13.auction.common.messages.RealtimeAccessMessages;
import com.group13.auction.network.server.security.RestrictedPacketPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestrictedPacketPolicy")
class RestrictedPacketPolicyTest {

    @Test
    @DisplayName("denialMessage() dùng RealtimeAccessMessages tiếng Việt")
    void denialMessage_usesCentralizedVietnameseCopy() {
        assertThat(RestrictedPacketPolicy.denialMessage())
            .isEqualTo(RealtimeAccessMessages.restrictedAccountDenial());
    }
}
