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
  /** Hạn thanh toán — 24h kể từ khi auction FINISHED. */
  private final LocalDateTime paymentDeadline;
  private PaymentStatus paymentStatus;

  /**
   * Hạn để winner bấm "Nhận hàng" - 7 ngày kể từ khi thanh toán thành công (FUNDS_HELD).
   * {@code null} cho đến khi thanh toán xong.
   * Hết hạn -> hệ thống tự chuyển tiền cho Seller.
   */
  private LocalDateTime confirmReceiptDeadline;

  /**
   * Hạn để winner gửi report - 3 ngày kể từ khi bấm "Nhận hàng".
   * {@code null} cho đến khi winner xác nhận nhận hàng.
   * Hết hạn -> hệ thống tự chuyển tiền cho Seller.
   */
  private LocalDateTime reportDeadline;

  private boolean isSecondOffer;

  // Static factory methods

  /**
   * Khai sinh AuctionWinner ngay khi auction FINISHED.
   * Hạn thanh toán = 24h từ lúc tạo.
   *
   * @param winner        người thắng
   * @param auctionId     id phiên đấu giá
   * @param finalPrice    giá cuối cùng
   * @param depositPaid   số tiền cọc đã đặt
   * @param isSecondOffer true nếu là second-chance offer
   * @return AuctionWinner mới
   */
  public static AuctionWinner create(
          NormalUser winner,
          String auctionId,
          long finalPrice,
          long depositPaid,
          boolean isSecondOffer) {
    return new AuctionWinner(winner, auctionId, finalPrice, depositPaid, isSecondOffer);
  }

  /**
   * Hồi sinh AuctionWinner từ DB — chỉ DAO được gọi method này.
   */
  public static AuctionWinner reconstitute(
          String id,
          LocalDateTime createdAt,
          LocalDateTime updatedAt,
          NormalUser winner,
          String auctionId,
          long finalPrice,
          long depositPaid,
          LocalDateTime paymentDeadline,
          LocalDateTime confirmReceiptDeadline,
          LocalDateTime reportDeadline,
          PaymentStatus paymentStatus,
          boolean isSecondOffer) {
    return new AuctionWinner(id, createdAt, updatedAt, winner, auctionId,
            finalPrice, depositPaid, paymentDeadline, confirmReceiptDeadline,
            reportDeadline, paymentStatus, isSecondOffer);
  }

  // Private constructors

  private AuctionWinner(
          NormalUser winner,
          String auctionId,
          long finalPrice,
          long depositPaid,
          boolean isSecondOffer) {
    super();
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.depositPaid = depositPaid;
    this.paymentDeadline = LocalDateTime.now().plusHours(24);
    this.confirmReceiptDeadline = null;
    this.reportDeadline = null;
    this.paymentStatus = PaymentStatus.PENDING;
    this.isSecondOffer = isSecondOffer;
  }

  private AuctionWinner(
          String id,
          LocalDateTime createdAt,
          LocalDateTime updatedAt,
          NormalUser winner,
          String auctionId,
          long finalPrice,
          long depositPaid,
          LocalDateTime paymentDeadline,
          LocalDateTime confirmReceiptDeadline,
          LocalDateTime reportDeadline,
          PaymentStatus paymentStatus,
          boolean isSecondOffer) {
    super(id, createdAt, updatedAt);
    this.winner = winner;
    this.auctionId = auctionId;
    this.finalPrice = finalPrice;
    this.depositPaid = depositPaid;
    this.paymentDeadline = paymentDeadline;
    this.confirmReceiptDeadline = confirmReceiptDeadline;
    this.reportDeadline = reportDeadline;
    this.paymentStatus = paymentStatus;
    this.isSecondOffer = isSecondOffer;
  }

  // Getters

  public NormalUser getWinner() { return winner; }
  public String getAuctionId() { return auctionId; }
  public long getFinalPrice() { return finalPrice; }
  public long getDepositPaid() { return depositPaid; }
  public LocalDateTime getPaymentDeadline() { return paymentDeadline; }
  public LocalDateTime getConfirmReceiptDeadline() { return confirmReceiptDeadline; }
  public LocalDateTime getReportDeadline() { return reportDeadline; }
  public PaymentStatus getPaymentStatus() { return paymentStatus; }
  public boolean getIsSecondOffer() { return isSecondOffer; }

  /** Số tiền còn phải trả sau khi trừ cọc. */
  public long getRemainingAmount() {
    return Math.max(0L, finalPrice - depositPaid);
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
    return LocalDateTime.now().isAfter(paymentDeadline)
            && paymentStatus == PaymentStatus.PENDING;
  }

  /**
   * Kiểm tra Winner đã quá hạn xác nhận nhận hàng chưa (7 ngày sau thanh toán).
   * Hết hạn -> hệ thống tự giải ngân cho Seller.
   *
   * @return true nếu đã quá confirmReceiptDeadline và tiền vẫn đang FUNDS_HELD
   */
  public boolean isConfirmReceiptOverdue() {
    return paymentStatus == PaymentStatus.FUNDS_HELD
            && confirmReceiptDeadline != null
            && LocalDateTime.now().isAfter(confirmReceiptDeadline);
  }

  /**
   * Kiểm tra Winner đã quá hạn report chưa (3 ngày sau bấm nhận hàng).
   * Hết hạn → hệ thống tự giải ngân cho Seller.
   *
   * @return true nếu đã quá reportDeadline và tiền vẫn đang FUNDS_HELD
   */
  public boolean isReportDeadlineOverdue() {
    return paymentStatus == PaymentStatus.FUNDS_HELD
            && reportDeadline != null
            && LocalDateTime.now().isAfter(reportDeadline);
  }

  // Setter - chỉ PaymentService gọi

  public void setPaymentStatus(PaymentStatus paymentStatus) {
    this.paymentStatus = paymentStatus;
    markUpdated();
  }

  /**
   * Đánh dấu winner đã thanh toán; kích hoạt đếm 7 ngày "nhận hàng".
   * Chỉ {@link com.group13.auction.service.PaymentService} gọi sau khi tiền vào SystemBank.
   */
  public void markFundsHeld() {
    this.paymentStatus = PaymentStatus.FUNDS_HELD;
    this.confirmReceiptDeadline = LocalDateTime.now().plusDays(7);
    markUpdated();
    // TODO: [DB] auctionWinnerDAO.updateFundsHeld(id, paymentStatus, confirmReceiptDeadline)
  }

  /**
   * Winner bấm "Nhận hàng"; kích hoạt đếm 3 ngày cho phép report.
   * Chỉ {@link com.group13.auction.service.PaymentService} gọi.
   */
  public void confirmReceipt() {
    this.reportDeadline = LocalDateTime.now().plusDays(3);
    markUpdated();
    // TODO: [DB] auctionWinnerDAO.updateReportDeadline(id, reportDeadline)
  }

  @Override
  public void printInfo() {
    System.out.println("THÔNG TIN WINNER CỦA PHIÊN");
    System.out.printf("Winner : %s%n", winner.getUsername());
    System.out.printf("Auction ID : %s%n", auctionId);
    System.out.printf("Giá cuối : %d%n", finalPrice);
    System.out.printf("Đã cọc : %d%n", depositPaid);
    System.out.printf("Còn lại : %d%n", getRemainingAmount());
    System.out.printf("Hạn TT : %s%n", getPaymentDeadline());
    System.out.printf("TT Status : %s%n", paymentStatus);
    System.out.println("======================================");
  }
}