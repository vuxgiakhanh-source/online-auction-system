package com.group13.auction.core.state;

/** Keys dùng chung khi truyền dữ liệu tạm thời giữa các màn hình JavaFX. */
public final class ScreenStateKeys {

  public static final String SELECTED_AUCTION_ID = "selectedAuctionId";

  /** {@code true} khi mở màn live bidding ở chế độ chỉ xem lịch sử (phiên đã kết thúc / đã hủy). */
  public static final String LIVE_BIDDING_READ_ONLY = "liveBiddingReadOnly";

  public static final String SELECTED_SELLER_AUCTION_ROW = "selectedSellerAuctionRow";
  public static final String SELECTED_WON_ORDER = "selectedWonOrder";

  /** Route để màn live bidding quay lại đúng nơi đã mở phiên, ví dụ màn Admin quản lý phiên. */
  public static final String LIVE_BIDDING_RETURN_ROUTE = "liveBiddingReturnRoute";

  /** Phạm vi danh sách báo cáo: {@code my} (Bidder) hoặc {@code seller}. */
  public static final String QUALITY_REPORT_LIST_SCOPE = "qualityReportListScope";

  private ScreenStateKeys() {
    // Utility class.
  }
}
