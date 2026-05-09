package com.group13.auction.auction;

import com.group13.auction.TestFixture;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link SecondChanceOffer}.
 *
 * <p>Chiến lược tránh flaky test với time-based logic:
 * <ul>
 *   <li>Dùng {@link SecondChanceOffer#reconstitute} để inject deadline cụ thể —
 *       deadline "đã qua" = {@code now().minusHours(1)}, "chưa qua" = {@code now().plusHours(1)}.
 *       Khoảng cách 1 giờ đủ lớn để không bao giờ bị race condition trong suốt một lần
 *       chạy test suite.</li>
 *   <li>Với {@code SecondChanceOffer.create()} (inject {@code now()+24h} nội bộ): chỉ
 *       test hành vi quan sát được (trạng thái ban đầu, deadline trong tương lai),
 *       không assert giá trị deadline chính xác vì đó là implementation detail.</li>
 *   <li>Mỗi test tạo object riêng — không share mutable state.</li>
 * </ul>
 *
 * <p>Contract của {@code SecondChanceOffer}:
 * <pre>
 *   getRemainingAmount() = Math.max(0, offerPrice - depositPaid)
 *   isExpired()          = now.isAfter(deadline) AND status == PENDING
 *   setStatus()          = thay đổi status, cập nhật updatedAt
 * </pre>
 */
@DisplayName("SecondChanceOffer")
class SecondChanceOfferTest {

    private NormalUser runnerUp;
    private String     auctionId;

    @BeforeEach
    void setUp() {
        runnerUp  = TestFixture.normalBidder("runnerUp1");
        auctionId = UUID.randomUUID().toString();
    }

    // =========================================================================
    // getRemainingAmount
    // =========================================================================

    @Nested
    @DisplayName("getRemainingAmount — số tiền còn phải trả nếu chấp nhận")
    class GetRemainingAmountTest {

        @Test
        @DisplayName("offerPrice > depositPaid → remaining = offerPrice − depositPaid")
        void remaining_whenDepositLessThanOffer_returnsPositiveDifference() {
            // Arrange
            SecondChanceOffer offer = reconstitute(1_000_000L, 300_000L);

            // Act
            long remaining = offer.getRemainingAmount();

            // Assert
            assertEquals(700_000L, remaining);
        }

        @Test
        @DisplayName("depositPaid = 0 → remaining = offerPrice")
        void remaining_whenNoDepositPaid_equalsOfferPrice() {
            // Arrange
            SecondChanceOffer offer = reconstitute(500_000L, 0L);

            // Act & Assert
            assertEquals(500_000L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("depositPaid = offerPrice → remaining = 0 (cọc đủ)")
        void remaining_whenDepositEqualsOfferPrice_returnsZero() {
            // Arrange
            SecondChanceOffer offer = reconstitute(800_000L, 800_000L);

            // Act & Assert
            assertEquals(0L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("depositPaid > offerPrice → remaining = 0 (không trả về âm)")
        void remaining_whenDepositExceedsOfferPrice_returnsZero() {
            // Arrange — Math.max(0, negative) = 0
            SecondChanceOffer offer = reconstitute(500_000L, 600_000L);

            // Act
            long remaining = offer.getRemainingAmount();

            // Assert
            assertEquals(0L, remaining);
        }

        @Test
        @DisplayName("offerPrice = 0, depositPaid = 0 → remaining = 0")
        void remaining_whenBothZero_returnsZero() {
            // Arrange
            SecondChanceOffer offer = reconstitute(0L, 0L);

            // Act & Assert
            assertEquals(0L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("offerPrice = 1, depositPaid = 0 → remaining = 1 (tối thiểu dương)")
        void remaining_minPositiveOfferPrice_returnsOne() {
            // Arrange
            SecondChanceOffer offer = reconstitute(1L, 0L);

            // Act & Assert
            assertEquals(1L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("offerPrice = 1, depositPaid = 1 → remaining = 0 (ranh giới bằng nhau)")
        void remaining_depositEqualsMinimumOffer_returnsZero() {
            // Arrange
            SecondChanceOffer offer = reconstitute(1L, 1L);

            // Act & Assert
            assertEquals(0L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("giá trị lớn (1 tỷ), deposit 30% → remaining = 70%")
        void remaining_withLargeValues_correctPrecision() {
            // Arrange
            long offerPrice  = 1_000_000_000L;
            long depositPaid = 300_000_000L;
            SecondChanceOffer offer = reconstitute(offerPrice, depositPaid);

            // Act & Assert
            assertEquals(700_000_000L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("getRemainingAmount là pure function — nhiều lần gọi cho cùng kết quả")
        void remaining_calledMultipleTimes_returnsSameValue() {
            // Arrange
            SecondChanceOffer offer = reconstitute(1_000_000L, 300_000L);

            // Act
            long first  = offer.getRemainingAmount();
            long second = offer.getRemainingAmount();
            long third  = offer.getRemainingAmount();

            // Assert
            assertEquals(first, second);
            assertEquals(second, third);
        }

        @Test
        @DisplayName("setStatus() không ảnh hưởng đến getRemainingAmount")
        void remaining_unchangedAfterStatusChange() {
            // Arrange
            SecondChanceOffer offer = reconstitute(1_000_000L, 300_000L);
            long before = offer.getRemainingAmount();

            // Act — thay đổi status
            offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

            // Assert
            assertEquals(before, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("remaining nhất quán giữa các OfferStatus khác nhau")
        void remaining_sameValue_acrossAllStatuses() {
            // Arrange
            long offerPrice  = 1_000_000L;
            long depositPaid = 300_000L;
            long expected    = 700_000L;

            SecondChanceOffer pending  = TestFixture.pendingOffer(runnerUp, auctionId, offerPrice, depositPaid);
            SecondChanceOffer accepted = TestFixture.acceptedOffer(runnerUp, auctionId, offerPrice, depositPaid);
            SecondChanceOffer declined = TestFixture.declinedOffer(runnerUp, auctionId, offerPrice, depositPaid);
            SecondChanceOffer expired  = reconstituteWithStatus(offerPrice, depositPaid,
                    LocalDateTime.now().minusHours(1), SecondChanceOffer.OfferStatus.EXPIRED);

            // Assert — remaining không phụ thuộc status
            assertEquals(expected, pending.getRemainingAmount());
            assertEquals(expected, accepted.getRemainingAmount());
            assertEquals(expected, declined.getRemainingAmount());
            assertEquals(expected, expired.getRemainingAmount());
        }
    }

    // =========================================================================
    // isExpired
    // =========================================================================

    @Nested
    @DisplayName("isExpired — hết 24h quyết định")
    class IsExpiredTest {

        // -- Điều kiện cần: PENDING ------------------------------------------

        @Test
        @DisplayName("PENDING + deadline đã qua → isExpired = true")
        void isExpired_whenPendingAndDeadlinePassed_returnsTrue() {
            // Arrange
            SecondChanceOffer offer = TestFixture.expiredPendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertTrue(offer.isExpired());
        }

        @Test
        @DisplayName("PENDING + deadline chưa qua → isExpired = false")
        void isExpired_whenPendingAndDeadlineNotPassed_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().plusHours(1),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("PENDING + deadline 1 giây sau → isExpired = false (chưa qua)")
        void isExpired_whenDeadlineOneSecondInFuture_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().plusSeconds(1),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("PENDING + deadline 30 ngày trước → isExpired = true (quá hạn lâu)")
        void isExpired_whenDeadlineLongPast_returnsTrue() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusDays(30),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert
            assertTrue(offer.isExpired());
        }

        // -- Điều kiện đủ: status phải là PENDING ---------------------------

        @Test
        @DisplayName("ACCEPTED + deadline đã qua → isExpired = false (không phải PENDING)")
        void isExpired_whenAcceptedAndDeadlinePassed_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    SecondChanceOffer.OfferStatus.ACCEPTED);

            // Assert
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("DECLINED + deadline đã qua → isExpired = false")
        void isExpired_whenDeclinedAndDeadlinePassed_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    SecondChanceOffer.OfferStatus.DECLINED);

            // Assert
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("EXPIRED status + deadline đã qua → isExpired = false (không phải PENDING)")
        void isExpired_whenStatusIsExpiredAndDeadlinePassed_returnsFalse() {
            // Arrange — status EXPIRED nhưng isExpired() chỉ kiểm tra PENDING
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusHours(1),
                    SecondChanceOffer.OfferStatus.EXPIRED);

            // Assert
            assertFalse(offer.isExpired());
        }

        // -- create() mặc định không expired --------------------------------

        @Test
        @DisplayName("create() → isExpired = false (deadline 24h trong tương lai)")
        void isExpired_whenJustCreated_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(offer.isExpired());
        }

        // -- Sau setStatus() ------------------------------------------------

        @Test
        @DisplayName("PENDING expired → setStatus(ACCEPTED) → isExpired = false")
        void isExpired_afterSetStatusToAccepted_returnsFalse() {
            // Arrange — bắt đầu là PENDING expired
            SecondChanceOffer offer = TestFixture.expiredPendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);
            assertTrue(offer.isExpired()); // precondition

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

            // Assert — không còn PENDING nên không còn expired
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("PENDING expired → setStatus(EXPIRED) → isExpired = false")
        void isExpired_afterSetStatusToExpired_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = TestFixture.expiredPendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Act — Scheduler đánh dấu là EXPIRED
            offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);

            // Assert — isExpired() = false vì status không còn là PENDING
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("PENDING chưa expired → setStatus(DECLINED) → isExpired = false")
        void isExpired_afterSetStatusToDeclined_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);
            assertFalse(offer.isExpired()); // precondition

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);

            // Assert
            assertFalse(offer.isExpired());
        }

        // -- Pure function check --------------------------------------------

        @Test
        @DisplayName("isExpired là pure: gọi nhiều lần không thay đổi state")
        void isExpired_calledMultipleTimes_noSideEffect() {
            // Arrange
            SecondChanceOffer offer = TestFixture.expiredPendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Act
            boolean first  = offer.isExpired();
            boolean second = offer.isExpired();

            // Assert
            assertEquals(first, second);
            assertEquals(SecondChanceOffer.OfferStatus.PENDING, offer.getStatus());
        }

        // -- Boundary: chỉ deadline quyết định, không phải createdAt -------

        @Test
        @DisplayName("PENDING + deadline rất xa tương lai → isExpired = false")
        void isExpired_whenDeadlineIsVeryFarFuture_returnsFalse() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().plusYears(100),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("PENDING + deadline rất xa quá khứ → isExpired = true")
        void isExpired_whenDeadlineIsVeryFarPast_returnsTrue() {
            // Arrange
            SecondChanceOffer offer = reconstituteWithStatus(
                    1_000_000L, 300_000L,
                    LocalDateTime.now().minusYears(1),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert
            assertTrue(offer.isExpired());
        }
    }

    // =========================================================================
    // setStatus — state mutation
    // =========================================================================

    @Nested
    @DisplayName("setStatus — thay đổi trạng thái")
    class SetStatusTest {

        @Test
        @DisplayName("setStatus(ACCEPTED) từ PENDING → status = ACCEPTED")
        void setStatus_toAccepted_changesStatus() {
            // Arrange
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

            // Assert
            assertEquals(SecondChanceOffer.OfferStatus.ACCEPTED, offer.getStatus());
        }

        @Test
        @DisplayName("setStatus(DECLINED) từ PENDING → status = DECLINED")
        void setStatus_toDeclined_changesStatus() {
            // Arrange
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);

            // Assert
            assertEquals(SecondChanceOffer.OfferStatus.DECLINED, offer.getStatus());
        }

        @Test
        @DisplayName("setStatus(EXPIRED) từ PENDING → status = EXPIRED")
        void setStatus_toExpired_changesStatus() {
            // Arrange
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);

            // Assert
            assertEquals(SecondChanceOffer.OfferStatus.EXPIRED, offer.getStatus());
        }

        @Test
        @DisplayName("setStatus không ảnh hưởng đến deadline (final field)")
        void setStatus_doesNotChangeDeadline() {
            // Arrange
            SecondChanceOffer offer = TestFixture.pendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);
            LocalDateTime deadlineBefore = offer.getDeadline();

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

            // Assert
            assertEquals(deadlineBefore, offer.getDeadline());
        }

        @Test
        @DisplayName("setStatus không ảnh hưởng đến offerPrice, depositPaid")
        void setStatus_doesNotChangeAmountFields() {
            // Arrange
            SecondChanceOffer offer = reconstitute(1_000_000L, 300_000L);
            long priceBefore   = offer.getOfferPrice();
            long depositBefore = offer.getDepositPaid();

            // Act
            offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);

            // Assert
            assertEquals(priceBefore,   offer.getOfferPrice());
            assertEquals(depositBefore, offer.getDepositPaid());
        }
    }

    // =========================================================================
    // create() — factory method contract
    // =========================================================================

    @Nested
    @DisplayName("create() — trạng thái ban đầu")
    class CreateTest {

        @Test
        @DisplayName("create() → status = PENDING")
        void create_initialStatus_isPending() {
            // Act
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertEquals(SecondChanceOffer.OfferStatus.PENDING, offer.getStatus());
        }

        @Test
        @DisplayName("create() → deadline ~24h sau now")
        void create_deadline_isApproximately24HoursFromNow() {
            // Arrange
            LocalDateTime before = LocalDateTime.now().plusHours(24).minusSeconds(1);

            // Act
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            LocalDateTime after = LocalDateTime.now().plusHours(24).plusSeconds(1);

            // Assert — deadline trong khoảng [now+24h-1s, now+24h+1s]
            assertTrue(offer.getDeadline().isAfter(before),
                    "deadline phải sau now+24h-1s");
            assertTrue(offer.getDeadline().isBefore(after),
                    "deadline phải trước now+24h+1s");
        }

        @Test
        @DisplayName("create() → isExpired = false ngay sau khi tạo")
        void create_isExpired_isFalseInitially() {
            // Act
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(offer.isExpired());
        }

        @Test
        @DisplayName("create() → lưu đúng runnerUp, auctionId, offerPrice, depositPaid")
        void create_storesAllFieldsCorrectly() {
            // Arrange
            long offerPrice  = 2_000_000L;
            long depositPaid = 600_000L;

            // Act
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, offerPrice, depositPaid);

            // Assert
            assertSame(runnerUp,  offer.getRunnerUp());
            assertEquals(auctionId,  offer.getAuctionId());
            assertEquals(offerPrice,  offer.getOfferPrice());
            assertEquals(depositPaid, offer.getDepositPaid());
        }

        @Test
        @DisplayName("create() → getRemainingAmount = offerPrice - depositPaid")
        void create_remainingAmount_correctOnCreation() {
            // Act
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertEquals(700_000L, offer.getRemainingAmount());
        }
    }

    // =========================================================================
    // Consistency — isExpired và getRemainingAmount không xung đột
    // =========================================================================

    @Nested
    @DisplayName("Consistency — contract toàn vẹn")
    class ConsistencyTest {

        @Test
        @DisplayName("offer mới tạo: isExpired=false, getRemainingAmount đúng")
        void newOffer_notExpired_hasCorrectRemaining() {
            // Arrange
            SecondChanceOffer offer = SecondChanceOffer.create(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(offer.isExpired());
            assertEquals(700_000L, offer.getRemainingAmount());
            assertEquals(SecondChanceOffer.OfferStatus.PENDING, offer.getStatus());
        }

        @Test
        @DisplayName("offer expired PENDING: isExpired=true, remaining không đổi")
        void expiredOffer_isExpiredTrue_remainingUnchanged() {
            // Arrange
            SecondChanceOffer offer = TestFixture.expiredPendingOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertTrue(offer.isExpired());
            assertEquals(700_000L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("offer ACCEPTED: isExpired=false, remaining đúng")
        void acceptedOffer_notExpired_hasCorrectRemaining() {
            // Arrange
            SecondChanceOffer offer = TestFixture.acceptedOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(offer.isExpired());
            assertEquals(700_000L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("offer DECLINED: isExpired=false, remaining đúng")
        void declinedOffer_notExpired_hasCorrectRemaining() {
            // Arrange
            SecondChanceOffer offer = TestFixture.declinedOffer(
                    runnerUp, auctionId, 1_000_000L, 300_000L);

            // Assert
            assertFalse(offer.isExpired());
            assertEquals(700_000L, offer.getRemainingAmount());
        }

        @Test
        @DisplayName("cùng offerPrice/deposit, deadline khác nhau → only deadline quyết định isExpired")
        void sameAmounts_differentDeadlines_differentExpiredResult() {
            // Arrange
            long offerPrice  = 1_000_000L;
            long depositPaid = 300_000L;

            SecondChanceOffer notExpired = reconstituteWithStatus(
                    offerPrice, depositPaid,
                    LocalDateTime.now().plusHours(1),
                    SecondChanceOffer.OfferStatus.PENDING);

            SecondChanceOffer expired = reconstituteWithStatus(
                    offerPrice, depositPaid,
                    LocalDateTime.now().minusHours(1),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert — remaining giống nhau, isExpired khác nhau
            assertEquals(notExpired.getRemainingAmount(), expired.getRemainingAmount());
            assertFalse(notExpired.isExpired());
            assertTrue(expired.isExpired());
        }

        @Test
        @DisplayName("deposit = offerPrice + 1: remaining = 0 và isExpired không bị ảnh hưởng")
        void depositExceedsOffer_remainingZero_isExpiredStillWorks() {
            // Arrange — deposit lớn hơn giá → remaining = 0, nhưng isExpired vẫn đúng
            SecondChanceOffer offer = reconstituteWithStatus(
                    500_000L, 600_000L,
                    LocalDateTime.now().minusHours(1),
                    SecondChanceOffer.OfferStatus.PENDING);

            // Assert
            assertEquals(0L, offer.getRemainingAmount());
            assertTrue(offer.isExpired());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Tạo SecondChanceOffer PENDING với deadline trong tương lai.
     * Dùng cho các test getRemainingAmount không cần quan tâm đến time.
     */
    private SecondChanceOffer reconstitute(long offerPrice, long depositPaid) {
        return SecondChanceOffer.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                runnerUp,
                auctionId,
                offerPrice,
                depositPaid,
                LocalDateTime.now().plusHours(24),
                SecondChanceOffer.OfferStatus.PENDING);
    }

    /**
     * Tạo SecondChanceOffer với deadline và status tùy chỉnh.
     * Dùng cho các test isExpired() cần kiểm soát chính xác deadline và status.
     */
    private SecondChanceOffer reconstituteWithStatus(long offerPrice, long depositPaid,
                                                     LocalDateTime deadline,
                                                     SecondChanceOffer.OfferStatus status) {
        return SecondChanceOffer.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                runnerUp,
                auctionId,
                offerPrice,
                depositPaid,
                deadline,
                status);
    }
}