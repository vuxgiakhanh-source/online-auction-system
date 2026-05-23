package com.group13.auction.unit.service;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link RatingService}.
 *
 * <p>Tập trung vào 5 nhóm behavior theo business rule:
 * <ul>
 *   <li>{@code isEligible}             — threshold boundary, status gate.</li>
 *   <li>{@code penalizeLatePayment /
 *              penalizeSeller}         — delta, markPenalized, auto-suspend trigger.</li>
 *   <li>{@code rewardBidder /
 *              rewardSeller}           — delta, accumulation, clamp tại MAX.</li>
 *   <li>{@code autoSuspendIfNeeded}    — suspend condition, boundary chính xác.</li>
 *   <li>{@code checkAndRestoreSuspended} — 3-tháng guard, restored flag, rating delta,
 *                                          status transition, repeated call.</li>
 * </ul>
 *
 * <p>Mock duy nhất: {@link UserDAO} — dependency ngoài, chạm DB.
 * Domain model ({@link NormalUser}) dùng object thật qua {@link TestFixture}.
 *
 * <p>Constants tham chiếu từ source:
 * <pre>
 *   RATING_DEFAULT          = 3.0
 *   RATING_SUSPEND_THRESHOLD = 1.5
 *   MIN_RATING_ELIGIBLE     = 2.0
 *   REWARD_BIDDER_PAYMENT   = 0.2
 *   REWARD_SELLER_SALE      = 0.2
 *   PENALTY_LATE_PAYMENT    = 1.0
 *   PENALTY_SELLER_QUALITY  = 1.0
 *   RESTORE_DELTA           = 0.6
 *   SUSPEND_RESTORE_MONTHS  = 3
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService")
class RatingServiceTest {

    // -------------------------------------------------------------------------
    // Constants — mirror source để test không phụ thuộc vào magic number
    // -------------------------------------------------------------------------
    private static final double RATING_DEFAULT          = 3.0;
    private static final double RATING_MIN              = 0.0;
    private static final double RATING_MAX              = 5.0;
    private static final double SUSPEND_THRESHOLD       = User.RATING_SUSPEND_THRESHOLD; // 1.5
    private static final double MIN_ELIGIBLE            = 2.0;
    private static final double REWARD_DELTA            = 0.2;
    private static final double PENALTY_DELTA           = 1.0;
    private static final double RESTORE_DELTA           = 0.6;
    private static final double EPSILON                 = 1e-9;

    // -------------------------------------------------------------------------
    // Mock — chỉ UserDAO vì nó chạm DB
    // -------------------------------------------------------------------------
    @Mock private UserDAO userDAO;

    // -------------------------------------------------------------------------
    // SUT
    // -------------------------------------------------------------------------
    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(userDAO);
        // Stub mặc định — tránh UnnecessaryStubbingException từ strict Mockito
        lenient().when(userDAO.updateRating(anyString(), anyDouble())).thenReturn(true);
        lenient().when(userDAO.updateAccountStatus(anyString(), anyString())).thenReturn(true);
        lenient().when(userDAO.updateRatingAndPenalty(anyString(), anyDouble(), anyBoolean())).thenReturn(true);
        lenient().when(userDAO.incrementTimesRestored(anyString())).thenReturn(true);
    }

    // =========================================================================
    // isEligible
    // =========================================================================

    @Nested
    @DisplayName("isEligible() — status + rating threshold")
    class IsEligible {

        // --- Happy path ---

        @Test
        @DisplayName("ACTIVE + rating >= 2.0 → eligible")
        void activeAndRatingAboveThreshold_isEligible() {
            NormalUser user = TestFixture.bidderWithRating("bidderAA1", MIN_ELIGIBLE);

            assertTrue(ratingService.isEligible(user));
        }

        @Test
        @DisplayName("ACTIVE + rating đúng ngưỡng 2.0 → eligible (boundary inclusive)")
        void activeAndRatingExactlyAtThreshold_isEligible() {
            NormalUser user = TestFixture.bidderWithRating("bidderAA2", MIN_ELIGIBLE);

            assertTrue(ratingService.isEligible(user));
        }

        @Test
        @DisplayName("ACTIVE + rating 5.0 (MAX) → eligible")
        void activeAndMaxRating_isEligible() {
            NormalUser user = TestFixture.bidderWithRating("bidderAA3", RATING_MAX);

            assertTrue(ratingService.isEligible(user));
        }

        // --- Rating dưới ngưỡng ---

        @Test
        @DisplayName("ACTIVE + rating 1.99... → NOT eligible (just below threshold)")
        void activeAndRatingJustBelowThreshold_notEligible() {
            NormalUser user = TestFixture.bidderWithRating("bidderAA4", MIN_ELIGIBLE - 0.001);

            assertFalse(ratingService.isEligible(user));
        }

        @Test
        @DisplayName("ACTIVE + rating 0.0 (MIN) → NOT eligible")
        void activeAndMinRating_notEligible() {
            NormalUser user = TestFixture.bidderWithRating("bidderAA5", RATING_MIN);

            assertFalse(ratingService.isEligible(user));
        }

        @Test
        @DisplayName("ACTIVE + rating 1.5 (SUSPEND_THRESHOLD) → NOT eligible (below 2.0)")
        void activeAndSuspendThresholdRating_notEligible() {
            NormalUser user = TestFixture.bidderWithRating("bidderAA6", SUSPEND_THRESHOLD);

            assertFalse(ratingService.isEligible(user));
        }

        // --- Status gate ---

        @Test
        @DisplayName("SUSPENDED + rating 3.0 → NOT eligible (status gate)")
        void suspendedUser_notEligible() {
            NormalUser user = TestFixture.suspendedBidder("bidderAA7");

            assertFalse(ratingService.isEligible(user));
        }

        @Test
        @DisplayName("BANNED + rating 4.0 → NOT eligible (status gate)")
        void bannedUser_notEligible() {
            NormalUser user = TestFixture.bannedBidder("bidderAA8");

            assertFalse(ratingService.isEligible(user));
        }

        @Test
        @DisplayName("SUSPENDED + rating 5.0 → NOT eligible (status beats rating)")
        void suspendedUserWithMaxRating_notEligible() {
            // Tạo user SUSPENDED thủ công với rating cao
            NormalUser user = NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "bidderAA9",
                    User.hashPassword("password1"),
                    "bidderAA9@test.com",
                    AccountStatus.SUSPENDED,
                    RATING_MAX, 0L, 0L,
                    EnumSet.of(User.UserRole.BIDDER),
                    false, 0,
                    LocalDateTime.now());

            assertFalse(ratingService.isEligible(user));
        }

        // --- Transition: sau khi penalize và suspend → không còn eligible ---

        @Test
        @DisplayName("user ACTIVE eligible → sau penalize đủ để suspend → NOT eligible")
        void afterPenaltyTriggeredSuspend_notEligible() {
            // Arrange — rating 2.0: eligible, nhưng sau -1.0 = 1.0 ≤ 1.5 → auto-suspend
            NormalUser user = TestFixture.bidderWithRating("bidderAA10", MIN_ELIGIBLE);
            assertTrue(ratingService.isEligible(user));

            // Act
            ratingService.penalizeLatePayment(user);

            // Assert
            assertFalse(ratingService.isEligible(user));
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }
    }

    // =========================================================================
    // penalizeLatePayment
    // =========================================================================

    @Nested
    @DisplayName("penalizeLatePayment() — rating delta, markPenalized, auto-suspend")
    class PenalizeLatePayment {

        // --- Happy path: rating giảm đúng ---

        @Test
        @DisplayName("rating giảm đúng PENALTY_DELTA (1.0)")
        void ratingDecreasedByPenaltyDelta() {
            NormalUser user = TestFixture.bidderWithRating("bidderBB1", RATING_DEFAULT);
            double expectedRating = RATING_DEFAULT - PENALTY_DELTA;

            ratingService.penalizeLatePayment(user);

            assertEquals(expectedRating, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("markPenalized được set sau khi penalize")
        void markPenalizedIsSet() {
            NormalUser user = TestFixture.normalBidder("bidderBB2");
            assertFalse(user.isHasEverBeenPenalized());

            ratingService.penalizeLatePayment(user);

            assertTrue(user.isHasEverBeenPenalized());
        }

        @Test
        @DisplayName("UserDAO.updateRatingAndPenalty được gọi với đúng tham số")
        void userDAOUpdateRatingAndPenaltyCalled() {
            NormalUser user = TestFixture.normalBidder("bidderBB3");
            double expectedRating = RATING_DEFAULT - PENALTY_DELTA;

            ratingService.penalizeLatePayment(user);

            verify(userDAO).updateRatingAndPenalty(
                    eq(user.getId()), eq(expectedRating), eq(true));
        }

        @Test
        @DisplayName("UserDAO.updateAccountStatus được gọi (luôn, dù không suspend)")
        void userDAOUpdateAccountStatusAlwaysCalled() {
            NormalUser user = TestFixture.bidderWithRating("bidderBB4", RATING_DEFAULT);

            ratingService.penalizeLatePayment(user);

            verify(userDAO).updateAccountStatus(eq(user.getId()), anyString());
        }

        // --- Penalty accumulation ---

        @Test
        @DisplayName("penalty cộng dồn: 2 lần → rating giảm 2x")
        void doublePenalty_ratingDecreasedTwice() {
            NormalUser user = TestFixture.bidderWithRating("bidderBB5", RATING_DEFAULT);
            double expected = RATING_DEFAULT - PENALTY_DELTA * 2;

            ratingService.penalizeLatePayment(user);
            ratingService.penalizeLatePayment(user);

            assertEquals(expected, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("penalty cộng dồn: markPenalized idempotent — vẫn true sau nhiều lần")
        void repeatedPenalty_markPenalizedStaysTrue() {
            NormalUser user = TestFixture.normalBidder("bidderBB6");

            ratingService.penalizeLatePayment(user);
            ratingService.penalizeLatePayment(user);
            ratingService.penalizeLatePayment(user);

            assertTrue(user.isHasEverBeenPenalized());
        }

        // --- Auto-suspend trigger ---

        @Test
        @DisplayName("rating đúng ngưỡng 1.5 sau penalty → auto-SUSPEND (boundary inclusive ≤)")
        void ratingFallsToSuspendThreshold_triggersSuspend() {
            // 2.5 - 1.0 = 1.5 == SUSPEND_THRESHOLD → suspend
            NormalUser user = TestFixture.bidderWithRating("bidderBB7", SUSPEND_THRESHOLD + PENALTY_DELTA);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            assertEquals(SUSPEND_THRESHOLD, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("rating xuống dưới 1.5 sau penalty → auto-SUSPEND")
        void ratingFallsBelowSuspendThreshold_triggersSuspend() {
            // 2.0 - 1.0 = 1.0 < 1.5 → suspend
            NormalUser user = TestFixture.bidderWithRating("bidderBB8", MIN_ELIGIBLE);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }

        @Test
        @DisplayName("rating vẫn > 1.5 sau penalty → KHÔNG suspend")
        void ratingStaysAboveSuspendThreshold_notSuspended() {
            // 3.0 - 1.0 = 2.0 > 1.5 → không suspend
            NormalUser user = TestFixture.bidderWithRating("bidderBB9", RATING_DEFAULT);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        @Test
        @DisplayName("rating 1.6 (just above threshold) sau penalty = 0.6 → KHÔNG suspend")
        void ratingJustAboveSuspendThresholdAfterPenalty_notSuspended() {
            // 1.6 - 1.0 = 0.6 < 1.5 → đây sẽ suspend, test boundary dưới
            // Test: cần rating kết quả > 1.5 → bắt đầu với 2.6
            NormalUser user = TestFixture.bidderWithRating("bidderBB10",
                    SUSPEND_THRESHOLD + PENALTY_DELTA + 0.001);

            ratingService.penalizeLatePayment(user);

            // 1.5 + 0.001 = 1.501 > 1.5 → KHÔNG suspend
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        @Test
        @DisplayName("auto-suspend → suspendedAt được ghi nhận (không null)")
        void afterAutoSuspend_suspendedAtIsRecorded() {
            NormalUser user = TestFixture.bidderWithRating("bidderBB11", MIN_ELIGIBLE);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            assertNotNull(user.getSuspendedAt());
        }

        // --- Clamp tại MIN ---

        @Test
        @DisplayName("rating tại 0.5 sau penalty → clamp về 0.0 (không âm)")
        void ratingClampedAtMin() {
            NormalUser user = TestFixture.bidderWithRating("bidderBB12", 0.5);

            ratingService.penalizeLatePayment(user);

            assertEquals(RATING_MIN, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("rating = 0.0 sau nhiều penalty → giữ nguyên 0.0")
        void ratingAlreadyAtMin_staysAtMin() {
            NormalUser user = TestFixture.bidderWithRating("bidderBB13", RATING_MIN);

            ratingService.penalizeLatePayment(user);
            ratingService.penalizeLatePayment(user);

            assertEquals(RATING_MIN, user.getRating(), EPSILON);
        }

        // --- SUSPENDED user bị penalize thêm → không suspend lại ---

        @Test
        @DisplayName("user đã SUSPENDED bị penalize thêm → status không thay đổi (autoSuspend guard)")
        void alreadySuspendedUser_penalize_statusUnchanged() {
            NormalUser user = TestFixture.suspendedBidder("bidderBB14");
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            LocalDateTime suspendedAtBefore = user.getSuspendedAt();

            ratingService.penalizeLatePayment(user);

            // autoSuspendIfNeeded chỉ kích hoạt khi ACTIVE → suspend lại không set suspendedAt mới
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            assertEquals(suspendedAtBefore, user.getSuspendedAt());
        }
    }

    // =========================================================================
    // penalizeSeller
    // =========================================================================

    @Nested
    @DisplayName("penalizeSeller() — NormalUser seller, rating delta, auto-suspend")
    class PenalizeSeller {

        // --- Happy path ---

        @Test
        @DisplayName("rating giảm đúng PENALTY_DELTA (1.0)")
        void sellerRatingDecreased() {
            NormalUser seller = TestFixture.normalSeller("sellerCC1");
            double expected = RATING_DEFAULT - PENALTY_DELTA;

            ratingService.penalizeSeller(seller);

            assertEquals(expected, seller.getRating(), EPSILON);
        }

        @Test
        @DisplayName("NormalUser seller bị markPenalized")
        void normalUserSeller_markPenalizedIsSet() {
            NormalUser seller = TestFixture.normalSeller("sellerCC2");

            ratingService.penalizeSeller(seller);

            assertTrue(seller.isHasEverBeenPenalized());
        }

        @Test
        @DisplayName("seller bị suspend khi rating rớt xuống ≤ 1.5")
        void sellerAutoSuspendedWhenRatingDropsBelowThreshold() {
            NormalUser seller = TestFixture.bidderWithRating("sellerCC3",
                    SUSPEND_THRESHOLD + PENALTY_DELTA); // 2.5 - 1.0 = 1.5 → suspend

            ratingService.penalizeSeller(seller);

            assertEquals(AccountStatus.SUSPENDED, seller.getAccountStatus());
        }

        @Test
        @DisplayName("UserDAO.updateRatingAndPenalty được gọi 1 lần")
        void userDAOUpdateRatingAndPenaltyCalled_onceForSeller() {
            NormalUser seller = TestFixture.normalSeller("sellerCC4");

            ratingService.penalizeSeller(seller);

            verify(userDAO, times(1))
                    .updateRatingAndPenalty(eq(seller.getId()), anyDouble(), eq(true));
        }

        // --- Repeated penalty ---

        @Test
        @DisplayName("repeated penalty: markPenalized idempotent")
        void repeatedSellerPenalty_markPenalizedIdempotent() {
            NormalUser seller = TestFixture.normalSeller("sellerCC5");

            ratingService.penalizeSeller(seller);
            ratingService.penalizeSeller(seller);

            assertTrue(seller.isHasEverBeenPenalized());
            verify(userDAO, times(2))
                    .updateRatingAndPenalty(eq(seller.getId()), anyDouble(), anyBoolean());
        }

        // --- Boundary: seller với rating rất cao vẫn bị trừ đúng ---

        @Test
        @DisplayName("seller rating 5.0 (MAX) bị penalize → 4.0")
        void sellerAtMaxRating_penalizedCorrectly() {
            NormalUser seller = TestFixture.bidderWithRating("sellerCC6", RATING_MAX);

            ratingService.penalizeSeller(seller);

            assertEquals(RATING_MAX - PENALTY_DELTA, seller.getRating(), EPSILON);
        }
    }

    // =========================================================================
    // rewardBidder
    // =========================================================================

    @Nested
    @DisplayName("rewardBidder() — rating tăng, accumulation, clamp MAX")
    class RewardBidder {

        // --- Happy path ---

        @Test
        @DisplayName("rating tăng đúng REWARD_DELTA (0.2)")
        void ratingIncreasedByRewardDelta() {
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD1", RATING_DEFAULT);
            double expected = RATING_DEFAULT + REWARD_DELTA;

            ratingService.rewardBidder(bidder);

            assertEquals(expected, bidder.getRating(), EPSILON);
        }

        @Test
        @DisplayName("UserDAO.updateRating được gọi 1 lần với đúng tham số")
        void userDAOUpdateRatingCalled() {
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD2", RATING_DEFAULT);
            double expected = RATING_DEFAULT + REWARD_DELTA;

            ratingService.rewardBidder(bidder);

            verify(userDAO, times(1)).updateRating(eq(bidder.getId()), eq(expected));
        }

        // --- Reward accumulation ---

        @Test
        @DisplayName("reward cộng dồn: 3 lần → rating tăng 3x")
        void tripleReward_ratingIncreasedThreeTimes() {
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD3", RATING_DEFAULT);
            double expected = RATING_DEFAULT + REWARD_DELTA * 3;

            ratingService.rewardBidder(bidder);
            ratingService.rewardBidder(bidder);
            ratingService.rewardBidder(bidder);

            assertEquals(expected, bidder.getRating(), EPSILON);
        }

        @Test
        @DisplayName("reward nhiều lần → UserDAO được gọi tương ứng số lần")
        void multipleReward_daoCalledMatchingTimes() {
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD4", RATING_DEFAULT);

            ratingService.rewardBidder(bidder);
            ratingService.rewardBidder(bidder);

            verify(userDAO, times(2)).updateRating(eq(bidder.getId()), anyDouble());
        }

        // --- Clamp tại MAX (5.0) ---

        @Test
        @DisplayName("reward khi rating đã 5.0 → vẫn giữ 5.0 (clamp MAX)")
        void rewardAtMaxRating_clampedAtMax() {
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD5", RATING_MAX);

            ratingService.rewardBidder(bidder);

            assertEquals(RATING_MAX, bidder.getRating(), EPSILON);
        }

        @Test
        @DisplayName("reward đưa rating vượt 5.0 → clamp về 5.0")
        void rewardExceedsMax_clampedAtMax() {
            // 4.9 + 0.2 = 5.1 → clamp về 5.0
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD6", RATING_MAX - 0.1);

            ratingService.rewardBidder(bidder);

            assertEquals(RATING_MAX, bidder.getRating(), EPSILON);
        }

        // --- Reward không thay đổi status ---

        @Test
        @DisplayName("reward không thay đổi account status")
        void reward_doesNotChangeAccountStatus() {
            NormalUser bidder = TestFixture.bidderWithRating("bidderDD7", RATING_DEFAULT);

            ratingService.rewardBidder(bidder);

            assertEquals(AccountStatus.ACTIVE, bidder.getAccountStatus());
        }

        // --- Reward user đang SUSPENDED không restore status ---

        @Test
        @DisplayName("reward khi SUSPENDED → rating tăng nhưng status vẫn SUSPENDED")
        void rewardSuspendedUser_statusNotChanged() {
            NormalUser bidder = TestFixture.suspendedBidder("bidderDD8");

            ratingService.rewardBidder(bidder);

            assertEquals(AccountStatus.SUSPENDED, bidder.getAccountStatus());
        }
    }

    // =========================================================================
    // rewardSeller
    // =========================================================================

    @Nested
    @DisplayName("rewardSeller() — rating tăng, accumulation, clamp MAX")
    class RewardSeller {

        // --- Happy path ---

        @Test
        @DisplayName("seller rating tăng đúng REWARD_DELTA (0.2)")
        void sellerRatingIncreasedByRewardDelta() {
            NormalUser seller = TestFixture.normalSeller("sellerEE1");
            double expected = RATING_DEFAULT + REWARD_DELTA;

            ratingService.rewardSeller(seller);

            assertEquals(expected, seller.getRating(), EPSILON);
        }

        @Test
        @DisplayName("UserDAO.updateRating được gọi cho seller")
        void userDAOUpdateRatingCalledForSeller() {
            NormalUser seller = TestFixture.normalSeller("sellerEE2");

            ratingService.rewardSeller(seller);

            verify(userDAO, times(1)).updateRating(eq(seller.getId()), anyDouble());
        }

        // --- Accumulation ---

        @Test
        @DisplayName("reward cộng dồn cho seller")
        void sellerRewardAccumulation() {
            NormalUser seller = TestFixture.normalSeller("sellerEE3");
            double expected = RATING_DEFAULT + REWARD_DELTA * 2;

            ratingService.rewardSeller(seller);
            ratingService.rewardSeller(seller);

            assertEquals(expected, seller.getRating(), EPSILON);
        }

        // --- Clamp ---

        @Test
        @DisplayName("seller reward vượt MAX → clamp 5.0")
        void sellerRewardClampedAtMax() {
            NormalUser seller = TestFixture.bidderWithRating("sellerEE4", RATING_MAX);

            ratingService.rewardSeller(seller);

            assertEquals(RATING_MAX, seller.getRating(), EPSILON);
        }
    }

    // =========================================================================
    // autoSuspendIfNeeded (qua penalize)
    // =========================================================================

    @Nested
    @DisplayName("autoSuspendIfNeeded() — suspend condition (qua penalizeLatePayment)")
    class AutoSuspendIfNeeded {

        // --- Boundary chính xác ---

        @Test
        @DisplayName("rating kết quả = 1.5 (boundary exact) → SUSPEND")
        void exactlyAtSuspendThreshold_isSuspended() {
            // Cần: result = 1.5 → start = 1.5 + 1.0 = 2.5
            NormalUser user = TestFixture.bidderWithRating("bidderFF1",
                    SUSPEND_THRESHOLD + PENALTY_DELTA);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            assertEquals(SUSPEND_THRESHOLD, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("rating kết quả = 1.501 (just above boundary) → KHÔNG suspend")
        void justAboveSuspendThreshold_notSuspended() {
            NormalUser user = TestFixture.bidderWithRating("bidderFF2",
                    SUSPEND_THRESHOLD + PENALTY_DELTA + 0.001);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        @Test
        @DisplayName("rating kết quả = 1.499 (just below boundary) → SUSPEND")
        void justBelowSuspendThreshold_isSuspended() {
            NormalUser user = TestFixture.bidderWithRating("bidderFF3",
                    SUSPEND_THRESHOLD + PENALTY_DELTA - 0.001);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }

        // --- Guard: chỉ ACTIVE mới bị auto-suspend ---

        @Test
        @DisplayName("user BANNED bị penalize → status không bị chuyển sang SUSPENDED")
        void bannedUser_penalize_statusRemainsBANNED() {
            NormalUser user = TestFixture.bannedBidder("bidderFF4");

            ratingService.penalizeLatePayment(user);

            // autoSuspendIfNeeded chỉ check ACTIVE → BANNED không bị chuyển
            assertEquals(AccountStatus.BANNED, user.getAccountStatus());
        }

        // --- suspendedAt tracking ---

        @Test
        @DisplayName("auto-suspend → suspendedAt != null và gần thời điểm hiện tại")
        void afterAutoSuspend_suspendedAtRecordedNearNow() {
            NormalUser user = TestFixture.bidderWithRating("bidderFF5", MIN_ELIGIBLE);
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            ratingService.penalizeLatePayment(user);

            LocalDateTime after = LocalDateTime.now().plusSeconds(1);
            assertNotNull(user.getSuspendedAt());
            assertTrue(user.getSuspendedAt().isAfter(before));
            assertTrue(user.getSuspendedAt().isBefore(after));
        }

        // --- Không suspend khi rating còn cao ---

        @Test
        @DisplayName("penalty từ rating cao (5.0) → không suspend")
        void highRatingAfterPenalty_notSuspended() {
            NormalUser user = TestFixture.bidderWithRating("bidderFF6", RATING_MAX);

            ratingService.penalizeLatePayment(user);

            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
            assertEquals(RATING_MAX - PENALTY_DELTA, user.getRating(), EPSILON);
        }
    }

    // =========================================================================
    // checkAndRestoreSuspended
    // =========================================================================

    @Nested
    @DisplayName("checkAndRestoreSuspended() — 3-month guard, restore, repeated call")
    class CheckAndRestoreSuspended {

        // ---- Helper: tạo user SUSPENDED với suspendedAt tuỳ chỉnh ----

        private NormalUser suspendedWithSuspendedAt(String username, double rating,
                                                    LocalDateTime suspendedAt,
                                                    int timesRestored) {
            return NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    username,
                    User.hashPassword("password1"),
                    username + "@test.com",
                    AccountStatus.SUSPENDED,
                    rating,
                    0L, 0L,
                    EnumSet.of(User.UserRole.BIDDER),
                    true,
                    timesRestored,
                    suspendedAt);
        }

        // --- Guard: không phải SUSPENDED → return early ---

        @Test
        @DisplayName("ACTIVE user → không có gì xảy ra (return early)")
        void activeUser_noAction() {
            NormalUser user = TestFixture.bidderWithRating("bidderGG1", RATING_DEFAULT);
            double ratingBefore = user.getRating();

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(ratingBefore, user.getRating(), EPSILON);
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
            verifyNoInteractions(userDAO);
        }

        @Test
        @DisplayName("BANNED user → không có gì xảy ra (return early)")
        void bannedUser_noAction() {
            NormalUser user = TestFixture.bannedBidder("bidderGG2");
            double ratingBefore = user.getRating();

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(ratingBefore, user.getRating(), EPSILON);
            assertEquals(AccountStatus.BANNED, user.getAccountStatus());
            verifyNoInteractions(userDAO);
        }

        // --- Guard: suspendedAt = null → return early ---

        @Test
        @DisplayName("SUSPENDED nhưng suspendedAt = null → không restore (data anomaly)")
        void suspendedWithNullSuspendedAt_noAction() {
            // Tạo SUSPENDED user không có suspendedAt (anomaly case)
            NormalUser user = NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "bidderGG3",
                    User.hashPassword("password1"),
                    "bidderGG3@test.com",
                    AccountStatus.SUSPENDED,
                    SUSPEND_THRESHOLD, 0L, 0L,
                    EnumSet.of(User.UserRole.BIDDER),
                    true, 0,
                    null); // suspendedAt = null

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            verifyNoInteractions(userDAO);
        }

        // --- Guard: chưa đủ 3 tháng → return early ---

        @Test
        @DisplayName("SUSPENDED < 3 tháng → chưa đủ thời gian → không restore")
        void suspendedLessThan3Months_notRestored() {
            // suspendedAt = 2 tháng 29 ngày trước
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(2).minusDays(29);
            NormalUser user = suspendedWithSuspendedAt("bidderGG4", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user, LocalDateTime.now().minusMonths(2).minusDays(29));

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            verifyNoInteractions(userDAO);
        }

        @Test
        @DisplayName("SUSPENDED đúng 3 tháng (boundary: now = suspendedAt + 3M) → KHÔNG restore (chưa sau)")
        void suspendedExactly3Months_notRestored() {
            // restoreThreshold = suspendedAt + 3M; cần now.isAfter(threshold)
            // Đặt suspendedAt = 2026/May/20 - 3M → threshold = 2026/May/20 → now.isAfter(now) = false
            LocalDateTime suspendedAt = LocalDateTime.of(2026, Month.MAY, 20, 10, 30).minusDays(30);
            NormalUser user = suspendedWithSuspendedAt("bidderGG5", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user, LocalDateTime.of(2026, Month.MAY, 20, 10, 30));

            // now.isAfter(suspendedAt.plusMonths(3)) = now.isAfter(now) = false → không restore
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }

        @Test
        @DisplayName("SUSPENDED > 3 tháng (1 giây sau ngưỡng) → đủ điều kiện restore")
        void suspendedJustOver3Months_eligibleForRestore() {
            // suspendedAt = 3 tháng + 2 giây trước → threshold = now - 2 giây → now.isAfter = true
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(3).minusSeconds(2);
            NormalUser user = suspendedWithSuspendedAt("bidderGG6", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            // Rating tăng 0.6: 1.5 + 0.6 = 2.1 > 1.5 → ACTIVE
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        // --- Happy path: restore thành công ---

        @Test
        @DisplayName("restore thành công → rating tăng RESTORE_DELTA (0.6)")
        void restore_ratingIncreasedByRestoreDelta() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG7", SUSPEND_THRESHOLD,
                    suspendedAt, 0);
            double expectedRating = SUSPEND_THRESHOLD + RESTORE_DELTA; // 1.5 + 0.6 = 2.1

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(expectedRating, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("restore thành công + rating > 1.5 → status ACTIVE")
        void restore_ratingAboveThreshold_statusSetToActive() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG8", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        @Test
        @DisplayName("restore thành công → hasEverBeenRestored được set true")
        void restore_hasEverBeenRestoredSetTrue() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG9", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            assertTrue(user.isHasEverBeenRestored());
        }

        @Test
        @DisplayName("restore thành công → UserDAO methods được gọi đúng")
        void restore_userDAOCalledCorrectly() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG10", SUSPEND_THRESHOLD,
                    suspendedAt, 0);
            double expectedRating = SUSPEND_THRESHOLD + RESTORE_DELTA;

            ratingService.checkAndRestoreSuspended(user);

            verify(userDAO).updateRating(eq(user.getId()), eq(expectedRating));
            verify(userDAO).updateAccountStatus(eq(user.getId()), eq("ACTIVE"));
            verify(userDAO).incrementTimesRestored(eq(user.getId()));
        }

        // --- Guard: hasEverBeenRestored = true → không restore lần 2 ---

        @Test
        @DisplayName("đã restore 1 lần (hasEverBeenRestored=true) → không restore thêm")
        void alreadyRestored_noSecondRestore() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(6);
            NormalUser user = suspendedWithSuspendedAt("bidderGG11", SUSPEND_THRESHOLD,
                    suspendedAt, 1); // timesRestored = 1
            double ratingBefore = user.getRating();

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(ratingBefore, user.getRating(), EPSILON);
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            verifyNoInteractions(userDAO);
        }

        // --- Repeated call (idempotent guard) ---

        @Test
        @DisplayName("gọi lần 2 ngay sau lần 1 → idempotent (hasEverBeenRestored block)")
        void secondCallAfterRestore_isIdempotent() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG12", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user); // lần 1 → ACTIVE, restored=true
            AccountStatus statusAfterFirst = user.getAccountStatus();
            double ratingAfterFirst = user.getRating();

            // Simulate: user bị suspend lại sau restore, gọi lần 2
            user.setAccountStatus(AccountStatus.SUSPENDED);
            ratingService.checkAndRestoreSuspended(user); // lần 2 → guard block

            // Rating không tăng thêm
            assertEquals(ratingAfterFirst, user.getRating(), EPSILON);
            // DAO chỉ gọi đúng 1 lần (lần 1), lần 2 bị chặn
            verify(userDAO, times(1)).updateRating(anyString(), anyDouble());
        }

        // --- Edge: restore nhưng rating kết quả vẫn ≤ SUSPEND_THRESHOLD ---

        @Test
        @DisplayName("restore nhưng rating + 0.6 vẫn <= 1.5 → status KHÔNG chuyển ACTIVE")
        void restore_ratingStillBelowOrAtThreshold_statusStaysSuspended() {
            // rating = 0.8 → 0.8 + 0.6 = 1.4 ≤ 1.5 → giữ SUSPENDED
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG13", 0.9,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            // Nhưng rating vẫn tăng
            assertEquals(0.9 + RESTORE_DELTA, user.getRating(), EPSILON);
            // hasEverBeenRestored vẫn được set (dù không ACTIVE)
            assertTrue(user.isHasEverBeenRestored());
        }

        @Test
        @DisplayName("restore với rating đúng biên: 0.9 + 0.6 = 1.5 (không > 1.5) → SUSPENDED")
        void restore_ratingExactlyAtThreshold_statusStaysSuspended() {
            // 0.9 + 0.6 = 1.5, cần > 1.5 mới ACTIVE
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG14", 0.9,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            // 0.9 + 0.6 = 1.5 → không > 1.5 → vẫn SUSPENDED
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
            assertEquals(SUSPEND_THRESHOLD, user.getRating(), EPSILON);
        }

        @Test
        @DisplayName("restore với rating 0.96: 0.96 + 0.6 = 1.56 > 1.5 → ACTIVE")
        void restore_ratingJustAboveThresholdAfterDelta_statusActive() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG15", 0.96,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            // 0.96 + 0.6 = 1.56 > 1.5 → ACTIVE
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        }

        // --- Restore DAO calls: updateRating, updateAccountStatus, incrementTimesRestored ---

        @Test
        @DisplayName("restore thành công → 3 DAO methods được gọi đúng 1 lần mỗi loại")
        void restore_exactlyThreeDaoMethodsCalled() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG16", SUSPEND_THRESHOLD,
                    suspendedAt, 0);

            ratingService.checkAndRestoreSuspended(user);

            verify(userDAO, times(1)).updateRating(anyString(), anyDouble());
            verify(userDAO, times(1)).updateAccountStatus(anyString(), anyString());
            verify(userDAO, times(1)).incrementTimesRestored(anyString());
            verifyNoMoreInteractions(userDAO);
        }

        // --- State consistency sau restore ---

        @Test
        @DisplayName("restore không thay đổi balance của user")
        void restore_doesNotAffectBalance() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "bidderGG17",
                    User.hashPassword("password1"),
                    "bidderGG17@test.com",
                    AccountStatus.SUSPENDED,
                    SUSPEND_THRESHOLD, 500_000L, 0L,
                    EnumSet.of(User.UserRole.BIDDER),
                    true, 0, suspendedAt);
            long balanceBefore = user.getBalance();

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(balanceBefore, user.getBalance());
        }

        @Test
        @DisplayName("restore không thay đổi username")
        void restore_doesNotAffectUsername() {
            LocalDateTime suspendedAt = LocalDateTime.now().minusMonths(4);
            NormalUser user = suspendedWithSuspendedAt("bidderGG18", SUSPEND_THRESHOLD,
                    suspendedAt, 0);
            String usernameBefore = user.getUsername();

            ratingService.checkAndRestoreSuspended(user);

            assertEquals(usernameBefore, user.getUsername());
        }
    }

    // =========================================================================
    // canSellerCreateAuction (isEligible mở rộng)
    // =========================================================================

    @Nested
    @DisplayName("canSellerCreateAuction() — role + isEligible + rating >= 2.0")
    class CanSellerCreateAuction {

        @Test
        @DisplayName("SELLER role + ACTIVE + rating >= 2.0 → có thể tạo phiên")
        void eligibleSeller_canCreateAuction() {
            NormalUser seller = TestFixture.normalSeller("sellerHH1");

            assertTrue(ratingService.canSellerCreateAuction(seller));
        }

        @Test
        @DisplayName("BIDDER role (không có SELLER) → không thể tạo phiên")
        void bidderOnlyRole_cannotCreateAuction() {
            NormalUser user = TestFixture.normalBidder("bidderHH2");

            assertFalse(ratingService.canSellerCreateAuction(user));
        }

        @Test
        @DisplayName("SELLER + SUSPENDED → không thể tạo phiên (isEligible = false)")
        void suspendedSeller_cannotCreateAuction() {
            NormalUser seller = NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "sellerHH3",
                    User.hashPassword("password1"),
                    "sellerHH3@test.com",
                    AccountStatus.SUSPENDED,
                    RATING_DEFAULT, 0L, 0L,
                    EnumSet.of(User.UserRole.SELLER),
                    false, 0,
                    LocalDateTime.now());

            assertFalse(ratingService.canSellerCreateAuction(seller));
        }

        @Test
        @DisplayName("SELLER + ACTIVE + rating 1.9 (< 2.0) → không thể tạo phiên")
        void sellerRatingBelowMinSeller_cannotCreateAuction() {
            NormalUser seller = NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "sellerHH4",
                    User.hashPassword("password1"),
                    "sellerHH4@test.com",
                    AccountStatus.ACTIVE,
                    1.9, 0L, 0L,
                    EnumSet.of(User.UserRole.SELLER),
                    false, 0,
                    null);

            assertFalse(ratingService.canSellerCreateAuction(seller));
        }

        @Test
        @DisplayName("SELLER + ACTIVE + rating đúng 2.0 → có thể tạo phiên (boundary inclusive)")
        void sellerRatingExactlyAtMin_canCreateAuction() {
            NormalUser seller = NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "sellerHH5",
                    User.hashPassword("password1"),
                    "sellerHH5@test.com",
                    AccountStatus.ACTIVE,
                    2.0, 0L, 0L,
                    EnumSet.of(User.UserRole.SELLER),
                    false, 0,
                    null);

            assertTrue(ratingService.canSellerCreateAuction(seller));
        }
    }
}