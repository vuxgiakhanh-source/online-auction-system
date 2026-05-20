package com.group13.auction.viewmodel.order;

/** Dữ liệu đã format để hiển thị một đơn hàng đấu giá đã thắng. */
public final class WonOrderViewModel {

  private final String auctionId;
  private final String itemName;
  private final String sellerUsername;
  private final String sellerText;
  private final String winningPriceText;
  private final String status;
  private final String statusText;
  private final String primaryImageUrl;
  private final boolean paid;
  private final boolean finished;

  /** Tạo view model cho một đơn hàng đã thắng. */
  public WonOrderViewModel(
      String auctionId,
      String itemName,
      String sellerUsername,
      String sellerText,
      String winningPriceText,
      String status,
      String statusText,
      String primaryImageUrl,
      boolean paid,
      boolean finished) {
    this.auctionId = auctionId == null ? "" : auctionId;
    this.itemName = itemName == null ? "--" : itemName;
    this.sellerUsername = sellerUsername == null ? "--" : sellerUsername;
    this.sellerText = sellerText == null ? "Người bán: --" : sellerText;
    this.winningPriceText = winningPriceText == null ? "--" : winningPriceText;
    this.status = status == null ? "" : status;
    this.statusText = statusText == null ? "Không rõ" : statusText;
    this.primaryImageUrl = primaryImageUrl == null ? "" : primaryImageUrl;
    this.paid = paid;
    this.finished = finished;
  }

  public String auctionId() {
    return auctionId;
  }

  public String itemName() {
    return itemName;
  }

  public String sellerUsername() {
    return sellerUsername;
  }

  public String sellerText() {
    return sellerText;
  }

  public String winningPriceText() {
    return winningPriceText;
  }

  public String status() {
    return status;
  }

  public String statusText() {
    return statusText;
  }

  public String primaryImageUrl() {
    return primaryImageUrl;
  }

  public boolean hasImage() {
    return !primaryImageUrl.isBlank();
  }

  public boolean paid() {
    return paid;
  }

  public boolean finished() {
    return finished;
  }

  public boolean reportable() {
    return paid;
  }
}