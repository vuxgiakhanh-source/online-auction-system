package com.group13.auction.ui.controller.report;

import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.network.http.ImageUploadService;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;

public final class QualityReportController extends BaseController implements PageLifecycle {

    @FXML private TextArea descArea;
    @FXML private Label evidenceLabel;
    private final List<String> evidenceUrls = new ArrayList<>();

    @Override
    public void onShow() {
        evidenceUrls.clear();
        evidenceLabel.setText("Chưa có ảnh bằng chứng");
    }

    @FXML
    private void onUploadEvidence() {
        FileChooser chooser = new FileChooser();
        List<File> files = chooser.showOpenMultipleDialog(descArea.getScene().getWindow());
        if (files == null) {
            return;
        }
        try {
            evidenceUrls.clear();
            for (File f : files) {
                evidenceUrls.add(ImageUploadService.getInstance().upload(Path.of(f.getAbsolutePath())));
            }
            evidenceLabel.setText(evidenceUrls.size() + " ảnh đã upload");
        } catch (Exception e) {
            AlertUtil.showError(e.getMessage());
        }
    }

    @FXML
    private void onSubmit() {
        String auctionId = screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse(null);
        if (auctionId == null) {
            AlertUtil.showWarning("Chọn phiên (auctionId) trước.");
            return;
        }
        ReportDTOs.QualityReportRequestDTO req = new ReportDTOs.QualityReportRequestDTO();
        req.setAuctionId(auctionId);
        req.setDescription(descArea.getText());
        req.setEvidenceUrls(new ArrayList<>(evidenceUrls));
        services().qualityReportService().submit(req);
    }
}
