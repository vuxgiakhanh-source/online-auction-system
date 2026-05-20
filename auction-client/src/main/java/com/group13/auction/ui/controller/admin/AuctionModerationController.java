package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.admin.AdminAuctionService;
import com.group13.auction.viewmodel.admin.AuctionModerationViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller cho màn Admin quản lý phiên đấu giá.
 *
 * <p>Controller chỉ chọn phiên, chọn lý do và gửi request hủy phiên tới server. Các rule liên quan
 * trạng thái phiên và hậu xử lý là trách nhiệm của server.
 */
public final class AuctionModerationController {

    private final AdminAuctionService adminAuctionService = new AdminAuctionService();

    @FXML private TableView<AuctionModerationViewModel> auctionTable;
    @FXML private TableColumn<AuctionModerationViewModel, String> auctionIdColumn;
    @FXML private TableColumn<AuctionModerationViewModel, String> titleColumn;
    @FXML private TableColumn<AuctionModerationViewModel, String> sellerColumn;
    @FXML private TableColumn<AuctionModerationViewModel, String> currentPriceColumn;
    @FXML private TableColumn<AuctionModerationViewModel, String> statusColumn;
    @FXML private TableColumn<AuctionModerationViewModel, String> startTimeColumn;
    @FXML private TableColumn<AuctionModerationViewModel, String> endTimeColumn;

    @FXML private ChoiceBox<String> cancelReasonChoiceBox;

    @FXML private Label statusLabel;
    @FXML private Label emptyStateLabel;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML private Button refreshButton;
    @FXML private Button cancelButton;
    @FXML private Button backButton;

    /** Khởi tạo bảng quản lý phiên đấu giá và tải dữ liệu lần đầu. */
    @FXML
    private void initialize() {
        configureTable();
        configureCancelReasons();
        configureSelectionBinding();
        setBusy(false);
        setCancelButtonDisabled(true);
        showEmptyState("Chưa có dữ liệu phiên đấu giá.");
        loadAuctions();
    }

    @FXML
    private void handleRefresh() {
        loadAuctions();
    }

    @FXML
    private void handleCancelAuction() {
        AuctionModerationViewModel selectedAuction = getSelectedAuction();
        if (selectedAuction == null) {
            showStatus("Vui lòng chọn phiên đấu giá cần hủy.");
            return;
        }

        if (!selectedAuction.isCancellable()) {
            showStatus("Phiên đấu giá này không thể hủy từ client.");
            return;
        }

        String reason = cancelReasonChoiceBox == null ? null : cancelReasonChoiceBox.getValue();
        setBusy(true);
        showStatus("Đang gửi yêu cầu hủy phiên đấu giá...");

        adminAuctionService
                .cancelAuctionAsAdmin(selectedAuction.getAuctionId(), reason)
                .whenComplete((updatedAuction, throwable) -> handleMutationResult(throwable));
    }

    @FXML
    private void handleBackToDashboard() {
        Navigator.getInstance().goToAdminDashboard();
    }

    private void loadAuctions() {
        setBusy(true);
        showStatus("Đang tải danh sách phiên đấu giá...");
        showEmptyState("");

        adminAuctionService
                .getAllAuctionsForAdmin()
                .whenComplete((auctions, throwable) -> handleAuctionsResult(auctions, throwable));
    }

    private void configureTable() {
        if (auctionIdColumn != null) {
            auctionIdColumn.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        }
        if (titleColumn != null) {
            titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        }
        if (sellerColumn != null) {
            sellerColumn.setCellValueFactory(new PropertyValueFactory<>("sellerName"));
        }
        if (currentPriceColumn != null) {
            currentPriceColumn.setCellValueFactory(new PropertyValueFactory<>("currentPriceText"));
        }
        if (statusColumn != null) {
            statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        }
        if (startTimeColumn != null) {
            startTimeColumn.setCellValueFactory(new PropertyValueFactory<>("startTimeText"));
        }
        if (endTimeColumn != null) {
            endTimeColumn.setCellValueFactory(new PropertyValueFactory<>("endTimeText"));
        }
    }

    private void configureCancelReasons() {
        if (cancelReasonChoiceBox == null) {
            return;
        }

        cancelReasonChoiceBox.setItems(
                FXCollections.observableArrayList(
                        "NO_WINNER",
                        "RESERVE_NOT_MET",
                        "SELLER_REQUEST",
                        "SYSTEM_ERROR",
                        "FRAUDULENT_ITEM"));
        cancelReasonChoiceBox.setValue("SYSTEM_ERROR");
    }

    private void configureSelectionBinding() {
        if (auctionTable == null) {
            return;
        }

        auctionTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selectedAuction) -> updateCancelButton(selectedAuction));
    }

    private void handleAuctionsResult(List<AuctionModerationViewModel> auctions, Throwable throwable) {
        Platform.runLater(
                () -> {
                    setBusy(false);

                    if (throwable != null) {
                        showStatus(errorMessage(throwable, "Không tải được danh sách phiên đấu giá."));
                        showEmptyState("Không tải được danh sách phiên đấu giá.");
                        setCancelButtonDisabled(true);
                        return;
                    }

                    List<AuctionModerationViewModel> safeAuctions = auctions == null ? List.of() : auctions;
                    if (auctionTable != null) {
                        auctionTable.setItems(FXCollections.observableArrayList(safeAuctions));
                    }

                    if (safeAuctions.isEmpty()) {
                        showStatus("Đã tải danh sách phiên đấu giá.");
                        showEmptyState("Không có phiên đấu giá nào.");
                    } else {
                        showStatus("Tải danh sách phiên đấu giá thành công.");
                        showEmptyState("");
                    }

                    updateCancelButton(getSelectedAuction());
                });
    }

    private void handleMutationResult(Throwable throwable) {
        Platform.runLater(
                () -> {
                    if (throwable != null) {
                        setBusy(false);
                        showStatus(errorMessage(throwable, "Không hủy được phiên đấu giá."));
                        updateCancelButton(getSelectedAuction());
                        return;
                    }

                    showStatus("Hủy phiên đấu giá thành công.");
                    loadAuctions();
                });
    }

    private void updateCancelButton(AuctionModerationViewModel selectedAuction) {
        if (cancelButton != null) {
            cancelButton.setDisable(selectedAuction == null || !selectedAuction.isCancellable());
        }
    }

    private void setCancelButtonDisabled(boolean disabled) {
        if (cancelButton != null) {
            cancelButton.setDisable(disabled);
        }
    }

    private AuctionModerationViewModel getSelectedAuction() {
        if (auctionTable == null) {
            return null;
        }

        return auctionTable.getSelectionModel().getSelectedItem();
    }

    private void setBusy(boolean busy) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(busy);
            loadingIndicator.setManaged(busy);
        }
        if (refreshButton != null) {
            refreshButton.setDisable(busy);
        }
        if (backButton != null) {
            backButton.setDisable(busy);
        }

        if (busy) {
            setCancelButtonDisabled(true);
        } else {
            updateCancelButton(getSelectedAuction());
        }
    }

    private void showStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private void showEmptyState(String message) {
        if (emptyStateLabel != null) {
            boolean visible = message != null && !message.isBlank();
            emptyStateLabel.setText(message == null ? "" : message);
            emptyStateLabel.setVisible(visible);
            emptyStateLabel.setManaged(visible);
        }
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