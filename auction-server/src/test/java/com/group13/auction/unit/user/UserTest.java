package com.group13.auction.unit.user;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User")
class UserTest {

    @Nested
    @DisplayName("adjustRating")
    class AdjustRating {

        @ParameterizedTest(name = "delta={0} → rating={1}")
        @CsvSource({
                "0.5, 3.5",
                "-1.0, 2.0",
                "10.0, 5.0",
                "-10.0, 0.0"
        })
        void clampAndDelta(double delta, double expected) {
            NormalUser user = TestFixture.normalBidder("ratingUser");
            user.adjustRating(delta);
            assertEquals(expected, user.getRating(), 1e-9);
        }

        @Test
        void multipleAdjustments_accumulate() {
            NormalUser user = TestFixture.normalBidder("ratingAccum");
            user.adjustRating(1.0);
            user.adjustRating(-0.5);
            assertEquals(3.5, user.getRating(), 1e-9);
        }
    }

    @Nested
    @DisplayName("hashPassword")
    class HashPassword {

        @Test
        void deterministic_notPlaintext_differentInputs() {
            String hash1 = User.hashPassword("secret");
            String hash2 = User.hashPassword("secret");
            assertEquals(hash1, hash2);
            assertNotEquals("secret", hash1);
            assertNotEquals(hash1, User.hashPassword("other"));
            assertTrue(hash1.matches("[0-9a-f]{64}"));
        }

        @Test
        void matchesFixtureUserPassword() {
            NormalUser user = TestFixture.normalBidder("hashUser");
            assertEquals(User.hashPassword("password1"), user.getHashedPassword());
        }
    }

    @Nested
    @DisplayName("setAccountStatus")
    class SetAccountStatus {

        @ParameterizedTest
        @CsvSource({
                "ACTIVE, BANNED",
                "ACTIVE, SUSPENDED",
                "SUSPENDED, ACTIVE",
                "BANNED, ACTIVE"
        })
        void transitions_updateStatus(AccountStatus from, AccountStatus to) {
            NormalUser user = userWithStatus(from);
            user.setAccountStatus(to);
            assertEquals(to, user.getAccountStatus());
        }

        @Test
        void suspend_recordsSuspendedAt_once() {
            NormalUser user = TestFixture.normalBidder("suspendOnce");
            user.setAccountStatus(AccountStatus.SUSPENDED);
            var first = user.getSuspendedAt();
            assertNotNull(first);
            user.setAccountStatus(AccountStatus.SUSPENDED);
            assertEquals(first, user.getSuspendedAt());
        }

        @Test
        void ban_doesNotSetSuspendedAt() {
            NormalUser user = TestFixture.normalBidder("banNoSuspend");
            user.setAccountStatus(AccountStatus.BANNED);
            assertNull(user.getSuspendedAt());
        }

        @Test
        void nullStatus_throws() {
            NormalUser user = TestFixture.normalBidder("nullStatus");
            assertThrows(NullPointerException.class, () -> user.setAccountStatus(null));
        }

        private static NormalUser userWithStatus(AccountStatus status) {
            return switch (status) {
                case SUSPENDED -> TestFixture.suspendedBidder("u_" + status);
                case BANNED -> TestFixture.bannedBidder("u_" + status);
                default -> TestFixture.normalBidder("u_" + status);
            };
        }
    }
}
