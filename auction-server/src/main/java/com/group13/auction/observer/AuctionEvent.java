package com.group13.auction.observer;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Bidder;

/**
 * Object chứa thông tin sự kiện truyền cho Observer.
 * Bao gồm đầy đủ tất cả loại sự kiện (lỗi #18).
 */
public class AuctionEvent {

  /** Tất cả loại sự kiện trong vòng đời phiên đấu giá. */
  public enum AuctionEventType {
    AUCTION_UPCOMING,    // sắp bắt đầu (5-10p trước) — lỗi #19
    AUCTION_STARTED,     // vừa bắt đầu RUNNING
    BID_PLACED,          // có bid mới
    AUCTION_ENDED,       // kết thúc FINISHED/CANCELED
    PAYMENT_COMPLETED,   // thanh toán thành công PAID — lỗi #17
    AUCTION_CANCELED     // bị admin huỷ — lỗi #12
  }

  private final AuctionEventType eventType;
  private final Auction auction;
  private final Bidder bidder;   // null nếu không liên quan đến bidder
  private final double bidAmount; // 0 nếu không liên quan đến bid

  /**
   * Khởi tạo AuctionEvent.
   *
   * @param eventType loại sự kiện
   * @param auction   phiên liên quan
   * @param bidder    người đặt giá (null nếu không liên quan)
   * @param bidAmount số tiền (0 nếu không liên quan)
   */
  public AuctionEvent(AuctionEventType eventType, Auction auction,
      Bidder bidder, double bidAmount) {
    this.eventType = eventType;
    this.auction = auction;
    this.bidder = bidder;
    this.bidAmount = bidAmount;
  }

  public AuctionEventType getEventType() {
    return eventType;
  }

  public Auction getAuction() {
    return auction;
  }

  public Bidder getBidder() {
    return bidder;
  }

  public double getBidAmount() {
    return bidAmount;
  }
}