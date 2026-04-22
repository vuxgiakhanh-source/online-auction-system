package com.group13.auction.ui.controller.auth;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller cho màn hình đăng ký.
 */
public final class RegisterController {

    @FXML
    private TextField emailField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    /**
     * Chuyển về màn hình đăng nhập.
     */
    @FXML
    public void handleGoToLogin() {
        Navigator.getInstance().goTo(ViewPath.LOGIN_VIEW);
    }

    /**
     * Tạm thời cho phép vào trang chủ khi nhấn nút Sign up.
     */
    @FXML
    public void handleSignUp() {
        Navigator.getInstance().goTo(ViewPath.HOME_LANDING_VIEW);
    }

    /**
     * Xóa dữ liệu đang nhập trong form đăng ký.
     */
    @FXML
    public void handleClearForm() {
        emailField.clear();
        usernameField.clear();
        passwordField.clear();
    }
}
