package com.group13.auction.core.navigation;

import com.group13.auction.config.ViewPath;

/**
 * Điều hướng giữa các màn hình JavaF cấp scene.
 */
public final class Navigator {

    private static Navigator instance;

    private final SceneManager sceneManager;

    /**
     * Tạo navigator mới và gán làm instance dùng chung.
     *
     * @param sceneManager bộ quản lý scene
     */
    public Navigator(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        instance = this;
    }

    /**
     * Lấy navigator toàn cục của ứng dụng.
     *
     * @return navigator đang được dùng
     */
    public static Navigator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Navigator chưa được khởi tạo.");
        }
        return instance;
    }

    /**
     * Chuyển tới view theo đường dẫn FXML.
     *
     * @param viewPath đường dẫn tuyệt đối tới file FXML
     */
    public void goTo(String viewPath) {
        sceneManager.switchTo(viewPath);
    }

    /**
     * Chuyển tới trang landing.
     */
    public void goToLanding() {
        goTo(ViewPath.LANDING_VIEW);
    }

    /**
     * Chuyển tới trang đăng nhập.
     */
    public void goToLogin() {
        goTo(ViewPath.LOGIN_VIEW);
    }

    /**
     * Chuyển tới trang đăng ký.
     */
    public void goToRegister() {
        goTo(ViewPath.REGISTER_VIEW);
    }

    /**
     * Chuyển tới trang chủ.
     */
    public void goToMainLayout() {
        goTo(ViewPath.MAIN_LAYOUT_VIEW);
    }
}
