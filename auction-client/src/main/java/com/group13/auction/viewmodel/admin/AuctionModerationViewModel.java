package com.group13.auction.viewmodel.admin;

import java.util.Locale;

/** View model hiển thị một phiên đấu giá trong màn Admin Auction Moderation. */
public class AuctionModerationViewModel {

  private final String auctionId;
  private final String title;
  private final String sellerName;
  private final String currentPriceText;
  private final String rawStatus;
  private final String status;
  private final String startTimeText;
  private final String endTimeText;
  private final boolean cancellable;

  /**
   * Tạo dữ liệu hiển thị cho một phiên đấu giá.
   *
   * @param auctionId mã phiên đấu giá
   * @param title tiêu đề phiên đấu giá
   * @param sellerName tên người bán
   * @param currentPriceText giá hiện tại đã format
   * @param rawStatus trạng thái gốc server trả về
   * @param status trạng thái phiên đấu giá đã format
   * @param startTimeText thời gian bắt đầu đã format
   * @param endTimeText thời gian kết thúc đã format
   * @param cancellable true nếu admin có thể hủy phiên này
   */
  public AuctionModerationViewModel(
      String auctionId,
      String title,
      String sellerName,
      String currentPriceText,
      String rawStatus,
      String status,
      String startTimeText,
      String endTimeText,
      boolean cancellable) {
    this.auctionId = auctionId;
    this.title = title;
    this.sellerName = sellerName;
    this.currentPriceText = currentPriceText;
    this.rawStatus = normalizeStatus(rawStatus);
    this.status = status;
    this.startTimeText = startTimeText;
    this.endTimeText = endTimeText;
    this.cancellable = cancellable;
  }

  public String getAuctionId() {
    return auctionId;
  }

  public String getTitle() {
    return title;
  }

  public String getSellerName() {
    return sellerName;
  }

  public String getCurrentPriceText() {
    return currentPriceText;
  }

  public String getRawStatus() {
    return rawStatus;
  }

  public String getStatus() {
    return status;
  }

  public String getStartTimeText() {
    return startTimeText;
  }

  public String getEndTimeText() {
    return endTimeText;
  }

  public boolean isCancellable() {
    return cancellable;
  }

  public boolean isLiveWatchable() {
    return "OPEN".equals(rawStatus) || "RUNNING".equals(rawStatus);
  }

  public boolean isHistoryViewable() {
    return !auctionId.isBlank() && !isLiveWatchable();
  }

  private static String normalizeStatus(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}