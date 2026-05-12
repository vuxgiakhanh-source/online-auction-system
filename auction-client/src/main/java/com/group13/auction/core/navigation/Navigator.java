package com.group13.auction.core.navigation;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.context.AppContext;
import java.util.Objects;

/**
 * Điều hướng giữa các màn hình JavaFX.
 *
 * <p>Controller chỉ nên gọi {@code Navigator}, không tự load FXML hoặc tự thay scene.
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
        this.sceneManager = Objects.requireNonNull(sceneManager, "sceneManager must not be null");
        instance = this;
        AppContext.getInstance().setNavigator(this);
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
     * Chuyển tới route tương ứng.
     *
     * @param route route cần mở
     */
    public void goTo(Route route) {
        Objects.requireNonNull(route, "route must not be null");
        sceneManager.switchTo(route);
    }

    /**
     * Chuyển tới view theo đường dẫn FXML.
     *
     * <p>Method này được giữ lại để tương thích với controller hiện tại của project.
     *
     * @param viewPath đường dẫn tuyệt đối tới file FXML
     */
    public void goTo(String viewPath) {
        sceneManager.switchTo(viewPath);
    }

    /** Chuyển tới trang landing. */
    public void goToLanding() {
        goTo(ViewPath.LANDING_VIEW);
    }

    /** Chuyển tới trang đăng nhập. */
    public void goToLogin() {
        goTo(ViewPath.LOGIN_VIEW);
    }

    /** Chuyển tới trang đăng ký. */
    public void goToRegister() {
        goTo(ViewPath.REGISTER_VIEW);
    }

    /** Chuyển tới trang chủ/layout chính. */
    public void goToMainLayout() {
        goTo(ViewPath.MAIN_LAYOUT_VIEW);
    }

    /** Chuyển tới danh sách phiên đấu giá. */
    public void goToAuctionList() {
        goTo(ViewPath.AUCTION_LIST_VIEW);
    }

    /** Chuyển tới chi tiết phiên đấu giá. */
    public void goToAuctionDetail() {
        goTo(ViewPath.AUCTION_DETAIL_VIEW);
    }

    /** Chuyển tới màn đấu giá trực tiếp. */
    public void goToLiveBidding() {
        goTo(ViewPath.LIVE_BIDDING_VIEW);
    }
}