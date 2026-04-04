package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionObserver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phiên đấu giá — chỉ lưu data và trạng thái. */
public class Auction extends Entity {

  public enum AuctionStatus {
    OPEN, RUNNING, FINISHED, PAID, CANCELED
  }

  private final Item item;
  private final LocalDateTime startTime;
  private final LocalDateTime endTime;
  private final List<String> bidTransactionIds;
  private final List<AuctionObserver> observers;
  private double currentPrice;
  private User currentLeader;
  private AuctionStatus status;
  private AuctionWinner winner;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh Auction mới ở trạng thái OPEN.
   * Có thể set lịch trước nhiều ngày — OPEN không có nghĩa đang chạy.
   *
   * @param item      sản phẩm đưa ra đấu giá
   * @param startTime thời điểm bắt đầu (có thể là tương lai)
   * @param endTime   thời điểm kết thúc
   * @return Auction mới
   */
  public static Auction create(Item item,
      LocalDateTime startTime, LocalDateTime endTime) {
    return new Auction(item, startTime, endTime);
  }

  /**
   * Hồi sinh Auction từ DB — chỉ DAO được gọi method này.
   *
   * @param id           id gốc
   * @param createdAt    thời gian tạo gốc
   * @param updatedAt    thời gian cập nhật gốc
   * @param item         sản phẩm
   * @param startTime    thời điểm bắt đầu
   * @param endTime      thời điểm kết thúc
   * @param currentPrice giá hiện tại
   * @param status       trạng thái hiện tại
   * @return Auction được phục hồi
   */
  public static Auction reconstitute(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, Item item, LocalDateTime startTime,
      LocalDateTime endTime, double currentPrice, AuctionStatus status) {
    return new Auction(id, createdAt, updatedAt, item, startTime,
        endTime, currentPrice, status);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Auction(Item item, LocalDateTime startTime, LocalDateTime endTime) {
    super();
    this.item = item;
    this.currentPrice = item.getStartingPrice();
    this.startTime = startTime;
    this.endTime = endTime;
    this.status = AuctionStatus.OPEN;
    this.bidTransactionIds = new ArrayList<>();
    this.observers = new ArrayList<>();
    this.winner = null;
  }

  private Auction(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      Item item, LocalDateTime startTime, LocalDateTime endTime,
      double currentPrice, AuctionStatus status) {
    super(id, createdAt, updatedAt);
    this.item = item;
    this.currentPrice = currentPrice;
    this.startTime = startTime;
    this.endTime = endTime;
    this.status = status;
    this.bidTransactionIds = new ArrayList<>();
    this.observers = new ArrayList<>();
    this.winner = null;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public Item getItem() { return item; }
  public LocalDateTime getStartTime() { return startTime; }
  public LocalDateTime getEndTime() { return endTime; }
  public double getCurrentPrice() { return currentPrice; }
  public User getCurrentLeader() { return currentLeader; }
  public AuctionStatus getStatus() { return status; }
  public AuctionWinner getWinner() { return winner; }

  public boolean isAcceptingBids() {
    return status == AuctionStatus.RUNNING;
  }

  public List<String> getBidTransactionIds() {
    return Collections.unmodifiableList(bidTransactionIds);
  }

  public List<AuctionObserver> getObservers() {
    return Collections.unmodifiableList(observers);
  }

  // ── Setters — chỉ AuctionService gọi ──────────────────────────────────────

  public void setStatus(AuctionStatus status) {
    this.status = status;
    markUpdated();
  }

  public void setCurrentPrice(double price) {
    this.currentPrice = price;
    markUpdated();
  }

  public void setCurrentLeader(User leader) {
    this.currentLeader = leader;
  }

  public void setWinner(AuctionWinner winner) {
    this.winner = winner;
    markUpdated();
  }

  public void addBidTransactionId(String bidId) {
    bidTransactionIds.add(bidId);
  }

  public void addObserver(AuctionObserver observer) {
    if (!observers.contains(observer)) {
      observers.add(observer);
    }
  }

  @Override
  public void printInfo() {
    System.out.println("=== AUCTION ==========================");
    System.out.printf("ID          : %s%n", getId());
    System.out.printf("Sản phẩm    : %s%n", item.getName());
    System.out.printf("Giá hiện tại: %.0f%n", currentPrice);
    System.out.printf("Dẫn đầu     : %s%n",
        currentLeader != null ? currentLeader.getUsername() : "Chưa có");
    System.out.printf("Trạng thái  : %s%n", status);
    System.out.printf("Bắt đầu     : %s%n", startTime);
    System.out.printf("Kết thúc    : %s%n", endTime);
    System.out.println("======================================");
  }
}