package com.group13.auction.ui.controller.auth;

import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller cho màn hình đăng nhập.
 */
public final class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    /**
     * Chuyển sang màn hình đăng ký.
     */
    @FXML
    public void handleGoToRegister() {
        Navigator.getInstance().goToRegister();
    }

    /**
     * Tạm thời cho phép vào trang chủ khi nhấn nút Sign in.
     */
    @FXML
    public void handleSignIn() {
        Navigator.getInstance().goToMainLayout();
    }

    /**
     * Xóa dữ liệu đang nhập trong form đăng nhập.
     */
    @FXML
    public void handleClearForm() {
        usernameField.clear();
        passwordField.clear();
    }
}
