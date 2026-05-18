package com.group13.auction.unit.notification;

import com.group13.auction.service.iservice.INotifier;
import com.group13.auction.service.notification.CompositeNotifier;
import com.group13.auction.service.notification.ConsoleNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests cho CompositeNotifier và ConsoleNotifier.
 * Không cần mock, không DB, không Thread.sleep.
 */
@DisplayName("CompositeNotifier & ConsoleNotifier — unit")
class CompositeNotifierTest {

    private CompositeNotifier composite;

    @BeforeEach
    void setUp() {
        composite = new CompositeNotifier();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Notifier đếm số lần được gọi và ghi lại args. */
    static class SpyNotifier implements INotifier {
        final List<String[]> calls = new ArrayList<>();
        @Override
        public void notify(String targetId, String title, String message) {
            calls.add(new String[]{targetId, title, message});
        }
        int callCount() { return calls.size(); }
    }

    /** Notifier ném RuntimeException khi được gọi. */
    static class ThrowingNotifier implements INotifier {
        @Override
        public void notify(String targetId, String title, String message) {
            throw new RuntimeException("simulated failure");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("AddNotifier")
    class AddNotifierTest {

        @Test
        @DisplayName("addNotifier(null) — bỏ qua, không NPE")
        void addNull_ignored() {
            assertThatCode(() -> composite.addNotifier(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Thêm một notifier — được gọi khi notify")
        void addOne_calledOnNotify() {
            SpyNotifier spy = new SpyNotifier();
            composite.addNotifier(spy);
            composite.notify("u1", "TITLE", "msg");
            assertThat(spy.callCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Thêm nhiều notifier — tất cả đều được gọi")
        void addMultiple_allCalled() {
            SpyNotifier s1 = new SpyNotifier();
            SpyNotifier s2 = new SpyNotifier();
            SpyNotifier s3 = new SpyNotifier();
            composite.addNotifier(s1);
            composite.addNotifier(s2);
            composite.addNotifier(s3);
            composite.notify("u2", "T", "m");
            assertThat(s1.callCount()).isEqualTo(1);
            assertThat(s2.callCount()).isEqualTo(1);
            assertThat(s3.callCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Thêm cùng một notifier hai lần — được gọi hai lần (không dedup)")
        void addSameTwice_calledTwice() {
            SpyNotifier spy = new SpyNotifier();
            composite.addNotifier(spy);
            composite.addNotifier(spy);
            composite.notify("u3", "T", "m");
            assertThat(spy.callCount()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("RemoveNotifier")
    class RemoveNotifierTest {

        @Test
        @DisplayName("removeNotifier(null) — bỏ qua, không NPE")
        void removeNull_ignored() {
            assertThatCode(() -> composite.removeNotifier(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Xóa notifier đã thêm — không còn được gọi")
        void removeExisting_notCalled() {
            SpyNotifier spy = new SpyNotifier();
            composite.addNotifier(spy);
            composite.removeNotifier(spy);
            composite.notify("u4", "T", "m");
            assertThat(spy.callCount()).isZero();
        }

        @Test
        @DisplayName("Xóa notifier chưa thêm — không lỗi")
        void removeNonExistent_noError() {
            SpyNotifier spy = new SpyNotifier();
            assertThatCode(() -> composite.removeNotifier(spy)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Sau khi xóa, các notifier còn lại vẫn hoạt động")
        void removeOne_othersStillCalled() {
            SpyNotifier s1 = new SpyNotifier();
            SpyNotifier s2 = new SpyNotifier();
            composite.addNotifier(s1);
            composite.addNotifier(s2);
            composite.removeNotifier(s1);
            composite.notify("u5", "T", "m");
            assertThat(s1.callCount()).isZero();
            assertThat(s2.callCount()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Notify — argument propagation")
    class NotifyArgTest {

        @Test
        @DisplayName("Tham số targetId / title / message được truyền đúng")
        void args_propagatedCorrectly() {
            SpyNotifier spy = new SpyNotifier();
            composite.addNotifier(spy);
            composite.notify("user-XYZ", "BID_PLACED", "Bạn bị vượt giá");
            assertThat(spy.calls).hasSize(1);
            String[] call = spy.calls.get(0);
            assertThat(call[0]).isEqualTo("user-XYZ");
            assertThat(call[1]).isEqualTo("BID_PLACED");
            assertThat(call[2]).isEqualTo("Bạn bị vượt giá");
        }

        @Test
        @DisplayName("notify khi không có notifier nào — không ném lỗi")
        void noNotifiers_noException() {
            assertThatCode(() -> composite.notify("u", "T", "m")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Nhiều lần notify — mỗi lần đều gọi đúng số lần")
        void multipleNotify_callCountCorrect() {
            SpyNotifier spy = new SpyNotifier();
            composite.addNotifier(spy);
            composite.notify("u", "T1", "m1");
            composite.notify("u", "T2", "m2");
            composite.notify("u", "T3", "m3");
            assertThat(spy.callCount()).isEqualTo(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ErrorIsolation — notifier ném exception")
    class ErrorIsolationTest {

        @Test
        @DisplayName("Nếu một notifier ném RuntimeException, composite bong bóng ra ngoài (hành vi hiện tại)")
        void throwingNotifier_propagatesException() {
            // Hành vi hiện tại: CompositeNotifier KHÔNG catch — exception lan ra caller.
            // Test này ghi lại contract thực tế để CI phát hiện nếu ai đó thay đổi hành vi.
            composite.addNotifier(new ThrowingNotifier());
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> composite.notify("u", "T", "m"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Notifier trước notifier-ném vẫn được gọi")
        void notifierBeforeThrower_isCalled() {
            SpyNotifier spy = new SpyNotifier();
            composite.addNotifier(spy);
            composite.addNotifier(new ThrowingNotifier());

            try {
                composite.notify("u", "T", "m");
            } catch (RuntimeException ignored) { /* expected */ }

            assertThat(spy.callCount()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ConsoleNotifier")
    class ConsoleNotifierTest {

        @Test
        @DisplayName("ConsoleNotifier.notify — không ném lỗi với mọi input")
        void notify_doesNotThrow() {
            ConsoleNotifier cn = new ConsoleNotifier();
            assertThatCode(() -> cn.notify("u", "TITLE", "message")).doesNotThrowAnyException();
            assertThatCode(() -> cn.notify(null, null, null)).doesNotThrowAnyException();
            assertThatCode(() -> cn.notify("", "", "")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ConsoleNotifier implement INotifier — polymorphism")
        void implementsINotifier() {
            INotifier notifier = new ConsoleNotifier();
            assertThatCode(() -> notifier.notify("u", "T", "m")).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Concurrency — thread-safety cơ bản")
    class ConcurrencyTest {

        @Test
        @DisplayName("Nhiều thread notify đồng thời — tổng call đúng")
        void concurrentNotify_totalCallsCorrect() throws InterruptedException {
            int THREADS = 10;
            int CALLS_PER_THREAD = 20;
            AtomicInteger counter = new AtomicInteger();
            composite.addNotifier((id, title, msg) -> counter.incrementAndGet());

            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                threads.add(new Thread(() -> {
                    for (int j = 0; j < CALLS_PER_THREAD; j++) {
                        composite.notify("u", "T", "m");
                    }
                }));
            }
            threads.forEach(Thread::start);
            for (Thread t : threads) t.join(5000);

            assertThat(counter.get()).isEqualTo(THREADS * CALLS_PER_THREAD);
        }
    }
}
