package com.group13.auction.unit.service;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.QualityReportService;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link QualityReportService}.
 *
 * <p>Tập trung vào orchestration và business logic của:
 * <ul>
 *   <li>{@code submitReport}  — happy path, null report, report không ảnh.</li>
 *   <li>{@code approveReport} — happy path, đã approve/reject, penalize + refund.</li>
 *   <li>{@code rejectReport}  — happy path, đã approve/reject, không gọi refund.</li>
 * </ul>
 *
 * <p>Mọi external dependency đều được mock.
 * Không DB, không network, không filesystem.
 * Chỉ verify interaction quan trọng ảnh hưởng business behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QualityReportService")
class QualityReportServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    @Mock IRatingService    ratingService;
    @Mock IPaymentService   paymentService;
    @Mock QualityReportDAO  qualityReportDAO;
    @Mock UserDAO           userDAO;

    QualityReportService qualityReportService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    NormalUser seller;
    NormalUser winner;
    Admin      admin;

    static final long STARTING_PRICE = 1_000_000L;
    static final long FINAL_PRICE    = 5_000_000L;
    static final long DEPOSIT        =   300_000L;

    @BeforeEach
    void setUp() throws Exception {
        // Bootstrap SystemAdmin Singleton không qua DB — bắt buộc vì
        // approveReport() gọi SystemAdmin.getInstance().autoBanIfNeeded()
        TestFixture.bootstrapSystemAdmin();

        qualityReportService = new QualityReportService(
                ratingService, paymentService, qualityReportDAO, userDAO);

        seller = TestFixture.normalSeller("sellerQR1");
        winner = TestFixture.bidderWithBalance("winnerQR2", 20_000_000L);
        admin  = buildAdmin("adminQR3");
    }

    @AfterEach
    void tearDown() throws Exception {
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // submitReport
    // =========================================================================

    @Nested
    @DisplayName("submitReport()")
    class SubmitReport {

        @Test
        @DisplayName("happy path: lưu PENDING, không gọi rating/payment")
        void happyPath_savesPendingReport() {
            QualityReport report = TestFixture.pendingReport(winner, "auction-001");
            when(qualityReportDAO.saveReport(report)).thenReturn(true);

            QualityReport result = qualityReportService.submitReport(report);

            assertSame(report, result);
            assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus());
            verify(qualityReportDAO).saveReport(report);
            verifyNoInteractions(ratingService);
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("report null: ném IllegalArgumentException, không gọi DAO")
        void nullReport_throwsIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> qualityReportService.submitReport(null));

            verifyNoInteractions(qualityReportDAO);
        }

        @Test
        @DisplayName("report không có ảnh: ném IllegalArgumentException từ QualityReport.create()")
        void noImages_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    QualityReport.create(winner, "auction-003", "Mô tả", List.of()));
        }
    }

    // =========================================================================
    // approveReport
    // =========================================================================

    @Nested
    @DisplayName("approveReport()")
    class ApproveReport {

        @Test
        @DisplayName("happy path: penalize, refund, persist APPROVED")
        void happyPath_fullOrchestration() {
            Auction auction = finishedAuctionWithWinner();
            QualityReport report = TestFixture.pendingReport(winner, auction.getId());

            qualityReportService.approveReport(admin, report, auction);

            assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
            assertTrue(report.isRefundCompleted());

            InOrder order = inOrder(ratingService, paymentService, qualityReportDAO, userDAO);
            order.verify(ratingService).penalizeSeller(seller);
            order.verify(paymentService).refundToWinnerFromBank(auction);
            order.verify(qualityReportDAO).updateReport(report);
            order.verify(userDAO).updateAccountStatus(eq(seller.getId()), any(String.class));
        }

        @Test
        @DisplayName("report đã APPROVED: ném IllegalStateException, không refund lần 2")
        void alreadyApproved_throwsIllegalState_noDoubleRefund() {
            // Arrange
            Auction auction = finishedAuctionWithWinner();
            QualityReport alreadyApproved = TestFixture.approvedReport(winner, auction.getId());

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.approveReport(admin, alreadyApproved, auction));

            // Không được refund khi đã APPROVED
            verify(paymentService, never()).refundToWinnerFromBank(any());
        }

        @Test
        @DisplayName("report đã REJECTED: ném IllegalStateException")
        void alreadyRejected_throwsIllegalState() {
            // Arrange
            Auction auction = finishedAuctionWithWinner();
            QualityReport rejected = TestFixture.rejectedReport(winner, auction.getId());

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.approveReport(admin, rejected, auction));
        }

        @Test
        @DisplayName("report không ở PENDING: DAO không được cập nhật")
        void notPending_daoNotCalled() {
            // Arrange
            Auction auction = finishedAuctionWithWinner();
            QualityReport approved = TestFixture.approvedReport(winner, auction.getId());

            // Act — bỏ qua exception
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.approveReport(admin, approved, auction));

            // Assert
            verify(qualityReportDAO, never()).updateReport(any());
        }

        @Test
        @DisplayName("auction không có winner (finalPrice = 0): refund không được gọi")
        void noWinner_refundNotCalled() {
            // Arrange — auction không có AuctionWinner
            Auction auction = TestFixture.finishedAuction(seller, winner, STARTING_PRICE, FINAL_PRICE);
            // Không setWinner → auction.getWinner() = null
            QualityReport report = TestFixture.pendingReport(winner, auction.getId());

            // Act
            qualityReportService.approveReport(admin, report, auction);

            // Assert — không được gọi refund khi finalPrice = 0
            verify(paymentService, never()).refundToWinnerFromBank(any());
        }
    }

    // =========================================================================
    // rejectReport
    // =========================================================================

    @Nested
    @DisplayName("rejectReport()")
    class RejectReport {

        @Test
        @DisplayName("happy path: REJECTED, persist, không refund/penalize")
        void happyPath_rejectsWithoutRefund() {
            QualityReport report = TestFixture.pendingReport(winner, "auction-010");

            qualityReportService.rejectReport(admin, report);

            assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
            verify(qualityReportDAO).updateReport(report);
            verifyNoInteractions(paymentService);
            verifyNoInteractions(ratingService);
        }

        @Test
        @DisplayName("report đã APPROVED: ném IllegalStateException")
        void alreadyApproved_throwsIllegalState() {
            // Arrange
            QualityReport approved = TestFixture.approvedReport(winner, "auction-014");

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.rejectReport(admin, approved));
        }

        @Test
        @DisplayName("report đã REJECTED: ném IllegalStateException (không reject 2 lần)")
        void alreadyRejected_throwsIllegalState() {
            // Arrange
            QualityReport rejected = TestFixture.rejectedReport(winner, "auction-015");

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.rejectReport(admin, rejected));
        }

        @Test
        @DisplayName("report không ở PENDING: DAO không được cập nhật")
        void notPending_daoNotCalled() {
            // Arrange
            QualityReport approved = TestFixture.approvedReport(winner, "auction-016");

            // Act
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.rejectReport(admin, approved));

            // Assert
            verify(qualityReportDAO, never()).updateReport(any());
        }
    }

    // =========================================================================
    // approve vs reject idempotency
    // =========================================================================

    @Nested
    @DisplayName("Idempotency / Double-call guard")
    class IdempotencyGuard {

        @Test
        @DisplayName("approve 2 lần liên tiếp: lần 2 ném IllegalStateException, refund chỉ xảy ra 1 lần")
        void approveCalledTwice_refundOnlyOnce() {
            // Arrange
            Auction auction = finishedAuctionWithWinner();
            QualityReport report = TestFixture.pendingReport(winner, auction.getId());

            // Lần 1 — thành công
            qualityReportService.approveReport(admin, report, auction);

            // Lần 2 — phải ném
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.approveReport(admin, report, auction));

            // Refund chỉ 1 lần
            verify(paymentService, times(1)).refundToWinnerFromBank(any());
        }

        @Test
        @DisplayName("reject 2 lần liên tiếp: lần 2 ném IllegalStateException, DAO chỉ gọi 1 lần")
        void rejectCalledTwice_daoOnlyOnce() {
            // Arrange
            QualityReport report = TestFixture.pendingReport(winner, "auction-020");

            // Lần 1 — thành công
            qualityReportService.rejectReport(admin, report);

            // Lần 2 — phải ném
            assertThrows(IllegalStateException.class,
                    () -> qualityReportService.rejectReport(admin, report));

            // DAO chỉ gọi 1 lần
            verify(qualityReportDAO, times(1)).updateReport(any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Tạo Admin không qua DB.
     * Dùng AdminFactory như đúng luồng khởi tạo của production code.
     */
    private Admin buildAdmin(String username) {
        com.group13.auction.model.user.AdminFactory factory =
                new com.group13.auction.model.user.AdminFactory();
        return (Admin) factory.createUser(username, "Admin@Pass1", username + "@admin.test");
    }

    /**
     * Tạo finished auction với AuctionWinner hợp lệ đã được set.
     * seller và winner lấy từ field của class.
     */
    private Auction finishedAuctionWithWinner() {
        Auction auction = TestFixture.finishedAuction(
                seller, winner, STARTING_PRICE, FINAL_PRICE);
        AuctionWinner aw = TestFixture.fundsHeldWinner(
                winner, auction.getId(), FINAL_PRICE, DEPOSIT);
        auction.setWinner(aw);
        return auction;
    }
}