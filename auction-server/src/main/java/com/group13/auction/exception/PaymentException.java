package com.group13.auction.exception;

/**
 * Ném khi thanh toán không hợp lệ.
 * VD: số tiền không đủ, thanh toán sai giai đoạn, hoặc seller
 * không hoàn trả đúng hạn.
 */
public class PaymentException extends RuntimeException {

    public enum Reason {
        INSUFFICIENT_BALANCE,
        WRONG_AMOUNT,
        PAYMENT_EXPIRED,
        SELLER_REFUND_OVERDUE
    }

    private final Reason reason;

    public PaymentException(Reason reason, String detail) {
        super(buildMessage(reason) + " — " + detail);
        this.reason = reason;
    }

    private static String buildMessage(Reason reason) {
        switch (reason) {
            case INSUFFICIENT_BALANCE:    return "Số dư không đủ.";
            case WRONG_AMOUNT:            return "Số tiền thanh toán không đúng.";
            case PAYMENT_EXPIRED:         return "Đã quá hạn thanh toán.";
            case SELLER_REFUND_OVERDUE:   return "Seller không hoàn trả đúng hạn.";
            default:                      return "Lỗi thanh toán.";
        }
    }

    public Reason getReason() { return reason; }
}
