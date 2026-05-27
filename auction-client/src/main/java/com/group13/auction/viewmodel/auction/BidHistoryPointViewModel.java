package com.group13.auction.viewmodel.auction;

/** Một điểm lịch sử bid đã format cho bảng và biểu đồ realtime. */
public final class BidHistoryPointViewModel {

  private final String auctionId;
  private final long price;
  private final String priceText;
  private final String bidderUsername;
  private final String timestampText;
  private final boolean autoBid;

  /** Tạo view model cho một điểm giá trong lịch sử bid. */
  public BidHistoryPointViewModel(
      String auctionId,
      long price,
      String priceText,
      String bidderUsername,
      String timestampText,
      boolean autoBid) {
    this.auctionId = auctionId;
    this.price = price;
    this.priceText = priceText;
    this.bidderUsername = bidderUsername;
    this.timestampText = timestampText;
    this.autoBid = autoBid;
  }

  public String auctionId() {
    return auctionId;
  }

  public long price() {
    return price;
  }

  public String priceText() {
    return priceText;
  }

  public String bidderUsername() {
    return bidderUsername;
  }

  public String timestampText() {
    return timestampText;
  }

  public boolean autoBid() {
    return autoBid;
  }
}
