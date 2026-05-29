package com.group13.auction.core.navigation;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.state.ScreenStateKeys;
import java.util.Objects;
import java.util.Optional;

/**
 * Điều hướng giữa các màn hình JavaFX.
 *
 * <p>Controller chỉ nên gọi {@code Navigator}, không tự load FXML hoặc tự thay scene.
 */
public final class Navigator {

  private static Navigator instance;

  private final SceneManager sceneManager;

  private Route currentRoute;
  private Route chatbotReturnRoute;

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

    if (route == Route.CHATBOT && currentRoute != null && currentRoute != Route.CHATBOT) {
      chatbotReturnRoute = currentRoute;
    }

    sceneManager.switchTo(route);
    currentRoute = route;
  }

  /**
   * Chuyển tới view theo đường dẫn FXML.
   *
   * <p>Method này được giữ lại để tương thích với controller hiện tại của project. Nếu đường dẫn
   * thuộc một route đã khai báo, navigator vẫn ghi nhận route hiện tại để các flow như chatbot có
   * thể quay lại đúng màn hình trước đó.
   *
   * @param viewPath đường dẫn tuyệt đối tới file FXML
   */
  public void goTo(String viewPath) {
    Objects.requireNonNull(viewPath, "viewPath must not be null");

    Optional<Route> route = Route.fromFxmlPath(viewPath);
    if (route.isPresent()) {
      goTo(route.get());
      return;
    }

    sceneManager.switchTo(viewPath);
    currentRoute = null;
  }

  /**
   * Quay lại màn hình đã mở chatbot trước đó.
   *
   * <p>Nếu người dùng mở chatbot khi chưa đăng nhập từ landing/login/register, nút quay lại sẽ trở
   * về đúng màn hình đó thay vì vào dashboard. Nếu không xác định được màn trước đó, client sẽ
   * fallback theo trạng thái đăng nhập.
   */
  public void goBackFromChatbot() {
    Route destination = chatbotReturnRoute;
    chatbotReturnRoute = null;

    if (destination != null && destination != Route.CHATBOT) {
      goTo(destination);
      return;
    }

    if (AppContext.getInstance().getSessionManager().isLoggedIn()) {
      goToMainLayout();
    } else {
      goToLanding();
    }
  }

  /**
   * Lấy route hiện tại nếu navigator nhận diện được.
   *
   * @return route hiện tại
   */
  public Optional<Route> getCurrentRoute() {
    return Optional.ofNullable(currentRoute);
  }

  /** Chuyển tới trang landing. */
  public void goToLanding() {
    goTo(Route.LANDING);
  }

  /** Chuyển tới trang đăng nhập. */
  public void goToLogin() {
    goTo(Route.LOGIN);
  }

  /** Chuyển tới trang đăng ký. */
  public void goToRegister() {
    goTo(Route.REGISTER);
  }

  /** Chuyển tới trang chủ/layout chính. */
  public void goToMainLayout() {
    goTo(Route.MAIN_LAYOUT);
  }

  /** Chuyển tới danh sách phiên đấu giá. */
  public void goToAuctionList() {
    goTo(Route.AUCTION_LIST);
  }

  /** Chuyển tới chi tiết phiên đấu giá. */
  public void goToAuctionDetail() {
    goTo(Route.AUCTION_DETAIL);
  }

  /** Chuyển tới màn đấu giá trực tiếp (tham gia / theo dõi realtime). */
  public void goToLiveBidding() {
    AppContext.getInstance().getScreenStateStore().remove(ScreenStateKeys.LIVE_BIDDING_READ_ONLY);
    goTo(Route.LIVE_BIDDING);
  }

  /** Chuyển tới màn xem lịch sử bid và biểu đồ — không join, không đặt giá. */
  public void goToLiveBiddingReadOnly(String auctionId) {
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.SELECTED_AUCTION_ID, auctionId);
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.LIVE_BIDDING_READ_ONLY, Boolean.TRUE);
    goTo(Route.LIVE_BIDDING);
  }

  /** Chuyển tới dashboard Seller. */
  public void goToSellerDashboard() {
    goTo(Route.SELLER_DASHBOARD);
  }

  /** Chuyển tới danh sách phiên của Seller. */
  public void goToSellerAuctionList() {
    goTo(Route.SELLER_AUCTION_LIST);
  }

  /** Chuyển tới form tạo phiên đấu giá. */
  public void goToCreateAuction() {
    goTo(Route.CREATE_AUCTION);
  }

  /** Chuyển tới form sửa phiên đấu giá. */
  public void goToEditAuction() {
    goTo(Route.EDIT_AUCTION);
  }

  /** Chuyển tới chi tiết phiên phía Seller. */
  public void goToSellerAuctionDetail() {
    goTo(Route.SELLER_AUCTION_DETAIL);
  }

  /** Chuyển tới màn ví người dùng. */
  public void goToWallet() {
    goTo(Route.WALLET);
  }

  /** Chuyển tới màn hồ sơ người dùng. */
  public void goToProfile() {
    goTo(Route.PROFILE);
  }

  /** Chuyển tới trung tâm thông báo. */
  public void goToNotificationCenter() {
    goTo(Route.NOTIFICATION_CENTER);
  }

  /** Chuyển tới màn đơn hàng đã thắng của người dùng. */
  public void goToMyOrders() {
    goTo(Route.MY_ORDERS);
  }

  /** Chuyển tới màn gửi báo cáo chất lượng. */
  public void goToQualityReport() {
    goTo(Route.QUALITY_REPORT);
  }

  /** Chuyển tới danh sách báo cáo chất lượng của Bidder. */
  public void goToMyQualityReports() {
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.QUALITY_REPORT_LIST_SCOPE, "my");
    goTo(Route.MY_QUALITY_REPORTS);
  }

  /** Chuyển tới danh sách báo cáo chất lượng của Seller. */
  public void goToSellerQualityReports() {
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.QUALITY_REPORT_LIST_SCOPE, "seller");
    goTo(Route.SELLER_QUALITY_REPORTS);
  }

  /** Chuyển tới màn chatbot OMNI. */
  public void goToChatbot() {
    goTo(Route.CHATBOT);
  }

  /** Chuyển tới dashboard Admin. */
  public void goToAdminDashboard() {
    goTo(Route.ADMIN_DASHBOARD);
  }

  /** Chuyển tới màn Admin quản lý người dùng. */
  public void goToAdminUsers() {
    goTo(Route.ADMIN_USERS);
  }

  /** Chuyển tới màn Admin quản lý phiên đấu giá. */
  public void goToAdminAuctions() {
    goTo(Route.ADMIN_AUCTIONS);
  }

  /** Chuyển tới màn Admin duyệt Seller. */
  public void goToAdminSellerApproval() {
    goTo(Route.ADMIN_SELLER_APPROVAL);
  }

  /** Chuyển tới màn Admin duyệt báo cáo chất lượng. */
  public void goToAdminReportReview() {
    goTo(Route.ADMIN_REPORT_REVIEW);
  }

  /** Chuyển tới màn System Admin quản lý Staff Admin. */
  public void goToAdminStaffManagement() {
    goTo(Route.ADMIN_STAFF_MANAGEMENT);
  }
}
