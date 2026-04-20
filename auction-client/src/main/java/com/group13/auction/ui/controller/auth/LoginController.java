package com.group13.auction.ui.controller.auth;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for the login page.
 */
public final class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML
    private void handleLogin() {
        // UI-only flow for deadline support. Real authentication will be wired later.
        usernameField.getText();
        passwordField.getText();
    }

    @FXML
    private void handleGoToRegister() {
        Navigator.getInstance().goTo(ViewPath.REGISTER_VIEW);
    }

    @FXML
    private void handleCurrentTab() {
        // No-op. The login tab is already active.
    }

    @FXML
    private void handleBackToHome() {
        Navigator.getInstance().goTo(ViewPath.HOME_LANDING_VIEW);
    }
}

