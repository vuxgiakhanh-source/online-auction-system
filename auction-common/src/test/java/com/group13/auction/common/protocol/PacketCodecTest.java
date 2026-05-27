package com.group13.auction.common.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PacketCodec — unit")
class PacketCodecTest {

  // ── encode() ──────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("encode()")
  class EncodeTest {

    @Test
    @DisplayName("encode packet với payload — JSON chứa type và payload")
    void encode_withPayload() {
      LoginRequestDTO dto = new LoginRequestDTO("alice", "pass");
      Packet<LoginRequestDTO> p = Packet.of(PacketType.LOGIN, dto, "req-1");
      String json = PacketCodec.encode(p);
      assertThat(json).contains("\"type\":\"LOGIN\"");
      assertThat(json).contains("alice");
      assertThat(json).contains("req-1");
    }

    @Test
    @DisplayName("encode packet null payload — JSON chứa \"payload\":null")
    void encode_nullPayload() {
      Packet<Void> p = Packet.of(PacketType.PING);
      String json = PacketCodec.encode(p);
      assertThat(json).contains("\"type\":\"PING\"");
      assertThat(json).contains("\"payload\":null");
    }

    @Test
    @DisplayName("encode packet với requestId null — requestId null trong JSON")
    void encode_nullRequestId() {
      Packet<Void> p = Packet.of(PacketType.LOGOUT);
      String json = PacketCodec.encode(p);
      assertThat(json).contains("LOGOUT");
    }
  }

  // ── decode(String, Class) ─────────────────────────────────────────────────
  @Nested
  @DisplayName("decode(String, Class)")
  class DecodeClassTest {

    @Test
    @DisplayName("roundtrip encode→decode — type, payload, requestId khớp")
    void roundtrip_type_payload_requestId() {
      LoginRequestDTO dto = new LoginRequestDTO("bob", "secret");
      String json = PacketCodec.encode(Packet.of(PacketType.LOGIN, dto, "req-99"));
      Packet<LoginRequestDTO> decoded = PacketCodec.decode(json, LoginRequestDTO.class);
      assertThat(decoded.getType()).isEqualTo(PacketType.LOGIN);
      assertThat(decoded.getRequestId()).isEqualTo("req-99");
      assertThat(decoded.getPayload().getUsername()).isEqualTo("bob");
      assertThat(decoded.getPayload().getPassword()).isEqualTo("secret");
    }

    @Test
    @DisplayName("decode packet không có requestId — requestId null")
    void decode_noRequestId_null() {
      Packet<Void> p = Packet.of(PacketType.PING);
      String json = PacketCodec.encode(p);
      Packet<Void> decoded = PacketCodec.decode(json, Void.class);
      assertThat(decoded.getRequestId()).isNull();
    }

    @Test
    @DisplayName("decode packet null payload — payload null")
    void decode_nullPayload() {
      Packet<Void> p = Packet.of(PacketType.LOGOUT);
      String json = PacketCodec.encode(p);
      Packet<Void> decoded = PacketCodec.decode(json, Void.class);
      assertThat(decoded.getPayload()).isNull();
    }

    @Test
    @DisplayName("decode với type không hợp lệ — ném IllegalArgumentException")
    void decode_invalidType_throws() {
      String bad = "{\"type\":\"NONEXISTENT_TYPE\",\"payload\":null}";
      assertThatThrownBy(() -> PacketCodec.decode(bad, Void.class))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("timestamp được preserve sau roundtrip")
    void roundtrip_timestampPreserved() {
      Packet<Void> p = Packet.of(PacketType.PING);
      long ts = p.getTimestamp();
      String json = PacketCodec.encode(p);
      Packet<Void> decoded = PacketCodec.decode(json, Void.class);
      assertThat(decoded.getTimestamp()).isEqualTo(ts);
    }
  }

  // ── peekType() ────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("peekType()")
  class PeekTypeTest {

    @Test
    @DisplayName("peekType — trả đúng PacketType")
    void peekType_correct() {
      String json = PacketCodec.encode(Packet.of(PacketType.PLACE_BID));
      assertThat(PacketCodec.peekType(json)).isEqualTo(PacketType.PLACE_BID);
    }

    @Test
    @DisplayName("peekType không deserialize payload — nhanh")
    void peekType_noPayloadDeserialization() {
      LoginRequestDTO dto = new LoginRequestDTO("alice", "pass");
      String json = PacketCodec.encode(Packet.of(PacketType.LOGIN, dto));
      PacketType type = PacketCodec.peekType(json);
      assertThat(type).isEqualTo(PacketType.LOGIN);
    }
  }

  // ── peekPayload() ─────────────────────────────────────────────────────────
  @Nested
  @DisplayName("peekPayload()")
  class PeekPayloadTest {

    @Test
    @DisplayName("peekPayload — trả JsonElement không null")
    void peekPayload_notNull() {
      ErrorDTO err = ErrorDTO.of("UNAUTHORIZED", "No access");
      String json = PacketCodec.encode(Packet.of(PacketType.SYSTEM_ERROR, err));
      JsonElement element = PacketCodec.peekPayload(json);
      assertThat(element).isNotNull();
    }

    @Test
    @DisplayName("peekPayload null payload — trả JsonNull")
    void peekPayload_nullPayload_returnsJsonNull() {
      String json = PacketCodec.encode(Packet.of(PacketType.PING));
      JsonElement element = PacketCodec.peekPayload(json);
      assertThat(element).isNotNull();
      assertThat(element.isJsonNull()).isTrue();
    }
  }

  // ── fromElement() ─────────────────────────────────────────────────────────
  @Nested
  @DisplayName("fromElement()")
  class FromElementTest {

    @Test
    @DisplayName("fromElement — deserialize JsonElement thành DTO đúng")
    void fromElement_correct() {
      LoginRequestDTO original = new LoginRequestDTO("eve", "pw123");
      String json = PacketCodec.encode(Packet.of(PacketType.LOGIN, original));
      JsonElement element = PacketCodec.peekPayload(json);
      LoginRequestDTO result = PacketCodec.fromElement(element, LoginRequestDTO.class);
      assertThat(result.getUsername()).isEqualTo("eve");
      assertThat(result.getPassword()).isEqualTo("pw123");
    }
  }

  // ── LocalDateTime support ─────────────────────────────────────────────────
  @Nested
  @DisplayName("LocalDateTime — serialize/deserialize qua LocalDateTimeAdapter")
  class LocalDateTimeTest {

    @Test
    @DisplayName("LocalDateTime roundtrip — ISO-8601 format")
    void localDateTime_roundtrip() {
      BidDTOs.BidResultDTO dto = new BidDTOs.BidResultDTO();
      LocalDateTime ts = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
      dto.setTimestamp(ts);
      String json = PacketCodec.encode(Packet.of(PacketType.PLACE_BID_SUCCESS, dto));
      Packet<BidDTOs.BidResultDTO> decoded = PacketCodec.decode(json, BidDTOs.BidResultDTO.class);
      assertThat(decoded.getPayload().getTimestamp()).isEqualTo(ts);
    }
  }

  // ── gson() ────────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("gson()")
  class GsonTest {

    @Test
    @DisplayName("gson() trả non-null instance")
    void gson_notNull() {
      assertThat(PacketCodec.gson()).isNotNull();
    }

    @Test
    @DisplayName("gson() trả cùng instance mỗi lần (singleton)")
    void gson_sameInstance() {
      assertThat(PacketCodec.gson()).isSameAs(PacketCodec.gson());
    }
  }
}
