package com.group13.auction.unit.user;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.NormalUserFactory;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link NormalUserFactory} — validation và tạo user.
 */
@DisplayName("UserFactory — validation")
class UserFactoryTest {

    private static final String VALID_USERNAME = "validUser";
    private static final String VALID_PASSWORD = "securePass1";
    private static final String VALID_EMAIL = "user@example.com";

    private NormalUserFactory factory;

    @BeforeEach
    void setUp() {
        factory = TestFixture.normalUserFactory();
    }

    @Nested
    @DisplayName("createUser() — happy path")
    class HappyPath {

        @Test
        @DisplayName("input hợp lệ → user đầy đủ field cơ bản")
        void validInputs_createsUserWithHashedPasswordAndId() {
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            assertNotNull(user);
            assertEquals(VALID_USERNAME, user.getUsername());
            assertEquals(VALID_EMAIL, user.getEmail());
            assertNotEquals(VALID_PASSWORD, user.getHashedPassword());
            assertNotNull(user.getId());
            assertFalse(user.getId().isBlank());
        }

        @Test
        @DisplayName("username/password đúng 8 ký tự (biên) → thành công")
        void boundaryLength8_accepted() {
            assertDoesNotThrow(() -> factory.createUser("12345678", "pass1234", VALID_EMAIL));
        }
    }

    @Nested
    @DisplayName("username validation")
    class UsernameValidation {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t"})
        @DisplayName("null / blank → IllegalArgumentException")
        void invalidUsername_throws(String username) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(username, VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("7 ký tự → IllegalArgumentException")
        void tooShort_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser("1234567", VALID_PASSWORD, VALID_EMAIL));
        }
    }

    @Nested
    @DisplayName("password validation")
    class PasswordValidation {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "abcdefg"})
        @DisplayName("null / blank / < 8 ký tự → IllegalArgumentException")
        void invalidPassword_throws(String password) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, password, VALID_EMAIL));
        }
    }

    @Nested
    @DisplayName("email validation")
    class EmailValidation {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "not-an-email", "user@", "@example.com", "user@example"})
        @DisplayName("null / blank / sai format → IllegalArgumentException")
        void invalidEmail_throws(String email) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, email));
        }

        @ParameterizedTest
        @ValueSource(strings = {"user@example.com", "user.name@example.org", "a@b.c"})
        @DisplayName("format hợp lệ → không ném exception")
        void validEmail_noException(String email) {
            assertDoesNotThrow(() -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, email));
        }
    }

    @Nested
    @DisplayName("validation order")
    class ValidationOrder {

        @Test
        @DisplayName("username invalid được báo trước password/email")
        void usernameValidatedFirst() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, null, null));
            assertTrue(ex.getMessage().contains("trống") || ex.getMessage().toLowerCase().contains("username"),
                    ex.getMessage());
        }
    }

    @Nested
    @DisplayName("factory không có DAO")
    class WithoutDao {

        @Test
        @DisplayName("isEmailAlreadyUsed luôn false")
        void isEmailAlreadyUsed_returnsFalse() {
            assertFalse(factory.isEmailAlreadyUsed(VALID_EMAIL));
        }

        @Test
        @DisplayName("hai user khác nhau → id khác nhau")
        void twoUsers_distinctIds() {
            NormalUser u1 = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);
            NormalUser u2 = factory.createUser("anotherUsr", VALID_PASSWORD, "other@example.com");
            assertNotEquals(u1.getId(), u2.getId());
        }
    }
}
