package com.group13.auction.ui.controller.admin;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.admin.AdminModerationService;
import com.group13.auction.service.auth.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Controller cho màn Admin Dashboard.
 *
 * <p>Controller kiểm tra quyền Admin và điều hướng tới các màn quản trị tương ứng.
 */
public final class AdminDashboardController {

  private final AdminModerationService adminModerationService = new AdminModerationService();
  private final AuthService authService = new AuthService();

  @FXML private Label titleLabel;
  @FXML private Label accessStatusLabel;

  @FXML private Button usersButton;
  @FXML private Button auctionsButton;
  @FXML private Button sellersButton;
  @FXML private Button reportsButton;
  @FXML private Button logoutButton;
  @FXML private Button staffAdminButton;
  @FXML private Button systemBankButton;

  @FXML private VBox staffAdminCard;
  @FXML private VBox systemBankCard;

  /** Khởi tạo dashboard và kiểm tra quyền Admin. */
  @FXML
  private void initialize() {
    boolean admin = adminModerationService.currentUserIsAdmin();

    if (titleLabel != null) {
      titleLabel.setText("Admin Dashboard");
    }

    if (accessStatusLabel != null) {
      accessStatusLabel.setText(adminModerationService.getCurrentAdminAccessLabel());
    }

    boolean masterAdmin = adminModerationService.currentUserIsMasterAdmin();
    setAdminActionsEnabled(admin, masterAdmin);
    setStaffAdminActionsVisible(masterAdmin);
    setSystemBankActionsVisible(admin);
  }

  @FXML
  private void handleOpenStaffAdminManagement() {
    if (adminModerationService.currentUserIsMasterAdmin()) {
      Navigator.getInstance().goToAdminStaffManagement();
    }
  }

  @FXML
  private void handleOpenSystemBank() {
    if (adminModerationService.currentUserIsAdmin()) {
      Navigator.getInstance().goToAdminSystemBank();
    }
  }

  @FXML
  private void handleOpenUserModeration() {
    if (adminModerationService.currentUserIsAdmin()) {
      Navigator.getInstance().goToAdminUsers();
    }
  }

  @FXML
  private void handleOpenAuctionModeration() {
    if (adminModerationService.currentUserIsAdmin()) {
      Navigator.getInstance().goToAdminAuctions();
    }
  }

  @FXML
  private void handleOpenSellerApproval() {
    if (adminModerationService.currentUserIsAdmin()) {
      Navigator.getInstance().goToAdminSellerApproval();
    }
  }

  @FXML
  private void handleOpenReportReview() {
    if (adminModerationService.currentUserIsAdmin()) {
      Navigator.getInstance().goToAdminReportReview();
    }
  }

  @FXML
  private void handleLogout() {
    authService.logout();
    Navigator.getInstance().goToLanding();
  }

  private void setAdminActionsEnabled(boolean admin, boolean masterAdmin) {
    if (usersButton != null) {
      usersButton.setDisable(!admin);
    }
    if (auctionsButton != null) {
      auctionsButton.setDisable(!admin);
    }
    if (sellersButton != null) {
      sellersButton.setDisable(!admin);
    }
    if (reportsButton != null) {
      reportsButton.setDisable(!admin);
    }
    if (staffAdminButton != null) {
      staffAdminButton.setDisable(!masterAdmin);
    }
    if (systemBankButton != null) {
      systemBankButton.setDisable(!admin);
    }
  }

  private void setStaffAdminActionsVisible(boolean visible) {
    if (staffAdminCard != null) {
      staffAdminCard.setVisible(visible);
      staffAdminCard.setManaged(visible);
    }
    if (staffAdminButton != null) {
      staffAdminButton.setVisible(visible);
      staffAdminButton.setManaged(visible);
      staffAdminButton.setDisable(!visible);
    }
  }

  private void setSystemBankActionsVisible(boolean visible) {
    if (systemBankCard != null) {
      systemBankCard.setVisible(visible);
      systemBankCard.setManaged(visible);
    }
    if (systemBankButton != null) {
      systemBankButton.setVisible(visible);
      systemBankButton.setManaged(visible);
      systemBankButton.setDisable(!visible);
    }
  }
}