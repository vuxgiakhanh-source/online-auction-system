package com.group13.auction.common.dto.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginRequestDTO — unit")
class LoginRequestDTOTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTest {
        @Test @DisplayName("default constructor — fields null")
        void defaultConstructor_null() {
            LoginRequestDTO dto = new LoginRequestDTO();
            assertThat(dto.getUsername()).isNull();
            assertThat(dto.getPassword()).isNull();
        }

        @Test @DisplayName("all-args constructor — fields set")
        void allArgsConstructor() {
            LoginRequestDTO dto = new LoginRequestDTO("alice", "secret");
            assertThat(dto.getUsername()).isEqualTo("alice");
            assertThat(dto.getPassword()).isEqualTo("secret");
        }
    }

    @Nested
    @DisplayName("Setters / Getters")
    class SettersGettersTest {
        @Test @DisplayName("setUsername / getUsername — roundtrip")
        void username_roundtrip() {
            LoginRequestDTO dto = new LoginRequestDTO();
            dto.setUsername("bob");
            assertThat(dto.getUsername()).isEqualTo("bob");
        }

        @Test @DisplayName("setPassword / getPassword — roundtrip")
        void password_roundtrip() {
            LoginRequestDTO dto = new LoginRequestDTO();
            dto.setPassword("p@ssw0rd");
            assertThat(dto.getPassword()).isEqualTo("p@ssw0rd");
        }

        @Test @DisplayName("overwrite — lần set sau ghi đè lần trước")
        void overwrite_lastWins() {
            LoginRequestDTO dto = new LoginRequestDTO("old", "old");
            dto.setUsername("new");
            dto.setPassword("new");
            assertThat(dto.getUsername()).isEqualTo("new");
            assertThat(dto.getPassword()).isEqualTo("new");
        }
    }
}
