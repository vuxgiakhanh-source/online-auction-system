package com.group13.auction.ui.controller.profile;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.profile.ProfileService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.profile.UserProfileViewModel;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

/** Controller cho màn hồ sơ người dùng. */
public final class ProfileController {

  private final ProfileService profileService = new ProfileService();

  @FXML private Label usernameLabel;
  @FXML private Label emailLabel;
  @FXML private Label rolesLabel;
  @FXML private Label accountStatusLabel;
  @FXML private Label ratingLabel;
  @FXML private Label restoredLabel;

  @FXML private Label balanceLabel;
  @FXML private Label availableBalanceLabel;
  @FXML private Label lockedDepositLabel;
  @FXML private Label createdAtLabel;
  @FXML private Label updatedAtLabel;

  @FXML private VBox sellerRequestCard;
  @FXML private Label sellerRequestHintLabel;
  @FXML private Button requestSellerRoleButton;

  @FXML private Label statusLabel;
  @FXML private Button refreshButton;
  @FXML private ProgressIndicator loadingIndicator;

  /** Khởi tạo màn hồ sơ và tải thông tin user hiện tại. */
  @FXML
  public void initialize() {
    loadProfile();
  }

  /** Quay lại dashboard chính. */
  @FXML
  public void handleBackToHome() {
    Navigator.getInstance().goToMainLayout();
  }

  /** Tải lại hồ sơ người dùng. */
  @FXML
  public void handleRefresh() {
    loadProfile();
  }

  /** Gửi yêu cầu mở quyền Seller cho tài khoản hiện tại. */
  @FXML
  public void handleRequestSellerRole() {
    if (!AlertUtil.confirm("Gửi yêu cầu mở quyền Seller cho tài khoản này?")) {
      return;
    }

    setLoading(true, "Đang gửi yêu cầu...");

    profileService
        .requestSellerRole()
        .thenAccept(
            profile ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      renderProfile(profile);
                      setLoading(false, "Yêu cầu đã được xử lý.");
                      AlertUtil.showInfo("Thông tin tài khoản đã được cập nhật.");
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không gửi được yêu cầu.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void loadProfile() {
    setLoading(true, "Đang tải hồ sơ...");

    profileService
        .getMyProfile()
        .thenAccept(
            profile ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      renderProfile(profile);
                      setLoading(false, "Đã tải hồ sơ mới nhất.");
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không tải được hồ sơ.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void renderProfile(UserProfileViewModel profile) {
    usernameLabel.setText(profile.username());
    emailLabel.setText(profile.email());
    rolesLabel.setText(profile.rolesText());
    accountStatusLabel.setText(profile.accountStatusText());
    ratingLabel.setText(profile.ratingText());
    restoredLabel.setText(profile.restoredText());

    balanceLabel.setText(profile.balanceText());
    availableBalanceLabel.setText(profile.availableBalanceText());
    lockedDepositLabel.setText(profile.lockedDepositText());

    createdAtLabel.setText(profile.createdAtText());
    updatedAtLabel.setText(profile.updatedAtText());

    updateSellerRequestState(profile);
  }

  private void updateSellerRequestState(UserProfileViewModel profile) {
    boolean showSellerRequest = !profile.seller() && !profile.admin();

    sellerRequestCard.setVisible(showSellerRequest);
    sellerRequestCard.setManaged(showSellerRequest);

    if (!showSellerRequest) {
      return;
    }

    if (profile.canRequestSellerRole()) {
      sellerRequestHintLabel.setText("Bạn có thể gửi yêu cầu mở quyền Seller cho tài khoản này.");
      requestSellerRoleButton.setText("Yêu cầu quyền Seller");
      requestSellerRoleButton.setDisable(false);
      return;
    }

    sellerRequestHintLabel.setText("Tài khoản hiện chưa thể gửi yêu cầu mở quyền Seller.");
    requestSellerRoleButton.setText("Không khả dụng");
    requestSellerRoleButton.setDisable(true);
  }

  private void setLoading(boolean loading, String message) {
    loadingIndicator.setVisible(loading);
    loadingIndicator.setManaged(loading);

    refreshButton.setDisable(loading);

    if (requestSellerRoleButton != null && requestSellerRoleButton.isVisible()) {
      requestSellerRoleButton.setDisable(loading);
    }

    statusLabel.setText(message);
  }

  private String extractMessage(Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    String message = current == null ? null : current.getMessage();
    return message == null || message.isBlank() ? "Có lỗi xảy ra khi xử lý hồ sơ." : message;
  }
}
