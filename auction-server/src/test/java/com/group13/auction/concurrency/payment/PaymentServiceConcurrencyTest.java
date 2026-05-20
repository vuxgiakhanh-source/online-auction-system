package com.group13.auction.concurrency.payment;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.*;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * PaymentServiceConcurrencyTest — GAP-A (đã sửa)
 *
 * THAY ĐỔI SO VỚI BẢN CŨ:
 *   P1: Trước đây chỉ assert upper-bound (không sinh tiền).
 *       Sau đây ENFORCE: chỉ đúng 1 lần releaseToSeller() được chạy thực sự.
 *       FIX CẦN: bọc synchronized(seller) trong PaymentService.releaseToSeller()
 *       quanh đoạn read-modify-write: setBalance(getBalance() + payout).
 *
 *   P2: Trước đây không enforce — chỉ check <= 2×finalPrice.
 *       Sau đây ENFORCE: balance == finalPrice (đúng 1 lần refund).
 *       FIX CẦN: bọc synchronized(winner) trong refundToWinnerFromBank()
 *       quanh đoạn setBalance(getBalance() + finalPrice).
 *
 *   P3: Không thay đổi — WalletService.executePaymentToBank đã synchronized OK.
 *   P4: Không thay đổi — bounds check hợp lệ.
 * ============================================================================
 */
@DisplayName("Payment: Concurrent releaseToSeller & refundToWinner (GAP-A)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentServiceConcurrencyTest extends ConcurrencyTestBase {

    private PaymentService      paymentService;
    private WalletService       walletService;
    private IAuctionService     mockAuctionService;
    private IRatingService      mockRatingService;
    private AuctionWinnerDAO    mockAuctionWinnerDAO;
    private SecondChanceOfferDAO mockSecondChanceOfferDAO;
    private BidTransactionDAO   mockBidTransactionDAO;
    private UserDAO             mockUserDAO;
    private FinancialTransactionDAO mockFinancialDAO;

    @BeforeEach
    void setUp() {
        mockAuctionService        = mock(IAuctionService.class);
        mockRatingService         = mock(IRatingService.class);
        mockAuctionWinnerDAO      = mock(AuctionWinnerDAO.class);
        mockSecondChanceOfferDAO  = mock(SecondChanceOfferDAO.class);
        mockBidTransactionDAO     = mock(BidTransactionDAO.class);
        mockUserDAO               = mock(UserDAO.class);
        mockFinancialDAO          = mock(FinancialTransactionDAO.class);

        when(mockRatingService.isEligible(any())).thenReturn(true);
        when(mockUserDAO.updateBalances(any(), anyLong(), anyLong())).thenReturn(true);
        when(mockUserDAO.addBalance(any(), anyLong())).thenReturn(true);
        when(mockUserDAO.updateRating(any(), anyDouble())).thenReturn(true);
        when(mockAuctionWinnerDAO.updatePaymentStatus(any(), any())).thenReturn(true);
        when(mockFinancialDAO.saveTransaction(any())).thenReturn(true);

        walletService = new WalletService(mockFinancialDAO, mockUserDAO, mockRatingService);

        paymentService = new PaymentService(
                mockAuctionService,
                mockRatingService,
                walletService,
                mockAuctionWinnerDAO,
                mockSecondChanceOfferDAO,
                mockBidTransactionDAO,
                mockUserDAO
        );
    }

    // ── P1 ────────────────────────────────────────────────────────────────────
    // BUG ROOT: seller.setBalance(seller.getBalance() + payout) — 2 threads đọc
    //   cùng lúc, tính ra cùng giá trị, ghi đè nhau → sinh tiền ảo.
    // FIX CẦN: synchronized(seller) { seller.setBalance(seller.getBalance() + payout) }
    // EXPECTED SAU FIX: sellerBalance == expectedPayoutPerCall (1 lần duy nhất).
    //
    // LƯU Ý: releaseToSeller() được thiết kế gọi 1 lần (scheduler gọi sau 3 ngày).
    //   2 threads gọi đồng thời là lỗi orchestration, nhưng code phải tự bảo vệ.

    @Test
    @Order(1)
    @DisplayName("P1: releaseToSeller() concurrent 2 lần — balance phải = 1 lần payout [ENFORCE]")
    @Timeout(value = 5)
    void releaseToSeller_concurrent_exactlyOnePayout() throws InterruptedException {
        // finalPrice < 1_000_000 để đảm bảo rơi vào bracket TAX_RATE_LOW (5%)
        // Boundary: PRICE_TIER_LOW = 1_000_000 → salePrice < 1_000_000 mới là 5%
        long finalPrice           = 500_000L;
        long expectedPayoutPerCall = 500_000L - Math.round(500_000L * 0.05); // 475_000

        NormalUser seller = buildUser("seller-P1", 0L);
        NormalUser winner = buildUser("winner-P1", finalPrice * 2);
        winner.lockDeposit(finalPrice * 3 / 10);

        Auction auction = buildRunningAuction(finalPrice, finalPrice,
                java.time.LocalDateTime.now().minusMinutes(1));
        AuctionWinner aw = AuctionWinner.create(winner, auction.getId(), finalPrice,
                finalPrice * 3 / 10, false);
        aw.markFundsHeld();
        auction.setWinner(aw);
        injectSeller(auction, seller);

        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                gate.await();
                paymentService.releaseToSeller(auction);
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

        long actualBalance = seller.getBalance();

        log.info("[P1] seller.balance={}, expectedPerCall={}, successes={}, failures={}",
                actualBalance, expectedPayoutPerCall, successes.get(), failures.get());

        // ENFORCE: balance phải đúng 1 lần payout — không sinh tiền ảo
        assertThat(actualBalance)
                .as("Balance phải = đúng 1 lần payout (%d), không double", expectedPayoutPerCall)
                .isEqualTo(expectedPayoutPerCall);
    }

    // ── P2 ────────────────────────────────────────────────────────────────────
    // BUG ROOT: winner.setBalance(winner.getBalance() + finalPrice) — race condition.
    // FIX CẦN: synchronized(winner) quanh read-modify-write.
    // EXPECTED SAU FIX: balance == finalPrice (1 lần refund).

    @Test
    @Order(2)
    @DisplayName("P2: refundToWinnerFromBank() concurrent 2 lần — balance phải = đúng 1 lần [ENFORCE]")
    @Timeout(value = 5)
    void refundToWinnerFromBank_concurrent_exactlyOneRefund() throws InterruptedException {
        long finalPrice = 800_000L;

        NormalUser winner = buildUser("winner-P2", 0L);
        Auction auction = buildRunningAuction(finalPrice, finalPrice,
                java.time.LocalDateTime.now().minusMinutes(1));
        AuctionWinner aw = AuctionWinner.create(winner, auction.getId(), finalPrice,
                finalPrice * 3 / 10, false);
        aw.markFundsHeld();
        auction.setWinner(aw);

        // SystemBank cần đủ tiền để refund 2 lần (worst case)
        com.group13.auction.bank.SystemBank.getInstance().receive(finalPrice * 2);

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    gate.await();
                    paymentService.refundToWinnerFromBank(auction);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        long actualBalance = winner.getBalance();

        log.info("[P2] winner.balance={}, finalPrice={}, successes={}, failures={}",
                actualBalance, finalPrice, successes.get(), failures.get());

        // ENFORCE: balance == finalPrice (1 lần refund, không double)
        assertThat(actualBalance)
                .as("Balance phải = 1 lần finalPrice (%d), không double", finalPrice)
                .isEqualTo(finalPrice);

        // Guard phụ: không âm
        assertThat(actualBalance)
                .as("Balance không được âm")
                .isGreaterThanOrEqualTo(0L);
    }

    // ── P3 ────────────────────────────────────────────────────────────────────
    // Không thay đổi — WalletService.executePaymentToBank đã synchronized đúng.

    @Test
    @Order(3)
    @DisplayName("P3: completePayment() concurrent 2 threads — chỉ 1 lần thanh toán thành công")
    @Timeout(value = 5)
    void completePayment_concurrent_onlyOneSucceeds() throws InterruptedException {
        long finalPrice  = 600_000L;
        long depositPaid = finalPrice * 3 / 10; // 180_000
        long initialBalance = finalPrice; // vừa đủ 1 lần

        NormalUser winner = buildUser("winner-P3", initialBalance);
        winner.lockDeposit(depositPaid);

        Auction auction = buildRunningAuction(finalPrice, finalPrice,
                java.time.LocalDateTime.now().minusMinutes(1));
        AuctionWinner aw = AuctionWinner.create(winner, auction.getId(), finalPrice, depositPaid, false);
        auction.setWinner(aw);

        CountDownLatch gate     = new CountDownLatch(1);
        CountDownLatch done     = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                gate.await();
                paymentService.completePayment(auction);
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

        log.info("[P3] successes={} failures={} winnerBalance={}",
                successes.get(), failures.get(), winner.getBalance());

        assertThat(successes.get())
                .as("Đúng 1 payment phải thành công")
                .isEqualTo(1);
        assertThat(winner.getBalance())
                .as("Balance sau 1 payment phải = 0")
                .isEqualTo(0L);
    }

    // ── P4 ────────────────────────────────────────────────────────────────────
    // Không thay đổi logic, chỉ thêm assert chặt hơn.

    @Test
    @Order(4)
    @DisplayName("P4: releaseToSeller() + refundToWinnerFromBank() concurrent — balance bounds hợp lệ")
    @Timeout(value = 5)
    void releaseAndRefund_concurrent_balanceBoundsValid() throws InterruptedException {
        long finalPrice  = 500_000L;
        long depositPaid = finalPrice * 3 / 10;
        long payout      = finalPrice - Math.round(finalPrice * 0.05); // 475_000

        NormalUser seller = buildUser("seller-P4", 0L);
        NormalUser winner = buildUser("winner-P4", 0L);

        Auction auction = buildRunningAuction(finalPrice, finalPrice,
                java.time.LocalDateTime.now().minusMinutes(1));
        AuctionWinner aw = AuctionWinner.create(winner, auction.getId(), finalPrice, depositPaid, false);
        aw.markFundsHeld();
        auction.setWinner(aw);
        injectSeller(auction, seller);

        com.group13.auction.bank.SystemBank.getInstance().receive(finalPrice * 2);

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        new Thread(() -> {
            try { gate.await(); paymentService.releaseToSeller(auction); }
            catch (Exception ignored) {}
            finally { done.countDown(); }
        }).start();

        new Thread(() -> {
            try { gate.await(); paymentService.refundToWinnerFromBank(auction); }
            catch (Exception ignored) {}
            finally { done.countDown(); }
        }).start();

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(seller.getBalance())
                .as("Seller balance không được âm").isGreaterThanOrEqualTo(0L);
        assertThat(seller.getBalance())
                .as("Seller balance không vượt 1 payout").isLessThanOrEqualTo(payout);
        assertThat(winner.getBalance())
                .as("Winner balance không được âm").isGreaterThanOrEqualTo(0L);
        assertThat(winner.getBalance())
                .as("Winner balance không vượt finalPrice").isLessThanOrEqualTo(finalPrice);
    }

    // ── P5 ────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("P5: completePayment() vs expirePayment() concurrent — chỉ một luồng thắng")
    @Timeout(value = 5)
    void completePayment_vsExpirePayment_onlyOneOutcome() throws InterruptedException {
        long finalPrice  = 600_000L;
        long depositPaid = finalPrice * 3 / 10;

        NormalUser winner = buildUser("winner-P5", finalPrice);
        winner.lockDeposit(depositPaid);

        Auction auction = buildRunningAuction(finalPrice, finalPrice,
                java.time.LocalDateTime.now().minusHours(2));
        auction.updateBid(finalPrice, winner);
        AuctionWinner aw = AuctionWinner.reconstitute(
                java.util.UUID.randomUUID().toString(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                winner,
                auction.getId(),
                finalPrice,
                depositPaid,
                java.time.LocalDateTime.now().minusMinutes(5),
                null,
                null,
                AuctionWinner.PaymentStatus.PENDING,
                false);
        auction.setWinner(aw);
        auction.transitionToClose(true);

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger completeOk = new AtomicInteger(0);
        AtomicInteger expireRan  = new AtomicInteger(0);

        new Thread(() -> {
            try {
                gate.await();
                paymentService.completePayment(auction);
                completeOk.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        }).start();

        new Thread(() -> {
            try {
                gate.await();
                paymentService.expirePayment(auction);
                if (aw.getPaymentStatus() == AuctionWinner.PaymentStatus.EXPIRED) {
                    expireRan.incrementAndGet();
                }
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        }).start();

        gate.countDown();
        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

        boolean paid = auction.getStatus() == com.group13.auction.model.auction.Auction.AuctionStatus.PAID;
        boolean expired = aw.getPaymentStatus() == AuctionWinner.PaymentStatus.EXPIRED;

        assertThat(paid && expired)
                .as("Không được vừa PAID (auction) vừa EXPIRED (winner) sau race")
                .isFalse();
        if (paid) {
            assertThat(expired).isFalse();
            assertThat(completeOk.get()).isEqualTo(1);
        } else if (expired) {
            assertThat(paid).isFalse();
            assertThat(expireRan.get()).isEqualTo(1);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void injectSeller(Auction auction, NormalUser seller) {
        try {
            java.lang.reflect.Field itemField = auction.getClass().getDeclaredField("item");
            itemField.setAccessible(true);
            Object item = itemField.get(auction);

            java.lang.reflect.Field sellerField =
                    item.getClass().getSuperclass().getDeclaredField("seller");
            sellerField.setAccessible(true);
            sellerField.set(item, seller);
        } catch (Exception e) {
            log.warn("[TEST] injectSeller failed: {}", e.getMessage());
        }
    }
}