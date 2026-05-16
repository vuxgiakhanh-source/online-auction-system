package com.group13.auction.ui.controller.admin;

import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public final class AdminQualityReportsController extends BaseController implements PageLifecycle {

    @FXML private TableView<ReportDTOs.QualityReportDTO> table;

    @FXML
    private void initialize() {
        TableColumn<ReportDTOs.QualityReportDTO, String> id = new TableColumn<>("ID");
        id.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReportId()));
        TableColumn<ReportDTOs.QualityReportDTO, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        table.getColumns().setAll(id, st);
        table.setItems(services().adminModerationService().pendingReports());
    }

    @Override
    public void onShow() {
        services().adminModerationService().loadQualityReports();
    }

    @FXML
    private void onApprove() {
        ReportDTOs.QualityReportDTO r = table.getSelectionModel().getSelectedItem();
        if (r != null) {
            services().adminModerationService().approveReport(r.getReportId());
        }
    }

    @FXML
    private void onReject() {
        ReportDTOs.QualityReportDTO r = table.getSelectionModel().getSelectedItem();
        if (r != null) {
            services().adminModerationService().rejectReport(r.getReportId());
        }
    }
}
