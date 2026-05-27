package com.group13.auction.common.dto.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ErrorDTO — unit")
class ErrorDTOTest {

  // ── Constructors ──────────────────────────────────────────────────────────
  @Nested
  @DisplayName("Constructors")
  class ConstructorTest {

    @Test
    @DisplayName("default constructor — tất cả null")
    void defaultConstructor_allNull() {
      ErrorDTO dto = new ErrorDTO();
      assertThat(dto.getCode()).isNull();
      assertThat(dto.getMessage()).isNull();
      assertThat(dto.getRequestId()).isNull();
    }

    @Test
    @DisplayName("2-arg constructor — requestId null")
    void twoArgConstructor() {
      ErrorDTO dto = new ErrorDTO("UNAUTHORIZED", "Chưa đăng nhập");
      assertThat(dto.getCode()).isEqualTo("UNAUTHORIZED");
      assertThat(dto.getMessage()).isEqualTo("Chưa đăng nhập");
      assertThat(dto.getRequestId()).isNull();
    }

    @Test
    @DisplayName("3-arg constructor — đủ 3 field")
    void threeArgConstructor() {
      ErrorDTO dto = new ErrorDTO("BID_TOO_LOW", "Giá quá thấp", "req-42");
      assertThat(dto.getCode()).isEqualTo("BID_TOO_LOW");
      assertThat(dto.getMessage()).isEqualTo("Giá quá thấp");
      assertThat(dto.getRequestId()).isEqualTo("req-42");
    }
  }

  // ── Static factories ──────────────────────────────────────────────────────
  @Nested
  @DisplayName("Static factory — of()")
  class StaticFactoryTest {

    @Test
    @DisplayName("of(code, message) — requestId null")
    void of_twoArg() {
      ErrorDTO dto = ErrorDTO.of("AUCTION_CLOSED", "Phiên đã đóng");
      assertThat(dto.getCode()).isEqualTo("AUCTION_CLOSED");
      assertThat(dto.getMessage()).isEqualTo("Phiên đã đóng");
      assertThat(dto.getRequestId()).isNull();
    }

    @Test
    @DisplayName("of(code, message, requestId) — đủ 3 field")
    void of_threeArg() {
      ErrorDTO dto = ErrorDTO.of("INSUFFICIENT_BALANCE", "Không đủ tiền", "r-5");
      assertThat(dto.getCode()).isEqualTo("INSUFFICIENT_BALANCE");
      assertThat(dto.getMessage()).isEqualTo("Không đủ tiền");
      assertThat(dto.getRequestId()).isEqualTo("r-5");
    }

    @Test
    @DisplayName("of() trả object mới mỗi lần — không shared instance")
    void of_returnsNewInstance() {
      ErrorDTO a = ErrorDTO.of("CODE", "msg");
      ErrorDTO b = ErrorDTO.of("CODE", "msg");
      assertThat(a).isNotSameAs(b);
    }
  }

  // ── Error code constants ──────────────────────────────────────────────────
  @Nested
  @DisplayName("Error code constants — tất cả constants định nghĩa đúng")
  class ErrorCodeConstantsTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "INSUFFICIENT_BALANCE", "AUCTION_CLOSED", "AUCTION_NOT_FOUND",
          "BID_TOO_LOW", "NOT_JOINED_AUCTION", "ALREADY_JOINED",
          "ACCOUNT_BANNED", "ACCOUNT_SUSPENDED", "SELLER_CANNOT_BID_OWN",
          "USER_NOT_FOUND", "INVALID_AMOUNT", "PAYMENT_EXPIRED",
          "PAYMENT_ALREADY_DONE", "REPORT_NOT_PENDING", "ALREADY_RATED",
          "UNAUTHORIZED", "INTERNAL_ERROR", "VALIDATION_ERROR",
          "SELLER_ROLE_REQUIRED", "DUPLICATE_USERNAME", "DUPLICATE_EMAIL",
          "WRONG_PASSWORD", "RESERVE_NOT_MET", "AUTO_BID_NOT_FOUND",
          "MAX_BID_TOO_LOW", "SECOND_CHANCE_EXPIRED", "SELLER_OWNS_AUCTION",
          "BALANCE_NOT_ZERO", "ACTIVE_AUCTION_EXISTS"
        })
    @DisplayName("constant tồn tại và không null/rỗng")
    void constant_notNullOrEmpty(String expected) throws Exception {
      // Dùng reflection để lấy constant theo tên
      java.lang.reflect.Field f = ErrorDTO.class.getDeclaredField(expected);
      String value = (String) f.get(null);
      assertThat(value).isNotNull().isNotEmpty().isEqualTo(expected);
    }

    @Test
    @DisplayName("mỗi constant bằng đúng tên của nó — no typo")
    void constants_valueEqualsName() {
      assertThat(ErrorDTO.INSUFFICIENT_BALANCE).isEqualTo("INSUFFICIENT_BALANCE");
      assertThat(ErrorDTO.AUCTION_CLOSED).isEqualTo("AUCTION_CLOSED");
      assertThat(ErrorDTO.UNAUTHORIZED).isEqualTo("UNAUTHORIZED");
      assertThat(ErrorDTO.INTERNAL_ERROR).isEqualTo("INTERNAL_ERROR");
      assertThat(ErrorDTO.USER_NOT_FOUND).isEqualTo("USER_NOT_FOUND");
      assertThat(ErrorDTO.DUPLICATE_USERNAME).isEqualTo("DUPLICATE_USERNAME");
      assertThat(ErrorDTO.WRONG_PASSWORD).isEqualTo("WRONG_PASSWORD");
    }
  }

  // ── toString ──────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("toString()")
  class ToStringTest {

    @Test
    @DisplayName("toString chứa code và message")
    void toString_containsCodeAndMessage() {
      ErrorDTO dto = new ErrorDTO("UNAUTHORIZED", "No access");
      String s = dto.toString();
      assertThat(s).contains("UNAUTHORIZED").contains("No access");
    }
  }

  // ── Setters/Getters ───────────────────────────────────────────────────────
  @Nested
  @DisplayName("Setters / Getters")
  class SettersGettersTest {

    @Test
    @DisplayName("setCode / getCode — roundtrip")
    void code_roundtrip() {
      ErrorDTO dto = new ErrorDTO();
      dto.setCode("BID_TOO_LOW");
      assertThat(dto.getCode()).isEqualTo("BID_TOO_LOW");
    }

    @Test
    @DisplayName("setMessage / getMessage — roundtrip")
    void message_roundtrip() {
      ErrorDTO dto = new ErrorDTO();
      dto.setMessage("Giá đặt thấp hơn tối thiểu");
      assertThat(dto.getMessage()).isEqualTo("Giá đặt thấp hơn tối thiểu");
    }

    @Test
    @DisplayName("setRequestId / getRequestId — roundtrip")
    void requestId_roundtrip() {
      ErrorDTO dto = new ErrorDTO();
      dto.setRequestId("req-99");
      assertThat(dto.getRequestId()).isEqualTo("req-99");
    }
  }
}
