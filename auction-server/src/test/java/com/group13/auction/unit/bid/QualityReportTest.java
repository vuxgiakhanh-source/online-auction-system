package com.group13.auction.unit.bid;

import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link QualityReport} — state transitions.
 */
@DisplayName("QualityReport")
class QualityReportTest {

    private NormalUser reporter;
    private String auctionId;

    @BeforeEach
    void setUp() {
        reporter = TestFixture.normalBidder("qrReporter1");
        auctionId = UUID.randomUUID().toString();
    }

    @Test
    void create_withImages_pending() {
        QualityReport report = QualityReport.create(reporter, auctionId,
                "Mô tả lỗi", List.of("http://img/1.jpg"));
        assertEquals(QualityReport.ReportStatus.PENDING, report.getStatus());
        assertFalse(report.getImageUrls().isEmpty());
    }

    @Test
    void create_emptyImages_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> QualityReport.create(reporter, auctionId, "desc", List.of()));
    }

    @Test
    void approve_fromPending() {
        QualityReport report = TestFixture.pendingReportWithImages(reporter, auctionId,
                List.of("http://img/a.jpg"));
        report.approve();
        assertEquals(QualityReport.ReportStatus.APPROVED, report.getStatus());
    }

    @Test
    void reject_fromPending() {
        QualityReport report = TestFixture.pendingReport(reporter, auctionId);
        report.reject();
        assertEquals(QualityReport.ReportStatus.REJECTED, report.getStatus());
    }

    @Test
    void markRefundCompleted_setsFlag() {
        QualityReport report = TestFixture.overdueSellerRefundReport(reporter, auctionId);
        report.markRefundCompleted();
        assertTrue(report.isRefundCompleted());
    }
}
