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
   * Có thể set lịch trước nhiều ngày.
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

  /**
   * Cập nhật trạng thái vòng đời phiên (OPEN → RUNNING → FINISHED/CANCELED → PAID).
   *
   * Chỉ {@link com.group13.auction.service.AuctionService} được gọi method này.
   * Cụ thể qua: {@code startAuction()}, {@code closeAuction()},
   * {@code markAsPaid()}, {@code cancelAuction()}.
   */
  public void setStatus(AuctionStatus status) {
    this.status = status;
    markUpdated();
  }

  /**
   * Cập nhật giá hiện tại khi có bid mới hợp lệ được chấp nhận.
   *
   * Chỉ {@link com.group13.auction.service.BidService} được gọi method này,
   * bên trong {@code placeBid()} sau khi strategy đã validate bid thành công.
   */
  public void setCurrentPrice(double price) {
    this.currentPrice = price;
    markUpdated();
  }

/**
 * Cập nhật người đang dẫn đầu phiên (người vừa đặt giá cao nhất).
 *
 * Chỉ {@link com.group13.auction.service.BidService} được gọi method này
 * bên trong {@code placeBid()} ngay sau {@code setCurrentPrice()}.
 */
  public void setCurrentLeader(User leader) {
    this.currentLeader = leader;
  }

/**
 * Ghi nhận người thắng phiên sau khi phiên kết thúc (RUNNING → FINISHED).
 *
 * Chỉ {@link com.group13.auction.service.AuctionService} được gọi method này,
 * bên trong {@code closeAuction()} — chỉ khi có {@code currentLeader} tồn tại.
 */
  public void setWinner(AuctionWinner winner) {
    this.winner = winner;
    markUpdated();
  }

  /**
   * Lưu id của một BidTransaction vừa được ghi nhận vào phiên.
   *
   * Chỉ {@link com.group13.auction.service.BidService} được gọi method này,
   * bên trong {@code placeBid()} sau khi tạo BidTransaction thành công.
   */
  public void addBidTransactionId(String bidId) {
    bidTransactionIds.add(bidId);
  }

  /**
   * Đăng ký observer để nhận notify về sự kiện trong phiên này.
   *
   * <p><b>Chỉ {@link com.group13.auction.service.AuctionService} được gọi method này</b>
   * (qua {@code addObserver()}), được trigger bởi
   * {@link com.group13.auction.service.BidService}
   * khi Bidder gọi {@code joinAuction()} hoặc {@code watchAuction()}.
   */
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