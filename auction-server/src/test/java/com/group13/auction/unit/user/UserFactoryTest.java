package com.group13.auction.unit.user;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.NormalUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link NormalUserFactory} / {@link com.group13.auction.model.user.UserFactory}.
 *
 * <p>Chiến lược:
 * <ul>
 *   <li><b>Boundary Value Analysis</b> — length biên ±1 tại ngưỡng 8 ký tự.</li>
 *   <li><b>Equivalence Partitioning</b> — phân hoạch null / blank / too-short /
 *       invalid-format / valid cho từng field.</li>
 *   <li><b>Validation Order</b> — username validate trước password, password trước email.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network.
 * Factory được tạo qua {@link TestFixture#normalUserFactory()} (userDAO = null →
 * bỏ qua unique-DB check, chỉ test validation format/length).
 */
@DisplayName("UserFactory — validation")
class UserFactoryTest {

    // Hằng biên — căn chỉnh theo rule: username & password >= 8 ký tự
    private static final int USERNAME_MIN_LENGTH = 8;
    private static final int PASSWORD_MIN_LENGTH = 8;

    // Fixture hợp lệ dùng xuyên suốt
    private static final String VALID_USERNAME = "validUser";       // 9 ký tự ≥ 8
    private static final String VALID_PASSWORD = "securePass1";     // 11 ký tự ≥ 8
    private static final String VALID_EMAIL    = "user@example.com";

    private NormalUserFactory factory;

    @BeforeEach
    void setUp() {
        factory = TestFixture.normalUserFactory(); // userDAO = null
    }

    // =========================================================================
    // createUser — happy path
    // =========================================================================

    @Nested
    @DisplayName("createUser() — happy path")
    class HappyPath {

        @Test
        @DisplayName("thông tin hợp lệ → trả về NormalUser không null")
        void validInputs_returnsNonNullUser() {
            // Act
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            // Assert
            assertNotNull(user);
        }

        @Test
        @DisplayName("thông tin hợp lệ → username được lưu đúng")
        void validInputs_usernameStoredCorrectly() {
            // Act
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            // Assert
            assertEquals(VALID_USERNAME, user.getUsername());
        }

        @Test
        @DisplayName("thông tin hợp lệ → email được lưu đúng")
        void validInputs_emailStoredCorrectly() {
            // Act
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            // Assert
            assertEquals(VALID_EMAIL, user.getEmail());
        }

        @Test
        @DisplayName("thông tin hợp lệ → password được hash (không lưu plaintext)")
        void validInputs_passwordIsHashed() {
            // Act
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            // Assert
            assertNotEquals(VALID_PASSWORD, user.getHashedPassword(),
                    "password phải được hash, không lưu plaintext");
        }

        @Test
        @DisplayName("thông tin hợp lệ → user có ID được sinh tự động")
        void validInputs_userHasGeneratedId() {
            // Act
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            // Assert
            assertNotNull(user.getId());
            assertFalse(user.getId().isBlank());
        }

        @Test
        @DisplayName("username đúng 8 ký tự (biên dưới) → tạo user thành công")
        void usernameExactly8Chars_accepted() {
            // Arrange — "12345678" = 8 ký tự
            String boundary = "12345678";
            assertEquals(USERNAME_MIN_LENGTH, boundary.length());

            // Act & Assert
            assertDoesNotThrow(
                    () -> factory.createUser(boundary, VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("password đúng 8 ký tự (biên dưới) → tạo user thành công")
        void passwordExactly8Chars_accepted() {
            // Arrange — "pass1234" = 8 ký tự
            String boundary = "pass1234";
            assertEquals(PASSWORD_MIN_LENGTH, boundary.length());

            // Act & Assert
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, boundary, VALID_EMAIL));
        }

        @Test
        @DisplayName("username rất dài (200 ký tự) → tạo user thành công")
        void veryLongUsername_accepted() {
            // Arrange
            String longUsername = "a".repeat(200);

            // Act & Assert
            assertDoesNotThrow(
                    () -> factory.createUser(longUsername, VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("password rất dài (200 ký tự) → tạo user thành công")
        void veryLongPassword_accepted() {
            // Arrange
            String longPassword = "p".repeat(200);

            // Act & Assert
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, longPassword, VALID_EMAIL));
        }
    }

    // =========================================================================
    // Username validation
    // =========================================================================

    @Nested
    @DisplayName("validateUsername() — null / blank / length")
    class UsernameValidation {

        // --- Null ---

        @Test
        @DisplayName("username null → IllegalArgumentException")
        void nullUsername_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username null → message đề cập 'trống'")
        void nullUsername_exceptionMessageMentionsEmpty() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, VALID_PASSWORD, VALID_EMAIL));

            // Assert
            assertTrue(ex.getMessage().contains("trống"),
                    "message phải đề cập 'trống', actual: " + ex.getMessage());
        }

        // --- Blank (Equivalence: empty / spaces / tabs) ---

        @Test
        @DisplayName("username rỗng \"\" → IllegalArgumentException")
        void emptyUsername_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser("", VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username chỉ khoảng trắng \" \" → IllegalArgumentException")
        void blankUsername_singleSpace_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(" ", VALID_PASSWORD, VALID_EMAIL));
        }

        @ParameterizedTest(name = "username=\"{0}\" → IllegalArgumentException")
        @ValueSource(strings = {"   ", "\t", "\n", "\t\n ", "        "}) // 8 spaces
        @DisplayName("username chỉ whitespace (nhiều dạng) → IllegalArgumentException")
        void whitespaceOnlyUsername_throwsIllegalArgumentException(String username) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(username, VALID_PASSWORD, VALID_EMAIL));
        }

        // --- Boundary: length < 8 ---

        @Test
        @DisplayName("username 1 ký tự (biên dưới - 7) → IllegalArgumentException")
        void username1Char_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser("a", VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username 7 ký tự (biên dưới - 1) → IllegalArgumentException")
        void username7Chars_throwsIllegalArgumentException() {
            // Arrange
            String sevenChars = "1234567";
            assertEquals(7, sevenChars.length());

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(sevenChars, VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username 7 ký tự → message đề cập '8 ký tự'")
        void username7Chars_exceptionMessageMentionsMinLength() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser("1234567", VALID_PASSWORD, VALID_EMAIL));

            // Assert
            assertTrue(ex.getMessage().contains("8"),
                    "message phải đề cập ngưỡng 8, actual: " + ex.getMessage());
        }

        // --- Boundary: length = 8 (accepted) và 9 (accepted) ---

        @Test
        @DisplayName("username 8 ký tự (biên) → KHÔNG ném exception")
        void username8Chars_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser("abcdefgh", VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username 9 ký tự (biên + 1) → KHÔNG ném exception")
        void username9Chars_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser("abcdefghi", VALID_PASSWORD, VALID_EMAIL));
        }

        // --- Special characters trong username hợp lệ ---

        @Test
        @DisplayName("username chứa ký tự đặc biệt (@#$) → KHÔNG ném exception (không bị filter)")
        void usernameWithSpecialChars_noException() {
            // Factory không filter ký tự đặc biệt trong username
            assertDoesNotThrow(
                    () -> factory.createUser("user@#$1", VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username chứa khoảng trắng ở giữa → KHÔNG ném exception (isBlank = false)")
        void usernameWithInternalSpace_noException() {
            // "user    " không bị isBlank() từ chối nếu không toàn whitespace
            assertDoesNotThrow(
                    () -> factory.createUser("user    ", VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username chỉ số → KHÔNG ném exception")
        void usernameAllDigits_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser("12345678", VALID_PASSWORD, VALID_EMAIL));
        }

        @Test
        @DisplayName("username Unicode tiếng Việt ≥ 8 ký tự → KHÔNG ném exception")
        void usernameUnicode_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser("nguyễnvănA", VALID_PASSWORD, VALID_EMAIL));
        }
    }

    // =========================================================================
    // Password validation
    // =========================================================================

    @Nested
    @DisplayName("validatePassword() — null / length")
    class PasswordValidation {

        // --- Null ---

        @Test
        @DisplayName("password null → IllegalArgumentException")
        void nullPassword_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, null, VALID_EMAIL));
        }

        @Test
        @DisplayName("password null → message đề cập '8 ký tự'")
        void nullPassword_exceptionMessageMentionsMinLength() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, null, VALID_EMAIL));

            assertTrue(ex.getMessage().contains("8"),
                    "message phải đề cập ngưỡng 8, actual: " + ex.getMessage());
        }

        // --- Blank / empty: length < 8, không qua isBlank guard riêng ---

        @Test
        @DisplayName("password rỗng \"\" (length=0 < 8) → IllegalArgumentException")
        void emptyPassword_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, "", VALID_EMAIL));
        }

        @Test
        @DisplayName("password 1 khoảng trắng (length=1 < 8) → IllegalArgumentException")
        void singleSpacePassword_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, " ", VALID_EMAIL));
        }

        @Test
        @DisplayName("password 7 khoảng trắng (length=7 < 8) → IllegalArgumentException")
        void sevenSpacePassword_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, "       ", VALID_EMAIL));
        }

        // Lưu ý: password 8 khoảng trắng length=8 → qua được (factory chỉ check length)
        @Test
        @DisplayName("password 8 khoảng trắng (length=8) → KHÔNG ném exception (chỉ check length)")
        void eightSpacePassword_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, "        ", VALID_EMAIL));
        }

        // --- Boundary: length < 8 ---

        @Test
        @DisplayName("password 1 ký tự → IllegalArgumentException")
        void password1Char_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, "a", VALID_EMAIL));
        }

        @Test
        @DisplayName("password 7 ký tự (biên - 1) → IllegalArgumentException")
        void password7Chars_throwsIllegalArgumentException() {
            // Arrange
            String sevenChars = "abcdefg";
            assertEquals(7, sevenChars.length());

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, sevenChars, VALID_EMAIL));
        }

        @ParameterizedTest(name = "password length={0} → IllegalArgumentException")
        @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
        @DisplayName("password length 1..7 (equivalence: too-short) → IllegalArgumentException")
        void passwordLengthLessThan8_throwsIllegalArgumentException(int length) {
            String shortPassword = "x".repeat(length);
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, shortPassword, VALID_EMAIL));
        }

        // --- Boundary: length = 8 (accepted) ---

        @Test
        @DisplayName("password 8 ký tự (biên dưới) → KHÔNG ném exception")
        void password8Chars_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, "abcdefgh", VALID_EMAIL));
        }

        @Test
        @DisplayName("password 9 ký tự (biên + 1) → KHÔNG ném exception")
        void password9Chars_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, "abcdefghi", VALID_EMAIL));
        }

        // --- Special characters trong password ---

        @Test
        @DisplayName("password chứa ký tự đặc biệt ≥ 8 ký tự → KHÔNG ném exception")
        void passwordWithSpecialChars_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, "P@$$w0rd!", VALID_EMAIL));
        }

        @Test
        @DisplayName("password chứa Unicode ≥ 8 ký tự → KHÔNG ném exception")
        void passwordWithUnicode_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, "mậtKhẩu1", VALID_EMAIL));
        }
    }

    // =========================================================================
    // Email validation
    // =========================================================================

    @Nested
    @DisplayName("validateEmail() — null / blank / format")
    class EmailValidation {

        // --- Null ---

        @Test
        @DisplayName("email null → IllegalArgumentException")
        void nullEmail_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, null));
        }

        @Test
        @DisplayName("email null → message đề cập 'trống'")
        void nullEmail_exceptionMessageMentionsEmpty() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, null));

            assertTrue(ex.getMessage().contains("trống"),
                    "message phải đề cập 'trống', actual: " + ex.getMessage());
        }

        // --- Blank ---

        @Test
        @DisplayName("email rỗng \"\" → IllegalArgumentException")
        void emptyEmail_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, ""));
        }

        @ParameterizedTest(name = "email=\"{0}\" → IllegalArgumentException")
        @ValueSource(strings = {" ", "   ", "\t", "\n"})
        @DisplayName("email chỉ whitespace → IllegalArgumentException")
        void whitespaceOnlyEmail_throwsIllegalArgumentException(String email) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, email));
        }

        // --- Format invalid: thiếu @ ---

        @Test
        @DisplayName("email không có @ → IllegalArgumentException")
        void emailMissingAt_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "userexample.com"));
        }

        @Test
        @DisplayName("email không có @ → message đề cập 'định dạng'")
        void emailMissingAt_exceptionMessageMentionsFormat() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "userexample.com"));

            assertTrue(ex.getMessage().contains("định dạng"),
                    "message phải đề cập 'định dạng', actual: " + ex.getMessage());
        }

        // --- Format invalid: nhiều @ ---

        @Test
        @DisplayName("email có nhiều @ → IllegalArgumentException")
        void emailMultipleAt_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "u@@example.com"));
        }

        // --- Format invalid: thiếu domain ---

        @Test
        @DisplayName("email chỉ có local@, không có domain → IllegalArgumentException")
        void emailMissingDomain_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user@"));
        }

        // --- Format invalid: thiếu TLD (không có dấu chấm sau domain) ---

        @Test
        @DisplayName("email không có TLD (user@example) → IllegalArgumentException")
        void emailMissingTld_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user@example"));
        }

        // --- Format invalid: local part rỗng ---

        @Test
        @DisplayName("email local part rỗng (@example.com) → IllegalArgumentException")
        void emailEmptyLocalPart_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "@example.com"));
        }

        // --- Format invalid: khoảng trắng trong email ---

        @Test
        @DisplayName("email chứa khoảng trắng trong local part → IllegalArgumentException")
        void emailWithSpaceInLocalPart_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user name@example.com"));
        }

        @Test
        @DisplayName("email chứa khoảng trắng trong domain → IllegalArgumentException")
        void emailWithSpaceInDomain_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user@exam ple.com"));
        }

        // --- Format invalid: @ ở đầu có whitespace local ---

        @Test
        @DisplayName("email chứa khoảng trắng trước @ → IllegalArgumentException (regex \\\\s)")
        void emailWithSpaceBeforeAt_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user @example.com"));
        }

        // --- Format valid: equivalence partition ---

        @ParameterizedTest(name = "email=\"{0}\" → hợp lệ")
        @ValueSource(strings = {
                "user@example.com",
                "user.name@example.com",
                "user+tag@example.org",
                "u@a.io",
                "user@sub.domain.com",
                "123@456.vn",
                "user_name@example-domain.com",
                "USER@EXAMPLE.COM",
                "a@b.c"
        })
        @DisplayName("email hợp lệ nhiều dạng → KHÔNG ném exception")
        void validEmailFormats_noException(String email) {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, email));
        }

        // --- Edge: TLD rất ngắn (1 ký tự sau dấu chấm) ---

        @Test
        @DisplayName("email TLD 1 ký tự (a@b.c) → KHÔNG ném exception (regex cho phép)")
        void emailWithSingleCharTld_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "a@b.c"));
        }

        // --- Edge: email có @ trong TLD (invalid) ---

        @Test
        @DisplayName("email dạng user@example.@com → IllegalArgumentException")
        void emailWithAtInTld_throwsIllegalArgumentException() {
            // regex [^@\\s]+@[^@\\s]+\\.[^@\\s]+ — TLD không được chứa @
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user@example.@com"));
        }

        // --- Edge: nhiều dấu chấm hợp lệ ---

        @Test
        @DisplayName("email subdomain nhiều cấp → KHÔNG ném exception")
        void emailWithMultipleSubdomains_noException() {
            assertDoesNotThrow(
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "user@a.b.c.d.com"));
        }
    }

    // =========================================================================
    // Validation order — username trước password, password trước email
    // =========================================================================

    @Nested
    @DisplayName("Validation order — username → password → email")
    class ValidationOrder {

        @Test
        @DisplayName("username invalid + password invalid → exception từ username (validate trước)")
        void invalidUsernameAndPassword_usernameValidatedFirst() {
            // Arrange — username null, password null
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, null, VALID_EMAIL));

            // Assert — message phải từ username validation
            assertTrue(ex.getMessage().contains("trống") || ex.getMessage().contains("Username"),
                    "exception phải từ username validation, actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("username invalid + email invalid → exception từ username (validate trước)")
        void invalidUsernameAndEmail_usernameValidatedFirst() {
            // Arrange — username null, email null
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, VALID_PASSWORD, null));

            assertTrue(ex.getMessage().contains("trống") || ex.getMessage().contains("Username"),
                    "exception phải từ username validation, actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("username valid + password invalid + email invalid → exception từ password")
        void validUsernameInvalidPasswordAndEmail_passwordValidatedSecond() {
            // Arrange — password null, email null
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, null, null));

            // Assert — message từ password validation (đề cập "8 ký tự")
            assertTrue(ex.getMessage().contains("8"),
                    "exception phải từ password validation, actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("username valid + password valid + email invalid → exception từ email")
        void validUsernameAndPasswordInvalidEmail_emailValidatedLast() {
            // Arrange — email null
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, null));

            // Assert — message từ email validation
            assertTrue(ex.getMessage().contains("trống") || ex.getMessage().contains("Email"),
                    "exception phải từ email validation, actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("tất cả invalid → chỉ ném 1 exception (fail-fast, không collect tất cả lỗi)")
        void allFieldsInvalid_onlyOneExceptionThrown() {
            // Assert — assertThrows chỉ catch 1 exception, không có nhiều exception
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, null, null));
        }
    }

    // =========================================================================
    // isEmailAlreadyUsed — không có DAO (luôn false)
    // =========================================================================

    @Nested
    @DisplayName("isEmailAlreadyUsed() — không có DAO")
    class IsEmailAlreadyUsed {

        @Test
        @DisplayName("không có DAO → isEmailAlreadyUsed luôn false")
        void noDao_isEmailAlreadyUsedReturnsFalse() {
            // Arrange — factory không có userDAO
            // Act & Assert
            assertFalse(factory.isEmailAlreadyUsed(VALID_EMAIL));
        }

        @Test
        @DisplayName("không có DAO → isEmailAlreadyUsed với email bất kỳ vẫn false")
        void noDao_anyEmail_returnsFalse() {
            assertFalse(factory.isEmailAlreadyUsed("anyone@anywhere.com"));
        }
    }

    // =========================================================================
    // Multiple createUser calls — factory stateless (không có DAO)
    // =========================================================================

    @Nested
    @DisplayName("createUser() — factory stateless, nhiều lần tạo")
    class FactoryStateless {

        @Test
        @DisplayName("tạo hai user cùng username → KHÔNG ném exception (không có DAO guard)")
        void sameUsernameTwice_noExceptionWithoutDao() {
            // Arrange — không có DAO, factory bỏ qua unique check
            // Act & Assert
            assertDoesNotThrow(() -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL));
            assertDoesNotThrow(() -> factory.createUser(VALID_USERNAME, VALID_PASSWORD, "other@example.com"));
        }

        @Test
        @DisplayName("tạo hai user khác nhau → hai instance độc lập")
        void twoDifferentUsers_returnSeparateInstances() {
            // Act
            NormalUser user1 = factory.createUser(VALID_USERNAME,      VALID_PASSWORD, VALID_EMAIL);
            NormalUser user2 = factory.createUser("anotherUser1", VALID_PASSWORD, "other@example.com");

            // Assert — không cùng reference, không cùng id
            assertNotSame(user1, user2);
            assertNotEquals(user1.getId(), user2.getId());
        }

        @Test
        @DisplayName("tạo user hợp lệ sau khi tạo user invalid → không bị ảnh hưởng bởi lần trước")
        void validAfterInvalid_factoryStillWorks() {
            // Arrange — lần 1: invalid (ném exception)
            assertThrows(IllegalArgumentException.class,
                    () -> factory.createUser(null, null, null));

            // Act — lần 2: valid
            NormalUser user = factory.createUser(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

            // Assert
            assertNotNull(user);
        }
    }
}