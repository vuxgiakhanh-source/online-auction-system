package com.group13.auction.config;

/**
 * Chứa đường dẫn đến các file FXML của client.
 *
 * <p>Giữ tập trung đường dẫn ở đây để controller và navigation không hard-code path rải rác.
 */
public final class ViewPath {

  public static final String LOGIN_VIEW = "/com/group13/auction/view/auth/login-view.fxml";
  public static final String REGISTER_VIEW = "/com/group13/auction/view/auth/register-view.fxml";
  public static final String LANDING_VIEW = "/com/group13/auction/view/home/landing-view.fxml";
  public static final String MAIN_LAYOUT_VIEW = "/com/group13/auction/view/home/main-layout.fxml";

  public static final String AUCTION_LIST_VIEW =
      "/com/group13/auction/view/auction/auction-list-view.fxml";
  public static final String AUCTION_DETAIL_VIEW =
      "/com/group13/auction/view/auction/auction-detail-view.fxml";
  public static final String LIVE_BIDDING_VIEW =
      "/com/group13/auction/view/auction/live-bidding-view.fxml";

  public static final String SELLER_DASHBOARD_VIEW =
      "/com/group13/auction/view/seller/seller-dashboard-view.fxml";
  public static final String SELLER_AUCTION_LIST_VIEW =
      "/com/group13/auction/view/seller/seller-auction-list-view.fxml";
  public static final String CREATE_AUCTION_VIEW =
      "/com/group13/auction/view/seller/create-auction-view.fxml";
  public static final String EDIT_AUCTION_VIEW =
      "/com/group13/auction/view/seller/edit-auction-view.fxml";
  public static final String SELLER_AUCTION_DETAIL_VIEW =
      "/com/group13/auction/view/seller/seller-auction-detail-view.fxml";

  public static final String WALLET_VIEW = "/com/group13/auction/view/wallet/wallet-view.fxml";

  public static final String PROFILE_VIEW = "/com/group13/auction/view/profile/profile-view.fxml";

  public static final String NOTIFICATION_CENTER_VIEW =
      "/com/group13/auction/view/notification/notification-center-view.fxml";

  public static final String MY_ORDERS_VIEW = "/com/group13/auction/view/order/my-orders-view.fxml";

  public static final String QUALITY_REPORT_VIEW =
      "/com/group13/auction/view/report/quality-report-view.fxml";

  public static final String QUALITY_REPORT_LIST_VIEW =
      "/com/group13/auction/view/report/quality-report-list-view.fxml";

  public static final String CHATBOT_VIEW = "/com/group13/auction/view/chatbot/chatbot-view.fxml";

  public static final String ADMIN_DASHBOARD_VIEW =
      "/com/group13/auction/view/admin/admin-dashboard-view.fxml";
  public static final String ADMIN_USERS_VIEW =
      "/com/group13/auction/view/admin/user-moderation-view.fxml";
  public static final String ADMIN_AUCTIONS_VIEW =
      "/com/group13/auction/view/admin/auction-moderation-view.fxml";
  public static final String ADMIN_SELLER_APPROVAL_VIEW =
      "/com/group13/auction/view/admin/seller-approval-view.fxml";
  public static final String ADMIN_REPORT_REVIEW_VIEW =
      "/com/group13/auction/view/admin/quality-report-review-view.fxml";
  public static final String ADMIN_STAFF_MANAGEMENT_VIEW =
      "/com/group13/auction/view/admin/staff-admin-management-view.fxml";
  public static final String ADMIN_SYSTEM_BANK_VIEW =
      "/com/group13/auction/view/admin/system-bank-view.fxml";

  private ViewPath() {
    // Utility class.
  }
}