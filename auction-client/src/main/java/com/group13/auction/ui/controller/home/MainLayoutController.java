package com.group13.auction.ui.controller.home;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Controller cho dashboard chính sau khi người dùng đăng nhập hoặc đăng ký. */
public final class MainLayoutController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label accountInfoLabel;

    /** Hiển thị thông tin session hiện tại lên dashboard. */
    @FXML
    public void initialize() {
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

    private void renderSession(UserSession session) {
        String roleText = session.getRoles().isEmpty()
                ? "Chưa có vai trò"
                : String.join(", ", session.getRoles());

        welcomeLabel.setText("Xin chào, " + session.getUsername() + "!");
        accountInfoLabel.setText(
                "Email: "
                        + session.getEmail()
                        + "  •  Vai trò: "
                        + roleText
                        + "  •  Trạng thái: "
                        + session.getAccountStatus());
    }

    private void renderGuestState() {
        welcomeLabel.setText("Xin chào!");
        accountInfoLabel.setText("Chưa tìm thấy session đăng nhập.");
    }
}