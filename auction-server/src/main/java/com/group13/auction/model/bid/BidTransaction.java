package com.group13.auction.model.bid;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ghi lại một lần đặt giá — chỉ lưu data. */
public class BidTransaction extends Entity {

  private static final Logger log = LoggerFactory.getLogger(BidTransaction.class);

  public enum BidResult {
    ACCEPTED,
    /** Bid được chấp nhận nhưng chưa đạt reserve price. */
    ACCEPTED_RESERVE_NOT_MET,
    REJECTED,
    /** Dùng cho autobid or bỏ */
    OUTBID,
    /** Bid bị hủy vì người đặt tự rời phiên khi đang dẫn đầu. */
    CANCELLED_BY_LEAVE
  }

  private final NormalUser bidder;
  private final String auctionId;
  private final long amount;
  private final LocalDateTime timestamp;
  private BidResult result;

  // Static factory methods

  /**
   * Khai sinh BidTransaction mới.
   *
   * @param bidder người đặt giá
   * @param auctionId id của phiên liên quan
   * @param amount số tiền đặt
   * @param result kết quả
   * @return BidTransaction mới
   */
  public static BidTransaction create(
      NormalUser bidder, String auctionId, long amount, BidResult result) {
    return new BidTransaction(bidder, auctionId, amount, result);
  }

  /** Hồi sinh BidTransaction từ DB — CHÚ Ý: chỉ DAO được gọi method này. */
  public static BidTransaction reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      NormalUser bidder,
      String auctionId,
      long amount,
      LocalDateTime timestamp,
      BidResult result) {
    return new BidTransaction(
        id, createdAt, updatedAt, bidder, auctionId, amount, timestamp, result);
  }

  // Private constructors

  private BidTransaction(NormalUser bidder, String auctionId, long amount, BidResult result) {
    super();
    this.bidder = bidder;
    this.auctionId = auctionId;
    this.amount = amount;
    this.timestamp = LocalDateTime.now();
    this.result = result;
  }

  private BidTransaction(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      NormalUser bidder,
      String auctionId,
      long amount,
      LocalDateTime timestamp,
      BidResult result) {
    super(id, createdAt, updatedAt);
    this.bidder = bidder;
    this.auctionId = auctionId;
    this.amount = amount;
    this.timestamp = timestamp;
    this.result = result;
  }

  // Getters

  public NormalUser getBidder() {
    return bidder;
  }

  public String getAuctionId() {
    return auctionId;
  }

  public long getAmount() {
    return amount;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public BidResult getResult() {
    return result;
  }

  // Setter - chỉ BidService gọi

  public void setResult(BidResult result) {
    this.result = result;
    markUpdated();
  }

  @Override
  public void printInfo() {
    log.info("THÔNG TIN GIAO DỊCH");
    log.info("Bidder   : {}", bidder.getUsername());
    log.info("Số tiền  : {}", amount);
    log.info("Kết quả  : {}", result);
    log.info("Thời gian: {}", timestamp);
    log.info("======================================");
  }
}
