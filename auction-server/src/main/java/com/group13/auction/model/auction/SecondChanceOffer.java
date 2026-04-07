package com.group13.auction.model.auction;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/**
 * Ghi nhận người đứng thứ 2 và cơ hội mua thứ cấp.
 *
 * <p>Khi winner không thanh toán, hệ thống tạo SecondChanceOffer cho
 * runner-up (người bid cao thứ 2). Runner-up có 24h để quyết định mua
 * với giá của mình (runner-up bid price). Không mua thì KHÔNG bị trừ điểm.
 */
public class SecondChanceOffer extends Entity {

    public enum OfferStatus {
        PENDING,   // đang chờ runner-up quyết định
        ACCEPTED,  // runner-up chấp nhận, cần thanh toán
        DECLINED,  // runner-up từ chối
        EXPIRED    // hết 24h
    }

    private final NormalUser    runnerUp;
    private final String        auctionId;
    /** Giá runner-up đã bid — giá họ sẽ mua nếu accept. */
    private final double        offerPrice;
    private final LocalDateTime deadline;
    private       OfferStatus   status;

    // ── Static factory methods ─────────────────────────────────────────────

    /**
     * Khai sinh SecondChanceOffer khi winner không thanh toán.
     * Hạn quyết định = 24h từ lúc tạo.
     *
     * @param runnerUp   người bid cao thứ 2
     * @param auctionId  id phiên
     * @param offerPrice giá runner-up đã bid
     * @return SecondChanceOffer mới
     */
    public static SecondChanceOffer create(NormalUser runnerUp,
                                           String auctionId, double offerPrice) {
        return new SecondChanceOffer(runnerUp, auctionId, offerPrice);
    }

    public static SecondChanceOffer reconstitute(String id, LocalDateTime createdAt,
                                                 LocalDateTime updatedAt, NormalUser runnerUp, String auctionId,
                                                 double offerPrice, LocalDateTime deadline, OfferStatus status) {
        return new SecondChanceOffer(id, createdAt, updatedAt,
                runnerUp, auctionId, offerPrice, deadline, status);
    }

    // ── Private constructors ───────────────────────────────────────────────

    private SecondChanceOffer(NormalUser runnerUp, String auctionId, double offerPrice) {
        super();
        this.runnerUp   = runnerUp;
        this.auctionId  = auctionId;
        this.offerPrice = offerPrice;
        this.deadline   = LocalDateTime.now().plusHours(24);
        this.status     = OfferStatus.PENDING;
    }

    private SecondChanceOffer(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                              NormalUser runnerUp, String auctionId, double offerPrice,
                              LocalDateTime deadline, OfferStatus status) {
        super(id, createdAt, updatedAt);
        this.runnerUp   = runnerUp;
        this.auctionId  = auctionId;
        this.offerPrice = offerPrice;
        this.deadline   = deadline;
        this.status     = status;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public NormalUser    getRunnerUp()   { return runnerUp; }
    public String        getAuctionId()  { return auctionId; }
    public double        getOfferPrice() { return offerPrice; }
    public LocalDateTime getDeadline()   { return deadline; }
    public OfferStatus   getStatus()     { return status; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(deadline) && status == OfferStatus.PENDING;
    }

    // ── Setter — chỉ PaymentService / AuctionService gọi ──────────────────

    public void setStatus(OfferStatus status) {
        this.status = status;
        markUpdated();
    }

    @Override
    public void printInfo() {
        System.out.println("=== SECOND CHANCE OFFER ==============");
        System.out.printf("Runner-up : %s%n", runnerUp.getUsername());
        System.out.printf("Auction ID: %s%n", auctionId);
        System.out.printf("Giá mua   : %.0f%n", offerPrice);
        System.out.printf("Hạn chót  : %s%n", deadline);
        System.out.printf("Trạng thái: %s%n", status);
        System.out.println("======================================");
    }
}