package com.group13.auction.ui.controller.report;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.report.QualityReportService;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
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
import javafx.stage.FileChooser;

/** Controller cho form gửi báo cáo chất lượng từ một đơn hàng đã chọn. */
public final class QualityReportController {

    private static final int MAX_IMAGE_COUNT = 5;

    private final QualityReportService qualityReportService = new QualityReportService();
    private final List<Path> selectedImagePaths = new ArrayList<>();

    private WonOrderViewModel selectedOrder;

    @FXML private Label itemNameLabel;
    @FXML private Label sellerLabel;
    @FXML private Label winningPriceLabel;
    @FXML private Label orderStatusLabel;
    @FXML private Label auctionIdLabel;

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

    /** Khởi tạo trạng thái ban đầu của form báo cáo. */
    @FXML
    private void initialize() {
        selectedOrder =
            AppContext.getInstance()
                .getScreenStateStore()
                .get(ScreenStateKeys.SELECTED_WON_ORDER, WonOrderViewModel.class)
                .orElse(null);

        setBusy(false);
        refreshSelectedImagesView();
        showResult("");

        if (selectedOrder == null) {
            renderMissingOrderState();
            return;
        }

        renderSelectedOrder(selectedOrder);

        if (selectedOrder.canSubmitReport()) {
            showStatus("Hãy mô tả vấn đề và chọn ảnh bằng chứng trước khi gửi báo cáo.");
        } else {
            showStatus("Vui lòng xác nhận đã nhận hàng trước khi gửi báo cáo.");
        }
    }

    @FXML
    private void handleChooseImages() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh bằng chứng");
        fileChooser
            .getExtensionFilters()
            .add(
                new FileChooser.ExtensionFilter(
                    "Ảnh (*.png, *.jpg, *.jpeg, *.webp)", "*.png", "*.jpg", "*.jpeg", "*.webp"));

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
        if (selectedOrder == null) {
            showStatus("Vui lòng chọn đơn hàng cần báo cáo.");
            return;
        }
        if (!selectedOrder.canSubmitReport()) {
            showStatus("Vui lòng xác nhận đã nhận hàng trước khi gửi báo cáo.");
            return;
        }

        String description = textOf(descriptionArea);

        setBusy(true);
        showStatus("Đang gửi báo cáo...");
        showResult("");

        qualityReportService
            .submitQualityReportWithImages(
                selectedOrder.auctionId(), description, List.copyOf(selectedImagePaths))
            .whenComplete((report, throwable) -> handleSubmitResult(report, throwable));
    }

    @FXML
    private void handleClearForm() {
        clearForm();
        showStatus("Đã xóa nội dung báo cáo.");
        showResult("");
    }

    @FXML
    private void handleBackToOrders() {
        Navigator.getInstance().goToMyOrders();
    }

    private void renderSelectedOrder(WonOrderViewModel order) {
        itemNameLabel.setText(order.itemName());
        sellerLabel.setText(order.sellerText());
        winningPriceLabel.setText(order.winningPriceText());
        orderStatusLabel.setText(order.statusText());
        auctionIdLabel.setText(order.auctionId());
        submitButton.setDisable(!order.canSubmitReport());
    }

    private void renderMissingOrderState() {
        itemNameLabel.setText("Chưa chọn đơn hàng");
        sellerLabel.setText("Người bán: --");
        winningPriceLabel.setText("--");
        orderStatusLabel.setText("--");
        auctionIdLabel.setText("--");

        if (submitButton != null) {
            submitButton.setDisable(true);
        }

        showStatus("Vui lòng chọn đơn hàng cần báo cáo từ danh sách đơn hàng của bạn.");
    }

    private void handleSubmitResult(QualityReportViewModel report, Throwable throwable) {
        Platform.runLater(
            () -> {
                setBusy(false);

                if (throwable != null) {
                    showStatus(errorMessage(throwable, "Không gửi được báo cáo."));
                    return;
                }

                showStatus("Đã gửi báo cáo thành công.");
                showResult(formatReportResult(report));
                clearForm();
            });
    }

    private String formatReportResult(QualityReportViewModel report) {
        if (report == null || report.getReportId() == null || report.getReportId().isBlank()) {
            return "Báo cáo đã được ghi nhận.";
        }

        return "Báo cáo đã được ghi nhận. Mã báo cáo: " + report.getReportId();
    }

    private void clearForm() {
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
            chooseImagesButton.setDisable(busy || selectedOrder == null);
        }
        if (removeSelectedImageButton != null) {
            removeSelectedImageButton.setDisable(busy || selectedImagePaths.isEmpty());
        }
        if (clearImagesButton != null) {
            clearImagesButton.setDisable(busy || selectedImagePaths.isEmpty());
        }
        if (submitButton != null) {
            submitButton.setDisable(busy || selectedOrder == null || !selectedOrder.canSubmitReport());
        }
        if (clearButton != null) {
            clearButton.setDisable(busy || selectedOrder == null);
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

    private String textOf(TextArea area) {
        return area == null || area.getText() == null ? "" : area.getText().trim();
    }

    private String errorMessage(Throwable throwable, String fallbackMessage) {
        Throwable root = unwrap(throwable);
        String message = root == null ? null : root.getMessage();
        return message == null || message.isBlank() ? fallbackMessage : message;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}