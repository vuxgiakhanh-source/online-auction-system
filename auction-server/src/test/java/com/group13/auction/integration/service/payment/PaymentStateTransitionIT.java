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

    seller = TestFixture.normalSeller("sellerUser");
    // balance phải >= FINAL_PRICE để availableBalance >= remaining sau khi lock DEPOSIT
    winner = TestFixture.bidderWithBalance("winnerUser", FINAL_PRICE + DEPOSIT);
    winner.lockDeposit(DEPOSIT); // simulate đã joinAuction

    auction = TestFixture.runningAuction(seller, STARTING_PRICE);
    auction.updateBid(FINAL_PRICE, winner); // winner dẫn đầu, reserve met

    ratingService = TestFixture.ratingServiceAllowAll();
    walletService = new WalletService(financialTransactionDAO, userDAO, ratingService);
    auctionService = new AuctionService(ratingService, auctionDAO);
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
      when(auctionWinnerDAO.updatePaymentStatus(anyString(), anyString())).thenReturn(true);
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
    @DisplayName("SystemBank nhận đúng finalPrice")
    void systemBank_receives_finalPrice() throws Exception {
      TestFixture.resetSystemBankBalance();
      long bankBefore = SystemBank.getInstance().getTotalBalance();

      paymentService.completePayment(auction);

      assertThat(SystemBank.getInstance().getTotalBalance() - bankBefore).isEqualTo(FINAL_PRICE);
    }

    @Test
    @DisplayName("confirmReceiptDeadline non-null sau FUNDS_HELD")
    void confirmReceiptDeadline_activated() {
      paymentService.completePayment(auction);
      assertThat(auction.getWinner().getConfirmReceiptDeadline()).isNotNull();
    }

    @Test
    @DisplayName("auctionWinnerDAO.updatePaymentStatus gọi đúng 1 lần với FUNDS_HELD")
    void winnerDAO_called_once_with_FUNDS_HELD() {
      paymentService.completePayment(auction);
      verify(auctionWinnerDAO, times(1))
          .updatePaymentStatus(
              eq(auction.getWinner().getId()), eq(PaymentStatus.FUNDS_HELD.name()));
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
