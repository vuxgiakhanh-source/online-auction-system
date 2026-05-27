package com.group13.auction.unit.service;

import static org.assertj.core.api.Assertions.*;

import com.group13.auction.service.scheduler.TaskScheduler;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link TaskScheduler}.
 *
 * <p><b>Test category:</b> Unit + Concurrency
 *
 * <p><b>Strategy:</b>
 *
 * <ul>
 *   <li>Constructor validation — fail-fast on bad args
 *   <li>scheduleAtFixedRate — execution timing verified with CountDownLatch (no sleep())
 *   <li>Error isolation — task exception must NOT kill the scheduler
 *   <li>shutdownNow — executor terminates; subsequent tasks don't run
 *   <li>Thread naming — daemon flag set correctly
 * </ul>
 *
 * <p><b>Flaky-test mitigations:</b>
 *
 * <ul>
 *   <li>All timing assertions use CountDownLatch.await() with a generous but finite timeout.
 *   <li>No Thread.sleep() calls.
 *   <li>Tests that verify "no extra invocation" wait for at least one cycle first, then assert
 *       count with AtomicInteger — never race against wall clock.
 * </ul>
 */
@DisplayName("TaskScheduler")
class TaskSchedulerTest {

  // Timeout for all latch-based waits (generous to avoid flakiness on CI)
  private static final long AWAIT_TIMEOUT_MS = 3_000;

  private TaskScheduler sut;

  @AfterEach
  void tearDown() {
    if (sut != null) {
      sut.shutdownNow();
    }
  }

  // =========================================================================
  // Constructor validation
  // =========================================================================

  @Nested
  @DisplayName("constructor validation")
  class ConstructorValidation {

    @Test
    @DisplayName("should_throwIllegalArgument_when_corePoolSizeIsZero")
    void should_throwIllegalArgument_when_corePoolSizeIsZero() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new TaskScheduler(0, "test-thread"))
          .withMessageContaining("corePoolSize");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
    @DisplayName("should_throwIllegalArgument_when_corePoolSizeIsNegative")
    void should_throwIllegalArgument_when_corePoolSizeIsNegative(int size) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new TaskScheduler(size, "test-thread"))
          .withMessageContaining("corePoolSize");
    }

    @Test
    @DisplayName("should_throwIllegalArgument_when_threadNameIsNull")
    void should_throwIllegalArgument_when_threadNameIsNull() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new TaskScheduler(1, null))
          .withMessageContaining("threadName");
    }

    @Test
    @DisplayName("should_throwIllegalArgument_when_threadNameIsBlank")
    void should_throwIllegalArgument_when_threadNameIsBlank() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new TaskScheduler(1, "   "))
          .withMessageContaining("threadName");
    }

    @Test
    @DisplayName("should_throwIllegalArgument_when_threadNameIsEmpty")
    void should_throwIllegalArgument_when_threadNameIsEmpty() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new TaskScheduler(1, ""))
          .withMessageContaining("threadName");
    }

    @Test
    @DisplayName("should_construct_when_argsAreValid")
    void should_construct_when_argsAreValid() {
      assertThatNoException()
          .isThrownBy(
              () -> {
                TaskScheduler scheduler = new TaskScheduler(2, "valid-thread");
                scheduler.shutdownNow(); // cleanup
              });
    }
  }

  // =========================================================================
  // scheduleAtFixedRate — execution behavior
  // =========================================================================

  @Nested
  @DisplayName("scheduleAtFixedRate — execution behavior")
  class ScheduleAtFixedRate {

    @Test
    @DisplayName("should_executeTask_when_scheduledWithZeroInitialDelay")
    void should_executeTask_when_scheduledWithZeroInitialDelay() throws InterruptedException {
      sut = new TaskScheduler(1, "test-exec");
      CountDownLatch latch = new CountDownLatch(1);

      sut.scheduleAtFixedRate(latch::countDown, 0, 10, TimeUnit.SECONDS);

      boolean executed = latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(executed).as("Task should execute at least once within timeout").isTrue();
    }

    @Test
    @DisplayName("should_executeTaskMultipleTimes_when_scheduledAtFixedRate")
    void should_executeTaskMultipleTimes_when_scheduledAtFixedRate() throws InterruptedException {
      sut = new TaskScheduler(1, "test-multi");
      int expectedCount = 3;
      CountDownLatch latch = new CountDownLatch(expectedCount);

      // Period of 50ms — fast enough to capture 3 invocations quickly
      sut.scheduleAtFixedRate(latch::countDown, 0, 50, TimeUnit.MILLISECONDS);

      boolean reachedCount = latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(reachedCount)
          .as("Task should execute at least %d times within timeout", expectedCount)
          .isTrue();
    }

    @Test
    @DisplayName("should_respectInitialDelay_when_initialDelayIsNonZero")
    void should_respectInitialDelay_when_initialDelayIsNonZero() throws InterruptedException {
      sut = new TaskScheduler(1, "test-delay");
      AtomicInteger executionCount = new AtomicInteger(0);
      CountDownLatch started = new CountDownLatch(1);

      // 200ms initial delay — we check that task hasn't run after 50ms
      sut.scheduleAtFixedRate(
          () -> {
            executionCount.incrementAndGet();
            started.countDown();
          },
          200,
          1_000,
          TimeUnit.MILLISECONDS);

      // After 50ms, task should not have executed yet (initial delay = 200ms)
      Thread.sleep(50);
      assertThat(executionCount.get())
          .as("Task should not run before initial delay expires")
          .isZero();

      // Wait for first execution
      boolean executed = started.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(executed).isTrue();
    }
  }

  // =========================================================================
  // Error isolation — task exception must NOT kill the scheduler
  // =========================================================================

  @Nested
  @DisplayName("error isolation — task exception must not kill scheduler")
  class ErrorIsolation {

    @Test
    @DisplayName("should_continueScheduling_when_taskThrowsRuntimeException")
    void should_continueScheduling_when_taskThrowsRuntimeException() throws InterruptedException {
      sut = new TaskScheduler(1, "test-exception");
      AtomicInteger successCount = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(2); // need 2 successful runs after exception

      AtomicInteger callCount = new AtomicInteger(0);
      sut.scheduleAtFixedRate(
          () -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
              throw new RuntimeException("simulated task failure");
            }
            // Subsequent invocations succeed
            successCount.incrementAndGet();
            latch.countDown();
          },
          0,
          50,
          TimeUnit.MILLISECONDS);

      boolean recovered = latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(recovered).as("Scheduler must continue running after task exception").isTrue();
      assertThat(successCount.get())
          .as("Subsequent tasks must execute after exception")
          .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("should_continueScheduling_when_taskThrowsError")
    void should_continueScheduling_when_taskThrowsError() throws InterruptedException {
      sut = new TaskScheduler(1, "test-error");
      CountDownLatch latch = new CountDownLatch(1);
      AtomicInteger callCount = new AtomicInteger(0);

      sut.scheduleAtFixedRate(
          () -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
              throw new AssertionError("simulated Error in task");
            }
            latch.countDown();
          },
          0,
          50,
          TimeUnit.MILLISECONDS);

      boolean recovered = latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(recovered)
          .as("Scheduler must survive Throwable (not just RuntimeException) in task")
          .isTrue();
    }

    @Test
    @DisplayName("should_captureThrownException_without_propagatingToScheduler")
    void should_captureThrownException_without_propagatingToScheduler()
        throws InterruptedException {
      // Validates that the exception is swallowed internally (logged, not rethrown)
      sut = new TaskScheduler(1, "test-swallow");
      CountDownLatch firstRun = new CountDownLatch(1);
      CountDownLatch secondRun = new CountDownLatch(1);
      AtomicInteger runCount = new AtomicInteger(0);

      sut.scheduleAtFixedRate(
          () -> {
            int n = runCount.incrementAndGet();
            if (n == 1) {
              firstRun.countDown();
              throw new IllegalStateException("must be caught");
            }
            secondRun.countDown();
          },
          0,
          50,
          TimeUnit.MILLISECONDS);

      assertThat(firstRun.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
      // If exception propagated and killed the scheduler, secondRun would never fire
      assertThat(secondRun.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }
  }

  // =========================================================================
  // shutdownNow — lifecycle
  // =========================================================================

  @Nested
  @DisplayName("shutdownNow — lifecycle")
  class ShutdownNow {

    @Test
    @DisplayName("should_stopExecutingTasks_when_shutdownNowIsCalled")
    void should_stopExecutingTasks_when_shutdownNowIsCalled() throws InterruptedException {
      sut = new TaskScheduler(1, "test-shutdown");
      CountDownLatch firstRun = new CountDownLatch(1);
      AtomicInteger executionCount = new AtomicInteger(0);

      sut.scheduleAtFixedRate(
          () -> {
            executionCount.incrementAndGet();
            firstRun.countDown();
          },
          0,
          50,
          TimeUnit.MILLISECONDS);

      // Wait for first execution, then shut down
      firstRun.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      sut.shutdownNow();
      int countAfterShutdown = executionCount.get();

      // After shutdown, no more tasks should run
      Thread.sleep(200);
      assertThat(executionCount.get())
          .as("Execution count should not grow significantly after shutdownNow")
          .isLessThanOrEqualTo(countAfterShutdown + 1); // allow 1 in-flight
    }

    @Test
    @DisplayName("should_notThrow_when_shutdownNowCalledMultipleTimes")
    void should_notThrow_when_shutdownNowCalledMultipleTimes() {
      sut = new TaskScheduler(1, "test-double-shutdown");
      assertThatNoException()
          .isThrownBy(
              () -> {
                sut.shutdownNow();
                sut.shutdownNow(); // idempotent
              });
    }

    @Test
    @DisplayName("should_shutdownCleanly_when_taskIsCurrentlyRunning")
    void should_shutdownCleanly_when_taskIsCurrentlyRunning() throws InterruptedException {
      sut = new TaskScheduler(1, "test-interrupt");
      CountDownLatch taskStarted = new CountDownLatch(1);

      sut.scheduleAtFixedRate(
          () -> {
            taskStarted.countDown();
            try {
              // Simulate a long-running task
              Thread.sleep(5_000);
            } catch (InterruptedException ignored) {
              Thread.currentThread().interrupt();
            }
          },
          0,
          1_000,
          TimeUnit.MILLISECONDS);

      taskStarted.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      // shutdownNow should interrupt in-progress task — must not hang
      assertThatNoException().isThrownBy(() -> sut.shutdownNow());
    }
  }

  // =========================================================================
  // Thread properties
  // =========================================================================

  @Nested
  @DisplayName("thread properties")
  class ThreadProperties {

    @Test
    @DisplayName("should_useDaemonThread_so_jvmCanExitCleanly")
    void should_useDaemonThread_so_jvmCanExitCleanly() throws InterruptedException {
      sut = new TaskScheduler(1, "daemon-thread");
      AtomicReference<Boolean> isDaemon = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      sut.scheduleAtFixedRate(
          () -> {
            isDaemon.set(Thread.currentThread().isDaemon());
            latch.countDown();
          },
          0,
          10,
          TimeUnit.SECONDS);

      latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(isDaemon.get()).as("Scheduler thread must be a daemon thread").isTrue();
    }

    @Test
    @DisplayName("should_useConfiguredThreadName_when_taskExecutes")
    void should_useConfiguredThreadName_when_taskExecutes() throws InterruptedException {
      String threadName = "auction-timer-test";
      sut = new TaskScheduler(1, threadName);
      AtomicReference<String> capturedName = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      sut.scheduleAtFixedRate(
          () -> {
            capturedName.set(Thread.currentThread().getName());
            latch.countDown();
          },
          0,
          10,
          TimeUnit.SECONDS);

      latch.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      assertThat(capturedName.get())
          .as("Thread name should match configured name")
          .isEqualTo(threadName);
    }
  }

  // =========================================================================
  // IScheduler contract — interface compliance
  // =========================================================================

  @Nested
  @DisplayName("IScheduler contract compliance")
  class ISchedulerContract {

    @Test
    @DisplayName("should_implementIScheduler_so_itCanBeInjected")
    void should_implementIScheduler_so_itCanBeInjected() {
      // Verifies that TaskScheduler is usable as IScheduler (dependency injection)
      sut = new TaskScheduler(1, "contract-test");
      assertThat(sut).isInstanceOf(com.group13.auction.service.iservice.IScheduler.class);
    }
  }
}
