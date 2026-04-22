package com.group13.auction.core.navigation;

/**
 * Điều hướng giữa các màn hình JavaFX.
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
}
