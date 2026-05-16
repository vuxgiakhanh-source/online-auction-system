package com.group13.auction.ui.controller.auth;

import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class RegisterController extends BaseController {

    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    public void handleGoToLogin() {
        navigator().goToLogin();
    }

    @FXML
    public void handleSignUp() {
        clearError();
        String email = text(emailField);
        String username = text(usernameField);
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (email.isBlank() || username.isBlank() || password.isBlank()) {
            showError("Vui lòng điền đầy đủ thông tin.");
            return;
        }
        services().authService().register(
                username,
                password,
                email,
                ok -> {
                    if (ok) {
                        clearError();
                        AlertUtil.showInfo("Đăng ký thành công!");
                        navigator().enterApp();
                    }
                },
                this::showError);
    }

    @FXML
    public void handleClearForm() {
        emailField.clear();
        usernameField.clear();
        passwordField.clear();
        clearError();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }
}
