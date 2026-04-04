package com.group13.auction.model.bid;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.Bidder;
import java.time.LocalDateTime;

/** Ghi lại một lần đặt giá — chỉ lưu data. */
public class BidTransaction extends Entity {

  public enum BidResult { ACCEPTED, REJECTED, OUTBID }

  private final Bidder bidder;
  private final Auction auction;
  private final double amount;
  private final LocalDateTime timestamp;
  private BidResult result;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh BidTransaction mới.
   *
   * @param bidder  người đặt giá
   * @param auction phiên liên quan
   * @param amount  số tiền đặt
   * @param result  kết quả
   * @return BidTransaction mới
   */
  public static BidTransaction create(Bidder bidder, Auction auction,
      double amount, BidResult result) {
    return new BidTransaction(bidder, auction, amount, result);
  }

  /**
   * Hồi sinh BidTransaction từ DB — chỉ DAO được gọi method này.
   *
   * @param id        id gốc
   * @param createdAt thời gian tạo gốc
   * @param updatedAt thời gian cập nhật gốc
   * @param bidder    người đặt giá
   * @param auction   phiên liên quan
   * @param amount    số tiền
   * @param timestamp thời điểm đặt giá gốc
   * @param result    kết quả
   * @return BidTransaction được phục hồi
   */
  public static BidTransaction reconstitute(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, Bidder bidder, Auction auction,
      double amount, LocalDateTime timestamp, BidResult result) {
    return new BidTransaction(id, createdAt, updatedAt, bidder,
        auction, amount, timestamp, result);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private BidTransaction(Bidder bidder, Auction auction,
      double amount, BidResult result) {
    super();
    this.bidder = bidder;
    this.auction = auction;
    this.amount = amount;
    this.timestamp = LocalDateTime.now();
    this.result = result;
  }

  private BidTransaction(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, Bidder bidder, Auction auction,
      double amount, LocalDateTime timestamp, BidResult result) {
    super(id, createdAt, updatedAt);
    this.bidder = bidder;
    this.auction = auction;
    this.amount = amount;
    this.timestamp = timestamp;
    this.result = result;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public Bidder getBidder() { return bidder; }
  public Auction getAuction() { return auction; }
  public double getAmount() { return amount; }
  public LocalDateTime getTimestamp() { return timestamp; }
  public BidResult getResult() { return result; }

  // ── Setter — chỉ BidService gọi ───────────────────────────────────────────

  public void setResult(BidResult result) {
    this.result = result;
    markUpdated();
  }

  @Override
  public void printInfo() {
    System.out.println("=== BID TRANSACTION ==================");
    System.out.printf("Bidder    : %s%n", bidder.getUsername());
    System.out.printf("Số tiền   : %.0f%n", amount);
    System.out.printf("Kết quả   : %s%n", result);
    System.out.printf("Thời gian : %s%n", timestamp);
    System.out.println("======================================");
  }
}