package com.group13.auction.unit.service;

import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.group13.auction.service.QualityReportService;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QualityReportService")
class QualityReportServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 10, 0);

    @Mock private IRatingService ratingService;
    @Mock private IPaymentService paymentService;
    @Mock private QualityReportDAO qualityReportDAO;
    @Mock private UserDAO userDAO;
    @Mock private UserDAO systemUserDAO;

    private QualityReportService sut;
    private Admin admin;
    private NormalUser seller;
    private NormalUser winner;
    private Auction auction;
    private QualityReport report;

    @BeforeEach
    void setUp() throws Exception {
        resetAuctionManager();
        bootstrapSystemAdmin(systemUserDAO);

        sut = new QualityReportService(ratingService, paymentService, qualityReportDAO, userDAO);
        admin = staffAdmin("staff01");
        seller = normalSeller("seller01", 3.0);
        winner = normalBidder("winner01", 3.0);
        auction = finishedAuctionWithWinner(seller, winner, 2_500_000L);
        report = pendingReport(winner, auction.getId());
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSystemAdmin();
        resetAuctionManager();
    }

    @Nested
    @DisplayName("submitReport()")
    class SubmitReport {

        @Test
        @DisplayName("submitReport() với report hợp lệ thì lưu report và trả về đúng object")
        void submitReport_validReport_savesAndReturnsSameObject() {
            // Arrange
            when(qualityReportDAO.saveReport(report)).thenReturn(true);

            // Act
            QualityReport result = sut.submitReport(report);

            // Assert
            assertThat(result).isSameAs(report);
            verify(qualityReportDAO, times(1)).saveReport(report);
        }

        @Test
        @DisplayName("submitReport() giữ nguyên trạng thái PENDING của report")
        void submitReport_validReport_keepsPendingStatus() {
            // Arrange
            when(qualityReportDAO.saveReport(report)).thenReturn(true);

            // Act
            sut.submitReport(report);

            // Assert
            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.PENDING);
            assertThat(report.isRefundCompleted()).isFalse();
        }

        @Test
        @DisplayName("submitReport() với report null thì ném IllegalArgumentException và không gọi DAO")
        void submitReport_nullReport_throwsAndDoesNotPersist() {
            // Act & Assert
            assertThatThrownBy(() -> sut.submitReport(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("QualityReport");

            verifyNoInteractions(qualityReportDAO);
        }
    }

    @Nested
    @DisplayName("approveReport()")
    class ApproveReport {

        @Test
        @DisplayName("approveReport() với PENDING report thì chuyển status sang APPROVED")
        void approveReport_pendingReport_setsApprovedStatus() {
            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.APPROVED);
        }

        @Test
        @DisplayName("approveReport() có winner và finalPrice > 0 thì refund từ bank và đánh dấu refund completed")
        void approveReport_withWinnerFinalPrice_refundsWinnerAndMarksRefundCompleted() {
            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            verify(paymentService, times(1)).refundToWinnerFromBank(auction);
            assertThat(report.isRefundCompleted()).isTrue();
        }

        @Test
        @DisplayName("approveReport() phạt seller qua ratingService")
        void approveReport_penalizesSeller() {
            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            verify(ratingService, times(1)).penalizeSeller(seller);
        }

        @Test
        @DisplayName("approveReport() cập nhật account status của seller sau moderation")
        void approveReport_updatesSellerAccountStatus() {
            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            verify(userDAO, times(1)).updateAccountStatus(seller.getId(), seller.getAccountStatus().name());
        }

        @Test
        @DisplayName("approveReport() khi seller bị auto ban thì persist status BANNED")
        void approveReport_sellerFallsBelowThreshold_persistsBannedStatus() {
            // Arrange
            seller = normalSeller("seller-low", 2.1);
            auction = finishedAuctionWithWinner(seller, winner, 2_500_000L);
            report = pendingReport(winner, auction.getId());
            doAnswer(invocation -> {
                seller.adjustRating(-1.0);
                return null;
            }).when(ratingService).penalizeSeller(seller);

            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            assertThat(seller.getAccountStatus()).isEqualTo(User.AccountStatus.BANNED);
            verify(systemUserDAO, times(1)).updateAccountStatus(seller.getId(), User.AccountStatus.BANNED.name());
            verify(userDAO, times(1)).updateAccountStatus(seller.getId(), User.AccountStatus.BANNED.name());
        }

        @Test
        @DisplayName("approveReport() không có winner thì không refund và không đánh dấu refund completed")
        void approveReport_withoutWinner_doesNotRefund() {
            // Arrange
            auction = auctionWithoutWinner(seller);
            report = pendingReport(winner, auction.getId());

            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            verify(paymentService, never()).refundToWinnerFromBank(auction);
            assertThat(report.isRefundCompleted()).isFalse();
        }

        @Test
        @DisplayName("approveReport() ghi action log cho admin thực hiện")
        void approveReport_recordsAdminActionLog() {
            // Arrange
            int logSizeBefore = admin.getActionLog().size();

            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            assertThat(admin.getActionLog()).hasSize(logSizeBefore + 1);
            assertThat(admin.getActionLog().get(admin.getActionLog().size() - 1))
                    .contains(admin.getUsername())
                    .contains(seller.getUsername())
                    .contains(winner.getUsername());
        }

        @Test
        @DisplayName("approveReport() notify staff và global observer bằng QUALITY_REPORT_APPROVED")
        void approveReport_notifiesStaffAndGlobalObservers() {
            // Arrange
            AuctionObserver staffObserver = org.mockito.Mockito.mock(AuctionObserver.class);
            AuctionObserver globalObserver = org.mockito.Mockito.mock(AuctionObserver.class);
            AuctionManager.getInstance().addStaffObserver(staffObserver);
            AuctionManager.getInstance().addGlobalObserver(globalObserver);

            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            ArgumentCaptor<AuctionEvent> staffCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
            ArgumentCaptor<AuctionEvent> globalCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(staffObserver, times(1)).onAuctionEnded(staffCaptor.capture());
            verify(globalObserver, times(1)).onAuctionEnded(globalCaptor.capture());

            assertThat(staffCaptor.getValue().getEventType())
                    .isEqualTo(AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED);
            assertThat(staffCaptor.getValue().getAuction()).isSameAs(auction);
            assertThat(staffCaptor.getValue().getBidder()).isSameAs(winner);
            assertThat(globalCaptor.getValue().getEventType())
                    .isEqualTo(AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED);
        }

        @Test
        @DisplayName("approveReport() với report APPROVED thì ném IllegalStateException và không gọi dependency")
        void approveReport_alreadyApproved_throwsAndDoesNotInteract() {
            // Arrange
            report.approve();

            // Act & Assert
            assertThatThrownBy(() -> sut.approveReport(admin, report, auction))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING");

            verifyNoInteractions(ratingService, paymentService, userDAO);
        }

        @Test
        @DisplayName("approveReport() với report REJECTED thì ném IllegalStateException và giữ nguyên REJECTED")
        void approveReport_rejectedReport_throwsAndKeepsRejected() {
            // Arrange
            report.reject();

            // Act & Assert
            assertThatThrownBy(() -> sut.approveReport(admin, report, auction))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REJECTED");

            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.REJECTED);
            verifyNoInteractions(ratingService, paymentService, userDAO);
        }

        @Test
        @DisplayName("approveReport() gọi lặp lại thì lần hai bị chặn")
        void approveReport_repeatedApprove_secondCallThrows() {
            // Arrange
            sut.approveReport(admin, report, auction);

            // Act & Assert
            assertThatThrownBy(() -> sut.approveReport(admin, report, auction))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APPROVED");

            verify(paymentService, times(1)).refundToWinnerFromBank(auction);
            verify(ratingService, times(1)).penalizeSeller(seller);
        }
    }

    @Nested
    @DisplayName("rejectReport()")
    class RejectReport {

        @Test
        @DisplayName("rejectReport() với PENDING report thì chuyển status sang REJECTED")
        void rejectReport_pendingReport_setsRejectedStatus() {
            // Act
            sut.rejectReport(admin, report);

            // Assert
            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.REJECTED);
        }

        @Test
        @DisplayName("rejectReport() không refund winner")
        void rejectReport_doesNotRefundWinner() {
            // Act
            sut.rejectReport(admin, report);

            // Assert
            verifyNoInteractions(paymentService);
            assertThat(report.isRefundCompleted()).isFalse();
        }

        @Test
        @DisplayName("rejectReport() không phạt seller")
        void rejectReport_doesNotPenalizeSeller() {
            // Act
            sut.rejectReport(admin, report);

            // Assert
            verifyNoInteractions(ratingService);
        }

        @Test
        @DisplayName("rejectReport() ghi action log cho admin")
        void rejectReport_recordsAdminActionLog() {
            // Arrange
            int logSizeBefore = admin.getActionLog().size();

            // Act
            sut.rejectReport(admin, report);

            // Assert
            assertThat(admin.getActionLog()).hasSize(logSizeBefore + 1);
            assertThat(admin.getActionLog().get(admin.getActionLog().size() - 1))
                    .contains(admin.getUsername())
                    .contains(winner.getUsername())
                    .contains(report.getAuctionId());
        }

        @Test
        @DisplayName("rejectReport() với report APPROVED thì ném IllegalStateException và giữ nguyên APPROVED")
        void rejectReport_approvedReport_throwsAndKeepsApproved() {
            // Arrange
            report.approve();

            // Act & Assert
            assertThatThrownBy(() -> sut.rejectReport(admin, report))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APPROVED");

            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.APPROVED);
            verifyNoInteractions(ratingService, paymentService, userDAO);
        }

        @Test
        @DisplayName("rejectReport() với report REJECTED thì ném IllegalStateException")
        void rejectReport_alreadyRejected_throws() {
            // Arrange
            report.reject();

            // Act & Assert
            assertThatThrownBy(() -> sut.rejectReport(admin, report))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REJECTED");

            verifyNoInteractions(ratingService, paymentService, userDAO);
        }

        @Test
        @DisplayName("rejectReport() gọi lặp lại thì lần hai bị chặn")
        void rejectReport_repeatedReject_secondCallThrows() {
            // Arrange
            sut.rejectReport(admin, report);

            // Act & Assert
            assertThatThrownBy(() -> sut.rejectReport(admin, report))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(admin.getActionLog()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("workflow consistency")
    class WorkflowConsistency {

        @Test
        @DisplayName("approveReport() sau rejectReport() bị chặn và không refund")
        void approveAfterReject_isBlocked() {
            // Arrange
            sut.rejectReport(admin, report);

            // Act & Assert
            assertThatThrownBy(() -> sut.approveReport(admin, report, auction))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.REJECTED);
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("rejectReport() sau approveReport() bị chặn và không đảo trạng thái")
        void rejectAfterApprove_isBlocked() {
            // Arrange
            sut.approveReport(admin, report, auction);

            // Act & Assert
            assertThatThrownBy(() -> sut.rejectReport(admin, report))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.APPROVED);
            assertThat(report.isRefundCompleted()).isTrue();
            verify(paymentService, times(1)).refundToWinnerFromBank(auction);
        }

        @Test
        @DisplayName("hai report độc lập không chia sẻ state khi một report được approve")
        void twoReports_approvingOneDoesNotAffectOther() {
            // Arrange
            QualityReport otherReport = pendingReport(winner, auction.getId());

            // Act
            sut.approveReport(admin, report, auction);

            // Assert
            assertThat(report.getStatus()).isEqualTo(QualityReport.ReportStatus.APPROVED);
            assertThat(otherReport.getStatus()).isEqualTo(QualityReport.ReportStatus.PENDING);
            assertThat(otherReport.isRefundCompleted()).isFalse();
        }
    }

    private static QualityReport pendingReport(NormalUser reporter, String auctionId) {
        return QualityReport.create(
                reporter,
                auctionId,
                "Hàng nhận được không đúng mô tả",
                List.of("https://evidence.test/image-1.jpg"));
    }

    private static Auction finishedAuctionWithWinner(NormalUser seller, NormalUser winner, long finalPrice) {
        Auction auction = auctionWithoutWinner(seller);
        auction.setWinner(AuctionWinner.create(winner, auction.getId(), finalPrice, finalPrice / 10, false));
        return auction;
    }

    private static Auction auctionWithoutWinner(NormalUser seller) {
        return Auction.create(
                art("Tranh kiểm thử", 1_000_000L, seller),
                NOW.minusDays(2),
                NOW.minusDays(1),
                2_000_000L);
    }

    private static Art art(String name, long startingPrice, NormalUser seller) {
        return Art.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(3),
                NOW.minusDays(3),
                name,
                "Mô tả kiểm thử",
                startingPrice,
                seller,
                "Họa sĩ",
                2020,
                "Sơn dầu");
    }

    private static NormalUser normalSeller(String username, double rating) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(10),
                NOW.minusDays(10),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                rating,
                10_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false,
                false,
                null);
    }

    private static NormalUser normalBidder(String username, double rating) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(10),
                NOW.minusDays(10),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                rating,
                10_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    private static Admin staffAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(10),
                NOW.minusDays(10),
                username,
                User.hashPassword("adminPass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                5.0,
                Admin.LEVEL_STAFF,
                null);
    }

    private static void bootstrapSystemAdmin(UserDAO userDAO) throws Exception {
        Constructor<SystemAdmin> constructor = SystemAdmin.class
                .getDeclaredConstructor(String.class, String.class, String.class);
        constructor.setAccessible(true);
        SystemAdmin systemAdmin = constructor.newInstance("SYSTEM", "systemPass1", "system@test.com");

        Field userDaoField = SystemAdmin.class.getDeclaredField("userDAO");
        userDaoField.setAccessible(true);
        userDaoField.set(systemAdmin, userDAO);

        Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, systemAdmin);
    }

    private static void resetSystemAdmin() throws Exception {
        Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static void resetAuctionManager() throws Exception {
        Field allAuctionsField = AuctionManager.class.getDeclaredField("allAuctions");
        allAuctionsField.setAccessible(true);
        ((Map<?, ?>) allAuctionsField.get(AuctionManager.getInstance())).clear();

        Field allUsersField = AuctionManager.class.getDeclaredField("allUsers");
        allUsersField.setAccessible(true);
        ((Map<?, ?>) allUsersField.get(AuctionManager.getInstance())).clear();

        Field globalObserversField = AuctionManager.class.getDeclaredField("globalObservers");
        globalObserversField.setAccessible(true);
        ((List<?>) globalObserversField.get(AuctionManager.getInstance())).clear();

        Field staffObserversField = AuctionManager.class.getDeclaredField("staffObservers");
        staffObserversField.setAccessible(true);
        ((List<?>) staffObserversField.get(AuctionManager.getInstance())).clear();
    }
}
