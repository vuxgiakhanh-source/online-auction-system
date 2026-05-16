package com.group13.auction.core.navigation;

import com.group13.auction.config.ViewPath;
import com.group13.auction.core.feature.ClientFeature;

/**
 * Route JavaFX — mỗi route gắn FXML, feature domain và yêu cầu quyền.
 */
public enum Route {

    // Public
    LANDING(ViewPath.LANDING_VIEW, ClientFeature.AUTH, false, false, false),
    LOGIN(ViewPath.LOGIN_VIEW, ClientFeature.AUTH, false, false, false),
    REGISTER(ViewPath.REGISTER_VIEW, ClientFeature.AUTH, false, false, false),

    // Authenticated — common
    MAIN_LAYOUT(ViewPath.MAIN_LAYOUT_VIEW, ClientFeature.AUTH, true, false, false),
    AUCTION_LIST(ViewPath.AUCTION_LIST_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, false, false),
    AUCTION_DETAIL(ViewPath.AUCTION_DETAIL_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, false, false),
    LIVE_BIDDING(ViewPath.LIVE_BIDDING_VIEW, ClientFeature.LIVE_BIDDING, true, false, false),
    WALLET(ViewPath.WALLET_VIEW, ClientFeature.WALLET, true, false, false),
    PROFILE(ViewPath.PROFILE_VIEW, ClientFeature.PROFILE, true, false, false),
    UPGRADE_SELLER(ViewPath.UPGRADE_SELLER_VIEW, ClientFeature.PROFILE, true, false, false),
    PAYMENT(ViewPath.PAYMENT_VIEW, ClientFeature.PAYMENT, true, false, false),
    SECOND_CHANCE(ViewPath.SECOND_CHANCE_VIEW, ClientFeature.PAYMENT, true, false, false),
    RATING_SELLER(ViewPath.RATING_SELLER_VIEW, ClientFeature.RATING, true, false, false),
    RATING_BIDDER(ViewPath.RATING_BIDDER_VIEW, ClientFeature.RATING, true, false, false),
    RATING_HISTORY(ViewPath.RATING_HISTORY_VIEW, ClientFeature.RATING, true, false, false),
    QUALITY_REPORT(ViewPath.QUALITY_REPORT_VIEW, ClientFeature.QUALITY_REPORT, true, false, false),
    NOTIFICATIONS(ViewPath.NOTIFICATIONS_VIEW, ClientFeature.NOTIFICATION, true, false, false),
    CHATBOT(ViewPath.CHATBOT_VIEW, ClientFeature.CHATBOT, false, false, false),

    // Seller
    SELLER_DASHBOARD(ViewPath.SELLER_DASHBOARD_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, true, false),
    SELLER_AUCTION_LIST(ViewPath.SELLER_AUCTION_LIST_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, true, false),
    SELLER_CREATE_AUCTION(ViewPath.SELLER_CREATE_AUCTION_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, true, false),
    SELLER_EDIT_AUCTION(ViewPath.SELLER_EDIT_AUCTION_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, true, false),
    SELLER_AUCTION_DETAIL(ViewPath.SELLER_AUCTION_DETAIL_VIEW, ClientFeature.AUCTION_MANAGEMENT, true, true, false),

    // Admin
    ADMIN_DASHBOARD(ViewPath.ADMIN_DASHBOARD_VIEW, ClientFeature.ADMIN, true, false, true),
    ADMIN_USERS(ViewPath.ADMIN_USERS_VIEW, ClientFeature.ADMIN, true, false, true),
    ADMIN_AUCTIONS(ViewPath.ADMIN_AUCTIONS_VIEW, ClientFeature.ADMIN, true, false, true),
    ADMIN_SELLER_APPROVALS(ViewPath.ADMIN_SELLER_APPROVALS_VIEW, ClientFeature.ADMIN, true, false, true),
    ADMIN_QUALITY_REPORTS(ViewPath.ADMIN_QUALITY_REPORTS_VIEW, ClientFeature.ADMIN, true, false, true);

    private final String fxmlPath;
    private final ClientFeature feature;
    private final boolean requiresAuth;
    private final boolean requiresSeller;
    private final boolean requiresAdmin;

    Route(
            String fxmlPath,
            ClientFeature feature,
            boolean requiresAuth,
            boolean requiresSeller,
            boolean requiresAdmin) {
        this.fxmlPath = fxmlPath;
        this.feature = feature;
        this.requiresAuth = requiresAuth;
        this.requiresSeller = requiresSeller;
        this.requiresAdmin = requiresAdmin;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }

    public ClientFeature getFeature() {
        return feature;
    }

    public boolean requiresAuth() {
        return requiresAuth;
    }

    public boolean requiresSeller() {
        return requiresSeller;
    }

    public boolean requiresAdmin() {
        return requiresAdmin;
    }
}
