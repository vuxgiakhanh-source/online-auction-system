package com.group13.auction.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.group13.auction.unit.TestFixture;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests cho {@link QualityReportService}.
 *
 * <p>Tập trung vào orchestration và business logic của:
 *
 * <ul>
 *   <li>{@code submitReport} — happy path, null report, report không ảnh.
 *   <li>{@code approveReport} — happy path, đã approve/reject, penalize + refund.
 *   <li>{@code rejectReport} — happy path, đã approve/reject, không gọi refund.
 * </ul>
 *
 * <p>Mọi external dependency đều được mock. Không DB, không network, không filesystem. Chỉ verify
 * interaction quan trọng ảnh hưởng business behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QualityReportService")
class QualityReportServiceTest {

  // ── Mocks ────────────────────────────────────────────────────────────────

  @Mock IRatingService ratingService;
  @Mock IPaymentService paymentService;
  @Mock QualityReportDAO qualityReportDAO;
  @Mock UserDAO userDAO;

  QualityReportService qualityReportService;

  // ── Fixtures ──────────────────────────────────────────────────────────────

  NormalUser seller;
  NormalUser winner;
  Admin admin;

  static final long STARTING_PRICE = 1_000_000L;
  static final long FINAL_PRICE = 5_000_000L;
  static final long DEPOSIT = 300_000L;

  @BeforeEach
  void setUp() throws Exception {
    // Bootstrap SystemAdmin Singleton không qua DB — bắt buộc vì
    // approveReport() gọi SystemAdmin.getInstance().autoBanIfNeeded()
    TestFixture.bootstrapSystemAdmin();

    qualityReportService =
        new QualityReportService(ratingService, paymentService, qualityReportDAO, userDAO);

    seller = TestFixture.normalSeller("sellerQR1");
    winner = TestFixture.bidderWithBalance("winnerQR2", 20_000_000L);
    admin = buildAdmin("adminQR3");
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
    @DisplayName("happy path: lưu report xuống DAO và trả về report")
    void happyPath_savesAndReturnsReport() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-001");
      when(qualityReportDAO.saveReport(report)).thenReturn(true);

      // Act
      QualityReport result = qualityReportService.submitReport(report);

      // Assert — DAO được gọi đúng 1 lần
      verify(qualityReportDAO, times(1)).saveReport(report);
      assertSame(report, result, "submitReport phải trả về cùng object report");
    }

    @Test
    @DisplayName("happy path: status sau khi submit vẫn là PENDING")
    void happyPath_statusRemainspending() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-002");
      when(qualityReportDAO.saveReport(any())).thenReturn(true);

      // Act
      qualityReportService.submitReport(report);

      // Assert
      assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus());
    }

    @Test
    @DisplayName("report null: ném IllegalArgumentException, không gọi DAO")
    void nullReport_throwsIllegalArgumentException() {
      // Act & Assert
      assertThrows(IllegalArgumentException.class, () -> qualityReportService.submitReport(null));

      verifyNoInteractions(qualityReportDAO);
    }

    @Test
    @DisplayName("report không có ảnh: ném IllegalArgumentException từ QualityReport.create()")
    void noImages_throwsIllegalArgumentException() {
      // Assert — QualityReport.create() sẽ ném trước khi vào service
      assertThrows(
          IllegalArgumentException.class,
          () -> QualityReport.create(winner, "auction-003", "Mô tả", List.of()));
    }

    @Test
    @DisplayName("happy path: ratingService và paymentService không được gọi khi submit")
    void happyPath_noRatingOrPaymentInteraction() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-004");
      when(qualityReportDAO.saveReport(any())).thenReturn(true);

      // Act
      qualityReportService.submitReport(report);

      // Assert
      verifyNoInteractions(ratingService);
      verifyNoInteractions(paymentService);
    }
  }

  // =========================================================================
  // approveReport
  // =========================================================================

  @Nested
  @DisplayName("approveReport()")
  class ApproveReport {

    @Test
    @DisplayName("happy path: status chuyển sang APPROVED")
    void happyPath_statusBecomesApproved() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Act
      qualityReportService.approveReport(admin, report, auction);

      // Assert
      assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
    }

    @Test
    @DisplayName("happy path: penalizeSeller được gọi đúng 1 lần với seller đúng")
    void happyPath_penalizeSellerCalled() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Act
      qualityReportService.approveReport(admin, report, auction);

      // Assert
      verify(ratingService, times(1)).penalizeSeller(seller);
    }

    @Test
    @DisplayName("happy path: refundToWinnerFromBank được gọi đúng 1 lần")
    void happyPath_refundCalledOnce() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Act
      qualityReportService.approveReport(admin, report, auction);

      // Assert — không double-refund
      verify(paymentService, times(1)).refundToWinnerFromBank(auction);
    }

    @Test
    @DisplayName("happy path: report.isRefundCompleted() = true sau approve")
    void happyPath_refundCompletedFlagSet() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Act
      qualityReportService.approveReport(admin, report, auction);

      // Assert
      assertTrue(
          report.isRefundCompleted(), "refundCompleted phải true sau khi approve thành công");
    }

    @Test
    @DisplayName("happy path: updateReport DAO được gọi để persist trạng thái")
    void happyPath_daoUpdateCalled() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Act
      qualityReportService.approveReport(admin, report, auction);

      // Assert
      verify(qualityReportDAO, times(1)).updateReport(report);
    }

    @Test
    @DisplayName("happy path: updateAccountStatus được gọi cho seller (có thể đã bị ban)")
    void happyPath_sellerAccountStatusUpdated() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Act
      qualityReportService.approveReport(admin, report, auction);

      // Assert
      verify(userDAO, times(1)).updateAccountStatus(eq(seller.getId()), any(String.class));
    }

    @Test
    @DisplayName("report đã APPROVED: ném IllegalStateException, không refund lần 2")
    void alreadyApproved_throwsIllegalState_noDoubleRefund() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport alreadyApproved = TestFixture.approvedReport(winner, auction.getId());

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
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
      assertThrows(
          IllegalStateException.class,
          () -> qualityReportService.approveReport(admin, rejected, auction));
    }

    @Test
    @DisplayName("report không ở PENDING: DAO không được cập nhật")
    void notPending_daoNotCalled() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport approved = TestFixture.approvedReport(winner, auction.getId());

      // Act — bỏ qua exception
      assertThrows(
          IllegalStateException.class,
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
    @DisplayName("happy path: status chuyển sang REJECTED")
    void happyPath_statusBecomesRejected() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-010");

      // Act
      qualityReportService.rejectReport(admin, report);

      // Assert
      assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
    }

    @Test
    @DisplayName("happy path: updateReport DAO được gọi để persist trạng thái")
    void happyPath_daoUpdateCalled() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-011");

      // Act
      qualityReportService.rejectReport(admin, report);

      // Assert
      verify(qualityReportDAO, times(1)).updateReport(report);
    }

    @Test
    @DisplayName("happy path: paymentService KHÔNG được gọi khi reject")
    void happyPath_noRefundOnReject() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-012");

      // Act
      qualityReportService.rejectReport(admin, report);

      // Assert — reject không hoàn tiền
      verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("happy path: ratingService KHÔNG được gọi khi reject")
    void happyPath_noRatingPenaltyOnReject() {
      // Arrange
      QualityReport report = TestFixture.pendingReport(winner, "auction-013");

      // Act
      qualityReportService.rejectReport(admin, report);

      // Assert
      verifyNoInteractions(ratingService);
    }

    @Test
    @DisplayName("report đã APPROVED: ném IllegalStateException")
    void alreadyApproved_throwsIllegalState() {
      // Arrange
      QualityReport approved = TestFixture.approvedReport(winner, "auction-014");

      // Act & Assert
      assertThrows(
          IllegalStateException.class, () -> qualityReportService.rejectReport(admin, approved));
    }

    @Test
    @DisplayName("report đã REJECTED: ném IllegalStateException (không reject 2 lần)")
    void alreadyRejected_throwsIllegalState() {
      // Arrange
      QualityReport rejected = TestFixture.rejectedReport(winner, "auction-015");

      // Act & Assert
      assertThrows(
          IllegalStateException.class, () -> qualityReportService.rejectReport(admin, rejected));
    }

    @Test
    @DisplayName("report không ở PENDING: DAO không được cập nhật")
    void notPending_daoNotCalled() {
      // Arrange
      QualityReport approved = TestFixture.approvedReport(winner, "auction-016");

      // Act
      assertThrows(
          IllegalStateException.class, () -> qualityReportService.rejectReport(admin, approved));

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
    @DisplayName("approve sau reject: ném IllegalStateException")
    void approveAfterReject_throwsIllegalState() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Reject trước
      qualityReportService.rejectReport(admin, report);

      // Act — thử approve sau khi đã reject
      assertThrows(
          IllegalStateException.class,
          () -> qualityReportService.approveReport(admin, report, auction));
    }

    @Test
    @DisplayName("reject sau approve: ném IllegalStateException")
    void rejectAfterApprove_throwsIllegalState() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Approve trước
      qualityReportService.approveReport(admin, report, auction);

      // Act — thử reject sau khi đã approve
      assertThrows(
          IllegalStateException.class, () -> qualityReportService.rejectReport(admin, report));
    }

    @Test
    @DisplayName(
        "approve 2 lần liên tiếp: lần 2 ném IllegalStateException, refund chỉ xảy ra 1 lần")
    void approveCalledTwice_refundOnlyOnce() {
      // Arrange
      Auction auction = finishedAuctionWithWinner();
      QualityReport report = TestFixture.pendingReport(winner, auction.getId());

      // Lần 1 — thành công
      qualityReportService.approveReport(admin, report, auction);

      // Lần 2 — phải ném
      assertThrows(
          IllegalStateException.class,
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
      assertThrows(
          IllegalStateException.class, () -> qualityReportService.rejectReport(admin, report));

      // DAO chỉ gọi 1 lần
      verify(qualityReportDAO, times(1)).updateReport(any());
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /** Tạo Admin không qua DB. Dùng AdminFactory như đúng luồng khởi tạo của production code. */
  private Admin buildAdmin(String username) {
    com.group13.auction.model.user.AdminFactory factory =
        new com.group13.auction.model.user.AdminFactory();
    return (Admin) factory.createUser(username, "Admin@Pass1", username + "@admin.test");
  }

  /**
   * Tạo finished auction với AuctionWinner hợp lệ đã được set. seller và winner lấy từ field của
   * class.
   */
  private Auction finishedAuctionWithWinner() {
    Auction auction = TestFixture.finishedAuction(seller, winner, STARTING_PRICE, FINAL_PRICE);
    AuctionWinner aw = TestFixture.fundsHeldWinner(winner, auction.getId(), FINAL_PRICE, DEPOSIT);
    auction.setWinner(aw);
    return auction;
  }
}
