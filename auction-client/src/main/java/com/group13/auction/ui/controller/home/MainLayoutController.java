package com.group13.auction.ui.controller.home;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.service.payment.SecondChanceRealtimeService;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/** Controller cho dashboard chính sau khi người dùng đăng nhập hoặc đăng ký. */
public final class MainLayoutController {

    @FXML private Label welcomeLabel;

    @FXML private Label accountInfoLabel;

    @FXML private Button sellerDashboardButton;

    @FXML private Button adminDashboardButton;

    private final SecondChanceRealtimeService secondChanceRealtimeService =
            SecondChanceRealtimeService.getInstance();

    /** Hiển thị thông tin session hiện tại lên dashboard. */
    @FXML
    public void initialize() {
        secondChanceRealtimeService.start();

        AppContext.getInstance()
                .getSessionManager()
                .getCurrentSession()
                .ifPresentOrElse(this::renderSession, this::renderGuestState);
    }

    /** Mở danh sách phiên đấu giá. */
    @FXML
    public void handleOpenAuctionList() {
        Navigator.getInstance().goToAuctionList();
    }

    /** Mở dashboard dành cho Seller. */
    @FXML
    public void handleOpenSellerDashboard() {
        AppContext.getInstance()
                .getSessionManager()
                .getCurrentSession()
                .filter(UserSession::isSeller)
                .ifPresentOrElse(
                        session -> Navigator.getInstance().goToSellerDashboard(),
                        () -> AlertUtil.showError("Tài khoản hiện tại chưa có quyền Seller."));
    }

    /** Mở màn Rating. */
    @FXML
    public void handleOpenRating() {
        Navigator.getInstance().goToRating();
    }

    /** Mở màn gửi Quality Report. */
    @FXML
    public void handleOpenQualityReport() {
        Navigator.getInstance().goToQualityReport();
    }

    /** Mở Admin Dashboard nếu tài khoản hiện tại có quyền Admin. */
    @FXML
    public void handleOpenAdminDashboard() {
        AppContext.getInstance()
                .getSessionManager()
                .getCurrentSession()
                .filter(UserSession::isAdmin)
                .ifPresentOrElse(
                        session -> Navigator.getInstance().goToAdminDashboard(),
                        () -> AlertUtil.showError("Tài khoản hiện tại không có quyền Admin."));
    }

    /** Đăng xuất ở phía client và đưa người dùng về màn đăng nhập. */
    @FXML
    public void handleLogout() {
        secondChanceRealtimeService.clear();
        AppContext.getInstance().getSessionManager().clearSession();
        Navigator.getInstance().goToLogin();
    }

    private void renderSession(UserSession session) {
        String roleText =
                session.getRoles().isEmpty() ? "Chưa có vai trò" : String.join(", ", session.getRoles());

        welcomeLabel.setText("Xin chào, " + session.getUsername() + "!");
        accountInfoLabel.setText(
                "Email: "
                        + session.getEmail()
                        + "  •  Vai trò: "
                        + roleText
                        + "  •  Trạng thái: "
                        + session.getAccountStatus());

        updateSellerAccess(session.isSeller());
        updateAdminAccess(session.isAdmin());
    }

    private void renderGuestState() {
        welcomeLabel.setText("Xin chào!");
        accountInfoLabel.setText("Chưa tìm thấy session đăng nhập.");
        updateSellerAccess(false);
        updateAdminAccess(false);
    }

    private void updateSellerAccess(boolean seller) {
        if (sellerDashboardButton == null) {
            return;
        }

        sellerDashboardButton.setDisable(!seller);
        sellerDashboardButton.setText(seller ? "Mở kênh Seller" : "Cần quyền Seller");
    }

    private void updateAdminAccess(boolean admin) {
        if (adminDashboardButton == null) {
            return;
        }

        adminDashboardButton.setVisible(admin);
        adminDashboardButton.setManaged(admin);
    }

    /** Mở màn ví người dùng. */
    @FXML
    public void handleOpenWallet() {
        Navigator.getInstance().goToWallet();
    }

    /** Mở màn hồ sơ người dùng. */
    @FXML
    public void handleOpenProfile() {
        Navigator.getInstance().goToProfile();
    }

    /** Mở trung tâm thông báo. */
    @FXML
    public void handleOpenNotificationCenter() {
        Navigator.getInstance().goToNotificationCenter();
    }
}