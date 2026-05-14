package com.group13.auction.stress.payment;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.RatingService;
import com.group13.auction.service.WalletService;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Stress test WalletService — mock DAO, không cần DB.
 *
 * WalletService.lockDeposit  : (NormalUser, long depositAmount, String auctionId)
 * WalletService.unlockDeposit: (NormalUser, long depositAmount, String auctionId)
 * UserDAO.updateBalances     : (String userId, long balance, long lockedBalance)
 * FinancialTransactionDAO.saveTransaction: (FinancialTransaction)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Stress — mock DAO, lock/unlock đồng thời")
class WalletServiceStressTest extends ConcurrencyTestBase {

    private static final int  USER_COUNT   = 20;
    private static final int  OPS_PER_USER = 10;
    private static final long DEPOSIT_AMT  = 500_000L;
    private static final int  TIMEOUT_SEC  = 30;

    @Mock FinancialTransactionDAO financialTransactionDAO;
    @Mock UserDAO                 userDAO;
    @Mock RatingService           ratingService;

    private WalletService    walletService;
    private List<NormalUser> users;

    @BeforeEach
    void setUp() throws Exception {                          // throws Exception — bootstrapSystemAdmin cần
        TestFixture.bootstrapSystemAdmin();
        lenient().when(userDAO.updateBalances(anyString(), anyLong(), anyLong())).thenReturn(true);
        lenient().when(financialTransactionDAO.saveTransaction(any())).thenReturn(true);
        lenient().when(ratingService.isEligible(any())).thenReturn(true);

        walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);

        users = new ArrayList<>();
        for (int i = 0; i < USER_COUNT; i++) {
            users.add(buildUser("stress_user_" + i, USER_BALANCE));
        }
    }

    @AfterEach
    void tearDown() throws Exception {                      // throws Exception — resetSystemAdmin cần
        TestFixture.resetSystemAdmin();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 20 user × 10 lockDeposit đồng thời, không deadlock, không NPE")
    void stress_concurrentLockDeposit_noDeadlock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger unexpectedErrors = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (NormalUser user : users) {
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                for (int i = 0; i < OPS_PER_USER; i++) {
                    try {
                        walletService.lockDeposit(user, DEPOSIT_AMT, "auction-stress");
                    } catch (AuctionBusinessException ignored) {
                        // INSUFFICIENT_DEPOSIT — expected khi hết số dư
                    } catch (Exception e) {
                        unexpectedErrors.incrementAndGet();
                        log.warn("[STRESS WALLET] Unexpected: {} — {}",
                                e.getClass().getSimpleName(), e.getMessage());
                    }
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpectedErrors.get())
                .as("Chỉ AuctionBusinessException được phép — không có NPE hay lỗi khác")
                .isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — lock/unlock xen kẽ, availableBalance không bao giờ âm")
    void stress_lockUnlockInterleaved_balanceNeverNegative() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (NormalUser user : users) {
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                for (int i = 0; i < 5; i++) {
                    try { walletService.lockDeposit(user, DEPOSIT_AMT, "auction-A"); }
                    catch (AuctionBusinessException ignored) {}
                }
                for (int i = 0; i < 5; i++) {
                    try { walletService.unlockDeposit(user, DEPOSIT_AMT, "auction-A"); }
                    catch (Exception ignored) {}
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (NormalUser user : users) {
            assertThat(user.getAvailableBalance())
                    .as("User %s: availableBalance không được âm", user.getUsername())
                    .isGreaterThanOrEqualTo(0L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Stress — 100 thread cùng lockDeposit 1 user, lockedDeposit ≤ balance")
    void stress_oneUser_manyThreadsLock_lockedDepositNotExceedBalance() throws Exception {
        NormalUser target = buildUser("single_target", USER_BALANCE);
        int THREADS = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
                try { walletService.lockDeposit(target, DEPOSIT_AMT, "auction-B"); }
                catch (AuctionBusinessException ignored) {}
                catch (Exception e) {
                    log.warn("[STRESS WALLET] Unexpected on single user: {} — {}",
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(target.getLockedDeposit())
                .as("lockedDeposit không vượt balance gốc")
                .isLessThanOrEqualTo(USER_BALANCE);
        assertThat(target.getAvailableBalance())
                .as("availableBalance không âm")
                .isGreaterThanOrEqualTo(0L);
    }
}
