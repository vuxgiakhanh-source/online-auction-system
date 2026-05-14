package com.group13.auction.concurrency.rating;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.RatingService;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * RatingConcurrencyTest — GAP 3 + GAP 9
 *
 * GAP 3: User.adjustRating() non-atomic plain double field.
 *   → this.rating = Math.max(MIN, Math.min(MAX, this.rating + delta))
 *   → read-modify-write không atomic → lost update có thể xảy ra.
 *   → KNOWN BUG documented: bounds [0.0, 5.0] phải được giữ tuyệt đối.
 *
 * GAP 9: Concurrent reward + penalize — net delta và SUSPEND threshold.
 *   → Verify: rating PHẢI trong bounds; SUSPENDED không bị bỏ sót.
 * ============================================================================
 */
@DisplayName("Rating: adjustRating() non-atomic + concurrent reward/penalize (GAP 3, GAP 9)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RatingConcurrencyTest extends ConcurrencyTestBase {

    private UserDAO mockUserDAO;

    @BeforeEach
    void setUp() {
        mockUserDAO = mock(UserDAO.class);
        when(mockUserDAO.updateBalances(any(), anyLong(), anyLong())).thenReturn(true);
    }

    // ── G3-1 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("G3-1: [KNOWN BUG] 50 threads adjustRating() concurrent — bounds [0,5] phải được giữ dù lost update")
    @Timeout(value = 5)
    void adjustRating_concurrent_boundsAlwaysRespected() throws InterruptedException {
        NormalUser user = buildUser("user-G3-1", 0L);
        // Initial rating = 3.0

        int N = 50;
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        // 25 threads reward (+0.3), 25 threads penalize (-0.3)
        for (int i = 0; i < N; i++) {
            final double delta = (i % 2 == 0) ? +0.3 : -0.3;
            new Thread(() -> {
                try {
                    gate.await();
                    user.adjustRating(delta);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        double finalRating = user.getRating();

        // CRITICAL: bounds phải được giữ tuyệt đối dù có race condition
        assertThat(finalRating)
                .as("Rating phải >= 0.0 (RATING_MIN) dù có lost update")
                .isGreaterThanOrEqualTo(0.0);
        assertThat(finalRating)
                .as("Rating phải <= 5.0 (RATING_MAX) dù có lost update")
                .isLessThanOrEqualTo(5.0);

        log.warn("[G3-1 KNOWN BUG] finalRating={} (expected near 3.0 nếu atomic). "
                + "Fix: AtomicLong lưu rating*100 với compareAndSet().", finalRating);
    }

    // ── G3-2 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("G3-2: RatingService.rewardBidder() + penalizeBidder() concurrent — bounds [0,5] không vi phạm")
    @Timeout(value = 5)
    void ratingService_concurrentRewardAndPenalize_boundsNeverViolated() throws InterruptedException {
        NormalUser user = buildUser("user-G3-2", 0L);
        RatingService rs = new RatingService(mockUserDAO);

        int N = 40;
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    gate.await();
                    if (idx % 2 == 0) rs.rewardBidder(user);
                    else              rs.penalizeLatePayment(user);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(user.getRating())
                .as("Rating PHẢI trong [0.0, 5.0] sau concurrent reward/penalize")
                .isBetween(0.0, 5.0);
    }

    // ── G9-1 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("G9-1: 30 rewards + 20 penalizes — bounds [0,5] không vi phạm, net delta documented")
    @Timeout(value = 5)
    void mixedRewardPenalize_boundsAlwaysHeld() throws InterruptedException {
        NormalUser user = buildUser("user-G9-1", 0L);
        RatingService rs = new RatingService(mockUserDAO);

        int rewardCount   = 30;
        int penalizeCount = 20;
        int total         = rewardCount + penalizeCount;

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < rewardCount;  i++) tasks.add(() -> rs.rewardBidder(user));
        for (int i = 0; i < penalizeCount; i++) tasks.add(() -> rs.penalizeLatePayment(user));
        Collections.shuffle(tasks, new Random(42));

        for (Runnable task : tasks) {
            new Thread(() -> {
                try { gate.await(); task.run(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        double finalRating = user.getRating();
        assertThat(finalRating).isBetween(0.0, 5.0);

        // Expected nếu atomic: 3.0 + 30*0.3 - 20*0.5 = 2.0 (clamped)
        double expected = Math.max(0.0, Math.min(5.0, 3.0 + rewardCount * 0.3 - penalizeCount * 0.5));
        log.info("[G9-1] finalRating={}, expectedIfAtomic={}", finalRating, expected);

        if (Math.abs(finalRating - expected) > 0.5) {
            log.warn("[G9-1 KNOWN BUG] Lost update: finalRating={} differs from expected={}. "
                    + "Fix: AtomicLong với rating*100 và compareAndSet().", finalRating, expected);
        }
    }

    // ── G9-2 ─────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("G9-2: Concurrent penalize → SUSPENDED state không bị bỏ sót khi rating < threshold")
    @Timeout(value = 5)
    void concurrentPenalize_suspendThreshold_notMissed() throws InterruptedException {
        NormalUser user = buildUser("user-G9-2", 0L);
        // Rating ban đầu 3.0, hạ xuống ~1.4 — dưới ngưỡng SUSPEND 1.5
        user.adjustRating(-1.6);

        RatingService rs = new RatingService(mockUserDAO);
        addSellerRole(user);

        int N = 10;
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    rs.penalizeSeller(user);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Rating phải trong bounds
        assertThat(user.getRating()).isBetween(0.0, 5.0);

        // Nếu rating < threshold → AccountStatus PHẢI là SUSPENDED
        if (user.getRating() < User.RATING_SUSPEND_THRESHOLD) {
            assertThat(user.getAccountStatus())
                    .as("User có rating < %.1f phải bị SUSPENDED", User.RATING_SUSPEND_THRESHOLD)
                    .isEqualTo(User.AccountStatus.SUSPENDED);
        }
    }
}
