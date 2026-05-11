package com.group13.auction.unit.bid;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link QualityReport}.
 *
 * <p>Contract cần kiểm tra:
 * <ul>
 *   <li>{@code create()} — validation: imageUrls không được null / rỗng.</li>
 *   <li>{@code approve()} — chuyển PENDING → APPROVED; ném exception nếu không đúng state.</li>
 *   <li>{@code reject()} — chuyển PENDING → REJECTED; ném exception nếu không đúng state.</li>
 *   <li>{@code markRefundCompleted()} — set {@code refundCompleted = true}.</li>
 *   <li>Immutability của {@code imageUrls} trả về.</li>
 *   <li>State consistency sau mỗi transition.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network.
 * Dùng object thật từ {@link TestFixture} và {@link QualityReport#reconstitute}.
 */
@DisplayName("QualityReport")
class QualityReportTest {

    private NormalUser reporter;
    private String     auctionId;

    @BeforeEach
    void setUp() {
        reporter  = TestFixture.normalBidder("winnerXX1");
        auctionId = UUID.randomUUID().toString();
    }

    // =========================================================================
    // create() — validation
    // =========================================================================

    @Nested
    @DisplayName("create() — validation đầu vào")
    class CreateValidationTest {

        // -- Happy path -------------------------------------------------------

        @Test
        @DisplayName("imageUrls có 1 phần tử → tạo thành công")
        void create_withOneImage_succeeds() {
            // Arrange
            List<String> images = List.of("http://evidence1.jpg");

            // Act
            QualityReport report = QualityReport.create(reporter, auctionId,
                    "Hàng không đúng", images);

            // Assert
            assertNotNull(report);
        }

        @Test
        @DisplayName("imageUrls có nhiều phần tử → tạo thành công")
        void create_withMultipleImages_succeeds() {
            // Arrange
            List<String> images = List.of(
                    "http://evidence1.jpg",
                    "http://evidence2.jpg",
                    "http://evidence3.jpg");

            // Act & Assert — không ném exception
            assertDoesNotThrow(() ->
                    QualityReport.create(reporter, auctionId, "Hàng lỗi", images));
        }

        // -- Initial state after create() ------------------------------------

        @Test
        @DisplayName("create() → status = PENDING")
        void create_initialStatus_isPending() {
            // Arrange & Act
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Assert
            assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus());
        }

        @Test
        @DisplayName("create() → refundCompleted = false")
        void create_initialRefundCompleted_isFalse() {
            // Arrange & Act
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Assert
            assertFalse(report.isRefundCompleted());
        }

        @Test
        @DisplayName("create() → lưu đúng reporter, auctionId, description")
        void create_storesAllFieldsCorrectly() {
            // Arrange
            String description = "Hàng không đúng mô tả";
            List<String> images = List.of("http://ev1.jpg");

            // Act
            QualityReport report = QualityReport.create(reporter, auctionId, description, images);

            // Assert
            assertSame(reporter,    report.getReporter());
            assertEquals(auctionId,   report.getAuctionId());
            assertEquals(description, report.getDescription());
        }

        @Test
        @DisplayName("create() → imageUrls được lưu đúng nội dung")
        void create_storesImageUrlsCorrectly() {
            // Arrange
            List<String> images = List.of("http://ev1.jpg", "http://ev2.jpg");

            // Act
            QualityReport report = QualityReport.create(reporter, auctionId, "Lỗi", images);

            // Assert
            assertEquals(2, report.getImageUrls().size());
            assertTrue(report.getImageUrls().containsAll(images));
        }

        // -- Invalid: null imageUrls -----------------------------------------

        @Test
        @DisplayName("imageUrls = null → ném IllegalArgumentException")
        void create_withNullImageUrls_throwsIllegalArgument() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    QualityReport.create(reporter, auctionId, "Lỗi hàng", null));
        }

        @Test
        @DisplayName("imageUrls = null → exception message mô tả yêu cầu ảnh")
        void create_withNullImageUrls_exceptionMessageDescribesRequirement() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    QualityReport.create(reporter, auctionId, "Lỗi hàng", null));

            // Assert
            assertNotNull(ex.getMessage());
            assertFalse(ex.getMessage().isBlank());
        }

        // -- Invalid: empty imageUrls ----------------------------------------

        @Test
        @DisplayName("imageUrls rỗng (empty list) → ném IllegalArgumentException")
        void create_withEmptyImageUrls_throwsIllegalArgument() {
            // Arrange
            List<String> emptyList = Collections.emptyList();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    QualityReport.create(reporter, auctionId, "Lỗi hàng", emptyList));
        }

        @Test
        @DisplayName("imageUrls rỗng → exception message giống với null")
        void create_withEmptyImageUrls_exceptionMessageNotBlank() {
            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    QualityReport.create(reporter, auctionId, "Lỗi", List.of()));

            // Assert
            assertNotNull(ex.getMessage());
            assertFalse(ex.getMessage().isBlank());
        }

        @Test
        @DisplayName("new ArrayList() rỗng (non-null empty) → ném IllegalArgumentException")
        void create_withMutableEmptyList_throwsIllegalArgument() {
            // Arrange — đảm bảo check isEmpty() hoạt động với cả mutable list
            List<String> mutableEmpty = new ArrayList<>();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                    QualityReport.create(reporter, auctionId, "Lỗi", mutableEmpty));
        }

        // -- Defensive copy of imageUrls -------------------------------------

        @Test
        @DisplayName("thay đổi list gốc sau create() không ảnh hưởng đến report")
        void create_mutatingOriginalList_doesNotAffectReport() {
            // Arrange
            List<String> mutableImages = new ArrayList<>();
            mutableImages.add("http://ev1.jpg");
            QualityReport report = QualityReport.create(reporter, auctionId, "Lỗi", mutableImages);

            // Act — thêm phần tử vào list gốc
            mutableImages.add("http://ev2.jpg");

            // Assert — report vẫn chỉ có 1 ảnh
            assertEquals(1, report.getImageUrls().size());
        }

        // -- Immutability of returned imageUrls ------------------------------

        @Test
        @DisplayName("getImageUrls() trả về unmodifiable list")
        void getImageUrls_returnsUnmodifiableList() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act & Assert
            assertThrows(UnsupportedOperationException.class, () ->
                    report.getImageUrls().add("http://extra.jpg"));
        }
    }

    // =========================================================================
    // approve()
    // =========================================================================

    @Nested
    @DisplayName("approve() — chuyển PENDING → APPROVED")
    class ApproveTest {

        // -- Happy path -------------------------------------------------------

        @Test
        @DisplayName("approve() từ PENDING → status = APPROVED")
        void approve_fromPending_setsStatusToApproved() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus()); // precondition

            // Act
            report.approve();

            // Assert
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
        }

        @Test
        @DisplayName("approve() không thay đổi reporter, auctionId, description, imageUrls")
        void approve_doesNotAlterImmutableFields() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            String descBefore   = report.getDescription();
            int    sizesBefore  = report.getImageUrls().size();

            // Act
            report.approve();

            // Assert
            assertSame(reporter,   report.getReporter());
            assertEquals(auctionId,  report.getAuctionId());
            assertEquals(descBefore, report.getDescription());
            assertEquals(sizesBefore, report.getImageUrls().size());
        }

        @Test
        @DisplayName("approve() không thay đổi refundCompleted (vẫn false)")
        void approve_doesNotSetRefundCompleted() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.approve();

            // Assert — refundCompleted chỉ được set bởi markRefundCompleted(), không phải approve()
            assertFalse(report.isRefundCompleted());
        }

        // -- approve() trên APPROVED (duplicate) -----------------------------

        @Test
        @DisplayName("approve() gọi lần hai trên APPROVED → idempotent, status vẫn APPROVED")
        void approve_calledTwiceOnApproved_statusRemainsApproved() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.approve(); // lần 1

            // Act — lần 2 (không có guard trong model, service mới guard)
            report.approve();

            // Assert — model không ném exception, status vẫn APPROVED
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
        }

        // -- approve() trên REJECTED -----------------------------------------

        @Test
        @DisplayName("approve() từ REJECTED → overwrite thành APPROVED (model không guard)")
        void approve_fromRejected_overwritesStatusToApproved() {
            // Arrange — model không có guard; guard nằm ở QualityReportService
            QualityReport report = TestFixture.rejectedReport(reporter, auctionId);
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus()); // precondition

            // Act
            report.approve();

            // Assert — model level: không ném exception, set APPROVED
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
        }
    }

    // =========================================================================
    // reject()
    // =========================================================================

    @Nested
    @DisplayName("reject() — chuyển PENDING → REJECTED")
    class RejectTest {

        // -- Happy path -------------------------------------------------------

        @Test
        @DisplayName("reject() từ PENDING → status = REJECTED")
        void reject_fromPending_setsStatusToRejected() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.reject();

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
        }

        @Test
        @DisplayName("reject() không thay đổi refundCompleted (vẫn false)")
        void reject_doesNotSetRefundCompleted() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.reject();

            // Assert
            assertFalse(report.isRefundCompleted());
        }

        @Test
        @DisplayName("reject() không thay đổi reporter, auctionId, imageUrls")
        void reject_doesNotAlterImmutableFields() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.reject();

            // Assert
            assertSame(reporter,  report.getReporter());
            assertEquals(auctionId, report.getAuctionId());
            assertFalse(report.getImageUrls().isEmpty());
        }

        // -- reject() trên REJECTED (duplicate) ------------------------------

        @Test
        @DisplayName("reject() gọi lần hai trên REJECTED → idempotent, status vẫn REJECTED")
        void reject_calledTwiceOnRejected_statusRemainsRejected() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.reject(); // lần 1

            // Act — lần 2
            report.reject();

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
        }

        // -- reject() trên APPROVED ------------------------------------------

        @Test
        @DisplayName("reject() từ APPROVED → overwrite thành REJECTED (model không guard)")
        void reject_fromApproved_overwritesStatusToRejected() {
            // Arrange — guard nằm ở QualityReportService, không nằm ở model
            QualityReport report = TestFixture.approvedReport(reporter, auctionId);
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus()); // precondition

            // Act
            report.reject();

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
        }
    }

    // =========================================================================
    // approve() vs reject() — mutual exclusion
    // =========================================================================

    @Nested
    @DisplayName("approve() vs reject() — mutual exclusion")
    class ApproveRejectMutualExclusionTest {

        @Test
        @DisplayName("approve() sau reject() → status = APPROVED (model không guard)")
        void approve_afterReject_setsApproved() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.reject();
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus()); // precondition

            // Act — model không chặn, chỉ service mới chặn
            report.approve();

            // Assert
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
        }

        @Test
        @DisplayName("reject() sau approve() → status = REJECTED (model không guard)")
        void reject_afterApprove_setsRejected() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.approve();
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus()); // precondition

            // Act
            report.reject();

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
        }

        @Test
        @DisplayName("approve() và reject() là mutually exclusive về kết quả cuối")
        void approveAndReject_lastCallWins() {
            // Arrange
            QualityReport reportA = TestFixture.pendingReport(reporter, auctionId);
            QualityReport reportB = TestFixture.pendingReport(reporter, auctionId);

            // Act
            reportA.approve();
            reportB.reject();

            // Assert — hai object độc lập, trạng thái đúng với lần gọi cuối
            assertEquals(QualityReport.ReportStatus.APPROVED, reportA.getStatus());
            assertEquals(QualityReport.ReportStatus.REJECTED, reportB.getStatus());
            assertNotEquals(reportA.getStatus(), reportB.getStatus());
        }
    }

    // =========================================================================
    // markRefundCompleted()
    // =========================================================================

    @Nested
    @DisplayName("markRefundCompleted() — đánh dấu hoàn tiền xong")
    class MarkRefundCompletedTest {

        @Test
        @DisplayName("markRefundCompleted() → refundCompleted = true")
        void markRefundCompleted_setsRefundCompletedToTrue() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            assertFalse(report.isRefundCompleted()); // precondition

            // Act
            report.markRefundCompleted();

            // Assert
            assertTrue(report.isRefundCompleted());
        }

        @Test
        @DisplayName("markRefundCompleted() gọi sau approve() → refundCompleted = true")
        void markRefundCompleted_afterApprove_setsTrue() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.approve();

            // Act
            report.markRefundCompleted();

            // Assert
            assertTrue(report.isRefundCompleted());
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
        }

        @Test
        @DisplayName("markRefundCompleted() không thay đổi status")
        void markRefundCompleted_doesNotChangeStatus() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.approve();
            QualityReport.ReportStatus statusBefore = report.getStatus();

            // Act
            report.markRefundCompleted();

            // Assert
            assertEquals(statusBefore, report.getStatus());
        }

        @Test
        @DisplayName("markRefundCompleted() gọi hai lần → vẫn true (idempotent)")
        void markRefundCompleted_calledTwice_remainsTrue() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.markRefundCompleted(); // lần 1

            // Act
            report.markRefundCompleted(); // lần 2

            // Assert
            assertTrue(report.isRefundCompleted());
        }

        @Test
        @DisplayName("approve() sau markRefundCompleted() không reset refundCompleted")
        void approve_afterMarkRefundCompleted_doesNotResetFlag() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);
            report.markRefundCompleted();
            assertTrue(report.isRefundCompleted()); // precondition

            // Act
            report.approve();

            // Assert — approve() không đụng đến refundCompleted
            assertTrue(report.isRefundCompleted());
        }
    }

    // =========================================================================
    // State consistency — toàn vẹn trạng thái
    // =========================================================================

    @Nested
    @DisplayName("State consistency — trạng thái toàn vẹn")
    class StateConsistencyTest {

        @Test
        @DisplayName("PENDING: approve=false, reject=false, refund=false ban đầu")
        void pendingReport_allFlagsAreFalseInitially() {
            // Arrange & Act
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Assert
            assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus());
            assertFalse(report.isRefundCompleted());
        }

        @Test
        @DisplayName("APPROVED: status=APPROVED, refundCompleted=false cho đến khi markRefundCompleted")
        void approvedReport_refundNotCompletedUntilExplicitCall() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.approve();

            // Assert
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
            assertFalse(report.isRefundCompleted()); // chưa gọi markRefundCompleted
        }

        @Test
        @DisplayName("full approve flow: PENDING → approve() → markRefundCompleted()")
        void fullApproveFlow_statusAndRefundCorrect() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.approve();
            report.markRefundCompleted();

            // Assert
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
            assertTrue(report.isRefundCompleted());
        }

        @Test
        @DisplayName("full reject flow: PENDING → reject() → refundCompleted vẫn false")
        void fullRejectFlow_statusRejectedRefundFalse() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report.reject();

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
            assertFalse(report.isRefundCompleted()); // seller không phải hoàn tiền khi reject
        }

        @Test
        @DisplayName("hai report độc lập từ cùng reporter không chia sẻ state")
        void twoReportsFromSameReporter_haveIndependentState() {
            // Arrange
            QualityReport report1 = TestFixture.pendingReport(reporter, auctionId);
            QualityReport report2 = TestFixture.pendingReport(reporter, auctionId);

            // Act
            report1.approve();
            report1.markRefundCompleted();

            // Assert — report2 không bị ảnh hưởng
            assertEquals(QualityReport.ReportStatus.PENDING,  report2.getStatus());
            assertFalse(report2.isRefundCompleted());
        }

        @Test
        @DisplayName("reconstitute APPROVED + refundCompleted=true → trạng thái đúng")
        void reconstitute_approvedWithRefundCompleted_correctState() {
            // Arrange
            QualityReport report = QualityReport.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    reporter,
                    auctionId,
                    "Hàng lỗi",
                    List.of("http://ev1.jpg"),
                    QualityReport.ReportStatus.APPROVED,
                    LocalDateTime.now().minusHours(1), // sellerRefundDeadline (không lưu vào field)
                    true); // refundCompleted

            // Assert
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
            assertTrue(report.isRefundCompleted());
        }

        @Test
        @DisplayName("reconstitute REJECTED + refundCompleted=false → trạng thái đúng")
        void reconstitute_rejectedWithRefundNotCompleted_correctState() {
            // Arrange
            QualityReport report = QualityReport.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    reporter,
                    auctionId,
                    "Hàng lỗi",
                    List.of("http://ev1.jpg"),
                    QualityReport.ReportStatus.REJECTED,
                    null,
                    false);

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
            assertFalse(report.isRefundCompleted());
        }

        @Test
        @DisplayName("reconstitute với nhiều ảnh → imageUrls được lưu đúng số lượng")
        void reconstitute_withMultipleImages_storedCorrectly() {
            // Arrange
            List<String> images = List.of("http://ev1.jpg", "http://ev2.jpg", "http://ev3.jpg");

            // Act
            QualityReport report = QualityReport.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    reporter,
                    auctionId,
                    "Lỗi nghiêm trọng",
                    images,
                    QualityReport.ReportStatus.PENDING,
                    null,
                    false);

            // Assert
            assertEquals(3, report.getImageUrls().size());
        }
    }

    // =========================================================================
    // TestFixture helpers contract
    // =========================================================================

    @Nested
    @DisplayName("TestFixture helpers — đảm bảo fixture đúng contract")
    class TestFixtureHelpersTest {

        @Test
        @DisplayName("TestFixture.pendingReport() → status PENDING, refundCompleted false")
        void fixture_pendingReport_correctInitialState() {
            // Act
            QualityReport report = TestFixture.pendingReport(reporter, auctionId);

            // Assert
            assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus());
            assertFalse(report.isRefundCompleted());
            assertFalse(report.getImageUrls().isEmpty());
        }

        @Test
        @DisplayName("TestFixture.approvedReport() → status APPROVED, refundCompleted false")
        void fixture_approvedReport_correctState() {
            // Act
            QualityReport report = TestFixture.approvedReport(reporter, auctionId);

            // Assert
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
            assertFalse(report.isRefundCompleted());
        }

        @Test
        @DisplayName("TestFixture.rejectedReport() → status REJECTED")
        void fixture_rejectedReport_correctState() {
            // Act
            QualityReport report = TestFixture.rejectedReport(reporter, auctionId);

            // Assert
            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
        }

        @Test
        @DisplayName("TestFixture.overdueButRefundCompletedReport() → refundCompleted true")
        void fixture_overdueButRefundCompleted_refundFlagTrue() {
            // Act
            QualityReport report = TestFixture.overdueButRefundCompletedReport(reporter, auctionId);

            // Assert
            assertTrue(report.isRefundCompleted());
            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
        }

        @Test
        @DisplayName("TestFixture.pendingReportWithImages() → lưu đúng số ảnh được truyền vào")
        void fixture_pendingReportWithImages_storesCorrectCount() {
            // Arrange
            List<String> images = List.of("http://a.jpg", "http://b.jpg");

            // Act
            QualityReport report = TestFixture.pendingReportWithImages(reporter, auctionId, images);

            // Assert
            assertEquals(2, report.getImageUrls().size());
        }
    }
}