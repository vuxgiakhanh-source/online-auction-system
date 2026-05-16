package com.group13.auction.config;

/**
 * Đường dẫn FXML tập trung — khớp cấu trúc thư mục {@code view/<domain>/}.
 */
public final class ViewPath {

    // ── Auth & Home ─────────────────────────────────────────────────────────
    public static final String LOGIN_VIEW = "/com/group13/auction/view/auth/login-view.fxml";
    public static final String REGISTER_VIEW = "/com/group13/auction/view/auth/register-view.fxml";
    public static final String LANDING_VIEW = "/com/group13/auction/view/home/landing-view.fxml";
    public static final String MAIN_LAYOUT_VIEW = "/com/group13/auction/view/home/main-layout.fxml";

    // ── Auction (Bidder) ────────────────────────────────────────────────────
    public static final String AUCTION_LIST_VIEW =
            "/com/group13/auction/view/auction/auction-list-view.fxml";
    public static final String AUCTION_DETAIL_VIEW =
            "/com/group13/auction/view/auction/auction-detail-view.fxml";
    public static final String LIVE_BIDDING_VIEW =
            "/com/group13/auction/view/auction/live-bidding-view.fxml";

    // ── Wallet & Profile ────────────────────────────────────────────────────
    public static final String WALLET_VIEW = "/com/group13/auction/view/wallet/wallet-view.fxml";
    public static final String PROFILE_VIEW = "/com/group13/auction/view/profile/profile-view.fxml";
    public static final String UPGRADE_SELLER_VIEW =
            "/com/group13/auction/view/profile/upgrade-seller-view.fxml";

    // ── Payment ─────────────────────────────────────────────────────────────
    public static final String PAYMENT_VIEW = "/com/group13/auction/view/payment/payment-view.fxml";
    public static final String SECOND_CHANCE_VIEW =
            "/com/group13/auction/view/payment/second-chance-view.fxml";

    // ── Seller ──────────────────────────────────────────────────────────────
    public static final String SELLER_DASHBOARD_VIEW =
            "/com/group13/auction/view/seller/seller-dashboard-view.fxml";
    public static final String SELLER_AUCTION_LIST_VIEW =
            "/com/group13/auction/view/seller/seller-auction-list-view.fxml";
    public static final String SELLER_CREATE_AUCTION_VIEW =
            "/com/group13/auction/view/seller/create-auction-view.fxml";
    public static final String SELLER_EDIT_AUCTION_VIEW =
            "/com/group13/auction/view/seller/edit-auction-view.fxml";
    public static final String SELLER_AUCTION_DETAIL_VIEW =
            "/com/group13/auction/view/seller/seller-auction-detail-view.fxml";

    // ── Rating & Report ─────────────────────────────────────────────────────
    public static final String RATING_SELLER_VIEW =
            "/com/group13/auction/view/rating/rate-seller-view.fxml";
    public static final String RATING_BIDDER_VIEW =
            "/com/group13/auction/view/rating/rate-bidder-view.fxml";
    public static final String RATING_HISTORY_VIEW =
            "/com/group13/auction/view/rating/rating-history-view.fxml";
    public static final String QUALITY_REPORT_VIEW =
            "/com/group13/auction/view/report/quality-report-view.fxml";

    // ── Admin ───────────────────────────────────────────────────────────────
    public static final String ADMIN_DASHBOARD_VIEW =
            "/com/group13/auction/view/admin/admin-dashboard-view.fxml";
    public static final String ADMIN_USERS_VIEW =
            "/com/group13/auction/view/admin/admin-users-view.fxml";
    public static final String ADMIN_AUCTIONS_VIEW =
            "/com/group13/auction/view/admin/admin-auctions-view.fxml";
    public static final String ADMIN_SELLER_APPROVALS_VIEW =
            "/com/group13/auction/view/admin/admin-seller-approvals-view.fxml";
    public static final String ADMIN_QUALITY_REPORTS_VIEW =
            "/com/group13/auction/view/admin/admin-quality-reports-view.fxml";

    // ── Notification & Chatbot ──────────────────────────────────────────────
    public static final String NOTIFICATIONS_VIEW =
            "/com/group13/auction/view/notification/notifications-view.fxml";
    public static final String CHATBOT_VIEW = "/com/group13/auction/view/chatbot/chatbot-view.fxml";

    private ViewPath() {}
}
