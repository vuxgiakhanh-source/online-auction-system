package com.group13.auction.common.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocalDateTimeAdapter — unit")
class LocalDateTimeAdapterTest {

  private Gson gson;

  @BeforeEach
  void setUp() {
    gson =
        new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
  }

  // ── serialize ─────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("serialize()")
  class SerializeTest {

    @Test
    @DisplayName("serialize LocalDateTime → ISO-8601 string")
    void serialize_isoFormat() {
      LocalDateTime dt = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
      String json = gson.toJson(dt, LocalDateTime.class);
      // Gson wraps string in quotes
      assertThat(json).isEqualTo("\"2025-06-15T10:30:00\"");
    }

    @Test
    @DisplayName("serialize với nanoseconds — giữ đủ precision")
    void serialize_withNanos() {
      LocalDateTime dt = LocalDateTime.of(2025, 1, 1, 0, 0, 0, 500_000_000);
      String json = gson.toJson(dt, LocalDateTime.class);
      assertThat(json).contains("2025-01-01T00:00:00");
    }
  }

  // ── deserialize ───────────────────────────────────────────────────────────
  @Nested
  @DisplayName("deserialize()")
  class DeserializeTest {

    @Test
    @DisplayName("deserialize ISO-8601 string → đúng LocalDateTime")
    void deserialize_isoString() {
      String json = "\"2025-06-15T10:30:00\"";
      LocalDateTime result = gson.fromJson(json, LocalDateTime.class);
      assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 10, 30, 0));
    }

    @Test
    @DisplayName("deserialize với time component đầy đủ")
    void deserialize_withSeconds() {
      String json = "\"2024-12-31T23:59:59\"";
      LocalDateTime result = gson.fromJson(json, LocalDateTime.class);
      assertThat(result.getYear()).isEqualTo(2024);
      assertThat(result.getMonthValue()).isEqualTo(12);
      assertThat(result.getDayOfMonth()).isEqualTo(31);
      assertThat(result.getHour()).isEqualTo(23);
      assertThat(result.getMinute()).isEqualTo(59);
      assertThat(result.getSecond()).isEqualTo(59);
    }

    @Test
    @DisplayName("deserialize sai format — ném exception")
    void deserialize_invalidFormat_throws() {
      String badJson = "\"2025/06/15 10:30:00\"";
      assertThatThrownBy(() -> gson.fromJson(badJson, LocalDateTime.class))
          .isInstanceOf(Exception.class);
    }
  }

  // ── roundtrip ─────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("Roundtrip serialize → deserialize")
  class RoundtripTest {

    @Test
    @DisplayName("roundtrip — LocalDateTime không thay đổi")
    void roundtrip_identity() {
      LocalDateTime original = LocalDateTime.of(2025, 3, 15, 8, 0, 0);
      String json = gson.toJson(original, LocalDateTime.class);
      LocalDateTime restored = gson.fromJson(json, LocalDateTime.class);
      assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("roundtrip nhiều giá trị — tất cả đúng")
    void roundtrip_multipleValues() {
      LocalDateTime[] values = {
        LocalDateTime.of(2020, 1, 1, 0, 0, 0),
        LocalDateTime.of(2025, 12, 31, 23, 59, 59),
        LocalDateTime.of(2023, 6, 15, 12, 30, 45),
      };
      for (LocalDateTime dt : values) {
        String json = gson.toJson(dt, LocalDateTime.class);
        LocalDateTime restored = gson.fromJson(json, LocalDateTime.class);
        assertThat(restored).isEqualTo(dt);
      }
    }
  }
}
