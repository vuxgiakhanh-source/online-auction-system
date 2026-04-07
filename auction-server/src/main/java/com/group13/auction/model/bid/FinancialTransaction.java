package com.group13.auction.model.bid;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/**
 * Ghi lại một giao dịch tài chính trong hệ thống.
 *
 * <p>Dùng để theo dõi dòng tiền: winner → SystemBank → seller (sau thuế).
 */
public class FinancialTransaction extends Entity {

    public enum TransactionType {
        DEPOSIT_LOCK,       // khóa tiền cọc
        DEPOSIT_UNLOCK,     // hoàn cọc
        DEPOSIT_TO_SELLER,  // cộng cọc vào seller (winner)
        PAYMENT_FROM_WINNER,// winner trả phần còn lại
        TAX_COLLECTED,      // thuế thu vào ngân hàng hệ thống
        PAYOUT_TO_SELLER,   // hệ thống chuyển tiền (sau thuế) cho seller
        REFUND_TO_WINNER    // hệ thống hoàn tiền cho winner (khi seller vi phạm)
    }

    private final String          fromUserId;
    private final String          toUserId;
    private final double          amount;
    private final TransactionType type;
    private final String          auctionId;

    // ── Static factory method ──────────────────────────────────────────────

    public static FinancialTransaction create(String fromUserId, String toUserId,
                                              double amount, TransactionType type,
                                              String auctionId) {
        return new FinancialTransaction(fromUserId, toUserId, amount, type, auctionId);
    }

    public static FinancialTransaction reconstitute(String id, LocalDateTime createdAt,
                                                    LocalDateTime updatedAt, String fromUserId, String toUserId,
                                                    double amount, TransactionType type, String auctionId) {
        return new FinancialTransaction(id, createdAt, updatedAt,
                fromUserId, toUserId, amount, type, auctionId);
    }

    private FinancialTransaction(String fromUserId, String toUserId,
                                 double amount, TransactionType type, String auctionId) {
        super();
        this.fromUserId = fromUserId;
        this.toUserId   = toUserId;
        this.amount     = amount;
        this.type       = type;
        this.auctionId  = auctionId;
    }

    private FinancialTransaction(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                                 String fromUserId, String toUserId, double amount,
                                 TransactionType type, String auctionId) {
        super(id, createdAt, updatedAt);
        this.fromUserId = fromUserId;
        this.toUserId   = toUserId;
        this.amount     = amount;
        this.type       = type;
        this.auctionId  = auctionId;
    }

    public String          getFromUserId() { return fromUserId; }
    public String          getToUserId()   { return toUserId; }
    public double          getAmount()     { return amount; }
    public TransactionType getType()       { return type; }
    public String          getAuctionId()  { return auctionId; }

    @Override
    public void printInfo() {
        System.out.printf("[FIN-TX] %s | %.0f | %s → %s | Auction: %s%n",
                type, amount, fromUserId, toUserId, auctionId);
    }
}