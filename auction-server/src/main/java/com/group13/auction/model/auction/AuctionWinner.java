package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Ghi nhận người chiến thắng và trạng thái thanh toán — chỉ lưu data. */
public class AuctionWinner extends Entity {

  public enum PaymentStatus {
    PENDING,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    /**
     * Winner đã thanh toán đủ; tiền đang giữ ở "ngân hàng" hệ thống
     * chờ chuyển cho seller (sau khi trừ thuế).
     */
    FUNDS_HELD
  }

  private final NormalUser winner;
  private final String auctionId;
  private final double finalPrice;
  /**
   * Số tiền cọc winner đã đặt khi joinAuction.
   * Cọc được tính vào finalPrice — winner chỉ cần trả phần còn lại.
   */
  private final double depositPaid;
  private final LocalDateTime deadline;
  private PaymentStatus paymentStatus;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh AuctionWinner ngay khi auction FINISHED.
   * Chỉ AuctionService tạo AuctionWinner, và chỉ khi closeAuction()
   * được gọi mà có currentLeader tồn tại và reserve price đã được đáp ứng.
   * Hạn thanh toán = 24h từ lúc tạo.
   *
   * @param winner người thắng
   * @param auctionId id phiên đấu giá
   * @param finalPrice giá cuối cùng
   * @param depositPaid số tiền cọc đã đặt
   * @return AuctionWinner mới
   */
  public static AuctionWinner create(NormalUser winner, String auctionId,
                                     double finalPrice, double depositPaid) {
    return new AuctionWinner(winner, auctionId, finalPrice, depositPaid);
  }

  /**
   * Hồi sinh AuctionWinner từ DB — chỉ DAO được gọi method này.
   */
  public static AuctionWinner reconstitute(String id, LocalDateTime createdAt,
                                           LocalDateTime updatedAt, NormalUser winner, String auctionId,
                                           double finalPrice, double depositPaid, LocalDateTime deadline,
                                           PaymentStatus paymentStatus) {
    return new AuctionWinner(id, createdAt, updatedAt, winner, auctionId,
            finalPrice, depositPaid, deadline, paymentStatus);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private AuctionWinner(NormalUser winner, String auctionId,
                        double finalPrice, double depositPaid) {
    super();
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.depositPaid = depositPaid;
    this.deadline = LocalDateTime.now().plusHours(24);
    this.paymentStatus = PaymentStatus.PENDING;
  }

  private AuctionWinner(String id, LocalDateTime createdAt,
                        LocalDateTime updatedAt, NormalUser winner, String auctionId,
                        double finalPrice, double depositPaid, LocalDateTime deadline,
                        PaymentStatus paymentStatus) {
    super(id, createdAt, updatedAt);
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.depositPaid = depositPaid;
    this.deadline = deadline;
    this.paymentStatus = paymentStatus;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public NormalUser getWinner() { return winner; }
  public String getAuctionId() { return auctionId; }
  public double getFinalPrice() { return finalPrice; }
  public double getDepositPaid() { return depositPaid; }
  public LocalDateTime getDeadline() { return deadline; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }

  /** Số tiền còn phải trả sau khi trừ cọc. */
  public double getRemainingAmount() {
    return Math.max(0, finalPrice - depositPaid);
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(deadline)
            && paymentStatus == PaymentStatus.PENDING;
  }

  // ── Setter — chỉ PaymentService gọi ──────────────────────────────────────

  public void setPaymentStatus(PaymentStatus paymentStatus) {
    this.paymentStatus = paymentStatus;
    markUpdated();
  }

  @Override
  public void printInfo() {
    System.out.println("=== AUCTION WINNER ===================");
    System.out.printf("Winner : %s%n", winner.getUsername());
    System.out.printf("Auction ID : %s%n", auctionId);
    System.out.printf("Giá cuối : %.0f%n", finalPrice);
    System.out.printf("Đã cọc : %.0f%n", depositPaid);
    System.out.printf("Còn lại : %.0f%n", getRemainingAmount());
    System.out.printf("Hạn TT : %s%n", deadline);
    System.out.printf("TT Status : %s%n", paymentStatus);
    System.out.println("======================================");
  }
}