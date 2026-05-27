package com.group13.auction.common.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterRequestDTO — unit")
class RegisterRequestDTOTest {

  @Nested
  @DisplayName("Constructors")
  class ConstructorTest {
    @Test
    @DisplayName("default constructor — fields null")
    void defaultConstructor_null() {
      RegisterRequestDTO dto = new RegisterRequestDTO();
      assertThat(dto.getUsername()).isNull();
      assertThat(dto.getPassword()).isNull();
      assertThat(dto.getEmail()).isNull();
    }

    @Test
    @DisplayName("all-args constructor — fields set")
    void allArgsConstructor() {
      RegisterRequestDTO dto = new RegisterRequestDTO("alice", "pass", "alice@t.com");
      assertThat(dto.getUsername()).isEqualTo("alice");
      assertThat(dto.getPassword()).isEqualTo("pass");
      assertThat(dto.getEmail()).isEqualTo("alice@t.com");
    }
  }

  @Nested
  @DisplayName("Setters / Getters")
  class SettersGettersTest {
    @Test
    @DisplayName("3 field roundtrip độc lập")
    void threeFields_roundtrip() {
      RegisterRequestDTO dto = new RegisterRequestDTO();
      dto.setUsername("bob");
      dto.setPassword("secret");
      dto.setEmail("bob@example.com");
      assertThat(dto.getUsername()).isEqualTo("bob");
      assertThat(dto.getPassword()).isEqualTo("secret");
      assertThat(dto.getEmail()).isEqualTo("bob@example.com");
    }
  }
}
