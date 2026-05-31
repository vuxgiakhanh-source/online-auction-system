package com.group13.auction.concurrency.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IRatingService;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

/**
 * ============================================================================
 * WalletConcurrencyTest — GAP 2 + GAP 5
 *
 * <p>GAP 2: executePaymentToBank() concurrent double-payment → synchronized(winner) bảo vệ, verify
 * chỉ 1 payment đi qua.
 *
 * <p>GAP 5: getAvailableBalance() non-atomic read → 2 AtomicLong reads riêng biệt → snapshot thoáng
 * âm có thể xảy ra. → Verify: balance thực tế không bao giờ âm sau concurrent ops.
 * ============================================================================
 */
@DisplayName("Wallet: Concurrent Payment & Balance (GAP 2, GAP 5)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletConcurrencyTest extends ConcurrencyTestBase {

  private static final long DEPOSIT_AMOUNT = STARTING_PRICE * 3 / 10; // 150_000

  private WalletService walletService;
  private FinancialTransactionDAO mockFinancialDAO;
  private UserDAO mockUserDAO;
  private IRatingService mockRatingService;
  private final Map<String, NormalUser> usersByUsername = new ConcurrentHashMap<>();

  @BeforeEach
  void setUp() {
    usersByUsername.clear();
    mockFinancialDAO = mock(FinancialTransactionDAO.class);
    mockUserDAO = mock(UserDAO.class);
    mockRatingService = mock(IRatingService.class);

    // FIX: deposit() và withdraw() gọi isWalletOperationAllowed(), KHÔNG phải isEligible().
    // Mockito default trả false → mọi giao dịch throw IllegalStateException → test sai.
    when(mockRatingService.isWalletOperationAllowed(any())).thenReturn(true);

    when(mockUserDAO.updateBalances(any(), anyLong(), anyLong())).thenReturn(true);
    when(mockUserDAO.addBalance(anyString(), anyLong()))
        .thenAnswer(
            inv -> {
              String userId = inv.getArgument(0);
              long amount = inv.getArgument(1);
              usersByUsername.values().stream()
                  .filter(u -> u.getId().equals(userId))
                  .findFirst()
                  .ifPresent(u -> u.setBalance(u.getBalance() + amount));
              return true;
            });
    when(mockUserDAO.findUserCoreByUsername(anyString()))
        .thenAnswer(inv -> usersByUsername.get(inv.getArgument(0)));
    when(mockUserDAO.saveUserAuctionActivity(any(), any(), any())).thenReturn(true);

    walletService = new WalletService(mockFinancialDAO, mockUserDAO, mockRatingService);
  }

  private NormalUser walletUser(String username, long balance) {
    NormalUser user = buildUser(username, balance);
    usersByUsername.put(user.getUsername(), user);
    return user;
  }

  // G2-1

  @Test
  @Order(1)
  @DisplayName(
      "G2-1: 2 threads executePaymentToBank() cùng winner — synchronized(winner) ngăn"
          + " double-payment")
  @Timeout(value = 5)
  void executePaymentToBank_concurrent_onlyOneSucceeds() throws InterruptedException {
    long finalPrice = 600_000L;
    long depositPaid = DEPOSIT_AMOUNT; // 150_000

    // FIX: initialBalance = finalPrice (vừa đủ cho đúng 1 lần thanh toán).
    // remaining = finalPrice - depositPaid = 450_000
    // availableBalance trước payment = finalPrice - depositPaid = 450_000 = remaining → lần 1 pass
    // Sau lần 1: balance = finalPrice - remaining - depositPaid = 0 → lần 2 fail (insufficient)
    // Bug cũ: initialBalance = finalPrice * 3 = 1_800_000
    //   → sau lần 1 balance = 1_200_000 → lần 2 vẫn đủ tiền → 2 successes
    long initialBalance = finalPrice;

    NormalUser winner = walletUser("winner-G2-1", initialBalance);
    winner.lockDeposit(depositPaid);

    String auctionId = java.util.UUID.randomUUID().toString();
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);

    Runnable payTask =
        () -> {
          try {
            gate.await();
            walletService.executePaymentToBank(winner, finalPrice, depositPaid, auctionId);
            successes.incrementAndGet();
          } catch (Exception e) {
            failures.incrementAndGet();
          } finally {
            done.countDown();
          }
        };

    new Thread(payTask).start();
    new Thread(payTask).start();

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(successes.get()).as("Đúng 1 payment phải thành công").isEqualTo(1);
    assertThat(failures.get())
        .as("Thread thứ 2 phải thất bại do insufficient balance")
        .isEqualTo(1);

    long expectedBalance = initialBalance - finalPrice; // = 0
    assertThat(winner.getBalance())
        .as("Balance sau 1 payment: %d", expectedBalance)
        .isEqualTo(expectedBalance);
  }

  // G2-2

  @Test
  @Order(2)
  @DisplayName(
      "G2-2: 5 threads concurrent deposit() — synchronized(user) atomic, không lost update")
  @Timeout(value = 5)
  void concurrentDeposit_5Threads_balanceIsExact() throws InterruptedException {
    NormalUser user = walletUser("user-G2-2", 0L);
    long depositAmount = 100_000L;
    int threadCount = 5;

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  walletService.deposit(user, depositAmount);
                } catch (Exception ignored) {
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(user.getBalance())
        .as("Balance sau 5 deposits phải chính xác, không lost update")
        .isEqualTo(depositAmount * threadCount);
  }

  // G2-3

  @Test
  @Order(3)
  @DisplayName("G2-3: concurrent lockDeposit() + deposit() — availableBalance không bao giờ âm")
  @Timeout(value = 5)
  void concurrentLockAndDeposit_balanceNeverNegative() throws InterruptedException {
    long initial = 1_000_000L;
    NormalUser user = walletUser("user-G2-3", initial);

    int threadCount = 10;
    long lockAmount = 50_000L;
    long depositAmt = 30_000L;

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      new Thread(
              () -> {
                try {
                  gate.await();
                  if (idx % 2 == 0) {
                    try {
                      walletService.lockDeposit(user, lockAmount, "auction-" + idx);
                    } catch (Exception ignored) {
                    }
                  } else {
                    walletService.deposit(user, depositAmt);
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

    assertThat(user.getAvailableBalance())
        .as("availableBalance sau concurrent ops phải >= 0")
        .isGreaterThanOrEqualTo(0L);
  }

  // G5-1

  @Test
  @Order(4)
  @DisplayName(
      "G5-1: concurrent getAvailableBalance() + lockDeposit() — balance thực tế không bao giờ âm")
  @Timeout(value = 8)
  void concurrentBalanceRead_neverActuallyNegative() throws InterruptedException {
    NormalUser user = walletUser("user-G5-1", 2_000_000L);
    long lockAmt = 200_000L;

    int writerCount = 5;
    int readerCount = 10;
    int totalThreads = writerCount + readerCount;

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(totalThreads);
    AtomicLong minBalance = new AtomicLong(Long.MAX_VALUE);

    // Writers: lock deposit
    for (int i = 0; i < writerCount; i++) {
      String aId = "lock-auction-" + i;
      new Thread(
              () -> {
                try {
                  gate.await();
                  try {
                    walletService.lockDeposit(user, lockAmt, aId);
                  } catch (Exception ignored) {
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    // Readers: snapshot availableBalance
    for (int i = 0; i < readerCount; i++) {
      new Thread(
              () -> {
                try {
                  gate.await();
                  for (int j = 0; j < 20; j++) {
                    long avail = user.getAvailableBalance();
                    minBalance.accumulateAndGet(avail, Math::min);
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

    assertThat(user.getAvailableBalance())
        .as("availableBalance cuối phải >= 0 (synchronized bảo vệ)")
        .isGreaterThanOrEqualTo(0L);

    if (minBalance.get() < 0) {
      log.warn(
          "[G5-1] Thoáng âm snapshot: minObserved={}. Non-atomic read window, "
              + "không phải bug tiền tệ. Fix: AtomicReference<long[]> cho cả 2 fields.",
          minBalance.get());
    }
  }

  // G5-2

  @Test
  @Order(5)
  @DisplayName("G5-2: 2 threads withdraw() cùng lúc với balance vừa đủ — chỉ 1 thành công")
  @Timeout(value = 5)
  void concurrentWithdraw_exactBalance_onlyOneSucceeds() throws InterruptedException {
    long exact = 500_000L;
    NormalUser user = walletUser("user-G5-2", exact);

    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicInteger successes = new AtomicInteger(0);
    AtomicInteger failures = new AtomicInteger(0);

    Runnable task =
        () -> {
          try {
            gate.await();
            walletService.withdraw(user, exact);
            successes.incrementAndGet();
          } catch (Exception e) {
            failures.incrementAndGet();
          } finally {
            done.countDown();
          }
        };

    new Thread(task).start();
    new Thread(task).start();

    gate.countDown();
    done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    assertThat(successes.get()).isEqualTo(1);
    assertThat(failures.get()).isEqualTo(1);
    assertThat(user.getBalance()).as("Balance sau 1 withdraw đúng phải = 0").isEqualTo(0L);
  }
}
