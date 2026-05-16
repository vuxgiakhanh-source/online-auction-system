package com.group13.auction.ui.controller.auth;

import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController extends BaseController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private javafx.scene.control.Label errorLabel;

    @FXML
    public void handleGoToRegister() {
        navigator().goToRegister();
    }

    @FXML
    public void handleSignIn() {
        clearError();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (username.isBlank() || password.isBlank()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }
        services().authService().login(
                username,
                password,
                ok -> {
                    if (ok) {
                        clearError();
                        navigator().enterApp();
                    }
                },
                this::showError);
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

    @FXML
    public void handleClearForm() {
        usernameField.clear();
        passwordField.clear();
    }
}
