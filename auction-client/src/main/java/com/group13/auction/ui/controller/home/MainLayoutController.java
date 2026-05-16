package com.group13.auction.ui.controller.home;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;

/** Controller cho dashboard shell sau khi người dùng đăng nhập hoặc đăng ký. */
public final class MainLayoutController {

    /** Quay lại landing page. */
    @FXML
    public void handleGoToLanding() {
        Navigator.getInstance().goToLanding();
    }

    /** Đăng xuất ở phía client và đưa người dùng về màn đăng nhập. */
    @FXML
    public void handleLogout() {
        AppContext.getInstance().getSessionManager().clearSession();
        Navigator.getInstance().goToLogin();
    }

    /**
     * Hiển thị thông báo cho các card chức năng sẽ được nối thật ở các đợt sau.
     *
     * <p>Đợt 1 chỉ dựng app shell/UI nền, không load các FXML auction đang placeholder để tránh lỗi
     * runtime.
     */
    @FXML
    public void handleComingSoon() {
        AlertUtil.showInfo("Màn này sẽ được nối thật ở các đợt tiếp theo.");
    }
}