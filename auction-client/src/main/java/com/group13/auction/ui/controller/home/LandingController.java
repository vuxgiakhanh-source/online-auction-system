package com.group13.auction.ui.controller.home;

import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;

/**
 * Controller cho trang landing.
 */
public class LandingController {

    /**
     * Chuyển sang màn hình đăng nhập khi nhấn nút Bắt đầu.
     */
    @FXML
    private void handleStart() {
        Navigator.getInstance().goToLogin();
    }

    /**
     * Chuyển sang màn hình đăng nhập từ top navigation.
     */
    @FXML
    private void handleGoToLogin() {
        Navigator.getInstance().goToLogin();
    }

    /**
     * Chuyển sang màn hình đăng ký từ top navigation.
     */
    @FXML
    private void handleGoToRegister() {
        Navigator.getInstance().goToRegister();
    }
}