package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.admin.AdminUserService;
import com.group13.auction.viewmodel.admin.UserModerationViewModel;
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
 * Controller cho màn Admin quản lý người dùng.
 *
 * <p>Controller chỉ render dữ liệu, đọc lựa chọn từ bảng và gọi service. Các rule như user nào
 * được khóa hoặc mở khóa là trách nhiệm của server.
 */
public final class UserModerationController {

    private final AdminUserService adminUserService = new AdminUserService();

    @FXML private TableView<UserModerationViewModel> userTable;
    @FXML private TableColumn<UserModerationViewModel, String> userIdColumn;
    @FXML private TableColumn<UserModerationViewModel, String> usernameColumn;
    @FXML private TableColumn<UserModerationViewModel, String> emailColumn;
    @FXML private TableColumn<UserModerationViewModel, String> roleColumn;
    @FXML private TableColumn<UserModerationViewModel, String> statusColumn;

    @FXML private ChoiceBox<String> banReasonChoiceBox;

    @FXML private Label statusLabel;
    @FXML private Label emptyStateLabel;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML private Button refreshButton;
    @FXML private Button banButton;
    @FXML private Button unbanButton;
    @FXML private Button backButton;

    /** Khởi tạo bảng quản lý người dùng và tải dữ liệu lần đầu. */
    @FXML
    private void initialize() {
        configureTable();
        configureBanReasons();
        configureSelectionBinding();
        setBusy(false);
        setActionButtonsDisabled(true);
        showEmptyState("Chưa có dữ liệu người dùng.");
        loadUsers();
    }

    @FXML
    private void handleRefresh() {
        loadUsers();
    }

    @FXML
    private void handleBanUser() {
        UserModerationViewModel selectedUser = getSelectedUser();
        if (selectedUser == null) {
            showStatus("Vui lòng chọn người dùng cần khóa.");
            return;
        }

        String reason = banReasonChoiceBox == null ? null : banReasonChoiceBox.getValue();
        setBusy(true);
        showStatus("Đang khóa tài khoản người dùng...");

        adminUserService
                .banUser(selectedUser.getUserId(), reason)
                .whenComplete((updatedUser, throwable) -> handleMutationResult(throwable));
    }

    @FXML
    private void handleUnbanUser() {
        UserModerationViewModel selectedUser = getSelectedUser();
        if (selectedUser == null) {
            showStatus("Vui lòng chọn người dùng cần mở khóa.");
            return;
        }

        setBusy(true);
        showStatus("Đang mở khóa tài khoản người dùng...");

        adminUserService
                .unbanUser(selectedUser.getUserId())
                .whenComplete((updatedUser, throwable) -> handleMutationResult(throwable));
    }

    @FXML
    private void handleBackToDashboard() {
        Navigator.getInstance().goToAdminDashboard();
    }

    private void loadUsers() {
        setBusy(true);
        showStatus("Đang tải danh sách người dùng...");
        showEmptyState("");

        adminUserService
                .getAllUsers()
                .whenComplete((users, throwable) -> handleUsersResult(users, throwable));
    }

    private void configureTable() {
        if (userIdColumn != null) {
            userIdColumn.setCellValueFactory(new PropertyValueFactory<>("userId"));
        }
        if (usernameColumn != null) {
            usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        }
        if (emailColumn != null) {
            emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        }
        if (roleColumn != null) {
            roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        }
        if (statusColumn != null) {
            statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        }
    }

    private void configureBanReasons() {
        if (banReasonChoiceBox == null) {
            return;
        }

        banReasonChoiceBox.setItems(
                FXCollections.observableArrayList("FRAUD", "LOW_RATING", "POLICY_VIOLATION", "OTHER"));
        banReasonChoiceBox.setValue("OTHER");
    }

    private void configureSelectionBinding() {
        if (userTable == null) {
            return;
        }

        userTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, selectedUser) -> updateActionButtons(selectedUser));
    }

    private void handleUsersResult(List<UserModerationViewModel> users, Throwable throwable) {
        Platform.runLater(
                () -> {
                    setBusy(false);

                    if (throwable != null) {
                        showStatus(errorMessage(throwable, "Không tải được danh sách người dùng."));
                        showEmptyState("Không tải được danh sách người dùng.");
                        setActionButtonsDisabled(true);
                        return;
                    }

                    List<UserModerationViewModel> safeUsers = users == null ? List.of() : users;
                    if (userTable != null) {
                        userTable.setItems(FXCollections.observableArrayList(safeUsers));
                    }

                    if (safeUsers.isEmpty()) {
                        showStatus("Đã tải danh sách người dùng.");
                        showEmptyState("Không có người dùng nào.");
                    } else {
                        showStatus("Tải danh sách người dùng thành công.");
                        showEmptyState("");
                    }

                    updateActionButtons(getSelectedUser());
                });
    }

    private void handleMutationResult(Throwable throwable) {
        Platform.runLater(
                () -> {
                    if (throwable != null) {
                        setBusy(false);
                        showStatus(errorMessage(throwable, "Không cập nhật được trạng thái người dùng."));
                        updateActionButtons(getSelectedUser());
                        return;
                    }

                    showStatus("Cập nhật trạng thái người dùng thành công.");
                    loadUsers();
                });
    }

    private void updateActionButtons(UserModerationViewModel selectedUser) {
        if (selectedUser == null) {
            setActionButtonsDisabled(true);
            return;
        }

        boolean banned = selectedUser.isBanned();
        if (banButton != null) {
            banButton.setDisable(banned);
        }
        if (unbanButton != null) {
            unbanButton.setDisable(!banned);
        }
    }

    private void setActionButtonsDisabled(boolean disabled) {
        if (banButton != null) {
            banButton.setDisable(disabled);
        }
        if (unbanButton != null) {
            unbanButton.setDisable(disabled);
        }
    }

    private UserModerationViewModel getSelectedUser() {
        if (userTable == null) {
            return null;
        }

        return userTable.getSelectionModel().getSelectedItem();
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
            setActionButtonsDisabled(true);
        } else {
            updateActionButtons(getSelectedUser());
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