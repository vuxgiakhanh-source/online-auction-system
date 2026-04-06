package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.Bidder;
import java.time.LocalDateTime;

/** Ghi nhận người chiến thắng và trạng thái thanh toán — chỉ lưu data. */
public class AuctionWinner extends Entity {

  public enum PaymentStatus {
    PENDING, COMPLETED, EXPIRED, CANCELLED
  }

  private final Bidder winner;
  private final String auctionId;
  private final double finalPrice;
  private final LocalDateTime deadline;
  private PaymentStatus paymentStatus;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh AuctionWinner ngay khi auction FINISHED.
   * Chỉ AuctionService tạo AuctionWinner, và chỉ khi closeAuction()
   * được gọi mà có currentLeader tồn tại.
   * Hạn thanh toán = 24h từ lúc tạo.
   *
   * @param winner     người thắng
   * @param auctionId  id phiên đấu giá
   * @param finalPrice giá cuối cùng
   * @return AuctionWinner mới
   */
  public static AuctionWinner create(Bidder winner,
      String auctionId, double finalPrice) {
    return new AuctionWinner(winner, auctionId, finalPrice);
  }

  /**
   * Hồi sinh AuctionWinner từ DB — chỉ DAO được gọi method này.
   *
   * @param id            id gốc
   * @param createdAt     thời gian tạo gốc
   * @param updatedAt     thời gian cập nhật gốc
   * @param winner        người thắng
   * @param auctionId     id phiên
   * @param finalPrice    giá cuối
   * @param deadline      hạn chót thanh toán gốc
   * @param paymentStatus trạng thái thanh toán
   * @return AuctionWinner được phục hồi
   */
  public static AuctionWinner reconstitute(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, Bidder winner, String auctionId,
      double finalPrice, LocalDateTime deadline, PaymentStatus paymentStatus) {
    return new AuctionWinner(id, createdAt, updatedAt, winner, auctionId,
        finalPrice, deadline, paymentStatus);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private AuctionWinner(Bidder winner, String auctionId, double finalPrice) {
    super();
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.deadline = LocalDateTime.now().plusHours(24);
    this.paymentStatus = PaymentStatus.PENDING;
  }

  private AuctionWinner(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, Bidder winner, String auctionId,
      double finalPrice, LocalDateTime deadline, PaymentStatus paymentStatus) {
    super(id, createdAt, updatedAt);
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.deadline = deadline;
    this.paymentStatus = paymentStatus;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public Bidder getWinner() { return winner; }
  public String getAuctionId() { return auctionId; }
  public double getFinalPrice() { return finalPrice; }
  public LocalDateTime getDeadline() { return deadline; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(deadline)
        && paymentStatus == PaymentStatus.PENDING;
  }

  // ── Setter — chỉ BidService gọi ───────────────────────────────────────────

  public void setPaymentStatus(PaymentStatus status) {
    this.paymentStatus = status;
    markUpdated();
  }

  @Override
  public void printInfo() {
    System.out.println("=== AUCTION WINNER ===================");
    System.out.printf("Winner        : %s%n", winner.getUsername());
    System.out.printf("Auction ID    : %s%n", auctionId);
    System.out.printf("Giá cuối      : %.0f%n", finalPrice);
    System.out.printf("Hạn thanh toán: %s%n", deadline);
    System.out.printf("Trạng thái    : %s%n", paymentStatus);
    System.out.println("======================================");
  }
}