package com.group13.auction.ui.controller.auth;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for the registration page.
 */
public final class RegisterController {

    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML
    private void handleRegister() {
        // UI-only flow for deadline support. Real registration will be wired later.
        emailField.getText();
        usernameField.getText();
        passwordField.getText();
    }

    @FXML
    private void handleGoToLogin() {
        Navigator.getInstance().goTo(ViewPath.LOGIN_VIEW);
    }

    @FXML
    private void handleCurrentTab() {
        // No-op. The register tab is already active.
    }

    @FXML
    private void handleBackToHome() {
        Navigator.getInstance().goTo(ViewPath.HOME_LANDING_VIEW);
    }
}
