package com.group13.auction.integration.service.payment;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.*;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.Auction.AuctionStatus;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.PaymentService;
import com.group13.auction.service.WalletService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Sandwich Integration Test — Payment & Auction State Transition.
 *
 * <p>Tầng trên (stub): AuctionDAO, AuctionWinnerDAO, UserDAO, FinancialTransactionDAO,
 * BidTransactionDAO, SecondChanceOfferDAO Tầng giữa (real): PaymentService ↔ WalletService ↔
 * AuctionService Tầng dưới (real): SystemBank, Auction, AuctionWinner domain objects
 *
 * <p>Luồng kiểm tra: RUNNING → closeAuction() → FINISHED → completePayment() → PAID
 *
 * <p>Lưu ý về balance: availableBalance = balance - lockedDeposit completePayment trừ: remaining =
 * finalPrice - depositPaid từ availableBalance → winner.balance phải >= remaining + depositPaid để
 * availableBalance >= remaining
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("[Integration] Payment & Auction State Transition (RUNNING → FINISHED → PAID)")
class PaymentStateTransitionIT {

  private static final long STARTING_PRICE = 2_000_000L;
  private static final long FINAL_PRICE = 5_000_000L;
  private static final long DEPOSIT = 600_000L; // 30% * 2_000_000

  @Mock AuctionDAO auctionDAO;
  @Mock AuctionWinnerDAO auctionWinnerDAO;
  @Mock UserDAO userDAO;
  @Mock FinancialTransactionDAO financialTransactionDAO;
  @Mock BidTransactionDAO bidTransactionDAO;
  @Mock SecondChanceOfferDAO secondChanceOfferDAO;

  IRatingService ratingService;
  AuctionService auctionService;
  WalletService walletService;
  PaymentService paymentService;

  NormalUser seller;
  NormalUser winner;
  Auction auction; // FINISHED sau setUp

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    TestFixture.resetSystemBankBalance();
    TestFixture.silenceGlobalSingletons();

    seller = TestFixture.normalSeller("sellerUser");
    // balance phải >= FINAL_PRICE để availableBalance >= remaining sau khi lock DEPOSIT
    winner = TestFixture.bidderWithBalance("winnerUser", FINAL_PRICE + DEPOSIT);
    winner.lockDeposit(DEPOSIT); // simulate đã joinAuction

    auction = TestFixture.runningAuction(seller, STARTING_PRICE);
    auction.updateBid(FINAL_PRICE, winner); // winner dẫn đầu, reserve met

    ratingService = TestFixture.ratingServiceAllowAll();
    walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
    // FIX: dùng 4-arg constructor để inject CẢ HAI mock (financialTransactionDAO + auctionWinnerDAO).
    // Constructor 2-arg cũ tự new FinancialTransactionDAO() + new AuctionWinnerDAO() thật:
    //  - real auctionWinnerDAO.saveWinner() INSERT vào DB không tồn tại → trả false
    //    → closeAuction() throw RuntimeException ("Không thể lưu AuctionWinner vào DB...")
    //    → @BeforeEach crash → toàn bộ 21 test trong class fail trước khi chạy assert.
    //  - real financialTransactionDAO.saveTransaction() trong recordWinnerDepositHeldInBank()
    //    cũng sẽ fail vì cùng lý do.
    auctionService =
        new AuctionService(ratingService, auctionDAO, financialTransactionDAO, auctionWinnerDAO);
    paymentService =
        new PaymentService(
            auctionService,
            ratingService,
            walletService,
            auctionWinnerDAO,
            secondChanceOfferDAO,
            bidTransactionDAO,
            userDAO);

    // Stub DAO calls fired by AuctionService.closeAuction()
    when(financialTransactionDAO.findLockedDepositAmount(winner.getId(), auction.getId()))
        .thenReturn(DEPOSIT);
    when(auctionDAO.updateAuctionStatus(anyString(), anyString())).thenReturn(true);
    when(auctionDAO.updateAuctionResult(any())).thenReturn(true);

    // FIX: stub thiếu → saveTransactionAndUpdatePrice trả false → IllegalStateException.
    // AuctionService.recordWinnerDepositHeldInBank() gọi
    // financialTransactionDAO.saveTransaction(tx)
    // để ghi audit trail cọc winner. Mock mặc định trả false → throw IllegalStateException
    // → toàn bộ test bị fail ở @BeforeEach trước khi test nào chạy được.
    when(financialTransactionDAO.saveTransaction(any())).thenReturn(true);

    // FIX: stub saveWinner() → true để AuctionService.closeAuction() TH2 (có winner)
    // không throw RuntimeException ở line 234. Đây là điều kiện CỐT LÕI để setUp()
    // hoàn thành transition RUNNING → FINISHED.
    when(auctionWinnerDAO.saveWinner(any())).thenReturn(true);

    auctionService.closeAuction(auction); // RUNNING → FINISHED

    assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FINISHED);
    assertThat(auction.getWinner()).isNotNull();
  }

  @AfterEach
  void tearDown() throws Exception {
    TestFixture.resetSystemAdmin();
    TestFixture.resetSystemBankBalance();
  }

  // =========================================================================
  // Happy Path
  // =========================================================================

  @Nested
  @DisplayName("Happy Path — thanh toán thành công")
  class HappyPath {

    @BeforeEach
    void stubPaymentDAOs() {
      // updatePaymentStatus vẫn được stub vì các flow khác (vd: expirePayment → EXPIRED)
      // có thể vẫn dùng. Strictness.LENIENT cho phép giữ stub không dùng.
      when(auctionWinnerDAO.updatePaymentStatus(anyString(), anyString())).thenReturn(true);
      // FIX: stub updateFundsHeld — production AuctionService.markAsPaid() gọi method này
      // để gộp set status FUNDS_HELD + set confirmReceiptDeadline thành 1 atomic call.
      // Thiếu stub này thì mock trả false; tuy không crash test (return value không check)
      // nhưng test winnerDAO_called_once_with_FUNDS_HELD verify method này phải được gọi.
      when(auctionWinnerDAO.updateFundsHeld(
          anyString(), anyString(), any(java.time.LocalDateTime.class)))
          .thenReturn(true);
      when(userDAO.updateBalances(anyString(), anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("Auction status FINISHED → PAID sau completePayment()")
    void auction_transitions_to_PAID() {
      paymentService.completePayment(auction);
      assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PAID);
    }

    @Test
    @DisplayName("AuctionWinner.paymentStatus → FUNDS_HELD sau completePayment()")
    void winner_paymentStatus_becomes_FUNDS_HELD() {
      paymentService.completePayment(auction);
      assertThat(auction.getWinner().getPaymentStatus()).isEqualTo(PaymentStatus.FUNDS_HELD);
    }

    @Test
    @DisplayName("lockedDeposit của winner = 0 sau thanh toán")
    void winner_lockedDeposit_is_zero() {
      paymentService.completePayment(auction);
      assertThat(winner.getLockedDeposit()).isZero();
    }

    @Test
    @DisplayName("Balance winner giảm đúng = remaining + deposit")
    void winner_balance_reduced_by_remaining_plus_deposit() {
      long balanceBefore = winner.getBalance();
      long remaining = FINAL_PRICE - DEPOSIT;

      paymentService.completePayment(auction);

      assertThat(winner.getBalance()).isEqualTo(balanceBefore - remaining - DEPOSIT);
    }

    @Test
    @DisplayName("SystemBank nhận đúng finalPrice (split-phase: deposit ở closeAuction + remaining ở completePayment)")
    void systemBank_receives_finalPrice() throws Exception {
      // FIX: Production split SystemBank credit thành 2 phase (xem comment ở
      // WalletService.executePaymentToBank line 340-349 và
      // AuctionService.recordWinnerDepositHeldInBank):
      //   - Phase 1 (closeAuction trong setUp): bank += depositPaid     (600_000)
      //   - Phase 2 (completePayment hiện tại): bank += remaining       (4_400_000)
      // Tổng credit cho phiên này = depositPaid + remaining = FINAL_PRICE.
      //
      // Lỗi cũ: test gọi TestFixture.resetSystemBankBalance() ngay đầu test
      // → xóa mất 600_000 đã ghi nhận ở phase 1 trong setUp
      // → chỉ đo được delta của phase 2 (4_400_000) → fail vì expect FINAL_PRICE.
      // Reset bank ở đây là sai bản chất: setUp ĐÃ commit phase 1 rồi.

      long bankBefore = SystemBank.getInstance().getTotalBalance(); // chứa DEPOSIT từ setUp
      long remaining = FINAL_PRICE - DEPOSIT;

      paymentService.completePayment(auction);

      long delta = SystemBank.getInstance().getTotalBalance() - bankBefore;

      assertAll(
          "Phase 2 chỉ chuyển remaining; tổng 2 phase = finalPrice",
          () -> assertThat(delta).as("Delta phase 2 = remaining").isEqualTo(remaining),
          () ->
              assertThat(bankBefore + delta)
                  .as("Tổng credit cho phiên này = finalPrice")
                  .isEqualTo(FINAL_PRICE));
    }

    @Test
    @DisplayName("confirmReceiptDeadline non-null sau FUNDS_HELD")
    void confirmReceiptDeadline_activated() {
      paymentService.completePayment(auction);
      assertThat(auction.getWinner().getConfirmReceiptDeadline()).isNotNull();
    }

    @Test
    @DisplayName("auctionWinnerDAO.updateFundsHeld gọi đúng 1 lần với FUNDS_HELD + deadline")
    void winnerDAO_called_once_with_FUNDS_HELD() {
      // FIX: Production đã refactor để gộp set-status + persist-deadline thành 1 atomic call
      // updateFundsHeld(id, status, deadline) — thay cho 2 call riêng lẻ trước đây
      // (updatePaymentStatus + setConfirmReceiptDeadline). Lý do refactor: xem comment ở
      // PaymentService.completePayment line 132-136 — gọi 2 lần làm reset deadline trễ 7 ngày.
      //
      // Test cũ verify updatePaymentStatus — đó là API CŨ, không còn được dùng nữa.
      // Refactor sang verify updateFundsHeld để khớp với production hiện tại.
      paymentService.completePayment(auction);
      verify(auctionWinnerDAO, times(1))
          .updateFundsHeld(
              eq(auction.getWinner().getId()),
              eq(PaymentStatus.FUNDS_HELD.name()),
              any(java.time.LocalDateTime.class));
    }

    @Test
    @DisplayName("userDAO.updateBalances gọi để persist balance winner")
    void userDAO_updateBalances_called() {
      paymentService.completePayment(auction);
      verify(userDAO, atLeastOnce()).updateBalances(eq(winner.getId()), anyLong(), anyLong());
    }
  }

  // =========================================================================
  // Failure Path
  // =========================================================================

  @Nested
  @DisplayName("Failure Path — thanh toán thất bại")
  class FailurePath {

    @Test
    @DisplayName("PaymentException khi winner không đủ tiền")
    void throws_PaymentException_when_insufficient() {
      // balance = 100_000 < remaining = 4_400_000 → fail
      NormalUser poor = TestFixture.bidderWithBalance("poorWinner", 100_000L);
      poor.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(poor);

      assertThatThrownBy(() -> paymentService.completePayment(a))
          .isInstanceOf(PaymentException.class);
    }

    @Test
    @DisplayName("Auction giữ nguyên FINISHED khi thất bại")
    void auction_remains_FINISHED_on_failure() {
      NormalUser poor = TestFixture.bidderWithBalance("poorWinner2", 50_000L);
      poor.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(poor);

      try {
        paymentService.completePayment(a);
      } catch (Exception ignored) {
      }

      assertThat(a.getStatus()).isEqualTo(AuctionStatus.FINISHED);
    }

    @Test
    @DisplayName("AuctionWinner.paymentStatus giữ nguyên PENDING khi thất bại")
    void winner_status_remains_PENDING_on_failure() {
      NormalUser poor = TestFixture.bidderWithBalance("poorWinner3", 0L);
      poor.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(poor);

      try {
        paymentService.completePayment(a);
      } catch (Exception ignored) {
      }

      assertThat(a.getWinner().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Balance winner không thay đổi khi thất bại (rollback)")
    void winner_balance_rolled_back_on_failure() {
      NormalUser poor = TestFixture.bidderWithBalance("poorWinner4", 100_000L);
      poor.lockDeposit(DEPOSIT);
      long before = poor.getBalance();
      Auction a = buildFinishedAuction(poor);

      try {
        paymentService.completePayment(a);
      } catch (Exception ignored) {
      }

      assertThat(poor.getBalance()).isEqualTo(before);
    }

    @Test
    @DisplayName("SystemBank không nhận tiền khi thất bại")
    void systemBank_unchanged_on_failure() throws Exception {
      TestFixture.resetSystemBankBalance();
      NormalUser poor = TestFixture.bidderWithBalance("poorWinner5", 0L);
      poor.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(poor);
      long bankBefore = SystemBank.getInstance().getTotalBalance();

      try {
        paymentService.completePayment(a);
      } catch (Exception ignored) {
      }

      assertThat(SystemBank.getInstance().getTotalBalance()).isEqualTo(bankBefore);
    }

    @Test
    @DisplayName("auctionDAO không được gọi với PAID khi thất bại")
    void auctionDAO_not_called_with_PAID_on_failure() {
      NormalUser poor = TestFixture.bidderWithBalance("poorWinner6", 0L);
      poor.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(poor);

      try {
        paymentService.completePayment(a);
      } catch (Exception ignored) {
      }

      verify(auctionDAO, never()).updateAuctionStatus(anyString(), eq(AuctionStatus.PAID.name()));
    }
  }

  // =========================================================================
  // Boundary Values
  // =========================================================================

  @Nested
  @DisplayName("Boundary Values — giá trị biên")
  class BoundaryValues {

    @BeforeEach
    void stubPaymentDAOs() {
      when(auctionWinnerDAO.updatePaymentStatus(anyString(), anyString())).thenReturn(true);
      when(userDAO.updateBalances(anyString(), anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("Thành công khi availableBalance == remaining (biên dưới đủ tiền)")
    void succeeds_when_availableBalance_equals_remaining_exactly() {
      long remaining = FINAL_PRICE - DEPOSIT; // 4_400_000
      // availableBalance = balance - lockedDeposit = (remaining + DEPOSIT) - DEPOSIT = remaining
      NormalUser exact = TestFixture.bidderWithBalance("exactWinner", remaining + DEPOSIT);
      exact.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(exact);

      assertThatCode(() -> paymentService.completePayment(a)).doesNotThrowAnyException();
      assertThat(a.getStatus()).isEqualTo(AuctionStatus.PAID);
    }

    @Test
    @DisplayName("PaymentException khi availableBalance = remaining - 1")
    void fails_when_availableBalance_one_less_than_remaining() {
      long remaining = FINAL_PRICE - DEPOSIT;
      // availableBalance = (remaining + DEPOSIT - 1) - DEPOSIT = remaining - 1 → không đủ
      NormalUser almost = TestFixture.bidderWithBalance("almostWinner", remaining + DEPOSIT - 1);
      almost.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(almost);

      assertThatThrownBy(() -> paymentService.completePayment(a))
          .isInstanceOf(PaymentException.class);
    }

    @Test
    @DisplayName("Thành công khi remaining = 0 (finalPrice == depositPaid)")
    void succeeds_when_remaining_is_zero() {
      // finalPrice = DEPOSIT → remaining = 0 → không cần thêm balance
      // availableBalance = DEPOSIT - DEPOSIT = 0 >= 0 → ok
      NormalUser zero = TestFixture.bidderWithBalance("zeroRemWinner", DEPOSIT);
      zero.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuctionWithPrice(zero, DEPOSIT);

      assertThatCode(() -> paymentService.completePayment(a)).doesNotThrowAnyException();
      assertThat(a.getStatus()).isEqualTo(AuctionStatus.PAID);
    }
  }

  // =========================================================================
  // State Integrity
  // =========================================================================

  @Nested
  @DisplayName("State Integrity — nhất quán trạng thái")
  class StateIntegrity {

    @BeforeEach
    void stubPaymentDAOs() {
      when(auctionWinnerDAO.updatePaymentStatus(anyString(), anyString())).thenReturn(true);
      when(userDAO.updateBalances(anyString(), anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("Auction.PAID và AuctionWinner.FUNDS_HELD nhất quán sau thanh toán")
    void auction_PAID_and_winner_FUNDS_HELD_consistent() {
      paymentService.completePayment(auction);
      assertAll(
          () -> assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PAID),
          () ->
              assertThat(auction.getWinner().getPaymentStatus())
                  .isEqualTo(PaymentStatus.FUNDS_HELD));
    }

    @Test
    @DisplayName("Auction.FINISHED và AuctionWinner.PENDING nhất quán sau thất bại")
    void auction_FINISHED_and_winner_PENDING_consistent_on_failure() {
      NormalUser poor = TestFixture.bidderWithBalance("consistencyPoor", 0L);
      poor.lockDeposit(DEPOSIT);
      Auction a = buildFinishedAuction(poor);

      try {
        paymentService.completePayment(a);
      } catch (Exception ignored) {
      }

      assertAll(
          () -> assertThat(a.getStatus()).isEqualTo(AuctionStatus.FINISHED),
          () -> assertThat(a.getWinner().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING));
    }

    @Test
    @DisplayName("completePayment() lần 2 trên auction PAID phải ném exception (idempotency)")
    void second_completePayment_throws() {
      paymentService.completePayment(auction);
      assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PAID);

      assertThatThrownBy(() -> paymentService.completePayment(auction))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("End-to-end: closeAuction() → completePayment() → PAID")
    void end_to_end_full_lifecycle() {
      NormalUser e2eWinner = TestFixture.bidderWithBalance("e2eWinner", FINAL_PRICE + DEPOSIT);
      e2eWinner.lockDeposit(DEPOSIT);
      Auction e2e =
          TestFixture.runningAuction(TestFixture.normalSeller("e2eSeller"), STARTING_PRICE);
      e2e.updateBid(FINAL_PRICE, e2eWinner);

      when(financialTransactionDAO.findLockedDepositAmount(e2eWinner.getId(), e2e.getId()))
          .thenReturn(DEPOSIT);
      auctionService.closeAuction(e2e);
      assertThat(e2e.getStatus()).isEqualTo(AuctionStatus.FINISHED);

      paymentService.completePayment(e2e);

      assertAll(
          () -> assertThat(e2e.getStatus()).isEqualTo(AuctionStatus.PAID),
          () -> assertThat(e2e.getWinner().getPaymentStatus()).isEqualTo(PaymentStatus.FUNDS_HELD),
          () -> assertThat(e2eWinner.getLockedDeposit()).isZero());
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /** Auction FINISHED với finalPrice = FINAL_PRICE, không qua closeAuction(). */
  private Auction buildFinishedAuction(NormalUser customWinner) {
    return buildFinishedAuctionWithPrice(customWinner, FINAL_PRICE);
  }

  private Auction buildFinishedAuctionWithPrice(NormalUser customWinner, long finalPrice) {
    Auction a = TestFixture.runningAuction(seller, STARTING_PRICE);
    a.updateBid(finalPrice, customWinner);
    AuctionWinner aw = AuctionWinner.create(customWinner, a.getId(), finalPrice, DEPOSIT, false);
    a.setWinner(aw);
    a.transitionToClose(true); // RUNNING → FINISHED
    return a;
  }
}