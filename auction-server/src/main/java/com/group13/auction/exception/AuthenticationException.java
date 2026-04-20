package com.group13.auction.exception;

/** Ném khi xác thực tài khoản thất bại. */
public class AuthenticationException extends RuntimeException {

  public enum Reason {
    WRONG_PASSWORD,
    ACCOUNT_BANNED,
    ACCOUNT_SUSPENDED,
    INSUFFICIENT_RATING,
    USER_NOT_FOUND
  }

  private final Reason reason;

  public AuthenticationException(Reason reason) {
    super(buildMessage(reason));
    this.reason = reason;
  }

  private static String buildMessage(Reason reason) {
    switch (reason) {
      case WRONG_PASSWORD:
        return "Sai mật khẩu.";
      case ACCOUNT_BANNED:
        return "Tài khoản đã bị khoá.";
      case ACCOUNT_SUSPENDED:
        return "Tài khoản đang bị tạm ngưng.";
      case INSUFFICIENT_RATING:
        return "Rating không đủ điều kiện tham gia.";
      case USER_NOT_FOUND:
        return "Không tìm thấy tài khoản";
      default:
        return "Xác thực thất bại.";
    }
  }

  public Reason getReason() { return reason; }
}