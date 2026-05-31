package com.group13.auction.concurrency.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.concurrency.ConcurrencyTestBase;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;

/**
 * ============================================================================
 * SystemBankConcurrencyTest — GAP-B
 *
 * <p>SystemBank dùng đồng thời cả synchronized method lẫn AtomicLong.addAndGet() bên trong — hai cơ
 * chế chồng chéo. Bộ test này verify:
 *
 * <p>B1: N threads receive() đồng thời → totalBalance chính xác (không lost update). B2: receive()
 * + payoutToSeller() concurrent → balance không âm sai. B3: receive() + refundToWinner() +
 * receiveForfeittedDeposit() hỗn hợp → net balance khớp với tổng đại số các operations. B4:
 * getTotalBalance() thread-safe — không bao giờ trả về giá trị ngoài khoảng [min_possible,
 * max_possible]. ============================================================================
 */
@DisplayName("SystemBank: Concurrent Operations (GAP-B)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemBankConcurrencyTest extends ConcurrencyTestBase {

  private SystemBank bank;

  @BeforeEach
  void setUp() throws Exception {
    bank = SystemBank.getInstance();
    // Reset totalBalance về 0 trước mỗi test bằng reflection
    Field balanceField = SystemBank.class.getDeclaredField("totalBalance");
    balanceField.setAccessible(true);
    ((java.util.concurrent.atomic.AtomicLong) balanceField.get(bank)).set(0L);
  }

  // B1

  @Test
  @Order(1)
  @DisplayName("B1: 20 threads receive() đồng thời — totalBalance chính xác, không lost update")
  @Timeout(value = 5)
  void concurrentReceive_balanceIsExact() throws InterruptedException {
    int threadCount = 20;
    long amount = 100_000L;
    long expected = threadCount * amount;

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.receive(amount);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(bank.getTotalBalance())
        .as("totalBalance sau %d lần receive(%d) phải = %d", threadCount, amount, expected)
        .isEqualTo(expected);
  }

  // B2

  @Test
  @Order(2)
  @DisplayName(
      "B2: receive() + payoutToSeller() concurrent — balance không âm, net value chính xác")
  @Timeout(value = 5)
  void concurrentReceiveAndPayout_balanceNeverNegative() throws InterruptedException {
    long salePrice = 500_000L;
    long tax = bank.calculateTax(salePrice); // 25_000 (5%)
    long payout = salePrice - tax; // 475_000

    int receiveCount = 10;
    int payoutCount = 5;
    int totalThreads = receiveCount + payoutCount;

    // Nạp đủ tiền trước để payout không âm
    bank.receive(salePrice * payoutCount);
    long baseline = bank.getTotalBalance();

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(totalThreads);
    AtomicLong netDelta = new AtomicLong(0L);

    for (int i = 0; i < receiveCount; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.receive(salePrice);
                  netDelta.addAndGet(salePrice);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    for (int i = 0; i < payoutCount; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.payoutToSeller(salePrice);
                  netDelta.addAndGet(-payout);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    long expectedFinal = baseline + netDelta.get();

    assertThat(bank.getTotalBalance())
        .as(
            "totalBalance phải = baseline(%d) + netDelta(%d) = %d",
            baseline, netDelta.get(), expectedFinal)
        .isEqualTo(expectedFinal);

    assertThat(bank.getTotalBalance()).as("totalBalance không được âm").isGreaterThanOrEqualTo(0L);
  }

  // B3

  @Test
  @Order(3)
  @DisplayName(
      "B3: receive() + refundToWinner() + receiveForfeittedDeposit() hỗn hợp — net balance khớp")
  @Timeout(value = 8)
  void mixedConcurrentOps_netBalanceCorrect() throws InterruptedException {
    long receiveAmt = 200_000L;
    long refundAmt = 150_000L;
    long forfeitAmt = 50_000L;

    int nReceive = 10;
    int nRefund = 5;
    int nForfeit = 5;
    int total = nReceive + nRefund + nForfeit;

    // Pre-load đủ tiền để refund không làm âm
    bank.receive(refundAmt * nRefund);
    long baseline = bank.getTotalBalance();

    // Net delta theo thiết kế
    long expectedNet =
        (long) nReceive * receiveAmt - (long) nRefund * refundAmt + (long) nForfeit * forfeitAmt;

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(total);

    for (int i = 0; i < nReceive; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.receive(receiveAmt);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }
    for (int i = 0; i < nRefund; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.refundToWinner(refundAmt);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }
    for (int i = 0; i < nForfeit; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.receiveForfeittedDeposit(forfeitAmt);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(bank.getTotalBalance())
        .as("Net balance phải = baseline(%d) + expectedNet(%d)", baseline, expectedNet)
        .isEqualTo(baseline + expectedNet);
  }

  // B4

  @Test
  @Order(4)
  @DisplayName(
      "B4: getTotalBalance() trong khi receive() đang chạy — giá trị luôn trong khoảng hợp lệ")
  @Timeout(value = 8)
  void getTotalBalance_duringConcurrentWrite_alwaysInBounds() throws InterruptedException {
    long receiveAmt = 100_000L;
    int writers = 10;
    int readers = 20;
    int total = writers + readers;
    long initialBalance = bank.getTotalBalance();
    long maxPossible = initialBalance + (long) writers * receiveAmt;

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(total);
    AtomicLong minObserved = new AtomicLong(Long.MAX_VALUE);
    AtomicLong maxObserved = new AtomicLong(Long.MIN_VALUE);

    for (int i = 0; i < writers; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  bank.receive(receiveAmt);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    for (int i = 0; i < readers; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  for (int j = 0; j < 10; j++) {
                    long v = bank.getTotalBalance();
                    minObserved.accumulateAndGet(v, Math::min);
                    maxObserved.accumulateAndGet(v, Math::max);
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(minObserved.get())
        .as("Không bao giờ đọc được giá trị nhỏ hơn initialBalance")
        .isGreaterThanOrEqualTo(initialBalance);
    assertThat(maxObserved.get())
        .as("Không bao giờ đọc được giá trị lớn hơn maxPossible")
        .isLessThanOrEqualTo(maxPossible);
    assertThat(bank.getTotalBalance())
        .as("Balance cuối = initialBalance + writers*receiveAmt")
        .isEqualTo(maxPossible);
  }
}
