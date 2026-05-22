package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.admin.AdminUserService;
import com.group13.auction.viewmodel.admin.StaffAdminViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller cho màn System Admin quản lý Staff Admin.
 *
 * <p>Màn này chỉ dùng các API đã có trong common/server: tạo Staff Admin mới và lấy danh sách
 * Staff Admin. Rule phân quyền MASTER được kiểm tra ở cả client service và server.
 */
public final class StaffAdminManagementController {

  private final AdminUserService adminUserService = new AdminUserService();

  @FXML private TableView<StaffAdminViewModel> staffTable;
  @FXML private TableColumn<StaffAdminViewModel, String> adminIdColumn;
  @FXML private TableColumn<StaffAdminViewModel, String> usernameColumn;
  @FXML private TableColumn<StaffAdminViewModel, String> emailColumn;
  @FXML private TableColumn<StaffAdminViewModel, String> adminTypeColumn;
  @FXML private TableColumn<StaffAdminViewModel, String> statusColumn;

  @FXML private TextField usernameField;
  @FXML private TextField emailField;
  @FXML private PasswordField passwordField;
  @FXML private PasswordField confirmPasswordField;

  @FXML private Label statusLabel;
  @FXML private Label emptyStateLabel;
  @FXML private ProgressIndicator loadingIndicator;

  @FXML private Button refreshButton;
  @FXML private Button createButton;
  @FXML private Button clearButton;
  @FXML private Button backButton;

  /** Khởi tạo màn quản lý Staff Admin và tải danh sách hiện có. */
  @FXML
  private void initialize() {
    configureTable();
    setBusy(false);
    showEmptyState("Chưa có dữ liệu Staff Admin.");
    loadStaffAdmins();
  }

  @FXML
  private void handleRefresh() {
    loadStaffAdmins();
  }

  @FXML
  private void handleCreateStaffAdmin() {
    String username = textOf(usernameField);
    String email = textOf(emailField);
    String password = textOf(passwordField);
    String confirmPassword = textOf(confirmPasswordField);

    if (!password.equals(confirmPassword)) {
      showStatus("Mật khẩu xác nhận chưa khớp.");
      focus(confirmPasswordField);
      return;
    }

    setBusy(true);
    showStatus("Đang tạo Staff Admin...");

    adminUserService
        .createStaffAdmin(username, password, email)
        .whenComplete((createdStaff, throwable) -> handleCreateResult(throwable));
  }

  @FXML
  private void handleClearForm() {
    clearForm();
    focus(usernameField);
  }

  @FXML
  private void handleBackToDashboard() {
    Navigator.getInstance().goToAdminDashboard();
  }

  private void loadStaffAdmins() {
    setBusy(true);
    showStatus("Đang tải danh sách Staff Admin...");
    showEmptyState("");

    adminUserService
        .getAllStaffAdmins()
        .whenComplete((staffAdmins, throwable) -> handleStaffAdminsResult(staffAdmins, throwable));
  }

  private void configureTable() {
    if (adminIdColumn != null) {
      adminIdColumn.setCellValueFactory(new PropertyValueFactory<>("adminId"));
    }
    if (usernameColumn != null) {
      usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
    }
    if (emailColumn != null) {
      emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
    }
    if (adminTypeColumn != null) {
      adminTypeColumn.setCellValueFactory(new PropertyValueFactory<>("adminType"));
    }
    if (statusColumn != null) {
      statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }
  }

  private void handleStaffAdminsResult(List<StaffAdminViewModel> staffAdmins, Throwable throwable) {
    Platform.runLater(
        () -> {
          setBusy(false);

          if (throwable != null) {
            showStatus(errorMessage(throwable, "Không tải được danh sách Staff Admin."));
            showEmptyState("Không tải được danh sách Staff Admin.");
            return;
          }

          List<StaffAdminViewModel> safeStaffAdmins =
              staffAdmins == null ? List.of() : staffAdmins;
          if (staffTable != null) {
            staffTable.setItems(FXCollections.observableArrayList(safeStaffAdmins));
          }

          if (safeStaffAdmins.isEmpty()) {
            showStatus("Đã tải danh sách Staff Admin.");
            showEmptyState("Chưa có Staff Admin nào.");
          } else {
            showStatus("Tải danh sách Staff Admin thành công.");
            showEmptyState("");
          }
        });
  }

  private void handleCreateResult(Throwable throwable) {
    Platform.runLater(
        () -> {
          if (throwable != null) {
            setBusy(false);
            showStatus(errorMessage(throwable, "Không tạo được Staff Admin."));
            return;
          }

          clearForm();
          showStatus("Tạo Staff Admin thành công.");
          loadStaffAdmins();
        });
  }

  private void clearForm() {
    if (usernameField != null) {
      usernameField.clear();
    }
    if (emailField != null) {
      emailField.clear();
    }
    if (passwordField != null) {
      passwordField.clear();
    }
    if (confirmPasswordField != null) {
      confirmPasswordField.clear();
    }
  }

  private void setBusy(boolean busy) {
    if (loadingIndicator != null) {
      loadingIndicator.setVisible(busy);
      loadingIndicator.setManaged(busy);
    }
    if (refreshButton != null) {
      refreshButton.setDisable(busy);
    }
    if (createButton != null) {
      createButton.setDisable(busy);
    }
    if (clearButton != null) {
      clearButton.setDisable(busy);
    }
    if (backButton != null) {
      backButton.setDisable(busy);
    }
    if (usernameField != null) {
      usernameField.setDisable(busy);
    }
    if (emailField != null) {
      emailField.setDisable(busy);
    }
    if (passwordField != null) {
      passwordField.setDisable(busy);
    }
    if (confirmPasswordField != null) {
      confirmPasswordField.setDisable(busy);
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

  private String textOf(TextField field) {
    return field == null || field.getText() == null ? "" : field.getText().trim();
  }

  private void focus(TextField field) {
    if (field != null) {
      field.requestFocus();
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