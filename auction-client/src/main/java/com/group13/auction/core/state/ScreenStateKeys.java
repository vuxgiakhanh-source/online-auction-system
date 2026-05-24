package com.group13.auction.core.state;

/** Keys dùng chung khi truyền dữ liệu tạm thời giữa các màn hình JavaFX. */
public final class ScreenStateKeys {

    public static final String SELECTED_AUCTION_ID = "selectedAuctionId";
    public static final String SELECTED_SELLER_AUCTION_ROW = "selectedSellerAuctionRow";
    public static final String SELECTED_WON_ORDER = "selectedWonOrder";

    /** Phạm vi danh sách báo cáo: {@code my} (Bidder) hoặc {@code seller}. */
    public static final String QUALITY_REPORT_LIST_SCOPE = "qualityReportListScope";

    private ScreenStateKeys() {
        // Utility class.
    }
}