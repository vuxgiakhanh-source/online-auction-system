package com.group13.auction.core.navigation;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.home.MainLayoutController;
import com.group13.auction.ui.util.AlertUtil;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Điều hướng full-scene (auth/landing) và shell (sau đăng nhập).
 */
public final class Navigator {

    private static final Set<Route> FULL_SCENE_ROUTES =
            Set.of(Route.LANDING, Route.LOGIN, Route.REGISTER);

    private static Navigator instance;

    private final SceneManager sceneManager;
    private final RouteGuard routeGuard;

    public Navigator(SceneManager sceneManager) {
        this(sceneManager, new RouteGuard());
    }

    Navigator(SceneManager sceneManager, RouteGuard routeGuard) {
        this.sceneManager = Objects.requireNonNull(sceneManager);
        this.routeGuard = Objects.requireNonNull(routeGuard);
        instance = this;
        AppContext.getInstance().setNavigator(this);
    }

    public static Navigator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Navigator chưa được khởi tạo.");
        }
        return instance;
    }

    public SceneManager getSceneManager() {
        return sceneManager;
    }

    public void goTo(Route route) {
        Objects.requireNonNull(route, "route");

        if (FULL_SCENE_ROUTES.contains(route)) {
            sceneManager.switchTo(route);
            return;
        }

        Optional<String> denial = routeGuard.check(route);
        if (denial.isPresent()) {
            AlertUtil.showWarning(denial.get());
            if (route.requiresAuth()) {
                goTo(Route.LOGIN);
            }
            return;
        }

        MainLayoutController shell = AppContext.getInstance().getShell();
        if (shell != null && AppContext.getInstance().getSessionManager().isLoggedIn()) {
            shell.navigateTo(route);
            return;
        }

        sceneManager.switchTo(route);
    }

    /** Sau LOGIN/REGISTER_SUCCESS — mở shell và trang đấu giá mặc định. */
    public void enterApp() {
        LoadedView loaded = sceneManager.loadView(ViewPath.MAIN_LAYOUT_VIEW);
        if (!(loaded.controller() instanceof MainLayoutController shell)) {
            throw new IllegalStateException("main-layout.fxml phải dùng MainLayoutController.");
        }
        shell.init(sceneManager);
        AppContext.getInstance().setShell(shell);
        sceneManager.applyRoot(loaded.root());
        shell.navigateTo(Route.AUCTION_LIST);
        services().walletService().refreshBalance();
        services().profileService().loadMyProfile();
        services().notificationService().refresh();
    }

    private com.group13.auction.core.context.ServiceRegistry services() {
        return AppContext.getInstance().services();
    }

    public void goToLanding() { goTo(Route.LANDING); }
    public void goToLogin() { goTo(Route.LOGIN); }
    public void goToRegister() { goTo(Route.REGISTER); }
    public void goToAuctionList() { goTo(Route.AUCTION_LIST); }
    public void goToAuctionDetail() { goTo(Route.AUCTION_DETAIL); }
    public void goToLiveBidding() { goTo(Route.LIVE_BIDDING); }
    public void goToWallet() { goTo(Route.WALLET); }
    public void goToProfile() { goTo(Route.PROFILE); }
    public void goToUpgradeSeller() { goTo(Route.UPGRADE_SELLER); }
    public void goToPayment() { goTo(Route.PAYMENT); }
    public void goToSecondChance() { goTo(Route.SECOND_CHANCE); }
    public void goToSellerDashboard() { goTo(Route.SELLER_DASHBOARD); }
    public void goToSellerAuctionList() { goTo(Route.SELLER_AUCTION_LIST); }
    public void goToSellerCreateAuction() { goTo(Route.SELLER_CREATE_AUCTION); }
    public void goToSellerEditAuction() { goTo(Route.SELLER_EDIT_AUCTION); }
    public void goToSellerAuctionDetail() { goTo(Route.SELLER_AUCTION_DETAIL); }
    public void goToAdminDashboard() { goTo(Route.ADMIN_DASHBOARD); }
    public void goToAdminUsers() { goTo(Route.ADMIN_USERS); }
    public void goToAdminAuctions() { goTo(Route.ADMIN_AUCTIONS); }
    public void goToAdminSellerApprovals() { goTo(Route.ADMIN_SELLER_APPROVALS); }
    public void goToAdminQualityReports() { goTo(Route.ADMIN_QUALITY_REPORTS); }
    public void goToNotifications() { goTo(Route.NOTIFICATIONS); }
    public void goToChatbot() { goTo(Route.CHATBOT); }
    public void goToQualityReport() { goTo(Route.QUALITY_REPORT); }
    public void goToRatingHistory() { goTo(Route.RATING_HISTORY); }

    public void openAuctionDetail(String auctionId) {
        AppContext.getInstance().getScreenStateStore().put(ScreenKeys.SELECTED_AUCTION_ID, auctionId);
        goToAuctionDetail();
    }

    public void openLiveBidding(String auctionId, String joinMode) {
        var store = AppContext.getInstance().getScreenStateStore();
        store.put(ScreenKeys.SELECTED_AUCTION_ID, auctionId);
        store.put(ScreenKeys.LIVE_SESSION_MODE, joinMode);
        goToLiveBidding();
    }

    public void logout() {
        AppContext.getInstance().services().authService().logout(() -> goToLogin());
    }
}
