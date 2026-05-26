package com.group13.auction.unit.service;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.RatingService;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link RatingService} — eligibility, penalty, reward, suspend, restore.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService")
class RatingServiceTest {

    private static final double MIN_ELIGIBLE = 2.0;
    private static final double SUSPEND_THRESHOLD = User.RATING_SUSPEND_THRESHOLD;
    private static final double PENALTY_DELTA = 1.0;
    private static final double REWARD_DELTA = 0.2;
    private static final double RESTORE_DELTA = 0.6;
    private static final double EPSILON = 1e-9;

    @Mock private UserDAO userDAO;
    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(userDAO);
        lenient().when(userDAO.updateRating(anyString(), anyDouble())).thenReturn(true);
        lenient().when(userDAO.updateAccountStatus(anyString(), anyString())).thenReturn(true);
        lenient().when(userDAO.updateRatingAndPenalty(anyString(), anyDouble(), anyBoolean())).thenReturn(true);
        lenient().when(userDAO.incrementTimesRestored(anyString())).thenReturn(true);
    }

    @Nested
    @DisplayName("isEligible")
    class IsEligible {

        @Test
        void activeRatingAtThreshold_eligible() {
            assertTrue(ratingService.isEligible(TestFixture.bidderWithRating("elig01", MIN_ELIGIBLE)));
        }

        @Test
        void suspended_notEligible() {
            NormalUser user = TestFixture.bidderWithRating("elig02", 4.0);
            user.setAccountStatus(AccountStatus.SUSPENDED);
            assertFalse(ratingService.isEligible(user));
        }

        @Test
        void ratingBelowThreshold_notEligible() {
            assertFalse(ratingService.isEligible(TestFixture.bidderWithRating("elig03", MIN_ELIGIBLE - 0.1)));
        }
    }

    @Nested
    @DisplayName("penalizeLatePayment")
    class PenalizeLatePayment {

        @Test
        void decreasesRatingAndMarksPenalized() {
            NormalUser user = TestFixture.bidderWithRating("pen01", 3.0);
            ratingService.penalizeLatePayment(user);
            assertEquals(2.0, user.getRating(), EPSILON);
            assertTrue(user.isHasEverBeenPenalized());
            verify(userDAO).updateRatingAndPenalty(eq(user.getId()), eq(2.0), eq(true));
        }

        @Test
        void atSuspendThreshold_autoSuspends() {
            NormalUser user = TestFixture.bidderWithRating("pen02", SUSPEND_THRESHOLD + PENALTY_DELTA);
            ratingService.penalizeLatePayment(user);
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }
    }

    @Nested
    @DisplayName("rewardBidder")
    class RewardBidder {

        @Test
        void increasesRating() {
            NormalUser user = TestFixture.bidderWithRating("rew01", 3.0);
            ratingService.rewardBidder(user);
            assertEquals(3.0 + REWARD_DELTA, user.getRating(), EPSILON);
            verify(userDAO).updateRating(user.getId(), user.getRating());
        }

        @Test
        void clampAtMax() {
            NormalUser user = TestFixture.bidderWithRating("rew02", 5.0);
            ratingService.rewardBidder(user);
            assertEquals(5.0, user.getRating(), EPSILON);
        }
    }

    @Nested
    @DisplayName("rewardSeller & penalizeSeller")
    class SellerRating {

        @Test
        void rewardSeller_increasesRating() {
            NormalUser seller = TestFixture.normalSeller("sellerRw1");
            seller.adjustRating(0);
            double before = seller.getRating();
            ratingService.rewardSeller(seller);
            assertEquals(before + REWARD_DELTA, seller.getRating(), EPSILON);
        }

        @Test
        void penalizeSeller_decreasesRating() {
            NormalUser seller = TestFixture.normalSeller("sellerPn1");
            double before = seller.getRating();
            ratingService.penalizeSeller(seller);
            assertEquals(before - PENALTY_DELTA, seller.getRating(), EPSILON);
        }
    }

    @Nested
    @DisplayName("checkAndRestoreSuspended")
    class RestoreSuspended {

        @Test
        void beforeThreeMonths_noRestore() {
            NormalUser user = suspendedUser(LocalDateTime.now().minusMonths(1));
            ratingService.checkAndRestoreSuspended(user);
            assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        }

        @Test
        void afterThreeMonths_restoresToActive() {
            NormalUser user = suspendedUser(LocalDateTime.of(2020, 1, 1, 0, 0));
            ratingService.checkAndRestoreSuspended(user);
            assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
            assertEquals(1.0 + RESTORE_DELTA, user.getRating(), EPSILON);
        }

        private static NormalUser suspendedUser(LocalDateTime suspendedAt) {
            return NormalUser.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(), LocalDateTime.now(),
                    "rstUser01", User.hashPassword("password1"), "rst@test.com",
                    AccountStatus.SUSPENDED, 1.0, 0L, 0L,
                    EnumSet.of(User.UserRole.BIDDER), true, 0, suspendedAt);
        }
    }

    @Nested
    @DisplayName("canSellerCreateAuction")
    class CanSellerCreateAuction {

        @Test
        void eligibleSeller_canCreate() {
            NormalUser seller = TestFixture.normalSeller("sellerOk1");
            assertTrue(ratingService.canSellerCreateAuction(seller));
        }

        @Test
        void lowRating_cannotCreate() {
            NormalUser seller = TestFixture.normalSeller("sellerNo1");
            while (seller.getRating() >= MIN_ELIGIBLE) {
                seller.adjustRating(-1.0);
            }
            assertFalse(ratingService.canSellerCreateAuction(seller));
        }
    }
}
