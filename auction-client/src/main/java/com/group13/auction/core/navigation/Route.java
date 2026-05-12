package com.group13.auction.core.navigation;

import com.group13.auction.config.ViewPath;

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
    LIVE_BIDDING(ViewPath.LIVE_BIDDING_VIEW);

    private final String fxmlPath;

    Route(String fxmlPath) {
        this.fxmlPath = fxmlPath;
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