package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.strategy.ReservePriceStrategy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phiên đấu giá — chỉ lưu data và trạng thái. */
public class Auction extends Entity {

  public enum AuctionStatus {
    OPEN, RUNNING, FINISHED, PAID, CANCELED,
    /** Phiên đã kết thúc nhưng reserve price chưa được đáp ứng. */
    RESERVE_NOT_MET
  }

  private final Item                  item;
  private final LocalDateTime         startTime;
  private final LocalDateTime         endTime;
  /**
   * Reserve price strategy — bắt buộc thiết lập khi tạo auction.
   * Seller phải chỉ định giá sàn ngay từ đầu.
   */
  private final ReservePriceStrategy  reserveStrategy;
  private final List<String>          bidTransactionIds;
  private final List<AuctionObserver> observers;
  private       double                currentPrice;
  private       NormalUser            currentLeader;
  private       AuctionStatus         status;
  private       AuctionWinner         winner;

  // ── Static factory methods ─────────────────────────────────────────────

  /**
   * Khai sinh Auction mới ở trạng thái OPEN.
   * Có thể set lịch trước nhiều ngày.
   *
   * @param item            sản phẩm đưa ra đấu giá
   * @param startTime       thời điểm bắt đầu (có thể là tương lai)
   * @param endTime         thời điểm kết thúc
   * @param reserveStrategy reserve price strategy — BẮT BUỘC
   * @return Auction mới
   */
  public static Auction create(Item item,
                               LocalDateTime startTime, LocalDateTime endTime,
                               ReservePriceStrategy reserveStrategy) {
    return new Auction(item, startTime, endTime, reserveStrategy);
  }

  /**
   * Hồi sinh Auction từ DB — chỉ DAO được gọi method này.
   */
  public static Auction reconstitute(String id, LocalDateTime createdAt,
                                     LocalDateTime updatedAt, Item item, LocalDateTime startTime,
                                     LocalDateTime endTime, double currentPrice, AuctionStatus status,
                                     ReservePriceStrategy reserveStrategy) {
    return new Auction(id, createdAt, updatedAt, item, startTime,
            endTime, currentPrice, status, reserveStrategy);
  }

  // ── Private constructors ───────────────────────────────────────────────

  private Auction(Item item, LocalDateTime startTime, LocalDateTime endTime,
                  ReservePriceStrategy reserveStrategy) {
    super();
    this.item              = item;
    this.currentPrice      = item.getStartingPrice();
    this.startTime         = startTime;
    this.endTime           = endTime;
    this.reserveStrategy   = reserveStrategy;
    this.status            = AuctionStatus.OPEN;
    this.bidTransactionIds = new ArrayList<>();
    this.observers         = new ArrayList<>();
    this.winner            = null;
  }

  private Auction(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                  Item item, LocalDateTime startTime, LocalDateTime endTime,
                  double currentPrice, AuctionStatus status,
                  ReservePriceStrategy reserveStrategy) {
    super(id, createdAt, updatedAt);
    this.item              = item;
    this.currentPrice      = currentPrice;
    this.startTime         = startTime;
    this.endTime           = endTime;
    this.reserveStrategy   = reserveStrategy;
    this.status            = status;
    this.bidTransactionIds = new ArrayList<>();
    this.observers         = new ArrayList<>();
    this.winner            = null;
  }

  // ── Getters ────────────────────────────────────────────────────────────

  public Item               getItem()            { return item; }
  public LocalDateTime      getStartTime()        { return startTime; }
  public LocalDateTime      getEndTime()          { return endTime; }
  public double             getCurrentPrice()     { return currentPrice; }
  public NormalUser         getCurrentLeader()    { return currentLeader; }
  public AuctionStatus      getStatus()           { return status; }
  public AuctionWinner      getWinner()           { return winner; }
  public ReservePriceStrategy getReserveStrategy(){ return reserveStrategy; }

  public boolean isAcceptingBids() {
    return status == AuctionStatus.RUNNING;
  }

  /**
   * Kiểm tra giá hiện tại có đáp ứng reserve price chưa.
   *
   * @return true nếu currentPrice >= reservePrice
   */
  public boolean isReserveMet() {
    return currentPrice >= reserveStrategy.getReservePrice();
  }

  public List<String> getBidTransactionIds() {
    return Collections.unmodifiableList(bidTransactionIds);
  }

  public List<AuctionObserver> getObservers() {
    return Collections.unmodifiableList(observers);
  }

  // ── Setters — chỉ AuctionService / BidService gọi ────────────────────

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
  public void setCurrentLeader(NormalUser leader) {
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
    System.out.printf("ID           : %s%n", getId());
    System.out.printf("Sản phẩm     : %s%n", item.getName());
    System.out.printf("Giá hiện tại : %.0f%n", currentPrice);
    System.out.printf("Reserve price: %.0f%n", reserveStrategy.getReservePrice());
    System.out.printf("Reserve met  : %s%n", isReserveMet() ? "Có" : "Chưa");
    System.out.printf("Dẫn đầu      : %s%n",
            currentLeader != null ? currentLeader.getUsername() : "Chưa có");
    System.out.printf("Trạng thái   : %s%n", status);
    System.out.printf("Bắt đầu      : %s%n", startTime);
    System.out.printf("Kết thúc     : %s%n", endTime);
    System.out.println("======================================");
  }
}