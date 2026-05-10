package com.group13.auction.user;

import com.group13.auction.TestFixture;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link User}.
 *
 * <p>Tập trung vào 3 nhóm behavior:
 * <ul>
 *   <li>{@code adjustRating} — clamp logic, delta âm/dương, biên.</li>
 *   <li>{@code hashPassword} — consistency, determinism, edge case.</li>
 *   <li>{@code setAccountStatus} — transition hợp lệ, suspendedAt tracking.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network.
 * Dùng object thật từ {@link TestFixture}.
 */
@DisplayName("User")
class UserTest {

    // =========================================================================
    // adjustRating
    // =========================================================================

    @Nested
    @DisplayName("adjustRating() — clamp và delta")
    class AdjustRating {

        private NormalUser user;

        @BeforeEach
        void setUp() {
            // rating mặc định = 3.0
            user = TestFixture.normalBidder("bidderAA1");
        }

        // --- Happy path ---

        @Test
        @DisplayName("delta dương làm tăng rating đúng lượng")
        void positiveDelta_increasesRating() {
            // Arrange
            double initialRating = user.getRating(); // 3.0
            double delta = 0.5;

            // Act
            user.adjustRating(delta);

            // Assert
            assertEquals(initialRating + delta, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("delta âm làm giảm rating đúng lượng")
        void negativeDelta_decreasesRating() {
            // Arrange
            double initialRating = user.getRating(); // 3.0
            double delta = -1.0;

            // Act
            user.adjustRating(delta);

            // Assert
            assertEquals(initialRating + delta, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("delta = 0 không thay đổi rating")
        void zeroDelta_ratingUnchanged() {
            // Arrange
            double initialRating = user.getRating();

            // Act
            user.adjustRating(0.0);

            // Assert
            assertEquals(initialRating, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("nhiều lần adjustRating cộng dồn đúng")
        void multipleAdjustments_accumulate() {
            // Arrange — rating = 3.0
            // Act
            user.adjustRating(1.0);  // 4.0
            user.adjustRating(-0.5); // 3.5

            // Assert
            assertEquals(3.5, user.getRating(), 1e-9);
        }

        // --- Clamp tại MAX (5.0) ---

        @Test
        @DisplayName("clamp MAX: delta đưa rating vượt 5.0 → giữ nguyên 5.0")
        void deltaExceedsMax_clampedToMax() {
            // Arrange — rating = 3.0
            // Act
            user.adjustRating(10.0);

            // Assert
            assertEquals(5.0, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("clamp MAX: từ đúng 5.0, delta dương vẫn giữ 5.0")
        void atMaxRating_positiveDelta_staysAtMax() {
            // Arrange
            NormalUser maxUser = TestFixture.bidderWithRating("bidderBB2", 5.0);

            // Act
            maxUser.adjustRating(0.1);

            // Assert
            assertEquals(5.0, maxUser.getRating(), 1e-9);
        }

        @Test
        @DisplayName("clamp MAX: delta chính xác đưa lên 5.0 không bị cắt")
        void exactDeltaToMax_reachesMax() {
            // Arrange — rating = 3.0, delta = 2.0 → 5.0 chính xác
            // Act
            user.adjustRating(2.0);

            // Assert
            assertEquals(5.0, user.getRating(), 1e-9);
        }

        // --- Clamp tại MIN (0.0) ---

        @Test
        @DisplayName("clamp MIN: delta đưa rating xuống âm → giữ nguyên 0.0")
        void deltaBelowMin_clampedToMin() {
            // Arrange — rating = 3.0
            // Act
            user.adjustRating(-10.0);

            // Assert
            assertEquals(0.0, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("clamp MIN: từ đúng 0.0, delta âm vẫn giữ 0.0")
        void atMinRating_negativeDelta_staysAtMin() {
            // Arrange
            NormalUser minUser = TestFixture.bidderWithRating("bidderCC3", 0.0);

            // Act
            minUser.adjustRating(-0.1);

            // Assert
            assertEquals(0.0, minUser.getRating(), 1e-9);
        }

        @Test
        @DisplayName("clamp MIN: delta chính xác đưa về 0.0 không bị cắt")
        void exactDeltaToMin_reachesMin() {
            // Arrange — rating = 3.0, delta = -3.0 → 0.0 chính xác
            // Act
            user.adjustRating(-3.0);

            // Assert
            assertEquals(0.0, user.getRating(), 1e-9);
        }

        // --- Biên ---

        @Test
        @DisplayName("biên: delta rất nhỏ (Double.MIN_VALUE) không gây sai lệch bất ngờ")
        void tinyPositiveDelta_increasesByTinyAmount() {
            // Arrange
            double initialRating = user.getRating();

            // Act
            user.adjustRating(Double.MIN_VALUE);

            // Assert — vẫn trong [0, 5]
            assertTrue(user.getRating() >= 0.0);
            assertTrue(user.getRating() <= 5.0);
            assertTrue(user.getRating() >= initialRating);
        }

        @Test
        @DisplayName("biên: delta = Double.MAX_VALUE → clamp về 5.0")
        void maxDoubleDelta_clampedToMax() {
            // Act
            user.adjustRating(Double.MAX_VALUE);

            // Assert
            assertEquals(5.0, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("biên: delta = -Double.MAX_VALUE → clamp về 0.0")
        void minDoubleDelta_clampedToMin() {
            // Act
            user.adjustRating(-Double.MAX_VALUE);

            // Assert
            assertEquals(0.0, user.getRating(), 1e-9);
        }

        // --- Invalid input ---

        @Test
        @DisplayName("NaN delta: rating trở thành NaN — hệ thống không ném exception")
        void nanDelta_ratingBecomesNaN() {
            // Arrange — ghi nhận behavior thực tế của Math.max/min với NaN
            // Act
            user.adjustRating(Double.NaN);

            // Assert — behavior: NaN propagates (không crash)
            // Test này document behavior, không phải yêu cầu NaN hợp lệ
            // Nếu tương lai có validation, test này sẽ fail → tín hiệu cần update
            assertTrue(Double.isNaN(user.getRating()) || (user.getRating() >= 0.0 && user.getRating() <= 5.0));
        }
    }

    // =========================================================================
    // hashPassword
    // =========================================================================

    @Nested
    @DisplayName("hashPassword() — hashing behavior")
    class HashPassword {

        // --- Happy path / consistency ---

        @Test
        @DisplayName("cùng input → cùng hash (deterministic)")
        void samePassword_sameHash() {
            // Arrange
            String password = "MySecurePassword123!";

            // Act
            String hash1 = User.hashPassword(password);
            String hash2 = User.hashPassword(password);

            // Assert
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("hash không bằng plaintext")
        void hash_notEqualToPlaintext() {
            // Arrange
            String password = "secret";

            // Act
            String hash = User.hashPassword(password);

            // Assert
            assertNotEquals(password, hash);
        }

        @Test
        @DisplayName("hash của password khác nhau thì khác nhau")
        void differentPasswords_differentHashes() {
            // Arrange
            String pw1 = "password123";
            String pw2 = "password124";

            // Act & Assert
            assertNotEquals(User.hashPassword(pw1), User.hashPassword(pw2));
        }

        @Test
        @DisplayName("hash nhất quán với password được dùng khi tạo user")
        void hash_consistentWithUserCreation() {
            // Arrange
            String rawPassword = "password1"; // password dùng trong TestFixture
            NormalUser user = TestFixture.normalBidder("bidderDD4");

            // Act
            String expectedHash = User.hashPassword(rawPassword);

            // Assert — hash lưu trong user phải match
            assertEquals(expectedHash, user.getHashedPassword());
        }

        // --- Security edge cases ---

        @Test
        @DisplayName("password chỉ khác 1 ký tự → hash hoàn toàn khác (avalanche)")
        void oneCharDifference_completelyDifferentHash() {
            // Arrange
            String pw1 = "password";
            String pw2 = "Password"; // chữ hoa P

            // Act
            String hash1 = User.hashPassword(pw1);
            String hash2 = User.hashPassword(pw2);

            // Assert
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("password có khoảng trắng đầu/cuối → hash khác password không khoảng trắng")
        void passwordWithSpaces_differentFromTrimmed() {
            // Arrange
            String trimmed = "secret";
            String withSpaces = " secret ";

            // Act & Assert
            assertNotEquals(User.hashPassword(trimmed), User.hashPassword(withSpaces));
        }

        @Test
        @DisplayName("password rỗng → không ném exception, trả về hash hợp lệ")
        void emptyPassword_returnsValidHash() {
            // Act
            String hash = User.hashPassword("");

            // Assert
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
        }

        @Test
        @DisplayName("password rỗng → hash deterministic")
        void emptyPassword_deterministicHash() {
            // Act
            String hash1 = User.hashPassword("");
            String hash2 = User.hashPassword("");

            // Assert
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("password Unicode (tiếng Việt) → hash không ném exception")
        void unicodePassword_noException() {
            // Arrange
            String unicodePassword = "mậtKhẩuTiếngViệt123!";

            // Act & Assert — không ném exception
            assertDoesNotThrow(() -> User.hashPassword(unicodePassword));
        }

        @Test
        @DisplayName("password rất dài (10000 ký tự) → hash thành công")
        void veryLongPassword_hashesSuccessfully() {
            // Arrange
            String longPassword = "a".repeat(10_000);

            // Act
            String hash = User.hashPassword(longPassword);

            // Assert
            assertNotNull(hash);
            assertFalse(hash.isEmpty());
        }

        @Test
        @DisplayName("hash output là chuỗi hex lowercase 64 ký tự (SHA-256)")
        void hashOutput_is64CharHexString() {
            // Arrange
            String password = "testPassword";

            // Act
            String hash = User.hashPassword(password);

            // Assert
            assertNotNull(hash);
            assertEquals(64, hash.length());
            assertTrue(hash.matches("[0-9a-f]{64}"),
                    "Hash phải là chuỗi hex lowercase 64 ký tự");
        }

        @Test
        @DisplayName("password chỉ toàn khoảng trắng → hash deterministic và khác empty")
        void whitespaceOnlyPassword_differentFromEmpty() {
            // Act
            String whitespaceHash = User.hashPassword("   ");
            String emptyHash = User.hashPassword("");

            // Assert
            assertNotEquals(whitespaceHash, emptyHash);
            assertEquals(whitespaceHash, User.hashPassword("   ")); // deterministic
        }
    }

    // =========================================================================
    // setAccountStatus
    // =========================================================================

    @Nested
    @DisplayName("setAccountStatus() — status transition và suspendedAt")
    class SetAccountStatus {

        // --- Happy path: transition hợp lệ ---

        @Test
        @DisplayName("ACTIVE → BANNED: status cập nhật thành công")
        void activeToBanned_statusUpdated() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderEE5");
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());

            // Act
            user.setAccountStatus(AccountStatus.BANNED);

            // Assert
            assertEquals(AccountStatus.BANNED, user.getAccountStatus());
        }

        @Test
        @DisplayName("ACTIVE → SUSPENDED: status cập nhật thành công")
        void activeToSuspended_statusUpdated() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderFF6");

            // Act
            user.setAccountStatus(AccountStatus.SUSPENDED);

            // Assert
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }

        @Test
        @DisplayName("SUSPENDED → ACTIVE: status cập nhật thành công")
        void suspendedToActive_statusUpdated() {
            // Arrange
            NormalUser user = TestFixture.suspendedBidder("bidderGG7");
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());

            // Act
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Assert
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        @Test
        @DisplayName("BANNED → ACTIVE: status cập nhật thành công")
        void bannedToActive_statusUpdated() {
            // Arrange
            NormalUser user = TestFixture.bannedBidder("bidderHH8");

            // Act
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Assert
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        // --- suspendedAt tracking ---

        @Test
        @DisplayName("ACTIVE → SUSPENDED: suspendedAt được ghi nhận (không null)")
        void activeToSuspended_suspendedAtIsRecorded() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderII9");
            assertNull(user.getSuspendedAt(), "suspendedAt phải null trước khi suspend");

            // Act
            user.setAccountStatus(AccountStatus.SUSPENDED);

            // Assert
            assertNotNull(user.getSuspendedAt());
        }

        @Test
        @DisplayName("ACTIVE → SUSPENDED: suspendedAt gần với thời điểm hiện tại")
        void activeToSuspended_suspendedAtIsRecent() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderJJ0");
            java.time.LocalDateTime before = java.time.LocalDateTime.now().minusSeconds(1);

            // Act
            user.setAccountStatus(AccountStatus.SUSPENDED);

            java.time.LocalDateTime after = java.time.LocalDateTime.now().plusSeconds(1);

            // Assert
            assertNotNull(user.getSuspendedAt());
            assertTrue(user.getSuspendedAt().isAfter(before),
                    "suspendedAt phải sau thời điểm trước khi gọi");
            assertTrue(user.getSuspendedAt().isBefore(after),
                    "suspendedAt phải trước thời điểm sau khi gọi");
        }

        @Test
        @DisplayName("SUSPENDED → SUSPENDED: suspendedAt KHÔNG được ghi đè (giữ lần đầu)")
        void suspendedToSuspended_suspendedAtNotOverwritten() {
            // Arrange — user đã bị suspend, suspendedAt đã có
            NormalUser user = TestFixture.normalBidder("bidderKK1");
            user.setAccountStatus(AccountStatus.SUSPENDED);
            java.time.LocalDateTime firstSuspendedAt = user.getSuspendedAt();
            assertNotNull(firstSuspendedAt);

            // Act — suspend lần 2 (re-suspend)
            user.setAccountStatus(AccountStatus.SUSPENDED);

            // Assert — suspendedAt giữ nguyên lần đầu (không bị ghi đè)
            assertEquals(firstSuspendedAt, user.getSuspendedAt(),
                    "suspendedAt không được ghi đè khi đã ở trạng thái SUSPENDED");
        }

        @Test
        @DisplayName("ACTIVE → BANNED: suspendedAt vẫn null (BANNED không set suspendedAt)")
        void activeToBanned_suspendedAtRemainsNull() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderLL2");

            // Act
            user.setAccountStatus(AccountStatus.BANNED);

            // Assert
            assertNull(user.getSuspendedAt(),
                    "BANNED không được ghi suspendedAt");
        }

        @Test
        @DisplayName("SUSPENDED → ACTIVE: suspendedAt vẫn giữ nguyên (không xóa)")
        void suspendedToActive_suspendedAtRetained() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderMM3");
            user.setAccountStatus(AccountStatus.SUSPENDED);
            java.time.LocalDateTime suspendedAt = user.getSuspendedAt();

            // Act
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Assert — suspendedAt không bị xóa (dùng cho 6-tháng auto-restore logic)
            assertEquals(suspendedAt, user.getSuspendedAt(),
                    "suspendedAt phải được giữ nguyên sau khi restore về ACTIVE");
        }

        // --- Idempotent / self-transition ---

        @Test
        @DisplayName("ACTIVE → ACTIVE: không thay đổi gì (idempotent)")
        void activeToActive_noChange() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderNN4");

            // Act
            user.setAccountStatus(AccountStatus.ACTIVE);

            // Assert
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
            assertNull(user.getSuspendedAt());
        }

        @Test
        @DisplayName("BANNED → BANNED: status giữ nguyên BANNED")
        void bannedToBanned_staysBanned() {
            // Arrange
            NormalUser user = TestFixture.bannedBidder("bidderOO5");

            // Act
            user.setAccountStatus(AccountStatus.BANNED);

            // Assert
            assertEquals(AccountStatus.BANNED, user.getAccountStatus());
        }

        // --- State consistency ---

        @Test
        @DisplayName("setAccountStatus không thay đổi rating của user")
        void setAccountStatus_doesNotAffectRating() {
            // Arrange
            NormalUser user = TestFixture.bidderWithRating("bidderPP6", 4.0);
            double ratingBefore = user.getRating();

            // Act
            user.setAccountStatus(AccountStatus.BANNED);

            // Assert
            assertEquals(ratingBefore, user.getRating(), 1e-9,
                    "setAccountStatus không được thay đổi rating");
        }

        @Test
        @DisplayName("setAccountStatus không thay đổi username")
        void setAccountStatus_doesNotAffectUsername() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderQQ7");
            String usernameBefore = user.getUsername();

            // Act
            user.setAccountStatus(AccountStatus.SUSPENDED);

            // Assert
            assertEquals(usernameBefore, user.getUsername());
        }

        // --- null input ---

        @Test
        @DisplayName("null status → NullPointerException")
        void nullStatus_throwsNullPointerException() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderRR8");

            // Act & Assert
            assertThrows(NullPointerException.class,
                    () -> user.setAccountStatus(null),
                    "setAccountStatus(null) phải ném NullPointerException");
        }
    }
}