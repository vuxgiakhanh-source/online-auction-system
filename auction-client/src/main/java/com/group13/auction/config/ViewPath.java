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

    private ViewPath() {
        // Utility class.
    }
}