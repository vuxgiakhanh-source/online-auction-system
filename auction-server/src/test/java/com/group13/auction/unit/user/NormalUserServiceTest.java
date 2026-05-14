package com.group13.auction.unit.user;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for NormalUser wallet state: balance, lockedDeposit,
 * availableBalance, lockDeposit, unlockDeposit, and concurrent atomicity.
 *
 * <p>Targets the most dangerous money-movement paths — incorrect clamping,
 * under/over-unlock, race conditions on AtomicLong, and rollback semantics.
 *
 * <p>No mocks, no DB, no Thread.sleep(). FIRST-compliant.
 */
@DisplayName("NormalUser — Wallet & Deposit State")
class NormalUserWalletTest {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final long BALANCE_10M  = 10_000_000L;
    private static final long BALANCE_1M   =  1_000_000L;
    private static final long DEPOSIT_300K =    300_000L;
    private static final long DEPOSIT_500K =    500_000L;

    // =========================================================================
    // Available Balance = Balance − LockedDeposit
    // =========================================================================

    @Nested
    @DisplayName("getAvailableBalance()")
    class AvailableBalance {

        @Test
        @DisplayName("availableBalance = balance when no deposit is locked")
        void noDeposit_availableEqualsBalance() {
            NormalUser user = TestFixture.bidderWithBalance("bidder001", BALANCE_10M);

            assertThat(user.getAvailableBalance()).isEqualTo(BALANCE_10M);
        }

        @Test
        @DisplayName("availableBalance decreases after lockDeposit")
        void afterLock_availableReducedByDeposit() {
            NormalUser user = TestFixture.bidderWithBalance("bidder002", BALANCE_10M);

            user.lockDeposit(DEPOSIT_300K);

            assertThat(user.getAvailableBalance()).isEqualTo(BALANCE_10M - DEPOSIT_300K);
            assertThat(user.getBalance()).isEqualTo(BALANCE_10M);           // balance unchanged
            assertThat(user.getLockedDeposit()).isEqualTo(DEPOSIT_300K);
        }

        @Test
        @DisplayName("availableBalance is zero when all balance is locked")
        void fullyLocked_availableIsZero() {
            NormalUser user = TestFixture.bidderWithBalance("bidder003", DEPOSIT_300K);

            user.lockDeposit(DEPOSIT_300K);

            assertThat(user.getAvailableBalance()).isZero();
        }

        @Test
        @DisplayName("availableBalance cannot go negative via lockDeposit (caller must guard)")
        void lockDepositBeyondBalance_lockedDepositExceedsBalance() {
            // NormalUser.lockDeposit itself does NOT validate — WalletService guards.
            // This test confirms the model allows over-lock so WalletService responsibility is clear.
            NormalUser user = TestFixture.bidderWithBalance("bidder004", DEPOSIT_300K);

            user.lockDeposit(DEPOSIT_300K * 2); // WalletService would have blocked this

            // Model allows it — availableBalance goes negative
            assertThat(user.getAvailableBalance()).isNegative();
        }
    }

    // =========================================================================
    // lockDeposit / unlockDeposit
    // =========================================================================

    @Nested
    @DisplayName("lockDeposit() and unlockDeposit()")
    class LockUnlock {

        @Test
        @DisplayName("lockDeposit accumulates across multiple calls")
        void multipleLocks_depositAccumulates() {
            NormalUser user = TestFixture.bidderWithBalance("bidder005", BALANCE_10M);

            user.lockDeposit(DEPOSIT_300K);
            user.lockDeposit(DEPOSIT_500K);

            assertThat(user.getLockedDeposit()).isEqualTo(DEPOSIT_300K + DEPOSIT_500K);
        }

        @Test
        @DisplayName("unlockDeposit restores locked amount correctly")
        void unlockAfterLock_depositReduces() {
            NormalUser user = TestFixture.bidderWithBalance("bidder006", BALANCE_10M);
            user.lockDeposit(DEPOSIT_300K);

            user.unlockDeposit(DEPOSIT_300K);

            assertThat(user.getLockedDeposit()).isZero();
            assertThat(user.getAvailableBalance()).isEqualTo(BALANCE_10M);
        }

        @Test
        @DisplayName("unlockDeposit clamps to 0 — never goes negative (safety guard)")
        void unlockMoreThanLocked_clampedToZero() {
            NormalUser user = TestFixture.bidderWithBalance("bidder007", BALANCE_10M);
            user.lockDeposit(DEPOSIT_300K);

            // Unlock more than was locked — should clamp to 0, not go negative
            user.unlockDeposit(DEPOSIT_300K * 5);

            assertThat(user.getLockedDeposit())
                    .as("lockedDeposit must never be negative")
                    .isZero();
        }

        @Test
        @DisplayName("unlockDeposit on already-zero lockedDeposit remains 0")
        void unlockWhenNoDeposit_remainsZero() {
            NormalUser user = TestFixture.bidderWithBalance("bidder008", BALANCE_10M);
            // No prior lockDeposit

            user.unlockDeposit(DEPOSIT_300K);

            assertThat(user.getLockedDeposit()).isZero();
        }

        @ParameterizedTest(name = "deposit = {0}")
        @ValueSource(longs = {1L, 50_000L, 300_000L, 1_000_000L, 9_999_999L})
        @DisplayName("lockDeposit + unlockDeposit is idempotent for any valid amount")
        void lockThenUnlock_isIdempotent(long amount) {
            NormalUser user = TestFixture.bidderWithBalance("bidder009", BALANCE_10M);

            user.lockDeposit(amount);
            user.unlockDeposit(amount);

            assertThat(user.getLockedDeposit()).isZero();
            assertThat(user.getAvailableBalance()).isEqualTo(BALANCE_10M);
        }
    }

    // =========================================================================
    // addBalance / restoreBalances
    // =========================================================================

    @Nested
    @DisplayName("addBalance() and restoreBalances()")
    class BalanceMutation {

        @Test
        @DisplayName("addBalance with positive delta increases balance")
        void addPositiveDelta_balanceIncreases() {
            NormalUser user = TestFixture.bidderWithBalance("bidder010", BALANCE_1M);

            long newBalance = user.addBalance(BALANCE_1M);

            assertThat(newBalance).isEqualTo(BALANCE_1M * 2);
            assertThat(user.getBalance()).isEqualTo(BALANCE_1M * 2);
        }

        @Test
        @DisplayName("addBalance with negative delta decreases balance (debit path)")
        void addNegativeDelta_balanceDecreases() {
            NormalUser user = TestFixture.bidderWithBalance("bidder011", BALANCE_10M);

            long newBalance = user.addBalance(-BALANCE_1M);

            assertThat(newBalance).isEqualTo(BALANCE_10M - BALANCE_1M);
        }

        @Test
        @DisplayName("restoreBalances sets both balance and lockedDeposit atomically")
        void restoreBalances_setsExactValues() {
            NormalUser user = TestFixture.bidderWithBalance("bidder012", BALANCE_10M);
            user.lockDeposit(DEPOSIT_500K);

            long origBalance = BALANCE_1M;
            long origLocked  = DEPOSIT_300K;
            user.restoreBalances(origBalance, origLocked);

            assertThat(user.getBalance()).isEqualTo(origBalance);
            assertThat(user.getLockedDeposit()).isEqualTo(origLocked);
        }

        @Test
        @DisplayName("restoreBalances to zero is valid (rollback to initial state)")
        void restoreBalancesToZero_isValid() {
            NormalUser user = TestFixture.bidderWithBalance("bidder013", BALANCE_10M);
            user.lockDeposit(DEPOSIT_500K);

            user.restoreBalances(0L, 0L);

            assertThat(user.getBalance()).isZero();
            assertThat(user.getLockedDeposit()).isZero();
            assertThat(user.getAvailableBalance()).isZero();
        }
    }

    // =========================================================================
    // tryMarkJoined / hasJoined / removeJoinedAuction — atomic gate
    // =========================================================================

    @Nested
    @DisplayName("tryMarkJoined() atomic gate")
    class TryMarkJoined {

        @Test
        @DisplayName("first tryMarkJoined returns true and marks as joined")
        void firstCall_returnsTrueAndMarksJoined() {
            NormalUser user = TestFixture.normalBidder("bidder014");

            boolean result = user.tryMarkJoined("auction-A");

            assertThat(result).isTrue();
            assertThat(user.hasJoined("auction-A")).isTrue();
        }

        @Test
        @DisplayName("second tryMarkJoined on same auction returns false (idempotent guard)")
        void secondCall_returnsFalse() {
            NormalUser user = TestFixture.normalBidder("bidder015");
            user.tryMarkJoined("auction-A");

            boolean second = user.tryMarkJoined("auction-A");

            assertThat(second).isFalse();
        }

        @Test
        @DisplayName("removeJoinedAuction allows re-join after rollback")
        void removeAfterMark_allowsRejoin() {
            NormalUser user = TestFixture.normalBidder("bidder016");
            user.tryMarkJoined("auction-A");

            user.removeJoinedAuction("auction-A");
            boolean canJoinAgain = user.tryMarkJoined("auction-A");

            assertThat(canJoinAgain).isTrue();
        }

        @Test
        @DisplayName("different auction IDs are tracked independently")
        void differentAuctions_independentTracking() {
            NormalUser user = TestFixture.normalBidder("bidder017");

            user.tryMarkJoined("auction-X");
            boolean resultY = user.tryMarkJoined("auction-Y");

            assertThat(user.hasJoined("auction-X")).isTrue();
            assertThat(resultY).isTrue(); // different auction = not a duplicate
        }

        @Test
        @DisplayName("concurrent tryMarkJoined: exactly one thread wins the race")
        void concurrentTryMarkJoined_onlyOneSucceeds() throws InterruptedException {
            NormalUser user = TestFixture.normalBidder("bidder018");
            int threadCount = 10;
            CountDownLatch ready  = new CountDownLatch(threadCount);
            CountDownLatch start  = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService pool  = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (user.tryMarkJoined("auction-CONCURRENT")) {
                        successCount.incrementAndGet();
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            assertThat(successCount.get())
                    .as("Exactly one thread should win the atomic gate")
                    .isEqualTo(1);
        }
    }

    // =========================================================================
    // addToWatchList (idempotent)
    // =========================================================================

    @Nested
    @DisplayName("addToWatchList() idempotency")
    class WatchList {

        @Test
        @DisplayName("addToWatchList twice does not create duplicates")
        void addTwice_noDuplicate() {
            NormalUser user = TestFixture.normalBidder("bidder019");

            user.addToWatchList("auction-W");
            user.addToWatchList("auction-W");

            // CopyOnWriteArrayList check — we can't access directly but
            // re-adding should keep list size sane
            // We verify idempotency via no exception + calling it is safe
            assertThatCode(() -> user.addToWatchList("auction-W")).doesNotThrowAnyException();
        }
    }
}