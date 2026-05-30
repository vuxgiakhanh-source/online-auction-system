package com.group13.auction.ui.controller.home;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.service.auth.AuthService;
import com.group13.auction.service.notification.NotificationService;
import com.group13.auction.service.payment.SecondChanceRealtimeService;
import com.group13.auction.service.support.AudioManager;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.notification.NotificationItemViewModel;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Controller cho dashboard chính sau khi người dùng đăng nhập hoặc đăng ký. */
public final class MainLayoutController {

  private static final String NOTIFICATION_BUTTON_UNREAD_CLASS =
      "main-header-notification-button-unread";

  @FXML private Label welcomeLabel;

  @FXML private Label accountInfoLabel;

  @FXML private Button sellerDashboardButton;

  @FXML private Button backgroundAudioButton;

  @FXML private Button effectsAudioButton;

  @FXML private VBox adminDashboardCard;

  @FXML private Button adminDashboardButton;

  @FXML private Button notificationButton;

  @FXML private Label notificationBadgeLabel;

  private final SecondChanceRealtimeService secondChanceRealtimeService =
      SecondChanceRealtimeService.getInstance();

  private final NotificationService notificationService = new NotificationService();
  private final AuthService authService = new AuthService();

  /** Hiển thị thông tin session hiện tại lên dashboard. */
  @FXML
  public void initialize() {
    secondChanceRealtimeService.start();
    updateAudioButtons();

    setNotificationButtonHasUnread(false);

    AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .ifPresentOrElse(
            session -> {
              renderSession(session);
              loadNotificationBadge();
            },
            this::renderGuestState);
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

  /** Mở màn đơn hàng đã thắng của người dùng. */
  @FXML
  public void handleOpenMyOrders() {
    Navigator.getInstance().goToMyOrders();
  }

  /** Mở danh sách báo cáo chất lượng do Bidder gửi. */
  @FXML
  public void handleOpenMyQualityReports() {
    Navigator.getInstance().goToMyQualityReports();
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

  /** Đăng xuất ở phía client và đưa người dùng về landing page. */
  @FXML
  public void handleLogout() {
    secondChanceRealtimeService.clear();
    authService.logout();
    Navigator.getInstance().goToLanding();
  }

  private void renderSession(UserSession session) {
    String roleText = formatRoleLabel(session);

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

  private static String formatRoleLabel(UserSession session) {
    if (session.isMasterAdmin()) {
      return "System Admin";
    }
    if (session.isStaffAdmin()) {
      return "Staff Admin";
    }
    if (session.isAdmin()) {
      return "Admin";
    }
    if (session.isSeller() && session.isBidder()) {
      return "Bidder / Seller";
    }
    if (session.isSeller()) {
      return "Seller";
    }
    if (session.isBidder()) {
      return "Bidder";
    }
    if (session.getRoles().isEmpty()) {
      return "Chưa có vai trò";
    }
    return String.join(", ", session.getRoles());
  }

  private void loadNotificationBadge() {
    notificationService
        .getNotifications()
        .thenAccept(
            notifications ->
                FxThreadUtil.runOnFxThread(
                    () -> renderNotificationBadge(countUnread(notifications))))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(this::hideNotificationBadge);
              return null;
            });
  }

  private long countUnread(List<NotificationItemViewModel> notifications) {
    if (notifications == null || notifications.isEmpty()) {
      return 0;
    }

    return notifications.stream().filter(notification -> !notification.read()).count();
  }

  private void renderNotificationBadge(long unreadCount) {
    if (notificationBadgeLabel == null) {
      return;
    }

    if (unreadCount <= 0) {
      hideNotificationBadge();
      return;
    }

    notificationBadgeLabel.setText(unreadCount > 9 ? "9+" : Long.toString(unreadCount));
    notificationBadgeLabel.setVisible(true);
    setNotificationButtonHasUnread(true);
  }

  private void hideNotificationBadge() {
    if (notificationBadgeLabel == null) {
      return;
    }

    notificationBadgeLabel.setVisible(false);
    setNotificationButtonHasUnread(false);
  }

  private void setNotificationButtonHasUnread(boolean hasUnread) {
    if (notificationButton == null) {
      return;
    }

    if (hasUnread) {
      if (!notificationButton.getStyleClass().contains(NOTIFICATION_BUTTON_UNREAD_CLASS)) {
        notificationButton.getStyleClass().add(NOTIFICATION_BUTTON_UNREAD_CLASS);
      }
      return;
    }

    notificationButton.getStyleClass().remove(NOTIFICATION_BUTTON_UNREAD_CLASS);
  }

  private void updateSellerAccess(boolean seller) {
    if (sellerDashboardButton == null) {
      return;
    }

    sellerDashboardButton.setDisable(!seller);
    sellerDashboardButton.setText(seller ? "Mở kênh Seller" : "Cần quyền Seller");
  }

  private void updateAdminAccess(boolean admin) {
    if (adminDashboardCard != null) {
      adminDashboardCard.setVisible(admin);
      adminDashboardCard.setManaged(admin);
    }

    if (adminDashboardButton != null) {
      adminDashboardButton.setDisable(!admin);
    }
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

  @FXML
  public void handleToggleBackgroundAudio() {
    if (AudioManager.isBackgroundMuted()) {
      AudioManager.unmuteBackgroundMusic();
    } else {
      AudioManager.muteBackgroundMusic();
    }
    updateAudioButtons();
  }

  @FXML
  public void handleToggleEffectsAudio() {
    if (AudioManager.isEffectsMuted()) {
      AudioManager.unmuteSoundEffects();
    } else {
      AudioManager.muteSoundEffects();
    }
    updateAudioButtons();
  }

  private void updateAudioButtons() {
    if (backgroundAudioButton != null) {
      backgroundAudioButton.setText(AudioManager.isBackgroundMuted() ? "Music OFF" : "Music ON");
    }

    if (effectsAudioButton != null) {
      effectsAudioButton.setText(AudioManager.isEffectsMuted() ? "SFX OFF" : "SFX ON");
    }
  }
}
