package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.admin.AdminUserService;
import com.group13.auction.viewmodel.admin.SellerApprovalViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller cho màn Admin duyệt quyền Seller.
 *
 * <p>Source hiện có API approve seller role, nhưng chưa có API list pending/reject seller request
 * riêng. Controller vì vậy hiển thị candidate từ danh sách user hiện có, kèm rating và trạng thái
 * có thể approve hay chưa.
 */
public final class SellerApprovalController {

  private final AdminUserService adminUserService = new AdminUserService();

  @FXML private TableView<SellerApprovalViewModel> sellerTable;
  @FXML private TableColumn<SellerApprovalViewModel, String> userIdColumn;
  @FXML private TableColumn<SellerApprovalViewModel, String> usernameColumn;
  @FXML private TableColumn<SellerApprovalViewModel, String> emailColumn;
  @FXML private TableColumn<SellerApprovalViewModel, String> roleColumn;
  @FXML private TableColumn<SellerApprovalViewModel, String> ratingColumn;
  @FXML private TableColumn<SellerApprovalViewModel, String> noteColumn;

  @FXML private Label statusLabel;
  @FXML private Label emptyStateLabel;
  @FXML private Label backendNoteLabel;
  @FXML private ProgressIndicator loadingIndicator;

  @FXML private Button refreshButton;
  @FXML private Button approveButton;
  @FXML private Button rejectButton;
  @FXML private Button backButton;

  /** Khởi tạo bảng candidate duyệt quyền Seller và tải dữ liệu lần đầu. */
  @FXML
  private void initialize() {
    configureTable();
    configureSelectionBinding();
    setBusy(false);
    setApproveButtonDisabled(true);
    configureBackendNote();
    loadCandidates();
  }

  @FXML
  private void handleRefresh() {
    loadCandidates();
  }

  @FXML
  private void handleApproveSeller() {
    SellerApprovalViewModel selectedCandidate = getSelectedCandidate();
    if (selectedCandidate == null) {
      showStatus("Vui lòng chọn người dùng cần duyệt quyền Seller.");
      return;
    }

    if (!selectedCandidate.isApprovable()) {
      showStatus("Người dùng này không thể duyệt Seller bằng API hiện tại.");
      return;
    }

    setBusy(true);
    showStatus("Đang duyệt quyền Seller...");

    adminUserService
        .approveSellerRole(selectedCandidate.getUserId())
        .whenComplete((updatedUser, throwable) -> handleMutationResult(throwable));
  }

  @FXML
  private void handleRejectSeller() {
    showStatus("Backend hiện chưa hỗ trợ API từ chối yêu cầu Seller.");
  }

  @FXML
  private void handleBackToDashboard() {
    Navigator.getInstance().goToAdminDashboard();
  }

  private void loadCandidates() {
    setBusy(true);
    showStatus("Đang tải danh sách candidate Seller...");
    showEmptyState("");

    adminUserService
        .getSellerApprovalCandidates()
        .whenComplete((candidates, throwable) -> handleCandidatesResult(candidates, throwable));
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
    if (ratingColumn != null) {
      ratingColumn.setCellValueFactory(new PropertyValueFactory<>("ratingText"));
    }
    if (noteColumn != null) {
      noteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));
    }
  }

  private void configureSelectionBinding() {
    if (sellerTable == null) {
      return;
    }

    sellerTable
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, oldValue, selectedCandidate) -> updateApproveButton(selectedCandidate));
  }

  private void configureBackendNote() {
    if (backendNoteLabel != null) {
      backendNoteLabel.setText(
          "Backend hiện hỗ trợ duyệt quyền Seller. API lấy danh sách yêu cầu đang chờ và API từ "
              + "chối yêu cầu Seller chưa được tách riêng; điều kiện rating tối thiểu là 2.0.");
    }
    if (rejectButton != null) {
      rejectButton.setDisable(true);
    }
  }

  private void handleCandidatesResult(
      List<SellerApprovalViewModel> candidates, Throwable throwable) {
    Platform.runLater(
        () -> {
          setBusy(false);

          if (throwable != null) {
            showStatus(errorMessage(throwable, "Không tải được danh sách candidate Seller."));
            showEmptyState("Không tải được danh sách candidate Seller.");
            setApproveButtonDisabled(true);
            return;
          }

          List<SellerApprovalViewModel> safeCandidates =
              candidates == null ? List.of() : candidates;
          if (sellerTable != null) {
            sellerTable.setItems(FXCollections.observableArrayList(safeCandidates));
          }

          if (safeCandidates.isEmpty()) {
            showStatus("Đã tải danh sách candidate Seller.");
            showEmptyState("Không có candidate Seller phù hợp với API hiện tại.");
          } else {
            showStatus("Tải danh sách candidate Seller thành công.");
            showEmptyState("");
          }

          updateApproveButton(getSelectedCandidate());
        });
  }

  private void handleMutationResult(Throwable throwable) {
    Platform.runLater(
        () -> {
          if (throwable != null) {
            setBusy(false);
            showStatus(errorMessage(throwable, "Không duyệt được quyền Seller."));
            updateApproveButton(getSelectedCandidate());
            return;
          }

          showStatus("Duyệt quyền Seller thành công.");
          loadCandidates();
        });
  }

  private void updateApproveButton(SellerApprovalViewModel selectedCandidate) {
    if (approveButton != null) {
      approveButton.setDisable(selectedCandidate == null || !selectedCandidate.isApprovable());
    }
  }

  private void setApproveButtonDisabled(boolean disabled) {
    if (approveButton != null) {
      approveButton.setDisable(disabled);
    }
  }

  private SellerApprovalViewModel getSelectedCandidate() {
    if (sellerTable == null) {
      return null;
    }

    return sellerTable.getSelectionModel().getSelectedItem();
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
    if (rejectButton != null) {
      rejectButton.setDisable(true);
    }

    if (busy) {
      setApproveButtonDisabled(true);
    } else {
      updateApproveButton(getSelectedCandidate());
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
