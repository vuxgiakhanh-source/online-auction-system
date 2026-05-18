package com.group13.auction.ui.controller.notification;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.notification.NotificationService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.notification.NotificationItemViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

/** Controller cho màn trung tâm thông báo. */
public final class NotificationCenterController {

    private final NotificationService notificationService = new NotificationService();

    @FXML private ListView<NotificationItemViewModel> notificationListView;

    @FXML private VBox emptyStateBox;

    @FXML private Label titleLabel;

    @FXML private Label bodyLabel;

    @FXML private Label typeLabel;

    @FXML private Label createdAtLabel;

    @FXML private Label readStateLabel;

    @FXML private Label relatedAuctionLabel;

    @FXML private Label statusLabel;

    @FXML private Button markReadButton;

    @FXML private Button openAuctionButton;

    @FXML private Button refreshButton;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo notification center và tải danh sách thông báo. */
    @FXML
    public void initialize() {
        configureListView();
        configureSelectionListener();
        clearDetail();
        loadNotifications();
    }

    /** Quay lại dashboard chính. */
    @FXML
    public void handleBackToHome() {
        Navigator.getInstance().goToMainLayout();
    }

    /** Tải lại danh sách thông báo. */
    @FXML
    public void handleRefresh() {
        loadNotifications();
    }

    /** Đánh dấu thông báo đang chọn là đã đọc. */
    @FXML
    public void handleMarkRead() {
        NotificationItemViewModel selected =
                notificationListView.getSelectionModel().getSelectedItem();

        if (selected == null) {
            AlertUtil.showWarning("Vui lòng chọn một thông báo.");
            return;
        }

        if (selected.read()) {
            AlertUtil.showInfo("Thông báo này đã được đánh dấu là đã đọc.");
            return;
        }

        setLoading(true, "Đang đánh dấu thông báo là đã đọc...");

        notificationService
                .markNotificationRead(selected.id())
                .thenRun(
                        () ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            replaceSelectedNotification(selected.markRead());
                                            setLoading(false, "Đã đánh dấu thông báo là đã đọc.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không đánh dấu được thông báo.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    /** Mở phiên đấu giá liên quan nếu thông báo có gắn mã phiên. */
    @FXML
    public void handleOpenRelatedAuction() {
        NotificationItemViewModel selected =
                notificationListView.getSelectionModel().getSelectedItem();

        if (selected == null || !selected.hasRelatedAuction()) {
            AlertUtil.showWarning("Thông báo này không gắn với phiên đấu giá cụ thể.");
            return;
        }

        AppContext.getInstance()
                .getScreenStateStore()
                .put(ScreenStateKeys.SELECTED_AUCTION_ID, selected.relatedAuctionId());
        Navigator.getInstance().goToAuctionDetail();
    }

    private void configureListView() {
        notificationListView.setCellFactory(
                ignored ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(
                                    NotificationItemViewModel notification, boolean empty) {
                                super.updateItem(notification, empty);

                                if (empty || notification == null) {
                                    setText(null);
                                    setGraphic(null);
                                    return;
                                }

                                setText(
                                        notification.title()
                                                + "\n"
                                                + notification.createdAtText()
                                                + "  •  "
                                                + notification.readStateText());
                            }
                        });
    }

    private void configureSelectionListener() {
        notificationListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selected) -> renderDetail(selected));
    }

    private void loadNotifications() {
        setLoading(true, "Đang tải thông báo...");

        notificationService
                .getNotifications()
                .thenAccept(
                        notifications ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            renderNotifications(notifications);
                                            setLoading(false, "Đã tải danh sách thông báo.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không tải được danh sách thông báo.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void renderNotifications(List<NotificationItemViewModel> notifications) {
        List<NotificationItemViewModel> safeNotifications =
                notifications == null ? List.of() : notifications;

        notificationListView.setItems(FXCollections.observableArrayList(safeNotifications));

        boolean empty = safeNotifications.isEmpty();
        emptyStateBox.setVisible(empty);
        emptyStateBox.setManaged(empty);
        notificationListView.setVisible(!empty);
        notificationListView.setManaged(!empty);

        if (empty) {
            clearDetail();
            statusLabel.setText("Server hiện chưa trả về thông báo nào.");
            return;
        }

        notificationListView.getSelectionModel().selectFirst();
    }

    private void renderDetail(NotificationItemViewModel notification) {
        if (notification == null) {
            clearDetail();
            return;
        }

        titleLabel.setText(notification.title());
        bodyLabel.setText(notification.body());
        typeLabel.setText(notification.type());
        createdAtLabel.setText(notification.createdAtText());
        readStateLabel.setText(notification.readStateText());

        if (notification.hasRelatedAuction()) {
            relatedAuctionLabel.setText(notification.relatedAuctionId());
            openAuctionButton.setDisable(false);
        } else {
            relatedAuctionLabel.setText("Không có");
            openAuctionButton.setDisable(true);
        }

        markReadButton.setDisable(notification.read());
    }

    private void clearDetail() {
        titleLabel.setText("Chưa chọn thông báo");
        bodyLabel.setText("Chọn một thông báo ở danh sách bên trái để xem chi tiết.");
        typeLabel.setText("--");
        createdAtLabel.setText("--");
        readStateLabel.setText("--");
        relatedAuctionLabel.setText("--");
        markReadButton.setDisable(true);
        openAuctionButton.setDisable(true);
    }

    private void replaceSelectedNotification(NotificationItemViewModel updatedNotification) {
        int selectedIndex = notificationListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        notificationListView.getItems().set(selectedIndex, updatedNotification);
        notificationListView.getSelectionModel().select(selectedIndex);
        renderDetail(updatedNotification);
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        refreshButton.setDisable(loading);
        notificationListView.setDisable(loading);

        NotificationItemViewModel selected =
                notificationListView.getSelectionModel().getSelectedItem();
        markReadButton.setDisable(loading || selected == null || selected.read());
        openAuctionButton.setDisable(loading || selected == null || !selected.hasRelatedAuction());

        statusLabel.setText(message);
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank()
                ? "Có lỗi xảy ra khi xử lý thông báo."
                : message;
    }
}