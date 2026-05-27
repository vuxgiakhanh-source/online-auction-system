package com.group13.auction.viewmodel.admin;

/** View model cho một bản ghi tài khoản đang bị khóa (bảng {@code account_bans} active). */
public class AccountBanViewModel {

  private final String userId;
  private final String username;
  private final String email;
  private final String reason;
  private final String bannedBy;
  private final String bannedAt;

  public AccountBanViewModel(
      String userId,
      String username,
      String email,
      String reason,
      String bannedBy,
      String bannedAt) {
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.reason = reason;
    this.bannedBy = bannedBy;
    this.bannedAt = bannedAt;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getReason() {
    return reason;
  }

  public String getBannedBy() {
    return bannedBy;
  }

  public String getBannedAt() {
    return bannedAt;
  }
}
