package com.group13.auction.service.admin;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

/**
 * Admin: duyệt báo cáo chất lượng & thông báo hủy phiên từ Seller.
 */
public final class AdminModerationService extends NetworkService implements ClientEventListener {

    private final ObservableList<ReportDTOs.QualityReportDTO> pendingReports =
            FXCollections.observableArrayList();

    public ObservableList<ReportDTOs.QualityReportDTO> pendingReports() {
        return pendingReports;
    }

    public void loadQualityReports() {
        network().adminGetQualityReports();
    }

    public void approveReport(String reportId) {
        network().adminApproveQualityReport(reportId);
    }

    public void rejectReport(String reportId) {
        network().adminRejectQualityReport(reportId);
    }

    @Override
    public void onAdminQualityReportsReceived(List<ReportDTOs.QualityReportDTO> reports) {
        pendingReports.setAll(reports != null ? reports : List.of());
    }

    @Override
    public void onAdminApproveQualityReportSuccess(ReportDTOs.QualityReportResultDTO result) {
        loadQualityReports();
    }

    @Override
    public void onAdminRejectQualityReportSuccess() {
        loadQualityReports();
    }

    @Override
    public void onSellerCancelRequestNotify(AuctionDTOs.SellerCancelRequestNotifyDTO dto) {}

    @Override
    public void onAdminApproveQualityReportFailed(ErrorDTO error) {}
}
