package com.group13.auction.ui.controller.auth;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.service.auth.AuthService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.auth.RegisterFormState;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller cho màn hình đăng ký.
 *
 * <p>Controller chỉ gom dữ liệu từ form, gọi {@link AuthService}, rồi điều hướng khi server xác
 * nhận đăng ký thành công.
 */
public final class RegisterController {

  private final AuthService authService = new AuthService();

  @FXML private TextField emailField;

  @FXML private TextField usernameField;

  @FXML private PasswordField passwordField;

  @FXML private PasswordField confirmPasswordField;

  @FXML private Button signUpButton;

  @FXML private Button backToLandingButton;

  @FXML private Button goToLoginTabButton;

  @FXML private Button goToLoginLinkButton;

  /** Chuyển về trang landing. */
  @FXML
  public void handleBackToLanding() {
    Navigator.getInstance().goToLanding();
  }

  /** Chuyển về màn hình đăng nhập. */
  @FXML
  public void handleGoToLogin() {
    Navigator.getInstance().goToLogin();
  }

  /** Gửi request đăng ký tới server. */
  @FXML
  public void handleSignUp() {
    setFormDisabled(true);

    RegisterFormState formState =
        new RegisterFormState(
            emailField.getText(),
            usernameField.getText(),
            passwordField.getText(),
            confirmPasswordField.getText());

    authService
        .register(formState)
        .thenAccept(this::handleRegisterSuccess)
        .exceptionally(
            throwable -> {
              handleRegisterFailure(throwable);
              return null;
            });
  }

  /** Xóa dữ liệu đang nhập trong form đăng ký. */
  @FXML
  public void handleClearForm() {
    emailField.clear();
    usernameField.clear();
    passwordField.clear();
    confirmPasswordField.clear();
    emailField.requestFocus();
  }

  private void handleRegisterSuccess(UserSession session) {
    FxThreadUtil.runOnFxThread(
        () -> {
          setFormDisabled(false);
          AlertUtil.showInfo("Đăng ký thành công. Xin chào " + session.getUsername() + "!");
          Navigator.getInstance().goToHomeForSession(session);
        });
  }

  private void handleRegisterFailure(Throwable throwable) {
    FxThreadUtil.runOnFxThread(
        () -> {
          setFormDisabled(false);
          AlertUtil.showError(extractMessage(throwable));
          passwordField.clear();
          confirmPasswordField.clear();
          passwordField.requestFocus();
        });
  }

  private void setFormDisabled(boolean disabled) {
    emailField.setDisable(disabled);
    usernameField.setDisable(disabled);
    passwordField.setDisable(disabled);
    confirmPasswordField.setDisable(disabled);
    signUpButton.setDisable(disabled);
    backToLandingButton.setDisable(disabled);
    goToLoginTabButton.setDisable(disabled);
    goToLoginLinkButton.setDisable(disabled);
    signUpButton.setText(disabled ? "Signing up..." : "Sign up");
  }

  private String extractMessage(Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    String message = current.getMessage();
    if (message == null || message.isBlank()) {
      return "Đăng ký thất bại.";
    }

    return message;
  }
}
