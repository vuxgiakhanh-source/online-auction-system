package com.group13.auction.service.report;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;

/**
 * Bidder gửi báo cáo chất lượng sau giao dịch.
 */
public final class QualityReportService extends NetworkService implements ClientEventListener {

    public void submit(ReportDTOs.QualityReportRequestDTO request) {
        network().submitQualityReport(request);
    }

    @Override
    public void onSubmitQualityReportSuccess(ReportDTOs.QualityReportDTO report) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Gửi báo cáo chất lượng thành công."));
    }

    @Override
    public void onSubmitQualityReportFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onQualityReportApprovedNotify(ReportDTOs.QualityReportResultDTO result) {}
}
