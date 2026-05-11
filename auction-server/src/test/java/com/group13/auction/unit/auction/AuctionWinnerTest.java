package com.group13.auction.unit.auction;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link AuctionWinner}.
 *
 * <p>Chiến lược xử lý time-based logic:
 * <ul>
 *   <li>Dùng {@link AuctionWinner#reconstitute} để inject deadline cụ thể thay vì
 *       phụ thuộc {@code LocalDateTime.now()} bên trong constructor — tránh hoàn toàn
 *       flaky test do race condition giữa test và system clock.</li>
 *   <li>Deadline "đã qua" = {@code now().minusHours(1)} — khoảng cách 1 giờ đủ lớn để
 *       không bao giờ bị lật ngược trong một lần chạy test.</li>
 *   <li>Deadline "chưa qua" = {@code now().plusHours(1)} — tương tự.</li>
 *   <li>Với {@code AuctionWinner.create()} (dùng {@code now()+24h} nội bộ): chỉ test
 *       hành vi ban đầu (PENDING, deadline trong tương lai), không test giá trị
 *       deadline chính xác vì đây là implementation detail.</li>
 * </ul>
 */
@DisplayName("AuctionWinner")
class AuctionWinnerTest {

    private NormalUser winner;
    private String auctionId;

    @BeforeEach
    void setUp() {
        winner    = TestFixture.normalBidder("bidderXX");
        auctionId = UUID.randomUUID().toString();
    }

    // =========================================================================
    // getRemainingAmount
    // =========================================================================

    @Nested
    @DisplayName("getRemainingAmount — số tiền còn phải trả")
    class GetRemainingAmountTest {

        @Test
        @DisplayName("finalPrice > depositPaid → remaining = finalPrice - depositPaid")
        void remainingAmount_whenDepositLessThanFinalPrice_returnsPositiveDifference() {
            // Arrange
            long finalPrice   = 1_000_000L;
            long depositPaid  = 300_000L;
            AuctionWinner w   = reconstitute(finalPrice, depositPaid);

            // Act
            long remaining = w.getRemainingAmount();

            // Assert
            assertEquals(700_000L, remaining);
        }

        @Test
        @DisplayName("depositPaid = 0 → remaining = finalPrice")
        void remainingAmount_whenNoDepositPaid_equalsFinalPrice() {
            // Arrange
            long finalPrice  = 500_000L;
            long depositPaid = 0L;
            AuctionWinner w  = reconstitute(finalPrice, depositPaid);

            // Act & Assert
            assertEquals(500_000L, w.getRemainingAmount());
        }

        @Test
        @DisplayName("depositPaid = finalPrice → remaining = 0 (đã cọc đủ)")
        void remainingAmount_whenDepositEqualsPrice_returnsZero() {
            // Arrange
            long finalPrice  = 800_000L;
            long depositPaid = 800_000L;
            AuctionWinner w  = reconstitute(finalPrice, depositPaid);

            // Act & Assert
            assertEquals(0L, w.getRemainingAmount());
        }

        @Test
        @DisplayName("depositPaid > finalPrice → remaining = 0 (không trả về âm)")
        void remainingAmount_whenDepositExceedsPrice_returnsZero() {
            // Arrange — trường hợp hiếm gặp nhưng phải guard bằng Math.max
            long finalPrice  = 500_000L;
            long depositPaid = 600_000L;
            AuctionWinner w  = reconstitute(finalPrice, depositPaid);

            // Act
            long remaining = w.getRemainingAmount();

            // Assert — Math.max(0, negative) = 0
            assertEquals(0L, remaining);
        }

        @Test
        @DisplayName("finalPrice = 0, depositPaid = 0 → remaining = 0")
        void remainingAmount_whenBothZero_returnsZero() {
            // Arrange
            AuctionWinner w = reconstitute(0L, 0L);

            // Act & Assert
            assertEquals(0L, w.getRemainingAmount());
        }

        @Test
        @DisplayName("finalPrice rất lớn (1 tỷ), deposit 30% → remaining = 70%")
        void remainingAmount_withLargeValues_correctPrecision() {
            // Arrange
            long finalPrice  = 1_000_000_000L;
            long depositPaid = 300_000_000L;
            AuctionWinner w  = reconstitute(finalPrice, depositPaid);

            // Act & Assert
            assertEquals(700_000_000L, w.getRemainingAmount());
        }

        @Test
        @DisplayName("remaining không thay đổi sau nhiều lần gọi (pure function)")
        void remainingAmount_calledMultipleTimes_returnsSameValue() {
            // Arrange
            AuctionWinner w = reconstitute(1_000_000L, 300_000L);

            // Act
            long first  = w.getRemainingAmount();
            long second = w.getRemainingAmount();
            long third  = w.getRemainingAmount();

            // Assert
            assertEquals(first, second);
            assertEquals(second, third);
        }
    }

    // =========================================================================
    // isExpired
    // =========================================================================

    @Nested
    @DisplayName("isExpired — quá hạn thanh toán 24h")
    class IsExpiredTest {

        @Test
        @DisplayName("PENDING + deadline đã qua → isExpired = true")
        void isExpired_whenPendingAndDeadlinePassed_returnsTrue() {
            // Arrange — deadline 1 giờ trước
            AuctionWinner w = TestFixture.expiredPendingWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertTrue(w.isExpired());
        }

        @Test
        @DisplayName("PENDING + deadline chưa qua → isExpired = false")
        void isExpired_whenPendingAndDeadlineNotYetPassed_returnsFalse() {
            // Arrange — deadline 1 giờ sau
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().plusHours(1),
                    AuctionWinner.PaymentStatus.PENDING);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("PENDING + deadline đúng bằng now → isExpired = false (not strictly after)")
        void isExpired_whenDeadlineIsExactlyNow_returnsFalse() {
            // Arrange — dùng plusNanos(1) để đảm bảo deadline thực sự sau now
            // Seeding deadline = now() tại thời điểm tạo: không thể test exactly-now
            // chính xác 100% với system clock. Thay vào đó verify rằng
            // deadline trong tương lai gần (1 giây) không bị xem là expired.
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().plusSeconds(1),
                    AuctionWinner.PaymentStatus.PENDING);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("COMPLETED + deadline đã qua → isExpired = false (không phải PENDING)")
        void isExpired_whenCompletedAndDeadlinePassed_returnsFalse() {
            // Arrange
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    AuctionWinner.PaymentStatus.COMPLETED);

            // Assert — chỉ PENDING mới expired
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("FUNDS_HELD + deadline đã qua → isExpired = false (không phải PENDING)")
        void isExpired_whenFundsHeldAndDeadlinePassed_returnsFalse() {
            // Arrange
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    AuctionWinner.PaymentStatus.FUNDS_HELD);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("EXPIRED status + deadline đã qua → isExpired = false (không phải PENDING)")
        void isExpired_whenStatusIsExpiredAndDeadlinePassed_returnsFalse() {
            // Arrange — status EXPIRED nhưng isExpired() kiểm tra PENDING
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    AuctionWinner.PaymentStatus.EXPIRED);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("CANCELLED + deadline đã qua → isExpired = false")
        void isExpired_whenCancelledAndDeadlinePassed_returnsFalse() {
            // Arrange
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    AuctionWinner.PaymentStatus.CANCELLED);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("mới create() → PENDING + deadline trong tương lai → isExpired = false")
        void isExpired_whenJustCreated_returnsFalse() {
            // Arrange — create() set paymentDeadline = now+24h nội bộ
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("isExpired thuần túy: không có side effect sau khi gọi")
        void isExpired_calledMultipleTimes_neverChangesState() {
            // Arrange
            AuctionWinner w = TestFixture.expiredPendingWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Act — gọi nhiều lần
            boolean first  = w.isExpired();
            boolean second = w.isExpired();

            // Assert — kết quả nhất quán, status không bị thay đổi
            assertEquals(first, second);
            assertEquals(AuctionWinner.PaymentStatus.PENDING, w.getPaymentStatus());
        }

        @Test
        @DisplayName("deadline quá khứ xa (30 ngày trước) → isExpired = true")
        void isExpired_whenDeadlineWasLongAgo_returnsTrue() {
            // Arrange
            AuctionWinner w = reconstituteWithDeadline(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusDays(30),
                    AuctionWinner.PaymentStatus.PENDING);

            // Assert
            assertTrue(w.isExpired());
        }
    }

    // =========================================================================
    // isConfirmReceiptOverdue
    // =========================================================================

    @Nested
    @DisplayName("isConfirmReceiptOverdue — quá hạn xác nhận nhận hàng")
    class IsConfirmReceiptOverdueTest {

        @Test
        @DisplayName("FUNDS_HELD + confirmReceiptDeadline đã qua → true")
        void isConfirmReceiptOverdue_whenFundsHeldAndDeadlinePassed_returnsTrue() {
            // Arrange
            AuctionWinner w = TestFixture.overdueConfirmReceiptWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertTrue(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD + confirmReceiptDeadline chưa qua → false")
        void isConfirmReceiptOverdue_whenFundsHeldAndDeadlineNotPassed_returnsFalse() {
            // Arrange
            AuctionWinner w = TestFixture.fundsHeldWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD + confirmReceiptDeadline = null → false (chưa xác nhận)")
        void isConfirmReceiptOverdue_whenFundsHeldButDeadlineNull_returnsFalse() {
            // Arrange — confirmReceiptDeadline = null: winner chưa thanh toán xong
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(30),
                    null,                               // confirmReceiptDeadline = null
                    null,
                    AuctionWinner.PaymentStatus.FUNDS_HELD,
                    false);

            // Assert
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("PENDING + confirmReceiptDeadline đã qua → false (không phải FUNDS_HELD)")
        void isConfirmReceiptOverdue_whenPendingAndDeadlinePassed_returnsFalse() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().minusHours(1), // deadline đã qua
                    null,
                    AuctionWinner.PaymentStatus.PENDING,
                    false);

            // Assert — PENDING không trigger isConfirmReceiptOverdue
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("COMPLETED + confirmReceiptDeadline đã qua → false (không phải FUNDS_HELD)")
        void isConfirmReceiptOverdue_whenCompletedAndDeadlinePassed_returnsFalse() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(30),
                    LocalDateTime.now().minusHours(1), // deadline đã qua
                    null,
                    AuctionWinner.PaymentStatus.COMPLETED,
                    false);

            // Assert
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("EXPIRED + confirmReceiptDeadline đã qua → false (không phải FUNDS_HELD)")
        void isConfirmReceiptOverdue_whenExpiredStatusAndDeadlinePassed_returnsFalse() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().minusHours(2),
                    LocalDateTime.now().minusHours(1), // deadline đã qua
                    null,
                    AuctionWinner.PaymentStatus.EXPIRED,
                    false);

            // Assert
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD + deadline 1 giây sau → false (chưa qua)")
        void isConfirmReceiptOverdue_whenDeadlineOneSecondInFuture_returnsFalse() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(30),
                    LocalDateTime.now().plusSeconds(1), // chưa qua
                    null,
                    AuctionWinner.PaymentStatus.FUNDS_HELD,
                    false);

            // Assert
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD + deadline 30 ngày trước → true (quá hạn lâu)")
        void isConfirmReceiptOverdue_whenDeadlineLongPast_returnsTrue() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(30),
                    LocalDateTime.now().minusDays(30), // deadline 30 ngày trước
                    null,
                    AuctionWinner.PaymentStatus.FUNDS_HELD,
                    false);

            // Assert
            assertTrue(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("isConfirmReceiptOverdue là pure: không có side effect")
        void isConfirmReceiptOverdue_calledMultipleTimes_noSideEffect() {
            // Arrange
            AuctionWinner w = TestFixture.overdueConfirmReceiptWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Act
            boolean first  = w.isConfirmReceiptOverdue();
            boolean second = w.isConfirmReceiptOverdue();

            // Assert
            assertEquals(first, second);
            assertEquals(AuctionWinner.PaymentStatus.FUNDS_HELD, w.getPaymentStatus());
        }
    }

    // =========================================================================
    // isReportDeadlineOverdue
    // =========================================================================

    @Nested
    @DisplayName("isReportDeadlineOverdue — quá hạn gửi report")
    class IsReportDeadlineOverdueTest {

        @Test
        @DisplayName("FUNDS_HELD + reportDeadline đã qua → true")
        void isReportDeadlineOverdue_whenFundsHeldAndReportDeadlinePassed_returnsTrue() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(30),
                    LocalDateTime.now().minusDays(2),
                    LocalDateTime.now().minusHours(1), // reportDeadline đã qua
                    AuctionWinner.PaymentStatus.FUNDS_HELD,
                    false);

            // Assert
            assertTrue(w.isReportDeadlineOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD + reportDeadline chưa qua → false")
        void isReportDeadlineOverdue_whenFundsHeldAndReportDeadlineNotPassed_returnsFalse() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(30),
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusHours(1), // reportDeadline chưa qua
                    AuctionWinner.PaymentStatus.FUNDS_HELD,
                    false);

            // Assert
            assertFalse(w.isReportDeadlineOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD + reportDeadline = null → false (chưa confirmReceipt)")
        void isReportDeadlineOverdue_whenReportDeadlineNull_returnsFalse() {
            // Arrange — confirmReceipt chưa được gọi nên reportDeadline = null
            AuctionWinner w = TestFixture.fundsHeldWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(w.isReportDeadlineOverdue());
        }

        @Test
        @DisplayName("PENDING + reportDeadline đã qua → false (không phải FUNDS_HELD)")
        void isReportDeadlineOverdue_whenPendingStatus_returnsFalse() {
            // Arrange
            AuctionWinner w = AuctionWinner.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    winner,
                    auctionId,
                    1_000_000L,
                    300_000L,
                    LocalDateTime.now().plusDays(1),
                    null,
                    LocalDateTime.now().minusHours(1), // reportDeadline đã qua
                    AuctionWinner.PaymentStatus.PENDING,
                    false);

            // Assert
            assertFalse(w.isReportDeadlineOverdue());
        }
    }

    // =========================================================================
    // State transitions: setPaymentStatus, markFundsHeld, confirmReceipt
    // =========================================================================

    @Nested
    @DisplayName("State transitions — setter và side effect")
    class StateTransitionTest {

        @Test
        @DisplayName("setPaymentStatus(COMPLETED) thay đổi status đúng")
        void setPaymentStatus_toCompleted_changesStatus() {
            // Arrange
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);
            assertEquals(AuctionWinner.PaymentStatus.PENDING, w.getPaymentStatus());

            // Act
            w.setPaymentStatus(AuctionWinner.PaymentStatus.COMPLETED);

            // Assert
            assertEquals(AuctionWinner.PaymentStatus.COMPLETED, w.getPaymentStatus());
        }

        @Test
        @DisplayName("markFundsHeld() → status = FUNDS_HELD, confirmReceiptDeadline != null")
        void markFundsHeld_setsStatusAndDeadline() {
            // Arrange
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Act
            w.markFundsHeld();

            // Assert
            assertEquals(AuctionWinner.PaymentStatus.FUNDS_HELD, w.getPaymentStatus());
            assertNotNull(w.getConfirmReceiptDeadline());
        }

        @Test
        @DisplayName("markFundsHeld() → confirmReceiptDeadline = ~7 ngày sau now")
        void markFundsHeld_setsConfirmReceiptDeadlineToSevenDays() {
            // Arrange
            AuctionWinner w  = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);
            LocalDateTime before = LocalDateTime.now().plusDays(7).minusSeconds(1);

            // Act
            w.markFundsHeld();

            LocalDateTime after = LocalDateTime.now().plusDays(7).plusSeconds(1);

            // Assert — deadline phải nằm trong khoảng [now+7d-1s, now+7d+1s]
            assertTrue(w.getConfirmReceiptDeadline().isAfter(before),
                    "confirmReceiptDeadline phải sau now+7d-1s");
            assertTrue(w.getConfirmReceiptDeadline().isBefore(after),
                    "confirmReceiptDeadline phải trước now+7d+1s");
        }

        @Test
        @DisplayName("markFundsHeld() → isConfirmReceiptOverdue() = false (deadline vừa set)")
        void markFundsHeld_confirmReceiptNotOverdueImmediately() {
            // Arrange
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Act
            w.markFundsHeld();

            // Assert — deadline 7 ngày sau nên chưa overdue
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("confirmReceipt() → reportDeadline != null (~3 ngày sau)")
        void confirmReceipt_setsReportDeadlineToThreeDays() {
            // Arrange
            AuctionWinner w  = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);
            w.markFundsHeld();
            LocalDateTime before = LocalDateTime.now().plusDays(3).minusSeconds(1);

            // Act
            w.confirmReceipt();

            LocalDateTime after = LocalDateTime.now().plusDays(3).plusSeconds(1);

            // Assert
            assertNotNull(w.getReportDeadline());
            assertTrue(w.getReportDeadline().isAfter(before));
            assertTrue(w.getReportDeadline().isBefore(after));
        }

        @Test
        @DisplayName("confirmReceipt() → isReportDeadlineOverdue() = false (deadline vừa set)")
        void confirmReceipt_reportDeadlineNotOverdueImmediately() {
            // Arrange
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);
            w.markFundsHeld();

            // Act
            w.confirmReceipt();

            // Assert
            assertFalse(w.isReportDeadlineOverdue());
        }

        @Test
        @DisplayName("sau setPaymentStatus(COMPLETED): isExpired = false")
        void afterSetCompleted_isExpiredReturnsFalse() {
            // Arrange — winner ban đầu đã expired
            AuctionWinner w = TestFixture.expiredPendingWinner(winner, auctionId, 1_000_000L, 300_000L);
            assertTrue(w.isExpired()); // precondition

            // Act
            w.setPaymentStatus(AuctionWinner.PaymentStatus.COMPLETED);

            // Assert — sau khi set COMPLETED, không còn expired nữa
            assertFalse(w.isExpired());
        }
    }

    // =========================================================================
    // create() — factory method contract
    // =========================================================================

    @Nested
    @DisplayName("create() — trạng thái ban đầu")
    class CreateTest {

        @Test
        @DisplayName("create() → paymentStatus = PENDING")
        void create_initialStatus_isPending() {
            // Arrange & Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertEquals(AuctionWinner.PaymentStatus.PENDING, w.getPaymentStatus());
        }

        @Test
        @DisplayName("create() → confirmReceiptDeadline = null")
        void create_confirmReceiptDeadline_isNull() {
            // Arrange & Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertNull(w.getConfirmReceiptDeadline());
        }

        @Test
        @DisplayName("create() → reportDeadline = null")
        void create_reportDeadline_isNull() {
            // Arrange & Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertNull(w.getReportDeadline());
        }

        @Test
        @DisplayName("create() → paymentDeadline ~24h sau now")
        void create_paymentDeadline_isApproximately24HoursFromNow() {
            // Arrange
            LocalDateTime before = LocalDateTime.now().plusHours(24).minusSeconds(1);

            // Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            LocalDateTime after = LocalDateTime.now().plusHours(24).plusSeconds(1);

            // Assert — deadline trong khoảng [now+24h-1s, now+24h+1s]
            assertTrue(w.getPaymentDeadline().isAfter(before));
            assertTrue(w.getPaymentDeadline().isBefore(after));
        }

        @Test
        @DisplayName("create() → isExpired = false (deadline chưa qua)")
        void create_isExpired_isFalseInitially() {
            // Arrange & Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertFalse(w.isExpired());
        }

        @Test
        @DisplayName("create() → isConfirmReceiptOverdue = false")
        void create_isConfirmReceiptOverdue_isFalseInitially() {
            // Arrange & Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("create() → winner, auctionId, finalPrice, depositPaid được lưu đúng")
        void create_storesAllFieldsCorrectly() {
            // Arrange
            long finalPrice  = 2_000_000L;
            long depositPaid = 600_000L;

            // Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, finalPrice, depositPaid, false);

            // Assert
            assertSame(winner, w.getWinner());
            assertEquals(auctionId, w.getAuctionId());
            assertEquals(finalPrice, w.getFinalPrice());
            assertEquals(depositPaid, w.getDepositPaid());
            assertFalse(w.getIsSecondOffer());
        }

        @Test
        @DisplayName("create() isSecondOffer = true → getIsSecondOffer() = true")
        void create_withSecondOfferFlag_storesCorrectly() {
            // Arrange & Act
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, true);

            // Assert
            assertTrue(w.getIsSecondOffer());
        }
    }

    // =========================================================================
    // Consistency: isExpired + isConfirmReceiptOverdue không đồng thời true
    // =========================================================================

    @Nested
    @DisplayName("Consistency — các trạng thái không xung đột nhau")
    class ConsistencyTest {

        @Test
        @DisplayName("winner mới create(): isExpired và isConfirmReceiptOverdue đều false")
        void newWinner_neitherExpiredNorConfirmOverdue() {
            // Arrange
            AuctionWinner w = AuctionWinner.create(winner, auctionId, 1_000_000L, 300_000L, false);

            // Assert
            assertFalse(w.isExpired());
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("PENDING expired: isExpired=true, isConfirmReceiptOverdue=false")
        void expiredPending_onlyIsExpiredIsTrue() {
            // Arrange
            AuctionWinner w = TestFixture.expiredPendingWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertTrue(w.isExpired());
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD confirmReceipt overdue: isExpired=false, isConfirmReceiptOverdue=true")
        void fundsHeldConfirmOverdue_onlyIsConfirmOverdueIsTrue() {
            // Arrange
            AuctionWinner w = TestFixture.overdueConfirmReceiptWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(w.isExpired());
            assertTrue(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("FUNDS_HELD normal: cả hai đều false")
        void fundsHeldNormal_neitherTrue() {
            // Arrange
            AuctionWinner w = TestFixture.fundsHeldWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(w.isExpired());
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("COMPLETED: cả hai đều false bất kể deadline")
        void completed_alwaysBothFalse() {
            // Arrange
            AuctionWinner w = TestFixture.completedWinner(winner, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(w.isExpired());
            assertFalse(w.isConfirmReceiptOverdue());
        }

        @Test
        @DisplayName("getRemainingAmount không bị ảnh hưởng bởi paymentStatus")
        void remainingAmount_independentOfPaymentStatus() {
            // Arrange — cùng finalPrice/deposit, status khác nhau
            long finalPrice  = 1_000_000L;
            long depositPaid = 300_000L;
            long expected    = 700_000L;

            AuctionWinner pending   = TestFixture.pendingWinner(winner, auctionId, finalPrice, depositPaid);
            AuctionWinner fundsHeld = TestFixture.fundsHeldWinner(winner, auctionId, finalPrice, depositPaid);
            AuctionWinner completed = TestFixture.completedWinner(winner, auctionId, finalPrice, depositPaid);

            // Assert — remaining không phụ thuộc status
            assertEquals(expected, pending.getRemainingAmount());
            assertEquals(expected, fundsHeld.getRemainingAmount());
            assertEquals(expected, completed.getRemainingAmount());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Tạo AuctionWinner PENDING với deadline trong tương lai (mặc định).
     * Dùng cho test getRemainingAmount không cần quan tâm đến time.
     */
    private AuctionWinner reconstitute(long finalPrice, long depositPaid) {
        return AuctionWinner.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                winner,
                auctionId,
                finalPrice,
                depositPaid,
                LocalDateTime.now().plusHours(24),
                null,
                null,
                AuctionWinner.PaymentStatus.PENDING,
                false);
    }

    /**
     * Tạo AuctionWinner với paymentDeadline và status tùy chỉnh.
     * Dùng cho các test isExpired() cần kiểm soát chính xác deadline.
     */
    private AuctionWinner reconstituteWithDeadline(long finalPrice, long depositPaid,
                                                   LocalDateTime paymentDeadline,
                                                   AuctionWinner.PaymentStatus status) {
        return AuctionWinner.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                winner,
                auctionId,
                finalPrice,
                depositPaid,
                paymentDeadline,
                null,
                null,
                status,
                false);
    }
}