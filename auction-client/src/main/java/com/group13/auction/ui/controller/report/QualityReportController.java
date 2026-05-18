package com.group13.auction.ui.controller.report;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.report.QualityReportService;
import com.group13.auction.viewmodel.report.QualityReportViewModel;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/**
 * Controller cho màn Quality Report.
 *
 * <p>Controller cho phép chọn ảnh local, service sẽ upload ảnh trước rồi gửi các URL bằng chứng
 * trong {@code QualityReportRequestDTO.evidenceUrls}.
 */
public final class QualityReportController {

    private static final int MAX_IMAGE_COUNT = 5;

    private final QualityReportService qualityReportService = new QualityReportService();
    private final List<Path> selectedImagePaths = new ArrayList<>();

    @FXML private TextField auctionIdField;
    @FXML private TextArea descriptionArea;
    @FXML private ListView<String> selectedImagesListView;

    @FXML private Label statusLabel;
    @FXML private Label resultLabel;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML private Button chooseImagesButton;
    @FXML private Button removeSelectedImageButton;
    @FXML private Button clearImagesButton;
    @FXML private Button submitButton;
    @FXML private Button clearButton;
    @FXML private Button backButton;

    /** Khởi tạo trạng thái ban đầu của form Quality Report. */
    @FXML
    private void initialize() {
        setBusy(false);
        refreshSelectedImagesView();
        showStatus("Nhập thông tin report và có thể đính kèm ảnh bằng chứng.");
        showResult("");
    }

    @FXML
    private void handleChooseImages() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh bằng chứng");
        fileChooser
                .getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                "Image files", "*.png", "*.jpg", "*.jpeg", "*.webp"));

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        for (File file : selectedFiles) {
            if (selectedImagePaths.size() >= MAX_IMAGE_COUNT) {
                showStatus("Chỉ được chọn tối đa " + MAX_IMAGE_COUNT + " ảnh bằng chứng.");
                break;
            }

            Path path = file.toPath();
            if (!selectedImagePaths.contains(path)) {
                selectedImagePaths.add(path);
            }
        }

        refreshSelectedImagesView();
    }

    @FXML
    private void handleRemoveSelectedImage() {
        if (selectedImagesListView == null) {
            return;
        }

        int selectedIndex = selectedImagesListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= selectedImagePaths.size()) {
            showStatus("Vui lòng chọn ảnh cần xóa.");
            return;
        }

        selectedImagePaths.remove(selectedIndex);
        refreshSelectedImagesView();
    }

    @FXML
    private void handleClearImages() {
        selectedImagePaths.clear();
        refreshSelectedImagesView();
        showStatus("Đã xóa danh sách ảnh đã chọn.");
    }

    @FXML
    private void handleSubmitReport() {
        String auctionId = textOf(auctionIdField);
        String description = textOf(descriptionArea);

        setBusy(true);
        showStatus(
                selectedImagePaths.isEmpty()
                        ? "Đang gửi Quality Report..."
                        : "Đang upload ảnh bằng chứng và gửi Quality Report...");
        showResult("");

        qualityReportService
                .submitQualityReportWithImages(auctionId, description, List.copyOf(selectedImagePaths))
                .whenComplete((report, throwable) -> handleSubmitResult(report, throwable));
    }

    @FXML
    private void handleClearForm() {
        clearForm();
        showStatus("Đã xóa form.");
        showResult("");
    }

    @FXML
    private void handleBackToMain() {
        Navigator.getInstance().goToMainLayout();
    }

    private void handleSubmitResult(QualityReportViewModel report, Throwable throwable) {
        Platform.runLater(
                () -> {
                    setBusy(false);

                    if (throwable != null) {
                        showStatus(errorMessage(throwable, "Không gửi được Quality Report."));
                        return;
                    }

                    showStatus("Gửi Quality Report thành công.");
                    showResult(formatReportResult(report));
                    clearForm();
                });
    }

    private String formatReportResult(QualityReportViewModel report) {
        if (report == null) {
            return "Report đã được tạo.";
        }

        return "Report ID: "
                + report.getReportId()
                + "\nAuction ID: "
                + report.getAuctionId()
                + "\nStatus: "
                + report.getStatus();
    }

    private void clearForm() {
        if (auctionIdField != null) {
            auctionIdField.clear();
        }
        if (descriptionArea != null) {
            descriptionArea.clear();
        }

        selectedImagePaths.clear();
        refreshSelectedImagesView();
    }

    private void refreshSelectedImagesView() {
        if (selectedImagesListView == null) {
            return;
        }

        selectedImagesListView
                .getItems()
                .setAll(selectedImagePaths.stream().map(path -> path.getFileName().toString()).toList());

        boolean hasImages = !selectedImagePaths.isEmpty();
        if (removeSelectedImageButton != null) {
            removeSelectedImageButton.setDisable(!hasImages);
        }
        if (clearImagesButton != null) {
            clearImagesButton.setDisable(!hasImages);
        }
    }

    private void setBusy(boolean busy) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(busy);
            loadingIndicator.setManaged(busy);
        }
        if (chooseImagesButton != null) {
            chooseImagesButton.setDisable(busy);
        }
        if (removeSelectedImageButton != null) {
            removeSelectedImageButton.setDisable(busy || selectedImagePaths.isEmpty());
        }
        if (clearImagesButton != null) {
            clearImagesButton.setDisable(busy || selectedImagePaths.isEmpty());
        }
        if (submitButton != null) {
            submitButton.setDisable(busy);
        }
        if (clearButton != null) {
            clearButton.setDisable(busy);
        }
        if (backButton != null) {
            backButton.setDisable(busy);
        }
    }

    private void showStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private void showResult(String message) {
        if (resultLabel != null) {
            boolean visible = message != null && !message.isBlank();
            resultLabel.setText(message == null ? "" : message);
            resultLabel.setVisible(visible);
            resultLabel.setManaged(visible);
        }
    }

    private String textOf(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private String textOf(TextArea area) {
        return area == null || area.getText() == null ? "" : area.getText().trim();
    }

    private String errorMessage(Throwable throwable, String fallbackMessage) {
        Throwable root = unwrap(throwable);
        String message = root.getMessage();
        return message == null || message.isBlank() ? fallbackMessage : message;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}