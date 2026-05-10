package com.group13.auction.user;

import com.group13.auction.TestFixture;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link NormalUser}.
 *
 * <p>Tập trung vào 4 nhóm behavior:
 * <ul>
 *   <li>{@code lockDeposit}       — cộng thêm lockedDeposit, bao gồm double lock.</li>
 *   <li>{@code unlockDeposit}     — giảm lockedDeposit, clamp không âm, unlock vượt quá.</li>
 *   <li>{@code getAvailableBalance} — invariant = balance − lockedDeposit.</li>
 *   <li>{@code markPenalized}     — set flag một chiều, idempotent.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network, không filesystem.
 * Dùng object thật từ {@link TestFixture}.
 */
@DisplayName("NormalUser")
class NormalUserTest {

    // =========================================================================
    // lockDeposit
    // =========================================================================

    @Nested
    @DisplayName("lockDeposit() — tăng lockedDeposit")
    class LockDeposit {

        private NormalUser user;

        @BeforeEach
        void setUp() {
            // balance = 1_000_000, lockedDeposit = 0
            user = TestFixture.bidderWithBalance("bidderAA1", 1_000_000L);
        }

        // --- Happy path ---

        @Test
        @DisplayName("lock amount hợp lệ → lockedDeposit tăng đúng lượng")
        void validAmount_lockedDepositIncreases() {
            // Arrange
            long amount = 200_000L;

            // Act
            user.lockDeposit(amount);

            // Assert
            assertEquals(200_000L, user.getLockedDeposit());
        }

        @Test
        @DisplayName("lock amount hợp lệ → balance KHÔNG thay đổi")
        void validAmount_balanceUnchanged() {
            // Arrange
            long balanceBefore = user.getBalance();

            // Act
            user.lockDeposit(200_000L);

            // Assert
            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("lock đúng bằng toàn bộ available balance → lockedDeposit = balance")
        void lockExactlyAvailableBalance_lockedEqualsBalance() {
            // Arrange — balance = 1_000_000, lockedDeposit = 0
            long fullBalance = user.getBalance();

            // Act
            user.lockDeposit(fullBalance);

            // Assert
            assertEquals(fullBalance, user.getLockedDeposit());
            assertEquals(0L, user.getAvailableBalance());
        }

        // --- Double lock (hai lần lock cộng dồn) ---

        @Test
        @DisplayName("double lock: hai lần lockDeposit cộng dồn vào lockedDeposit")
        void doubleLock_lockedDepositAccumulates() {
            // Arrange
            long first  = 300_000L;
            long second = 200_000L;

            // Act
            user.lockDeposit(first);
            user.lockDeposit(second);

            // Assert
            assertEquals(first + second, user.getLockedDeposit());
        }

        @Test
        @DisplayName("double lock: balance vẫn không thay đổi sau hai lần lock")
        void doubleLock_balanceStillUnchanged() {
            // Arrange
            long balanceBefore = user.getBalance();

            // Act
            user.lockDeposit(300_000L);
            user.lockDeposit(200_000L);

            // Assert
            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("double lock: available balance giảm đúng tổng hai lần lock")
        void doubleLock_availableBalanceDecreasesByTotal() {
            // Arrange
            long balanceBefore = user.getBalance();
            long first  = 300_000L;
            long second = 200_000L;

            // Act
            user.lockDeposit(first);
            user.lockDeposit(second);

            // Assert
            assertEquals(balanceBefore - first - second, user.getAvailableBalance());
        }

        // --- Zero amount ---

        @Test
        @DisplayName("lock zero → lockedDeposit không thay đổi")
        void zeroAmount_lockedDepositUnchanged() {
            // Arrange
            long lockedBefore = user.getLockedDeposit();

            // Act
            user.lockDeposit(0L);

            // Assert
            assertEquals(lockedBefore, user.getLockedDeposit());
        }

        @Test
        @DisplayName("lock zero → available balance không thay đổi")
        void zeroAmount_availableBalanceUnchanged() {
            // Arrange
            long availableBefore = user.getAvailableBalance();

            // Act
            user.lockDeposit(0L);

            // Assert
            assertEquals(availableBefore, user.getAvailableBalance());
        }

        // --- Negative amount (model không guard — document behavior) ---

        @Test
        @DisplayName("lock negative → lockedDeposit giảm (AtomicLong.addAndGet âm)")
        void negativeAmount_lockedDepositDecreases() {
            // Arrange — lock trước để có lockedDeposit > 0
            user.lockDeposit(500_000L);
            long lockedBefore = user.getLockedDeposit();

            // Act — model không validate âm: addAndGet(-x) làm giảm
            user.lockDeposit(-100_000L);

            // Assert — document actual behavior của implementation
            assertEquals(lockedBefore - 100_000L, user.getLockedDeposit());
        }

        // --- Vượt available balance (model không guard — WalletService guard) ---

        @Test
        @DisplayName("lock vượt available balance → lockedDeposit vẫn tăng (model không guard)")
        void lockExceedingAvailableBalance_lockedDepositIncreasesAnyway() {
            // Arrange — balance = 1_000_000, lock vượt quá
            long excessAmount = 2_000_000L;

            // Act — model không throw; WalletService chịu trách nhiệm kiểm tra
            user.lockDeposit(excessAmount);

            // Assert — lockedDeposit tăng đúng amount, available balance âm
            assertEquals(excessAmount, user.getLockedDeposit());
            assertTrue(user.getAvailableBalance() < 0L,
                    "available balance phải âm khi lock vượt balance (model không guard)");
        }

        // --- Long.MAX_VALUE ---

        @Test
        @DisplayName("lock Long.MAX_VALUE → lockedDeposit = Long.MAX_VALUE (overflow wrap nếu dùng addAndGet)")
        void maxLongAmount_lockedDepositSetToMaxLong() {
            // Arrange — lockedDeposit hiện = 0
            // Act
            user.lockDeposit(Long.MAX_VALUE);

            // Assert — document behavior: addAndGet(MAX_VALUE) từ 0 = MAX_VALUE
            assertEquals(Long.MAX_VALUE, user.getLockedDeposit());
        }
    }

    // =========================================================================
    // unlockDeposit
    // =========================================================================

    @Nested
    @DisplayName("unlockDeposit() — giảm lockedDeposit, clamp tại 0")
    class UnlockDeposit {

        private NormalUser user;

        @BeforeEach
        void setUp() {
            // balance = 1_000_000, lockedDeposit = 0
            user = TestFixture.bidderWithBalance("bidderBB2", 1_000_000L);
            user.lockDeposit(600_000L); // lockedDeposit = 600_000
        }

        // --- Happy path ---

        @Test
        @DisplayName("unlock amount hợp lệ → lockedDeposit giảm đúng lượng")
        void validAmount_lockedDepositDecreases() {
            // Arrange
            long unlockAmount = 200_000L;
            long lockedBefore = user.getLockedDeposit(); // 600_000

            // Act
            user.unlockDeposit(unlockAmount);

            // Assert
            assertEquals(lockedBefore - unlockAmount, user.getLockedDeposit());
        }

        @Test
        @DisplayName("unlock amount hợp lệ → balance KHÔNG thay đổi")
        void validAmount_balanceUnchanged() {
            // Arrange
            long balanceBefore = user.getBalance();

            // Act
            user.unlockDeposit(200_000L);

            // Assert
            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("unlock đúng bằng lockedDeposit → lockedDeposit về 0")
        void unlockExactLocked_lockedDepositBecomesZero() {
            // Arrange
            long fullLocked = user.getLockedDeposit(); // 600_000

            // Act
            user.unlockDeposit(fullLocked);

            // Assert
            assertEquals(0L, user.getLockedDeposit());
        }

        @Test
        @DisplayName("unlock đúng bằng lockedDeposit → available balance phục hồi về balance")
        void unlockExactLocked_availableBalanceRestored() {
            // Arrange
            long fullLocked = user.getLockedDeposit();
            long balance    = user.getBalance();

            // Act
            user.unlockDeposit(fullLocked);

            // Assert
            assertEquals(balance, user.getAvailableBalance());
        }

        // --- Unlock invalid amount: vượt quá lockedDeposit → clamp về 0 ---

        @Test
        @DisplayName("unlock vượt quá lockedDeposit → clamp về 0 (không âm)")
        void unlockExceedsLocked_clampedToZero() {
            // Arrange — lockedDeposit = 600_000
            long excessAmount = 1_000_000L;

            // Act
            user.unlockDeposit(excessAmount);

            // Assert — Math.max(0, 600_000 - 1_000_000) = 0
            assertEquals(0L, user.getLockedDeposit());
        }

        @Test
        @DisplayName("unlock Long.MAX_VALUE khi có lockedDeposit → clamp về 0")
        void unlockMaxLong_clampedToZero() {
            // Arrange — lockedDeposit = 600_000
            // Act
            user.unlockDeposit(Long.MAX_VALUE);

            // Assert
            assertEquals(0L, user.getLockedDeposit());
        }

        @Test
        @DisplayName("unlock khi lockedDeposit = 0 → vẫn giữ 0 (không âm)")
        void unlockWhenAlreadyZero_staysZero() {
            // Arrange — reset về 0
            user.unlockDeposit(user.getLockedDeposit());
            assertEquals(0L, user.getLockedDeposit());

            // Act
            user.unlockDeposit(100_000L);

            // Assert
            assertEquals(0L, user.getLockedDeposit());
        }

        // --- Zero amount ---

        @Test
        @DisplayName("unlock zero → lockedDeposit không thay đổi")
        void zeroAmount_lockedDepositUnchanged() {
            // Arrange
            long lockedBefore = user.getLockedDeposit();

            // Act
            user.unlockDeposit(0L);

            // Assert
            assertEquals(lockedBefore, user.getLockedDeposit());
        }

        @Test
        @DisplayName("unlock zero → available balance không thay đổi")
        void zeroAmount_availableBalanceUnchanged() {
            // Arrange
            long availableBefore = user.getAvailableBalance();

            // Act
            user.unlockDeposit(0L);

            // Assert
            assertEquals(availableBefore, user.getAvailableBalance());
        }

        // --- Lock rồi unlock: tính đối xứng ---

        @Test
        @DisplayName("lock rồi unlock cùng amount → lockedDeposit và available balance khôi phục")
        void lockThenUnlockSameAmount_restoresState() {
            // Arrange
            long lockedBefore    = user.getLockedDeposit();
            long availableBefore = user.getAvailableBalance();
            long amount          = 100_000L;

            // Act
            user.lockDeposit(amount);
            user.unlockDeposit(amount);

            // Assert
            assertEquals(lockedBefore, user.getLockedDeposit());
            assertEquals(availableBefore, user.getAvailableBalance());
        }

        @Test
        @DisplayName("unlock từng phần nhiều lần → lockedDeposit giảm đúng tổng")
        void partialUnlockMultipleTimes_accumulatesCorrectly() {
            // Arrange — lockedDeposit = 600_000
            // Act
            user.unlockDeposit(200_000L);
            user.unlockDeposit(150_000L);
            user.unlockDeposit(100_000L);

            // Assert — 600_000 - 200_000 - 150_000 - 100_000 = 150_000
            assertEquals(150_000L, user.getLockedDeposit());
        }

        // --- Negative amount (document behavior) ---

        @Test
        @DisplayName("unlock negative → lockedDeposit tăng (Math.max(0, current - (-x)) = current + x)")
        void negativeAmount_lockedDepositIncreases() {
            // Arrange — lockedDeposit = 600_000
            long lockedBefore = user.getLockedDeposit();

            // Act — Math.max(0, 600_000 - (-100_000)) = 700_000
            user.unlockDeposit(-100_000L);

            // Assert — document actual behavior
            assertEquals(lockedBefore + 100_000L, user.getLockedDeposit());
        }
    }

    // =========================================================================
    // getAvailableBalance
    // =========================================================================

    @Nested
    @DisplayName("getAvailableBalance() — invariant balance − lockedDeposit")
    class GetAvailableBalance {

        // --- Happy path ---

        @Test
        @DisplayName("không có lock → availableBalance = balance")
        void noLock_availableEqualsBalance() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderCC3", 500_000L);

            // Act & Assert
            assertEquals(500_000L, user.getAvailableBalance());
        }

        @Test
        @DisplayName("sau lock → availableBalance = balance − lockedDeposit")
        void afterLock_availableIsBalanceMinusLocked() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderDD4", 1_000_000L);
            user.lockDeposit(300_000L);

            // Act & Assert
            assertEquals(700_000L, user.getAvailableBalance());
        }

        @Test
        @DisplayName("sau unlock → availableBalance tăng đúng lượng unlock")
        void afterUnlock_availableIncreasesCorrectly() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderEE5", 1_000_000L);
            user.lockDeposit(400_000L);
            long availableAfterLock = user.getAvailableBalance(); // 600_000

            // Act
            user.unlockDeposit(200_000L);

            // Assert
            assertEquals(availableAfterLock + 200_000L, user.getAvailableBalance());
        }

        // --- Consistency qua nhiều thao tác ---

        @Test
        @DisplayName("consistency: availableBalance luôn = balance − lockedDeposit sau mọi thao tác")
        void consistency_availableAlwaysEqualsBalanceMinusLocked() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderFF6", 2_000_000L);

            // Act & Assert — kiểm tra sau mỗi thao tác
            user.lockDeposit(500_000L);
            assertEquals(user.getBalance() - user.getLockedDeposit(), user.getAvailableBalance(),
                    "sau lock 1");

            user.lockDeposit(300_000L);
            assertEquals(user.getBalance() - user.getLockedDeposit(), user.getAvailableBalance(),
                    "sau lock 2");

            user.unlockDeposit(200_000L);
            assertEquals(user.getBalance() - user.getLockedDeposit(), user.getAvailableBalance(),
                    "sau unlock 1");

            user.setBalance(3_000_000L);
            assertEquals(user.getBalance() - user.getLockedDeposit(), user.getAvailableBalance(),
                    "sau setBalance");
        }

        @Test
        @DisplayName("setBalance thay đổi → availableBalance cập nhật ngay")
        void setBalance_updatesAvailableBalance() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderGG7", 1_000_000L);
            user.lockDeposit(200_000L);

            // Act
            user.setBalance(5_000_000L);

            // Assert
            assertEquals(5_000_000L - 200_000L, user.getAvailableBalance());
        }

        // --- Edge: balance = 0 ---

        @Test
        @DisplayName("balance = 0, không lock → availableBalance = 0")
        void zeroBalance_noLock_availableIsZero() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderHH8", 0L);

            // Act & Assert
            assertEquals(0L, user.getAvailableBalance());
        }

        @Test
        @DisplayName("lock toàn bộ balance → availableBalance = 0")
        void lockAll_availableIsZero() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderII9", 1_000_000L);

            // Act
            user.lockDeposit(user.getBalance());

            // Assert
            assertEquals(0L, user.getAvailableBalance());
        }

        // --- restoreBalances ---

        @Test
        @DisplayName("restoreBalances → availableBalance = balance − lockedDeposit mới")
        void restoreBalances_availableRecalculated() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderJJ0", 1_000_000L);
            user.lockDeposit(500_000L);

            // Act
            user.restoreBalances(800_000L, 100_000L);

            // Assert
            assertEquals(700_000L, user.getAvailableBalance());
        }
    }

    // =========================================================================
    // markPenalized
    // =========================================================================

    @Nested
    @DisplayName("markPenalized() — set flag một chiều")
    class MarkPenalized {

        // --- Happy path ---

        @Test
        @DisplayName("user chưa bị penalize → sau markPenalized flag = true")
        void notPenalized_afterMark_flagIsTrue() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderKK1");
            assertFalse(user.isHasEverBeenPenalized(), "flag phải false trước khi gọi");

            // Act
            user.markPenalized();

            // Assert
            assertTrue(user.isHasEverBeenPenalized());
        }

        // --- Idempotent: gọi nhiều lần vẫn true ---

        @Test
        @DisplayName("gọi markPenalized hai lần → flag vẫn true (idempotent)")
        void markPenalizedTwice_flagRemainsTrue() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderLL2");

            // Act
            user.markPenalized();
            user.markPenalized();

            // Assert
            assertTrue(user.isHasEverBeenPenalized());
        }

        @Test
        @DisplayName("gọi markPenalized nhiều lần → flag luôn true (idempotent N lần)")
        void markPenalizedManyTimes_flagAlwaysTrue() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderMM3");

            // Act
            for (int i = 0; i < 10; i++) {
                user.markPenalized();
            }

            // Assert
            assertTrue(user.isHasEverBeenPenalized());
        }

        // --- State isolation: không ảnh hưởng field khác ---

        @Test
        @DisplayName("markPenalized không thay đổi balance")
        void markPenalized_doesNotAffectBalance() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderNN4", 500_000L);
            long balanceBefore = user.getBalance();

            // Act
            user.markPenalized();

            // Assert
            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("markPenalized không thay đổi lockedDeposit")
        void markPenalized_doesNotAffectLockedDeposit() {
            // Arrange
            NormalUser user = TestFixture.bidderWithBalance("bidderOO5", 1_000_000L);
            user.lockDeposit(200_000L);
            long lockedBefore = user.getLockedDeposit();

            // Act
            user.markPenalized();

            // Assert
            assertEquals(lockedBefore, user.getLockedDeposit());
        }

        @Test
        @DisplayName("markPenalized không thay đổi rating")
        void markPenalized_doesNotAffectRating() {
            // Arrange
            NormalUser user = TestFixture.bidderWithRating("bidderPP6", 4.5);
            double ratingBefore = user.getRating();

            // Act
            user.markPenalized();

            // Assert
            assertEquals(ratingBefore, user.getRating(), 1e-9);
        }

        @Test
        @DisplayName("markPenalized không thay đổi account status")
        void markPenalized_doesNotAffectAccountStatus() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderQQ7");
            var statusBefore = user.getAccountStatus();

            // Act
            user.markPenalized();

            // Assert
            assertEquals(statusBefore, user.getAccountStatus());
        }

        @Test
        @DisplayName("markPenalized không thay đổi hasEverBeenRestored")
        void markPenalized_doesNotAffectRestoredFlag() {
            // Arrange
            NormalUser user = TestFixture.normalBidder("bidderRR8");
            boolean restoredBefore = user.isHasEverBeenRestored();

            // Act
            user.markPenalized();

            // Assert
            assertEquals(restoredBefore, user.isHasEverBeenRestored());
        }

        // --- Penalized flag từ reconstitute ---

        @Test
        @DisplayName("user reconstitute với hasEverBeenPenalized=true → flag đã true, không cần gọi markPenalized")
        void reconstituteWithPenalizedTrue_flagAlreadyTrue() {
            // Arrange — dùng fixture chuyên dụng
            NormalUser user = TestFixture.penalizedBidder("bidderSS9");

            // Act & Assert — không gọi markPenalized
            assertTrue(user.isHasEverBeenPenalized());
        }

        @Test
        @DisplayName("penalizedBidder gọi thêm markPenalized → flag vẫn true")
        void reconstitutePenalized_thenMarkAgain_flagStillTrue() {
            // Arrange
            NormalUser user = TestFixture.penalizedBidder("bidderTT0");

            // Act
            user.markPenalized();

            // Assert
            assertTrue(user.isHasEverBeenPenalized());
        }

        // --- Flag độc lập giữa các user instance ---

        @Test
        @DisplayName("markPenalized trên user A không ảnh hưởng user B")
        void markPenalized_doesNotAffectOtherInstances() {
            // Arrange
            NormalUser userA = TestFixture.normalBidder("bidderUU1");
            NormalUser userB = TestFixture.normalBidder("bidderVV2");

            // Act
            userA.markPenalized();

            // Assert
            assertTrue(userA.isHasEverBeenPenalized());
            assertFalse(userB.isHasEverBeenPenalized(),
                    "flag của userB phải độc lập với userA");
        }
    }
}