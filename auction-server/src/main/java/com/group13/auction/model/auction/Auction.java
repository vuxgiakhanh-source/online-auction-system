package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phiên đấu giá - chỉ lưu data và trạng thái. */
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
  private LocalDateTime endTime;
  /**
   * Giá sàn bí mật do Seller đặt khi tạo auction.
   * Phiên chỉ có winner khi {@code currentPrice >= reservePrice}.
   */
  private final long reservePrice;
  private final List<String> bidTransactionIds;
  private long currentPrice;
  private NormalUser currentLeader;

  /**
   * State object hiện tại.
   * Mọi chuyển trạng thái đều đi qua state object, không if-else.
   */
  private AuctionState state;

  private AuctionWinner winner;

  /** Số người xem (watchlist) hiện tại, dùng để sort. */
  private int viewerCount;

  // Static factory method

  /**
   * Khai sinh Auction mới ở trạng thái OPEN.
   *
   * @param item         sản phẩm đưa ra đấu giá
   * @param startTime    thời điểm bắt đầu (có thể là tương lai)
   * @param endTime      thời điểm kết thúc
   * @param reservePrice giá sàn bí mật của Seller (> 0)
   * @return Auction mới
   */
  public static Auction create(
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long reservePrice) {
    return new Auction(item, startTime, endTime, reservePrice);
  }

  /**
   * Hồi sinh Auction từ DB - chỉ DAO được gọi method này.
   */
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

  // Private constructors

  private Auction(
          Item item,
          LocalDateTime startTime,
          LocalDateTime endTime,
          long reservePrice) {
    super();
    this.item = item;
    this.currentPrice = item.getStartingPrice();
    this.startTime = startTime;
    this.originalEndTime = endTime;
    this.endTime = endTime;
    this.reservePrice = reservePrice;
    this.state = OpenState.INSTANCE;
    this.bidTransactionIds = new ArrayList<>();
    this.winner = null;
    this.viewerCount = 0;
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
    this.state = resolveState(status);
    this.bidTransactionIds = new ArrayList<>();
    this.winner = null;
    this.viewerCount = 0;
  }

  /**
   * Chuyển đổi AuctionStatus thành AuctionState tương ứng.
   * Dùng khi hồi sinh Auction từ DB qua reconstitute().
   *
   * @param status trạng thái từ DB
   * @return AuctionState tương ứng
   */
  private static AuctionState resolveState(AuctionStatus status) {
    switch (status) {
      case OPEN:
        return OpenState.INSTANCE;
      case RUNNING:
        return RunningState.INSTANCE;
      case FINISHED:
        return FinishedState.INSTANCE;
      case PAID:
        return PaidState.INSTANCE;
      case CANCELED:
        return CanceledState.INSTANCE;
      default:
        throw new IllegalArgumentException("AuctionStatus không được hỗ trợ: " + status);
    }
  }

  // Getters

  public Item getItem() {
    return item;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public LocalDateTime getOriginalEndTime() {
    return originalEndTime;
  }

  public long getCurrentPrice() {
    return currentPrice;
  }

  public NormalUser getCurrentLeader() {
    return currentLeader;
  }

  /**
   * Trạng thái hiện tại dưới dạng enum - tương thích ngược với DAO và log.
   *
   * @return AuctionStatus tương ứng với state object hiện tại
   */
  public AuctionStatus getStatus() {
    return state.getStatus();
  }

  public AuctionWinner getWinner() {
    return winner;
  }

  public long getReservePrice() {
    return reservePrice;
  }

  public int getViewerCount() {
    return viewerCount;
  }

  public boolean isAcceptingBids() {
    return state.getStatus() == AuctionStatus.RUNNING;
  }

  /**
   * Kiểm tra giá hiện tại có đáp ứng reserve price chưa.
   *
   * @return true nếu currentPrice >= reservePrice
   */
  public boolean isReserveMet() {
    return currentPrice >= getReservePrice();
  }

  public List<String> getBidTransactionIds() {
    return Collections.unmodifiableList(bidTransactionIds);
  }

  // State transitions - chỉ AuctionService gọi

  /**
   * Chuyển sang RUNNING (OPEN -> RUNNING).
   *
   * @throws IllegalStateException nếu phiên không ở OPEN
   */
  public void transitionToRunning() {
    this.state = state.start();
    markUpdated();
  }

  /**
   * Đóng phiên khi hết giờ
   * (RUNNING -> FINISHED nếu có winner, -> CANCELED nếu không có winner).
   *
   * @param hasWinner true nếu có currentLeader và reserve đã OK
   * @throws IllegalStateException nếu phiên không ở RUNNING
   */
  public void transitionToClose(boolean hasWinner) {
    this.state = state.close(hasWinner);
    markUpdated();
  }

  /**
   * Hủy phiên (OPEN / RUNNING / CANCEL_REQUESTED -> CANCELED).
   *
   * @throws IllegalStateException nếu trạng thái hiện tại không hủy được
   */
  public void transitionToCancel() {
    this.state = state.cancel();
    markUpdated();
  }

  /**
   * Đánh dấu đã thanh toán (FINISHED -> PAID).
   *
   * @throws IllegalStateException nếu phiên không ở FINISHED
   */
  public void transitionToPaid() {
    this.state = state.markPaid();
    markUpdated();
  }

  // Setters - chỉ AuctionService / BidService gọi

  /**
   * Cập nhật giá và leader trong một thao tác atomic.
   *
   * <p>Chỉ {@link com.group13.auction.service.BidService} được gọi method này,
   * bên trong {@code placeBid()} sau khi strategy đã validate bid thành công.
   *
   * @param newPrice  giá mới (đã validated)
   * @param newLeader người vừa đặt giá cao nhất
   */
  public void updateBid(long newPrice, NormalUser newLeader) {
    this.currentPrice = newPrice;
    this.currentLeader = newLeader;
    markUpdated();
  }

  /**
   * Gia hạn phiên (anti-sniping).
   *
   * @param extension khoảng thời gian gia hạn (phải > 0)
   */
  public void extendEndTime(Duration extension) {
    if (extension == null || extension.isZero() || extension.isNegative()) {
      throw new IllegalArgumentException("extension phải > 0.");
    }
    this.endTime = this.endTime.plus(extension);
    markUpdated();
  }

  /**
   * Ghi nhận người thắng phiên sau khi phiên kết thúc (RUNNING -> FINISHED).
   *
   * <p>Chỉ {@link com.group13.auction.service.AuctionService} được gọi method này,
   * bên trong {@code closeAuction()} - chỉ khi có {@code currentLeader} tồn tại.
   */
  public void setWinner(AuctionWinner winner) {
    this.winner = winner;
    markUpdated();
  }

  /**
   * Lưu id của một BidTransaction vừa được ghi nhận vào phiên.
   *
   * <p>Chỉ {@link com.group13.auction.service.BidService} được gọi method này,
   * bên trong {@code placeBid()} sau khi tạo BidTransaction thành công.
   */
  public void addBidTransactionId(String bidId) {
    bidTransactionIds.add(bidId);
  }

  /**
   * Tăng số người xem khi có bidder join hoặc watch.
   * Phục vụ cho quá trình sort.
   */
  public void incrementViewerCount() {
    this.viewerCount++;
  }

  @Override
  public void printInfo() {
    System.out.println("THÔNG TIN PHIÊN ĐẤU GIÁ");
    System.out.printf("ID      : %s%n", getId());
    System.out.printf("Item    : %s%n", item.getName());
    System.out.printf("Giá     : %d%n", currentPrice);
    System.out.printf("Status  : %s%n", getStatus());
    System.out.printf(
            "Leader  : %s%n", currentLeader != null ? currentLeader.getUsername() : "Chưa có");
    System.out.printf("Viewers : %d%n", viewerCount);
    System.out.println("==========================================");
  }
}