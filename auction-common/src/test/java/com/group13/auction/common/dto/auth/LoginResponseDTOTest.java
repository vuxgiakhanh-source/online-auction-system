package com.group13.auction.common.dto.auth;

import com.group13.auction.common.dto.user.UserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginResponseDTO — unit")
class LoginResponseDTOTest {

    @Nested
    @DisplayName("Constructors")
    class ConstructorTest {
        @Test @DisplayName("default constructor — fields null")
        void defaultConstructor_null() {
            LoginResponseDTO dto = new LoginResponseDTO();
            assertThat(dto.getToken()).isNull();
            assertThat(dto.getUser()).isNull();
        }

        @Test @DisplayName("all-args constructor — fields set")
        void allArgsConstructor() {
            UserDTO user = new UserDTO();
            user.setUsername("alice");
            LoginResponseDTO dto = new LoginResponseDTO("token-xyz", user);
            assertThat(dto.getToken()).isEqualTo("token-xyz");
            assertThat(dto.getUser()).isSameAs(user);
        }
    }

    @Nested
    @DisplayName("Setters / Getters")
    class SettersGettersTest {
        @Test @DisplayName("token roundtrip")
        void token_roundtrip() {
            LoginResponseDTO dto = new LoginResponseDTO();
            dto.setToken("abc-123");
            assertThat(dto.getToken()).isEqualTo("abc-123");
        }

        @Test @DisplayName("user roundtrip — object identity preserved")
        void user_roundtrip() {
            UserDTO user = new UserDTO();
            user.setId("u-1");
            LoginResponseDTO dto = new LoginResponseDTO();
            dto.setUser(user);
            assertThat(dto.getUser()).isSameAs(user);
            assertThat(dto.getUser().getId()).isEqualTo("u-1");
        }

        @Test @DisplayName("user null — set null lại được")
        void user_setNull() {
            UserDTO user = new UserDTO();
            LoginResponseDTO dto = new LoginResponseDTO("t", user);
            dto.setUser(null);
            assertThat(dto.getUser()).isNull();
        }
    }
}
