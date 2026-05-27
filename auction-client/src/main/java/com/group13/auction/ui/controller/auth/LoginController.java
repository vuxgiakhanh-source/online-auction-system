package com.group13.auction.ui.controller.auth;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.service.auth.AuthService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.auth.LoginFormState;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller cho màn hình đăng nhập.
 *
 * <p>Controller chỉ nhận input từ UI, gọi {@link AuthService}, rồi điều hướng sau khi server xác
 * nhận đăng nhập thành công.
 */
public final class LoginController {

  private final AuthService authService = new AuthService();

  @FXML private TextField usernameField;

  @FXML private PasswordField passwordField;

  @FXML private Button signInButton;

  @FXML private Button backToLandingButton;

  @FXML private Button goToRegisterTabButton;

  @FXML private Button goToRegisterLinkButton;

  /** Chuyển về trang landing. */
  @FXML
  public void handleBackToLanding() {
    Navigator.getInstance().goToLanding();
  }

  /** Chuyển sang màn hình đăng ký. */
  @FXML
  public void handleGoToRegister() {
    Navigator.getInstance().goToRegister();
  }

  /** Gửi request đăng nhập tới server. */
  @FXML
  public void handleSignIn() {
    setFormDisabled(true);

    LoginFormState formState = new LoginFormState(usernameField.getText(), passwordField.getText());

    authService
        .login(formState)
        .thenAccept(this::handleLoginSuccess)
        .exceptionally(
            throwable -> {
              handleLoginFailure(throwable);
              return null;
            });
  }

  /** Xóa dữ liệu đang nhập trong form đăng nhập. */
  @FXML
  public void handleClearForm() {
    usernameField.clear();
    passwordField.clear();
    usernameField.requestFocus();
  }

  private void handleLoginSuccess(UserSession session) {
    FxThreadUtil.runOnFxThread(
        () -> {
          setFormDisabled(false);
          AlertUtil.showInfo("Đăng nhập thành công. Xin chào " + session.getUsername() + "!");
          Navigator.getInstance().goToMainLayout();
        });
  }

  private void handleLoginFailure(Throwable throwable) {
    FxThreadUtil.runOnFxThread(
        () -> {
          setFormDisabled(false);
          AlertUtil.showError(extractMessage(throwable));
          passwordField.clear();
          passwordField.requestFocus();
        });
  }

  private void setFormDisabled(boolean disabled) {
    usernameField.setDisable(disabled);
    passwordField.setDisable(disabled);
    signInButton.setDisable(disabled);
    backToLandingButton.setDisable(disabled);
    goToRegisterTabButton.setDisable(disabled);
    goToRegisterLinkButton.setDisable(disabled);
    signInButton.setText(disabled ? "Signing in..." : "Sign in");
  }

  private String extractMessage(Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    String message = current.getMessage();
    if (message == null || message.isBlank()) {
      return "Đăng nhập thất bại.";
    }

    return message;
  }
}
