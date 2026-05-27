package com.group13.auction.observer;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;

/** Object chứa thông tin sự kiện truyền cho Observer. */
public class AuctionEvent {

  /** Tất cả loại sự kiện trong vòng đời phiên đấu giá. */
  public enum AuctionEventType {
    AUCTION_UPCOMING, // sắp bắt đầu (5-10p trước) (chưa done, có thể bỏ)
    AUCTION_STARTED, // vừa bắt đầu RUNNING
    BID_PLACED, // có bid mới
    BID_RESERVE_NOT_MET, // bid được chấp nhận nhưng chưa đạt reserve
    AUCTION_EXTENDED, // anti-sniping: gia hạn thời gian kết thúc
    AUCTION_ENDED, // kết thúc FINISHED (reserve met, có winner)
    AUCTION_NO_WINNER, // kết thúc không có người đặt giá
    RESERVE_NOT_MET_CLOSED, // kết thúc nhưng giá cao nhất chưa đạt reserve
    PAYMENT_COMPLETED, // thanh toán thành công PAID
    AUCTION_CANCELED, // bị hủy
    SECOND_CHANCE_OFFERED, // đề nghị mua thứ cấp cho runner-up
    QUALITY_REPORT_APPROVED, // báo cáo chất lượng được duyệt
    FRAUD_DETECTED, // gian lận phát hiện (chỉ gửi global/admin)
    SELLER_CANCEL_REQUEST, // seller yêu cầu hủy - gửi cho SystemAdmin xem xét
    SELLER_CANCEL_REQUEST_ACCEPTED // hệ thống chấp nhận yêu cầu hủy
  }

  private final AuctionEventType eventType;
  private final Auction auction;
  private final NormalUser bidder; // null nếu không liên quan đến bidder
  private final long bidAmount; // 0 nếu không liên quan đến bid

  /** Thông điệp tùy theo đối tượng nhận. */
  private final String message;

  /**
   * Khởi tạo AuctionEvent.
   *
   * @param eventType loại sự kiện
   * @param auction phiên liên quan
   * @param bidder người đặt giá (null nếu không liên quan)
   * @param bidAmount số tiền (0 nếu không liên quan)
   */
  public AuctionEvent(
      AuctionEventType eventType, Auction auction, NormalUser bidder, long bidAmount) {
    this(eventType, auction, bidder, bidAmount, null);
  }

  /** Khởi tạo AuctionEvent với custom message. */
  public AuctionEvent(
      AuctionEventType eventType,
      Auction auction,
      NormalUser bidder,
      long bidAmount,
      String message) {
    this.eventType = eventType;
    this.auction = auction;
    this.bidder = bidder;
    this.bidAmount = bidAmount;
    this.message = message;
  }

  public AuctionEventType getEventType() {
    return eventType;
  }

  public Auction getAuction() {
    return auction;
  }

  public NormalUser getBidder() {
    return bidder;
  }

  public long getBidAmount() {
    return bidAmount;
  }

  public String getMessage() {
    return message;
  }
}
