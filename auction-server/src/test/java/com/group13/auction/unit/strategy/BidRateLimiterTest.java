package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.BidRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho {@link BidRateLimiter}.
 * Không cần DB, không cần Spring. Chạy nhanh.
 */
@DisplayName("BidRateLimiter")
class BidRateLimiterTest {

    private BidRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = BidRateLimiter.getInstance();
        limiter.clearAll(); // Reset trạng thái singleton giữa các test
    }

    @AfterEach
    void tearDown() {
        limiter.clearAll();
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("tryConsume — trong giới hạn")
    class WithinLimit {

        @Test
        @DisplayName("5 lần đầu liên tiếp → đều được phép (return true)")
        void first5Attempts_allAllowed() {
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.tryConsume("user1"))
                        .as("Lần %d phải được phép", i + 1)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("User khác nhau → bucket độc lập, không ảnh hưởng nhau")
        void differentUsers_independentBuckets() {
            // Exhaust limit cho user1
            for (int i = 0; i < 5; i++) limiter.tryConsume("userA");

            // user2 vẫn có full budget
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.tryConsume("userB"))
                        .as("userB không bị ảnh hưởng bởi userA")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("tryConsume 1 lần → size() = 1 (bucket được tạo)")
        void firstConsume_createsBucket() {
            limiter.tryConsume("userSingle");
            assertThat(limiter.size()).isEqualTo(1);
        }
    }

    // =========================================================================
    // Rate limit triggered
    // =========================================================================

    @Nested
    @DisplayName("tryConsume — vượt giới hạn")
    class ExceedLimit {

        @Test
        @DisplayName("Lần thứ 6 trong cùng 1 window → bị chặn (return false)")
        void sixthAttemptInWindow_blocked() {
            for (int i = 0; i < 5; i++) limiter.tryConsume("userLim");
            assertThat(limiter.tryConsume("userLim"))
                    .as("Lần thứ 6 phải bị chặn")
                    .isFalse();
        }

        @Test
        @DisplayName("10 lần liên tiếp → 5 đầu true, 5 sau false")
        void tenAttempts_first5AllowedRest5Blocked() {
            int allowed = 0, blocked = 0;
            for (int i = 0; i < 10; i++) {
                if (limiter.tryConsume("userTen")) allowed++;
                else blocked++;
            }
            assertThat(allowed).isEqualTo(5);
            assertThat(blocked).isEqualTo(5);
        }
    }

    // =========================================================================
    // Window reset
    // =========================================================================

    @Nested
    @DisplayName("tryConsume — window reset")
    class WindowReset {

        @Test
        @DisplayName("Sau 1.1 giây kể từ lần đầu → window mới, được phép lại")
        void afterWindowExpires_bucketResets() throws InterruptedException {
            // Exhaust limit
            for (int i = 0; i < 5; i++) limiter.tryConsume("userWin");
            assertThat(limiter.tryConsume("userWin")).isFalse();

            // Chờ window mới (1.1 giây)
            Thread.sleep(1_100);

            // Phải được phép lại
            assertThat(limiter.tryConsume("userWin"))
                    .as("Sau khi window reset, phải được phép lại")
                    .isTrue();
        }
    }

    // =========================================================================
    // remove + clearAll
    // =========================================================================

    @Nested
    @DisplayName("remove() và clearAll()")
    class Removal {

        @Test
        @DisplayName("remove() xóa bucket của user → user được reset về 0 bid")
        void remove_resetsBucketForUser() {
            // Exhaust
            for (int i = 0; i < 5; i++) limiter.tryConsume("userRemove");
            assertThat(limiter.tryConsume("userRemove")).isFalse();

            // Remove
            limiter.remove("userRemove");

            // Phải được phép lại
            assertThat(limiter.tryConsume("userRemove"))
                    .as("Sau remove(), phải được phép bid lại")
                    .isTrue();
        }

        @Test
        @DisplayName("remove() user không tồn tại → không throw exception")
        void remove_nonExistentUser_noException() {
            // Should not throw
            limiter.remove("ghostUser");
            assertThat(limiter.size()).isZero();
        }

        @Test
        @DisplayName("clearAll() → tất cả bucket bị xóa, size = 0")
        void clearAll_removesAllBuckets() {
            limiter.tryConsume("u1");
            limiter.tryConsume("u2");
            limiter.tryConsume("u3");
            assertThat(limiter.size()).isEqualTo(3);

            limiter.clearAll();
            assertThat(limiter.size()).isZero();
        }
    }

    // =========================================================================
    // Thread safety
    // =========================================================================

    @Nested
    @DisplayName("Thread safety — concurrent tryConsume")
    class ThreadSafety {

        @Test
        @DisplayName("50 threads cùng consume cho 1 user → đúng 5 được phép, 45 bị chặn")
        void concurrent50Threads_exactly5Allowed() throws InterruptedException {
            int THREADS = 50;
            AtomicInteger allowed = new AtomicInteger(0);
            AtomicInteger blocked = new AtomicInteger(0);

            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);

            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        gate.await();
                        if (limiter.tryConsume("concurrentUser")) allowed.incrementAndGet();
                        else blocked.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            gate.countDown();
            done.await(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(allowed.get())
                    .as("Đúng 5 request được phép (rate limit = 5/giây)")
                    .isEqualTo(5);
            assertThat(blocked.get())
                    .as("45 request còn lại bị chặn")
                    .isEqualTo(45);
        }

        @Test
        @DisplayName("50 threads × 2 users khác nhau → mỗi user được 5 (tổng 10 allowed)")
        void concurrent_twoUsersIndependent_each5Allowed() throws InterruptedException {
            int THREADS_PER_USER = 25;
            AtomicInteger allowedA = new AtomicInteger(0);
            AtomicInteger allowedB = new AtomicInteger(0);

            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS_PER_USER * 2);
            ExecutorService pool = Executors.newFixedThreadPool(THREADS_PER_USER * 2);

            for (int i = 0; i < THREADS_PER_USER; i++) {
                pool.submit(() -> {
                    try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (limiter.tryConsume("twoUserA")) allowedA.incrementAndGet();
                    done.countDown();
                });
                pool.submit(() -> {
                    try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (limiter.tryConsume("twoUserB")) allowedB.incrementAndGet();
                    done.countDown();
                });
            }

            gate.countDown();
            done.await(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(allowedA.get()).as("User A được phép đúng 5 bid").isEqualTo(5);
            assertThat(allowedB.get()).as("User B được phép đúng 5 bid").isEqualTo(5);
        }
    }
}
