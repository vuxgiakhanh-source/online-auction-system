package com.group13.auction.core.navigation;

import com.group13.auction.config.ViewPath;
import java.util.Arrays;
import java.util.Optional;

/**
 * Các route chính của JavaFX client.
 *
 * <p>Mỗi route ánh xạ tới đúng một file FXML trong {@link ViewPath}.
 */
public enum Route {
    LANDING(ViewPath.LANDING_VIEW),
    LOGIN(ViewPath.LOGIN_VIEW),
    REGISTER(ViewPath.REGISTER_VIEW),
    MAIN_LAYOUT(ViewPath.MAIN_LAYOUT_VIEW),

    AUCTION_LIST(ViewPath.AUCTION_LIST_VIEW),
    AUCTION_DETAIL(ViewPath.AUCTION_DETAIL_VIEW),
    LIVE_BIDDING(ViewPath.LIVE_BIDDING_VIEW),

    SELLER_DASHBOARD(ViewPath.SELLER_DASHBOARD_VIEW),
    SELLER_AUCTION_LIST(ViewPath.SELLER_AUCTION_LIST_VIEW),
    CREATE_AUCTION(ViewPath.CREATE_AUCTION_VIEW),
    EDIT_AUCTION(ViewPath.EDIT_AUCTION_VIEW),
    SELLER_AUCTION_DETAIL(ViewPath.SELLER_AUCTION_DETAIL_VIEW),

    WALLET(ViewPath.WALLET_VIEW),
    PROFILE(ViewPath.PROFILE_VIEW),
    NOTIFICATION_CENTER(ViewPath.NOTIFICATION_CENTER_VIEW),

    RATING(ViewPath.RATING_VIEW),
    QUALITY_REPORT(ViewPath.QUALITY_REPORT_VIEW),

    CHATBOT(ViewPath.CHATBOT_VIEW),

    ADMIN_DASHBOARD(ViewPath.ADMIN_DASHBOARD_VIEW),
    ADMIN_USERS(ViewPath.ADMIN_USERS_VIEW),
    ADMIN_AUCTIONS(ViewPath.ADMIN_AUCTIONS_VIEW),
    ADMIN_SELLER_APPROVAL(ViewPath.ADMIN_SELLER_APPROVAL_VIEW),
    ADMIN_REPORT_REVIEW(ViewPath.ADMIN_REPORT_REVIEW_VIEW);

    private final String fxmlPath;

    Route(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    /**
     * Tìm route tương ứng với đường dẫn FXML.
     *
     * @param fxmlPath đường dẫn FXML cần tìm
     * @return route nếu đường dẫn thuộc một route đã khai báo
     */
    public static Optional<Route> fromFxmlPath(String fxmlPath) {
        if (fxmlPath == null || fxmlPath.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(values())
            .filter(route -> route.fxmlPath.equals(fxmlPath))
            .findFirst();
    }

    /**
     * Trả về đường dẫn FXML của route.
     *
     * @return đường dẫn resource tuyệt đối
     */
    public String getFxmlPath() {
        return fxmlPath;
    }
}