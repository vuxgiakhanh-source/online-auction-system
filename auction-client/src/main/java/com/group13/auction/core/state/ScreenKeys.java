package com.group13.auction.core.state;

/**
 * Khóa chuẩn cho {@link com.group13.auction.core.state.ScreenStateStore} — truyền context giữa các màn.
 */
public final class ScreenKeys {

    public static final String SELECTED_AUCTION_ID = "selectedAuctionId";
    public static final String SELECTED_USER_ID = "selectedUserId";
    public static final String SELECTED_REPORT_ID = "selectedReportId";
    public static final String SELECTED_NOTIFICATION_ID = "selectedNotificationId";
    public static final String LIVE_SESSION_MODE = "liveSessionMode";
    public static final String RETURN_ROUTE = "returnRoute";
    public static final String ERROR_MESSAGE = "errorMessage";

    /** {@code JOIN} hoặc {@code WATCH} khi vào live bidding. */
    public static final String BIDDING_MODE_JOIN = "JOIN";
    public static final String BIDDING_MODE_WATCH = "WATCH";

    private ScreenKeys() {}
}
