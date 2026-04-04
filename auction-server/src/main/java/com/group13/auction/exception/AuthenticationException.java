package com.group13.auction.exception;

/** Ném khi xác thực tài khoản thất bại. */
public class AuthenticationException extends RuntimeException {

  public enum Reason {
    WRONG_PASSWORD,
    ACCOUNT_BANNED,
    ACCOUNT_SUSPENDED,
    INSUFFICIENT_RATING,
    /** Chưa gọi joinAuction cho phiên này — không được đặt giá. */
    NOT_JOINED_AUCTION
  }

  private final Reason reason;

  public AuthenticationException(Reason reason) {
    super(buildMessage(reason));
    this.reason = reason;
  }

  private static String buildMessage(Reason reason) {
    switch (reason) {
      case WRONG_PASSWORD:        return "Sai mật khẩu.";
      case ACCOUNT_BANNED:        return "Tài khoản đã bị khoá.";
      case ACCOUNT_SUSPENDED:     return "Tài khoản đang bị tạm ngưng.";
      case INSUFFICIENT_RATING:   return "Rating không đủ điều kiện tham gia.";
      case NOT_JOINED_AUCTION:    return "Bạn chưa tham gia phiên đấu giá này (hãy join trước).";
      default:                    return "Xác thực thất bại.";
    }
  }

  public Reason getReason() { return reason; }
}