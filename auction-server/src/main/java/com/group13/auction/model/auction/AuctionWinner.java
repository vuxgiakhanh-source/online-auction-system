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
     * Winner đã thanh toán đủ; tiền đang giữ ở SystemBank,
     * chờ chuyển cho seller (có trừ thuế).
     */
    FUNDS_HELD
  }

  private final NormalUser winner;
  private final String auctionId;
  private final long finalPrice;
  /**
   * Số tiền cọc winner đã đặt khi joinAuction.
   * Cọc được tính vào finalPrice - winner chỉ cần trả phần còn lại.
   */
  private final long depositPaid;
  private final LocalDateTime deadline;
  private PaymentStatus paymentStatus;

  private boolean isSecondOffer;

  // Static factory methods

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
                                     long finalPrice, long depositPaid,
                                     boolean isSecondOffer) {
    return new AuctionWinner(winner, auctionId, finalPrice, depositPaid, isSecondOffer);
  }

  /**
   * Hồi sinh AuctionWinner từ DB — chỉ DAO được gọi method này.
   */
  public static AuctionWinner reconstitute(String id, LocalDateTime createdAt,
                                           LocalDateTime updatedAt, NormalUser winner, String auctionId,
                                           long finalPrice, long depositPaid, LocalDateTime deadline,
                                           PaymentStatus paymentStatus, boolean isSecondOffer) {
    return new AuctionWinner(id, createdAt, updatedAt, winner, auctionId,
            finalPrice, depositPaid, deadline, paymentStatus, isSecondOffer);
  }

  // Private constructors

  private AuctionWinner(NormalUser winner, String auctionId,
                        long finalPrice, long depositPaid,
                        boolean isSecondOffer) {
    super();
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.depositPaid = depositPaid;
    this.deadline = LocalDateTime.now().plusHours(24);
    this.paymentStatus = PaymentStatus.PENDING;
    this.isSecondOffer = isSecondOffer;
  }

  private AuctionWinner(String id, LocalDateTime createdAt,
                        LocalDateTime updatedAt, NormalUser winner, String auctionId,
                        long finalPrice, long depositPaid, LocalDateTime deadline,
                        PaymentStatus paymentStatus, boolean isSecondOffer) {
    super(id, createdAt, updatedAt);
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.depositPaid = depositPaid;
    this.deadline = deadline;
    this.paymentStatus = paymentStatus;
    this.isSecondOffer = isSecondOffer;
  }

  // Getters

  public NormalUser getWinner() { return winner; }
  public String getAuctionId() { return auctionId; }
  public long getFinalPrice() { return finalPrice; }
  public long getDepositPaid() { return depositPaid; }
  public LocalDateTime getDeadline() { return deadline; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }

  /** Số tiền còn phải trả sau khi trừ cọc. */
  public long getRemainingAmount() {
    return Math.max(0L, finalPrice - depositPaid);
  }
  public boolean getIsSecondOffer() {
    return this.isSecondOffer;
  }

  /**
   * Kiểm tra Winner đã quá hạn thanh toán chưa.
   *
   * <p>Đã thực hiện TODO (phân tách trách nhiệm): Logic scheduler không thuộc lớp Model.
   * Model chỉ cung cấp hàm query {@code isExpired()} thuần túy.
   * Scheduler (ví dụ: {@code ScheduledExecutorService} chạy mỗi 1 phút) nằm ở tầng
   * Service/infrastructure và sẽ:
   * <ol>
   * <li>Quét toàn bộ {@code AuctionWinner} có {@code paymentStatus == PENDING}.</li>
   * <li>Nếu {@code isExpired() == true} → gọi
   *     {@link com.group13.auction.service.PaymentService#expirePayment(com.group13.auction.model.auction.Auction)}
   *     để tịch thu cọc và kích hoạt luồng SecondChanceOffer.</li>
   * </ol>
   *
   * @return true nếu đã quá deadline và chưa thanh toán
   */
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(deadline)
            && paymentStatus == PaymentStatus.PENDING;
  }

  // Setter - chỉ PaymentService gọi

  public void setPaymentStatus(PaymentStatus paymentStatus) {
    this.paymentStatus = paymentStatus;
    markUpdated();
  }

  @Override
  public void printInfo() {
    System.out.println("THÔNG TIN WINNER CỦA PHIÊN");
    System.out.printf("Winner : %s%n", winner.getUsername());
    System.out.printf("Auction ID : %s%n", auctionId);
    System.out.printf("Giá cuối : %d%n", finalPrice);
    System.out.printf("Đã cọc : %d%n", depositPaid);
    System.out.printf("Còn lại : %d%n", getRemainingAmount());
    System.out.printf("Hạn TT : %s%n", deadline);
    System.out.printf("TT Status : %s%n", paymentStatus);
    System.out.println("======================================");
  }
}