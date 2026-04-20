package com.group13.auction.common.dto.core;

/**
 * DTO lỗi nghiệp vụ — payload của mọi packet {@code _FAILED} và {@code SYSTEM_ERROR}.
 */
public class ErrorDTO {

    /** Mã lỗi định danh (ví dụ: "INSUFFICIENT_BALANCE", "AUCTION_CLOSED"). */
    private String code;

    /** Thông điệp hiển thị cho người dùng. */
    private String message;

    /** requestId của packet gây ra lỗi (để client match). */
    private String requestId;

    public ErrorDTO() {}

    public ErrorDTO(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorDTO(String code, String message, String requestId) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
    }

    // ── Static factory ────────────────────────────────────────────────────────

    public static ErrorDTO of(String code, String message) {
        return new ErrorDTO(code, message);
    }

    public static ErrorDTO of(String code, String message, String requestId) {
        return new ErrorDTO(code, message, requestId);
    }

    // ── Common error codes ────────────────────────────────────────────────────

    public static final String INSUFFICIENT_BALANCE    = "INSUFFICIENT_BALANCE";
    public static final String AUCTION_CLOSED          = "AUCTION_CLOSED";
    public static final String AUCTION_NOT_FOUND       = "AUCTION_NOT_FOUND";
    public static final String BID_TOO_LOW             = "BID_TOO_LOW";
    public static final String NOT_JOINED_AUCTION      = "NOT_JOINED_AUCTION";
    public static final String ALREADY_JOINED          = "ALREADY_JOINED";
    public static final String ACCOUNT_BANNED          = "ACCOUNT_BANNED";
    public static final String ACCOUNT_SUSPENDED       = "ACCOUNT_SUSPENDED";
    public static final String SELLER_CANNOT_BID_OWN  = "SELLER_CANNOT_BID_OWN";
    public static final String USER_NOT_FOUND          = "USER_NOT_FOUND";
    public static final String INVALID_AMOUNT          = "INVALID_AMOUNT";
    public static final String PAYMENT_EXPIRED         = "PAYMENT_EXPIRED";
    public static final String PAYMENT_ALREADY_DONE    = "PAYMENT_ALREADY_DONE";
    public static final String REPORT_NOT_PENDING      = "REPORT_NOT_PENDING";
    public static final String ALREADY_RATED           = "ALREADY_RATED";
    public static final String UNAUTHORIZED            = "UNAUTHORIZED";
    public static final String INTERNAL_ERROR          = "INTERNAL_ERROR";
    public static final String VALIDATION_ERROR        = "VALIDATION_ERROR";
    public static final String SELLER_ROLE_REQUIRED    = "SELLER_ROLE_REQUIRED";
    public static final String DUPLICATE_USERNAME      = "DUPLICATE_USERNAME";
    public static final String DUPLICATE_EMAIL         = "DUPLICATE_EMAIL";
    public static final String WRONG_PASSWORD          = "WRONG_PASSWORD";
    public static final String RESERVE_NOT_MET         = "RESERVE_NOT_MET";
    public static final String AUTO_BID_NOT_FOUND      = "AUTO_BID_NOT_FOUND";
    public static final String MAX_BID_TOO_LOW         = "MAX_BID_TOO_LOW";
    public static final String SECOND_CHANCE_EXPIRED   = "SECOND_CHANCE_EXPIRED";
    public static final String SELLER_OWNS_AUCTION     = "SELLER_OWNS_AUCTION";
    public static final String BALANCE_NOT_ZERO        = "BALANCE_NOT_ZERO";
    public static final String ACTIVE_AUCTION_EXISTS   = "ACTIVE_AUCTION_EXISTS";

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    @Override
    public String toString() {
        return "ErrorDTO{code='" + code + "', message='" + message + "'}";
    }
}