package com.group13.auction.ui.controller.home;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.LoadedView;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.navigation.Route;
import com.group13.auction.core.navigation.SceneManager;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.core.state.ConnectionState;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.FormatUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Shell chính sau đăng nhập — sidebar + vùng nội dung.
 */
public final class MainLayoutController extends BaseController {

    @FXML private Label pageTitleLabel;
    @FXML private Label userLabel;
    @FXML private Label balanceLabel;
    @FXML private Label connectionLabel;
    @FXML private StackPane contentHost;
    @FXML private VBox sellerNav;
    @FXML private VBox adminNav;
    @FXML private Label notificationBadge;

    private SceneManager sceneManager;

    public void init(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        bindHeader();
        updateNavVisibility();
    }

    @FXML
    private void initialize() {
        AppContext.getInstance().connectionStateProperty().addListener((obs, o, n) ->
                FxThreadUtil.runOnFxThread(() -> connectionLabel.setText(
                        n == ConnectionState.CONNECTED ? "● Đã kết nối" : "○ Mất kết nối")));
    }

    public void navigateTo(Route route) {
        if (sceneManager == null) {
            return;
        }
        LoadedView loaded = sceneManager.loadView(route.getFxmlPath());
        contentHost.getChildren().setAll(loaded.root());
        pageTitleLabel.setText(titleFor(route));
        if (loaded.controller() instanceof PageLifecycle lifecycle) {
            lifecycle.onShow();
        }
    }

    private void bindHeader() {
        session().getCurrentSession().ifPresent(this::applySession);
        session().loggedInProperty().addListener((obs, o, loggedIn) -> {
            if (loggedIn) {
                session().getCurrentSession().ifPresent(this::applySession);
            }
        });
        services().walletService().balanceProperty().addListener((obs, o, bal) ->
                FxThreadUtil.runOnFxThread(() -> updateBalance(bal)));
        services().profileService().currentProfileProperty().addListener((obs, o, user) ->
                FxThreadUtil.runOnFxThread(this::updateNavVisibility));
        ConnectionState state = appContext().getConnectionState();
        connectionLabel.setText(state == ConnectionState.CONNECTED ? "● Đã kết nối" : "○ Mất kết nối");
    }

    private void applySession(UserSession session) {
        userLabel.setText(session.getUsername());
        updateNavVisibility();
    }

    private void updateBalance(PaymentDTOs.WalletBalanceResponseDTO bal) {
        if (bal != null) {
            balanceLabel.setText("Khả dụng: " + FormatUtil.currency(bal.getAvailableBalance()));
        }
    }

    private void updateNotificationBadge(Number count) {
        int n = count != null ? count.intValue() : 0;
        notificationBadge.setText(n > 0 ? String.valueOf(n) : "");
        notificationBadge.setVisible(n > 0);
        notificationBadge.setManaged(n > 0);
    }

    private void updateNavVisibility() {
        UserDTO profile = services().profileService().currentProfileProperty().get();
        boolean seller = profile != null && profile.getRoles() != null
                && (profile.getRoles().contains("SELLER") || profile.getRoles().contains("BIDDER_SELLER"));
        boolean admin = profile != null && profile.getAdminType() != null
                && !profile.getAdminType().isBlank();
        sellerNav.setVisible(seller);
        sellerNav.setManaged(seller);
        adminNav.setVisible(admin);
        adminNav.setManaged(admin);
    }

    private static String titleFor(Route route) {
        return switch (route) {
            case AUCTION_LIST -> "Danh sách đấu giá";
            case AUCTION_DETAIL -> "Chi tiết phiên";
            case LIVE_BIDDING -> "Phòng đấu giá trực tiếp";
            case WALLET -> "Ví của tôi";
            case PROFILE -> "Hồ sơ";
            case UPGRADE_SELLER -> "Nâng cấp Seller";
            case PAYMENT -> "Thanh toán";
            case SECOND_CHANCE -> "Second Chance";
            case NOTIFICATIONS -> "Thông báo";
            case CHATBOT -> "Hỗ trợ FAQ";
            case QUALITY_REPORT -> "Báo cáo chất lượng";
            case RATING_SELLER -> "Đánh giá Seller";
            case RATING_BIDDER -> "Đánh giá Bidder";
            case RATING_HISTORY -> "Lịch sử đánh giá";
            case SELLER_DASHBOARD -> "Khu vực Seller";
            case SELLER_AUCTION_LIST -> "Phiên của tôi";
            case SELLER_CREATE_AUCTION -> "Tạo phiên đấu giá";
            case SELLER_EDIT_AUCTION -> "Sửa phiên";
            case SELLER_AUCTION_DETAIL -> "Chi tiết (Seller)";
            case ADMIN_DASHBOARD -> "Quản trị";
            case ADMIN_USERS -> "Quản lý người dùng";
            case ADMIN_AUCTIONS -> "Quản lý phiên";
            case ADMIN_SELLER_APPROVALS -> "Duyệt Seller";
            case ADMIN_QUALITY_REPORTS -> "Báo cáo chất lượng";
            default -> "OmniBid";
        };
    }

    @FXML private void onAuctions() { Navigator.getInstance().goToAuctionList(); }
    @FXML private void onWallet() { Navigator.getInstance().goToWallet(); }
    @FXML private void onProfile() { Navigator.getInstance().goToProfile(); }
    @FXML private void onNotifications() { Navigator.getInstance().goToNotifications(); }
    @FXML private void onChatbot() { Navigator.getInstance().goToChatbot(); }
    @FXML private void onSeller() { Navigator.getInstance().goToSellerDashboard(); }
    @FXML private void onAdmin() { Navigator.getInstance().goToAdminDashboard(); }
    @FXML private void onLogout() { Navigator.getInstance().logout(); }
}
