package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Đề nghị mua thứ cấp cho runner-up khi winner không thanh toán.
 *
 * <p>Runner-up có 24h để quyết định chấp nhận hay từ chối. Nếu chấp nhận, sẽ kích hoạt luồng giao
 * dịch tương tự winner ban đầu.
 */
public class SecondChanceOffer extends Entity {

  private static final Logger log = LoggerFactory.getLogger(SecondChanceOffer.class);

  public enum OfferStatus {
    PENDING, // chờ runner-up quyết định
    ACCEPTED, // runner-up chấp nhận -> kích hoạt giao dịch
    DECLINED, // runner-up từ chối
    EXPIRED // hết 24h
  }

  private final NormalUser runnerUp;
  private final String auctionId;

  /** Giá mua = giá bid cao nhất của runner-up (không phải giá winner). */
  private final long offerPrice;

  private final long depositPaid;
  private final LocalDateTime deadline;
  private OfferStatus status;

  // Static factory methods

  /**
   * Khai sinh SecondChanceOffer sau khi winner không thanh toán.
   *
   * @param runnerUp runner-up nhận đề nghị
   * @param auctionId id phiên
   * @param offerPrice giá runner-up đã bid
   * @param depositPaid cọc runner-up đã đặt khi joinAuction
   * @return SecondChanceOffer mới
   */
  public static SecondChanceOffer create(
      NormalUser runnerUp, String auctionId, long offerPrice, long depositPaid) {
    return new SecondChanceOffer(runnerUp, auctionId, offerPrice, depositPaid);
  }

  public static SecondChanceOffer reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      NormalUser runnerUp,
      String auctionId,
      long offerPrice,
      long depositPaid,
      LocalDateTime deadline,
      OfferStatus status) {
    return new SecondChanceOffer(
        id, createdAt, updatedAt, runnerUp, auctionId, offerPrice, depositPaid, deadline, status);
  }

  // Private constructors

  private SecondChanceOffer(
      NormalUser runnerUp, String auctionId, long offerPrice, long depositPaid) {
    super();
    this.runnerUp = runnerUp;
    this.auctionId = auctionId;
    this.offerPrice = offerPrice;
    this.depositPaid = depositPaid;
    this.deadline = LocalDateTime.now().plusHours(24);
    this.status = OfferStatus.PENDING;
  }

  private SecondChanceOffer(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      NormalUser runnerUp,
      String auctionId,
      long offerPrice,
      long depositPaid,
      LocalDateTime deadline,
      OfferStatus status) {
    super(id, createdAt, updatedAt);
    this.runnerUp = runnerUp;
    this.auctionId = auctionId;
    this.offerPrice = offerPrice;
    this.depositPaid = depositPaid;
    this.deadline = deadline;
    this.status = status;
  }

  // Getters

  public NormalUser getRunnerUp() {
    return runnerUp;
  }

  public String getAuctionId() {
    return auctionId;
  }

  public long getOfferPrice() {
    return offerPrice;
  }

  public long getDepositPaid() {
    return depositPaid;
  }

  public LocalDateTime getDeadline() {
    return deadline;
  }

  public OfferStatus getStatus() {
    return status;
  }

  /** Số tiền còn phải trả nếu chấp nhận (offerPrice - deposit đã khóa). */
  public long getRemainingAmount() {
    return Math.max(0L, offerPrice - depositPaid);
  }

  /**
   * Kiểm tra đề nghị đã hết hạn chưa.
   *
   * <p>Đã thực hiện TODO (phân tách trách nhiệm): Scheduler không thuộc Model. Scheduler (ví dụ:
   * {@code ScheduledExecutorService} chạy mỗi 1 phút) nằm ở tầng Service/infrastructure và sẽ:
   *
   * <ol>
   *   <li>Quét toàn bộ {@code SecondChanceOffer} có {@code status == PENDING}.
   *   <li>Nếu {@code isExpired() == true} -> set {@code status = EXPIRED} và persist xuống DB qua
   *       {@code SecondChanceOfferDAO.updateOfferStatus()}.
   *   <li>Sau đó cancel auction (no-winner) nếu không còn runner-up nào khác.
   * </ol>
   */
  public boolean isExpired() {
    // (TODO cũ "notificationDao.save()" đã xóa: side effect notification thuộc Service layer,
    // không thuộc Model. Đã có PaymentService.expireSecondChanceOfferIfDue() + persist DB.)
    return LocalDateTime.now().isAfter(deadline) && status == OfferStatus.PENDING;
  }

  // Setter - chỉ PaymentService / AuctionService gọi

  public void setStatus(OfferStatus status) {
    this.status = status;
    markUpdated();
  }

  @Override
  public void printInfo() {
    log.info("CƠ HỘI THỨ HAI DÀNH CHO RUNNER-UP");
    log.info("Runner-up  : {}", runnerUp.getUsername());
    log.info("Auction ID : {}", auctionId);
    log.info("Giá mua    : {}", offerPrice);
    log.info("Đã cọc     : {}", depositPaid);
    log.info("Còn lại    : {}", getRemainingAmount());
    log.info("Hạn chót   : {}", deadline);
    log.info("Trạng thái : {}", status);
    log.info("======================================");
  }
}