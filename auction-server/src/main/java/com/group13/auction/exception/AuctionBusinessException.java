package com.group13.auction.exception;

/* Ném khi vi phạm các logic nghiệp vụ */
public class AuctionBusinessException extends RuntimeException {

    public enum Reason {
        /** Chưa gọi joinAuction cho phiên này - không được đặt giá. */
        NOT_JOINED_AUCTION,
        /** Tài khoản chưa đủ số dư cọc để tham gia phiên. */
        INSUFFICIENT_DEPOSIT,
        /** Seller không được tự đấu giá món hàng của chính mình. */
        SELLER_CANNOT_BID_OWN_ITEM,
        /** User đã từng rời phiên này — không được tham gia lại. */
        ALREADY_LEFT_AUCTION
    }

    private final Reason reason;

    public AuctionBusinessException(Reason reason) {
        super(buildMessage(reason));
        this.reason = reason;
    }

    private static String buildMessage(Reason reason) {
        switch (reason) {
            case NOT_JOINED_AUCTION:
                return "Bạn chưa tham gia phiên đấu này (hãy join trước).";
            case INSUFFICIENT_DEPOSIT:
                return "Số dư không đủ để đặt cọc (cần ít nhất 30% giá khởi điểm).";
            case SELLER_CANNOT_BID_OWN_ITEM:
                return "Seller không được tự đấu giá món hàng của chính mình.";
            case ALREADY_LEFT_AUCTION:
                return "Bạn đã rời phiên đấu giá này và không thể tham gia lại.";
            default:
                return "Vi phạm logic hệ thống.";
        }
    }

    public Reason getReason() { return reason; }
}