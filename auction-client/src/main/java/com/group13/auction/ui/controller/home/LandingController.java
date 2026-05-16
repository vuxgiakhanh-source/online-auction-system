package com.group13.auction.ui.controller.home;

import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;

/** Controller cho màn landing/welcome của OmniBid client. */
public class LandingController {

    /** Chuyển sang màn đăng nhập khi người dùng nhấn nút bắt đầu. */
    @FXML
    private void handleStart() {
        Navigator.getInstance().goToLogin();
    }

    /** Chuyển sang màn đăng nhập từ header. */
    @FXML
    private void handleGoToLogin() {
        Navigator.getInstance().goToLogin();
    }

    /** Chuyển sang màn đăng ký từ header. */
    @FXML
    private void handleGoToRegister() {
        Navigator.getInstance().goToRegister();
    }
}