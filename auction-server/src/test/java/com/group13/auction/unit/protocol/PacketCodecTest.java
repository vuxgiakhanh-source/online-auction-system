package com.group13.auction.unit.protocol;

import static org.assertj.core.api.Assertions.*;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit test cho {@link PacketCodec} — encode / decode / peekType / peekPayload.
 *
 * <p>Không DB, không network. Thuần serialization logic.
 */
@DisplayName("PacketCodec — serialize / deserialize")
class PacketCodecTest {
  // encode / decode roundtrip
  @Nested
  @DisplayName("encode → decode roundtrip")
  class Roundtrip {

    @Test
    @DisplayName("encode + decode với String payload giữ nguyên type và payload")
    void stringPayload_roundtrip() {
      Packet<String> original = Packet.of(PacketType.LOGIN, "hello", "rid-1");

      String json = PacketCodec.encode(original);
      Packet<String> decoded = PacketCodec.decode(json, String.class);

      assertThat(decoded.getType()).isEqualTo(PacketType.LOGIN);
      assertThat(decoded.getPayload()).isEqualTo("hello");
      assertThat(decoded.getRequestId()).isEqualTo("rid-1");
    }

    @Test
    @DisplayName("encode + decode với DTO payload giữ nguyên toàn bộ fields")
    void dtoPayload_roundtrip() {
      LoginRequestDTO dto = new LoginRequestDTO("alice", "secret123");
      Packet<LoginRequestDTO> original = Packet.of(PacketType.LOGIN, dto, "rid-dto");

      String json = PacketCodec.encode(original);
      Packet<LoginRequestDTO> decoded = PacketCodec.decode(json, LoginRequestDTO.class);

      assertThat(decoded.getPayload().getUsername()).isEqualTo("alice");
      assertThat(decoded.getPayload().getPassword()).isEqualTo("secret123");
    }

    @Test
    @DisplayName("encode + decode với null payload không NPE")
    void nullPayload_roundtrip() {
      Packet<Void> original = Packet.of(PacketType.PING, null, null);

      String json = PacketCodec.encode(original);
      Packet<Void> decoded = PacketCodec.decode(json, Void.class);

      assertThat(decoded.getType()).isEqualTo(PacketType.PING);
      assertThat(decoded.getPayload()).isNull();
      assertThat(decoded.getRequestId()).isNull();
    }

    @Test
    @DisplayName("encode + decode với null requestId giữ nguyên null")
    void nullRequestId_roundtrip() {
      Packet<String> original = Packet.of(PacketType.LOGOUT, null, null);

      String json = PacketCodec.encode(original);
      Packet<String> decoded = PacketCodec.decode(json, String.class);

      assertThat(decoded.getRequestId()).isNull();
    }

    @Test
    @DisplayName("LocalDateTime trong payload được serialize/deserialize đúng ISO-8601")
    void localDateTimePayload_roundtrip() {
      BidDTOs.BidUpdateDTO dto = new BidDTOs.BidUpdateDTO();
      dto.setAuctionId("auc-1");
      dto.setNewCurrentPrice(1_500_000L);
      LocalDateTime ts = LocalDateTime.of(2026, 5, 14, 10, 30, 0);
      dto.setTimestamp(ts);

      String json = PacketCodec.encode(Packet.of(PacketType.BID_UPDATE, dto, "r1"));
      Packet<BidDTOs.BidUpdateDTO> decoded = PacketCodec.decode(json, BidDTOs.BidUpdateDTO.class);

      assertThat(decoded.getPayload().getTimestamp()).isEqualTo(ts);
    }
  }
  // peekType
  @Nested
  @DisplayName("peekType")
  class PeekType {

    @Test
    @DisplayName("peekType trả về đúng PacketType không cần decode toàn bộ")
    void peekType_returnsCorrectType() {
      String json = PacketCodec.encode(Packet.of(PacketType.PLACE_BID, null, null));

      assertThat(PacketCodec.peekType(json)).isEqualTo(PacketType.PLACE_BID);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "LOGIN", "REGISTER", "LOGOUT",
          "PLACE_BID", "JOIN_AUCTION", "WATCH_AUCTION",
          "DEPOSIT", "WITHDRAW", "PING"
        })
    @DisplayName("peekType hoạt động đúng với mọi PacketType trong danh sách")
    void peekType_multipleTypes(String typeName) {
      PacketType type = PacketType.valueOf(typeName);
      String json = PacketCodec.encode(Packet.of(type, null, null));

      assertThat(PacketCodec.peekType(json)).isEqualTo(type);
    }

    @Test
    @DisplayName("peekType với JSON malformed → ném exception (không nuốt)")
    void peekType_malformedJson_throws() {
      assertThatThrownBy(() -> PacketCodec.peekType("{ not valid json"))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("peekType với type không tồn tại → IllegalArgumentException")
    void peekType_unknownType_throws() {
      String json = "{\"type\":\"FAKE_TYPE_XYZ\",\"payload\":null}";

      assertThatThrownBy(() -> PacketCodec.peekType(json))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
  // peekPayload
  @Nested
  @DisplayName("peekPayload")
  class PeekPayload {

    @Test
    @DisplayName("peekPayload trả về đúng JsonElement")
    void peekPayload_returnsElement() {
      LoginRequestDTO dto = new LoginRequestDTO("bob", "pw123");
      String json = PacketCodec.encode(Packet.of(PacketType.LOGIN, dto, null));

      JsonElement el = PacketCodec.peekPayload(json);

      assertThat(el).isNotNull();
      assertThat(el.isJsonNull()).isFalse();
    }

    @Test
    @DisplayName("peekPayload với null payload → trả về JsonNull")
    void peekPayload_nullPayload_returnsJsonNull() {
      String json = PacketCodec.encode(Packet.of(PacketType.PING, null, null));

      JsonElement el = PacketCodec.peekPayload(json);

      // Either null or JsonNull — both acceptable; what matters is no NPE
      assertThat(el == null || el.isJsonNull()).isTrue();
    }

    @Test
    @DisplayName("fromElement deserialize đúng sau peekPayload")
    void fromElement_afterPeekPayload_correctDeserialize() {
      BidDTOs.BidRequestDTO original = new BidDTOs.BidRequestDTO("auc-42", 2_000_000L);
      String json = PacketCodec.encode(Packet.of(PacketType.PLACE_BID, original, "r1"));

      JsonElement el = PacketCodec.peekPayload(json);
      BidDTOs.BidRequestDTO decoded = PacketCodec.fromElement(el, BidDTOs.BidRequestDTO.class);

      assertThat(decoded.getAuctionId()).isEqualTo("auc-42");
      assertThat(decoded.getAmount()).isEqualTo(2_000_000L);
    }
  }
  // Timestamp trong encoded JSON
  @Nested
  @DisplayName("timestamp field")
  class TimestampField {

    @Test
    @DisplayName("encoded JSON chứa timestamp > 0")
    void encode_includesPositiveTimestamp() {
      String json = PacketCodec.encode(Packet.of(PacketType.PING, null, null));

      assertThat(json).contains("\"timestamp\"");
      // timestamp phải là số hợp lệ
      Packet<Void> decoded = PacketCodec.decode(json, Void.class);
      assertThat(decoded.getTimestamp()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("decode với thiếu timestamp field sử dụng currentTimeMillis")
    void decode_missingTimestamp_usesCurrentTime() {
      long before = System.currentTimeMillis();
      String json = "{\"type\":\"PING\",\"payload\":null,\"requestId\":null}";

      Packet<Void> decoded = PacketCodec.decode(json, Void.class);

      assertThat(decoded.getTimestamp()).isGreaterThanOrEqualTo(before);
    }
  }
}
