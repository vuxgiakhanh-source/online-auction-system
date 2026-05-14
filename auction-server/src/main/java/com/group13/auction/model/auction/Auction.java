package com.group13.auction.model.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phiên đấu giá - chỉ lưu data và trạng thái.
 *
 * ═══════════════════════════════════════════════════════════
 * PERFORMANCE FIX #3 — Thread safety cho shared state:
 *
 * volatile currentPrice / currentLeader / endTime / state:
 *   BidService dùng per-auction synchronized block khi WRITE.
 *   volatile đảm bảo sau khi release lock, các thread khác
 *   đọc được giá trị mới nhất ngay (Java Memory Model visibility).
 *   Không cần synchronized khi READ vì volatile đủ cho primitive long.
 *
 * bidTransactionIds = Collections.synchronizedList:
 *   addBidTransactionId() gọi từ trong synchronized(lock) của BidService,
 *   nhưng getBidTransactionIds() có thể gọi từ thread khác bất kỳ lúc nào.
 *   synchronizedList bảo vệ cả hai phía mà không cần lock thêm.
 * ═══════════════════════════════════════════════════════════
 */
public class Auction extends Entity {

  private static final Logger log = LoggerFactory.getLogger(Auction.class);

  public enum AuctionStatus {
    OPEN,
    RUNNING,
    FINISHED,
    PAID,
    CANCELED
  }

  private final Item item;
  private final LocalDateTime startTime;
  private final LocalDateTime originalEndTime;

  /** volatile — publish sau synchronized block trong BidService (anti-sniping). */
  private volatile LocalDateTime endTime;

  /** Giá sàn bí mật — immutable sau khi tạo. */
  private final long reservePrice;

  /**
   * FIX #3: volatile — đảm bảo thread đọc currentPrice sau lock release
   * luôn thấy giá trị mới nhất từ auction.updateBid().
   */
  private volatile long currentPrice;

  /** volatile — cùng lý do với currentPrice. */
  private volatile NormalUser currentLeader;

  /**
   * FIX #3: synchronizedList — addBidTransactionId() gọi trong lock của BidService,
   * getBidTransactionIds() (unmodifiable view) gọi ngoài lock.
   */
  private final List<String> bidTransactionIds =
          Collections.synchronizedList(new ArrayList<>());

  /**
   * FIX: AtomicReference thay volatile — transitionToRunning/Close/Cancel/Paid có thể gọi
   * từ AuctionService trên thread khác với thread đang bid. AtomicReference đảm bảo
   * cả read lẫn write là atomic (không có race condition read-modify-write).
   */
  private final AtomicReference<AuctionState> state = new AtomicReference<>();

  private volatile AuctionWinner winner;

  /** FIX: AtomicInteger thay volatile int — viewerCount++ là read-modify-write, không atomic nếu dùng volatile. */
  private final AtomicInteger viewerCount = new AtomicInteger(0);

  // =========================================================================
  // Static factory methods
  // =========================================================================

  public static Auction create(
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long reservePrice) {
    return new Auction(item, startTime, endTime, reservePrice);
  }

  public static Auction reconstitute(
          String id,
          LocalDateTime createdAt,
          LocalDateTime updatedAt,
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long currentPrice,
          AuctionStatus status,
          long reservePrice) {
    return new Auction(
            id, createdAt, updatedAt, item, startTime, endTime, currentPrice, status, reservePrice);
  }

  // =========================================================================
  // Private constructors
  // =========================================================================

  private Auction(Item item, LocalDateTime startTime, LocalDateTime endTime, long reservePrice) {
    super();
    this.item = item;
    this.currentPrice = item.getStartingPrice();
    this.startTime = startTime;
    this.originalEndTime = endTime;
    this.endTime = endTime;
    this.reservePrice = reservePrice;
    this.state.set(OpenState.INSTANCE);
    this.winner = null;
  }

  private Auction(
          String id,
          LocalDateTime createdAt,
          LocalDateTime updatedAt,
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long currentPrice,
          AuctionStatus status,
          long reservePrice) {
    super(id, createdAt, updatedAt);
    this.item = item;
    this.currentPrice = currentPrice;
    this.startTime = startTime;
    this.originalEndTime = endTime;
    this.endTime = endTime;
    this.reservePrice = reservePrice;
    this.state.set(resolveState(status));
    this.winner = null;
  }

  private static AuctionState resolveState(AuctionStatus status) {
    switch (status) {
      case OPEN:     return OpenState.INSTANCE;
      case RUNNING:  return RunningState.INSTANCE;
      case FINISHED: return FinishedState.INSTANCE;
      case PAID:     return PaidState.INSTANCE;
      case CANCELED: return CanceledState.INSTANCE;
      default:
        throw new IllegalArgumentException("AuctionStatus không được hỗ trợ: " + status);
    }
  }

  // =========================================================================
  // Getters
  // =========================================================================

  public Item getItem()                    { return item; }
  public LocalDateTime getStartTime()      { return startTime; }
  public LocalDateTime getEndTime()        { return endTime; }
  public LocalDateTime getOriginalEndTime(){ return originalEndTime; }
  public long getCurrentPrice()            { return currentPrice; }
  public NormalUser getCurrentLeader()     { return currentLeader; }
  public AuctionStatus getStatus()         { return state.get().getStatus(); }
  public AuctionWinner getWinner()         { return winner; }
  public long getReservePrice()            { return reservePrice; }
  public int getViewerCount()              { return viewerCount.get(); }

  public boolean isAcceptingBids() {
    return state.get().getStatus() == AuctionStatus.RUNNING;
  }

  public boolean isReserveMet() {
    return currentPrice >= reservePrice;
  }

  public List<String> getBidTransactionIds() {
    return Collections.unmodifiableList(bidTransactionIds);
  }

  // =========================================================================
  // State transitions — chỉ AuctionService gọi
  // =========================================================================

  public void transitionToRunning() {
    this.state.updateAndGet(AuctionState::start);
    markUpdated();
  }

  public void transitionToClose(boolean hasWinner) {
    this.state.updateAndGet(s -> s.close(hasWinner));
    markUpdated();
  }

  public void transitionToCancel() {
    this.state.updateAndGet(AuctionState::cancel);
    markUpdated();
  }

  public void transitionToPaid() {
    this.state.updateAndGet(AuctionState::markPaid);
    markUpdated();
  }

  // =========================================================================
  // Setters — chỉ BidService / AuctionService gọi
  // =========================================================================

  /**
   * Cập nhật giá và leader.
   *
   * Thread safety: gọi bên trong per-auction synchronized block của BidService.
   * volatile trên currentPrice/currentLeader publish giá trị ra ngoài lock.
   */
  public void updateBid(long newPrice, NormalUser newLeader) {
    this.currentPrice = newPrice;
    this.currentLeader = newLeader;
    markUpdated();
  }

  /**
   * Gia hạn phiên (anti-sniping).
   * Gọi bên trong synchronized(lock) của BidService.
   */
  public void extendEndTime(Duration extension) {
    if (extension == null || extension.isZero() || extension.isNegative()) {
      throw new IllegalArgumentException("extension phải > 0.");
    }
    this.endTime = this.endTime.plus(extension);
    markUpdated();
  }

  public void setWinner(AuctionWinner winner) {
    this.winner = winner;
    markUpdated();
  }

  /** Thread-safe nhờ synchronizedList (FIX #3). */
  public void addBidTransactionId(String bidId) {
    bidTransactionIds.add(bidId);
  }

  public void incrementViewerCount() {
    this.viewerCount.incrementAndGet();
  }

  @Override
  public void printInfo() {
    log.warn("THÔNG TIN PHIÊN ĐẤU GIÁ");
    log.warn("ID      : {}", getId());
    log.warn("Item    : {}", item.getName());
    log.warn("Giá     : {}", currentPrice);
    log.warn("Status  : {}", getStatus());
    log.warn("Leader  : {}", currentLeader != null ? currentLeader.getUsername() : "Chưa có");
    log.warn("Viewers : {}", viewerCount.get());
    log.warn("==========================================");
  }
}