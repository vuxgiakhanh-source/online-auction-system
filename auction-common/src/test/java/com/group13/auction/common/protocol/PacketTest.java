package com.group13.auction.common.protocol;

import com.group13.auction.common.dto.auth.LoginRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Packet — unit")
class PacketTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTest {

        @Test @DisplayName("default constructor — type/payload/requestId null, timestamp > 0")
        void defaultConstructor() {
            Packet<?> p = new Packet<>();
            assertThat(p.getType()).isNull();
            assertThat(p.getPayload()).isNull();
            assertThat(p.getRequestId()).isNull();
            assertThat(p.getTimestamp()).isPositive();
        }

        @Test @DisplayName("2-arg constructor — type và payload set, requestId null")
        void twoArgConstructor() {
            LoginRequestDTO dto = new LoginRequestDTO("alice", "pass");
            Packet<LoginRequestDTO> p = new Packet<>(PacketType.LOGIN, dto);
            assertThat(p.getType()).isEqualTo(PacketType.LOGIN);
            assertThat(p.getPayload()).isSameAs(dto);
            assertThat(p.getRequestId()).isNull();
            assertThat(p.getTimestamp()).isPositive();
        }

        @Test @DisplayName("3-arg constructor — đủ 3 field + timestamp")
        void threeArgConstructor() {
            Packet<String> p = new Packet<>(PacketType.PING, "data", "req-1");
            assertThat(p.getType()).isEqualTo(PacketType.PING);
            assertThat(p.getPayload()).isEqualTo("data");
            assertThat(p.getRequestId()).isEqualTo("req-1");
        }
    }

    @Nested
    @DisplayName("Static factory — of()")
    class StaticFactoryTest {

        @Test @DisplayName("of(type, payload) — requestId null")
        void of_twoArg() {
            Packet<String> p = Packet.of(PacketType.PLACE_BID, "payload");
            assertThat(p.getType()).isEqualTo(PacketType.PLACE_BID);
            assertThat(p.getPayload()).isEqualTo("payload");
            assertThat(p.getRequestId()).isNull();
        }

        @Test @DisplayName("of(type) — payload null, Void")
        void of_typeOnly() {
            Packet<Void> p = Packet.of(PacketType.PING);
            assertThat(p.getType()).isEqualTo(PacketType.PING);
            assertThat(p.getPayload()).isNull();
        }

        @Test @DisplayName("of(type, payload, requestId) — đủ 3 field")
        void of_threeArg() {
            Packet<String> p = Packet.of(PacketType.LOGIN, "payload", "req-42");
            assertThat(p.getType()).isEqualTo(PacketType.LOGIN);
            assertThat(p.getRequestId()).isEqualTo("req-42");
        }

        @Test @DisplayName("of() trả object mới mỗi lần")
        void of_returnsNewInstance() {
            Packet<Void> a = Packet.of(PacketType.PING);
            Packet<Void> b = Packet.of(PacketType.PING);
            assertThat(a).isNotSameAs(b);
        }
    }

    @Nested
    @DisplayName("Setters / Getters")
    class SettersGettersTest {

        @Test @DisplayName("setType / getType — roundtrip")
        void type_roundtrip() {
            Packet<Void> p = new Packet<>();
            p.setType(PacketType.LOGOUT);
            assertThat(p.getType()).isEqualTo(PacketType.LOGOUT);
        }

        @Test @DisplayName("setPayload / getPayload — object identity preserved")
        void payload_identity() {
            Packet<LoginRequestDTO> p = new Packet<>();
            LoginRequestDTO dto = new LoginRequestDTO("u", "pw");
            p.setPayload(dto);
            assertThat(p.getPayload()).isSameAs(dto);
        }

        @Test @DisplayName("setRequestId / getRequestId — roundtrip")
        void requestId_roundtrip() {
            Packet<Void> p = new Packet<>();
            p.setRequestId("req-99");
            assertThat(p.getRequestId()).isEqualTo("req-99");
        }

        @Test @DisplayName("setTimestamp / getTimestamp — roundtrip")
        void timestamp_roundtrip() {
            Packet<Void> p = new Packet<>();
            p.setTimestamp(12345L);
            assertThat(p.getTimestamp()).isEqualTo(12345L);
        }

        @Test @DisplayName("payload null — set null lại được")
        void payload_setNull() {
            Packet<String> p = Packet.of(PacketType.PING, "x");
            p.setPayload(null);
            assertThat(p.getPayload()).isNull();
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test @DisplayName("toString chứa type, requestId, timestamp")
        void toString_containsFields() {
            Packet<Void> p = Packet.of(PacketType.PING, null, "r-1");
            String s = p.toString();
            assertThat(s).contains("PING").contains("r-1");
        }
    }

    @Nested
    @DisplayName("Timestamp")
    class TimestampTest {

        @Test @DisplayName("timestamp được set tại thời điểm tạo — gần System.currentTimeMillis()")
        void timestamp_setAtCreation() {
            long before = System.currentTimeMillis();
            Packet<Void> p = new Packet<>();
            long after = System.currentTimeMillis();
            assertThat(p.getTimestamp()).isBetween(before, after);
        }
    }
}
