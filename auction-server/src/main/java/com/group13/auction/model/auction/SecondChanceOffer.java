package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/**
 * Đề nghị mua thứ cấp cho runner-up khi winner không thanh toán.
 *
 * <p>Runner-up có 24h để quyết định chấp nhận hay từ chối.
 * Nếu chấp nhận, sẽ kích hoạt luồng giao dịch tương tự winner ban đầu.
 */
public class SecondChanceOffer extends Entity {

    public enum OfferStatus {
        PENDING, // chờ runner-up quyết định
        ACCEPTED, // runner-up chấp nhận -> kích hoạt giao dịch
        DECLINED, // runner-up từ chối
        EXPIRED // hết 24h
    }

    private final NormalUser runnerUp;
    private final String auctionId;
    /** Giá mua = giá bid cao nhất của runner-up (không phải giá winner). */
    private final double offerPrice;
    private final double depositPaid;
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
    public static SecondChanceOffer create(NormalUser runnerUp,
                                           String auctionId, double offerPrice, double depositPaid) {
        return new SecondChanceOffer(runnerUp, auctionId, offerPrice, depositPaid);
    }

    public static SecondChanceOffer reconstitute(String id, LocalDateTime createdAt,
                                                 LocalDateTime updatedAt, NormalUser runnerUp, String auctionId,
                                                 double offerPrice, double depositPaid, LocalDateTime deadline, OfferStatus status) {
        return new SecondChanceOffer(id, createdAt, updatedAt,
                runnerUp, auctionId, offerPrice, depositPaid, deadline, status);
    }

    // Private constructors

    private SecondChanceOffer(NormalUser runnerUp, String auctionId,
                              double offerPrice, double depositPaid) {
        super();
        this.runnerUp = runnerUp;
        this.auctionId = auctionId;
        this.offerPrice = offerPrice;
        this.depositPaid = depositPaid;
        this.deadline = LocalDateTime.now().plusHours(24);
        this.status = OfferStatus.PENDING;
    }

    private SecondChanceOffer(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                              NormalUser runnerUp, String auctionId, double offerPrice, double depositPaid,
                              LocalDateTime deadline, OfferStatus status) {
        super(id, createdAt, updatedAt);
        this.runnerUp = runnerUp;
        this.auctionId = auctionId;
        this.offerPrice = offerPrice;
        this.depositPaid = depositPaid;
        this.deadline = deadline;
        this.status = status;
    }

    // Getters

    public NormalUser getRunnerUp() { return runnerUp; }
    public String getAuctionId() { return auctionId; }
    public double getOfferPrice() { return offerPrice; }
    public double getDepositPaid() { return depositPaid; }
    public LocalDateTime getDeadline() { return deadline; }
    public OfferStatus getStatus() { return status; }

    /** Số tiền còn phải trả nếu chấp nhận (offerPrice - deposit đã khóa). */
    public double getRemainingAmount() {
        return Math.max(0, offerPrice - depositPaid);
    }

    /** TODO: Xây dựng scheduler quét định kỳ toàn bộ SecondChanceOffer có
     * {@code status == PENDING}.
     * sau 24h -> chuyển trạng thái sang EXPIRED
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(deadline) && status == OfferStatus.PENDING;
    }

    // Setter - chỉ PaymentService / AuctionService gọi

    public void setStatus(OfferStatus status) {
        this.status = status;
        markUpdated();
    }

    @Override
    public void printInfo() {
        System.out.println("CƠ HỘI THỨ HAI DÀNH CHO RUNNER-UP");
        System.out.printf("Runner-up : %s%n", runnerUp.getUsername());
        System.out.printf("Auction ID: %s%n", auctionId);
        System.out.printf("Giá mua : %.0f%n", offerPrice);
        System.out.printf("Đã cọc : %.0f%n", depositPaid);
        System.out.printf("Còn lại : %.0f%n", getRemainingAmount());
        System.out.printf("Hạn chót : %s%n", deadline);
        System.out.printf("Trạng thái: %s%n", status);
        System.out.println("======================================");
    }
}