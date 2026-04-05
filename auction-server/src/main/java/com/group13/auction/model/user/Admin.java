package com.group13.auction.model.user;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Quản trị viên — chỉ lưu data. */
public class Admin extends User {

  public enum BanReason {
    LOW_RATING, PAYMENT_VIOLATION, FRAUDULENT_BIDDING, SPAM, OTHER
  }

  public enum CancelReason {
    FRAUDULENT_ITEM, SELLER_REQUEST, DISPUTE, SYSTEM_ERROR, OTHER
  }

  private final int adminLevel;
  private final List<String> actionLog;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh Admin mới.
   *
   * @param username   tên đăng nhập
   * @param password   mật khẩu thô
   * @param email      email
   * @param adminLevel cấp độ quyền (1 = thấp nhất)
   * @return Admin mới
   */
  public static Admin create(String username, String password,
      String email, int adminLevel) {
    return new Admin(username, password, email, adminLevel);
  }

  /**
   * Hồi sinh Admin từ DB — chỉ DAO được gọi method này.
   *
   * @param id             id gốc
   * @param createdAt      thời gian tạo gốc
   * @param updatedAt      thời gian cập nhật gốc
   * @param username       tên đăng nhập
   * @param hashedPassword password đã hash
   * @param email          email
   * @param accountStatus  trạng thái tài khoản
   * @param rating         rating
   * @param adminLevel     cấp độ quyền
   * @return Admin được phục hồi
   */
  protected static Admin reconstitute(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, String username, String hashedPassword,
      String email, AccountStatus accountStatus, double rating,
      int adminLevel) {
    return new Admin(id, createdAt, updatedAt, username, hashedPassword,
        email, accountStatus, rating, adminLevel);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Admin(String username, String password,
      String email, int adminLevel) {
    super(username, password, email, UserRole.ADMIN);
    this.adminLevel = adminLevel;
    this.actionLog = new ArrayList<>();
  }

  private Admin(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String username, String hashedPassword, String email,
      AccountStatus accountStatus, double rating, int adminLevel) {
    super(id, createdAt, updatedAt, username, hashedPassword, email,
        UserRole.ADMIN, accountStatus, rating);
    this.adminLevel = adminLevel;
    this.actionLog = new ArrayList<>();
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public int getAdminLevel() { return adminLevel; }

  public List<String> getActionLog() {
    return Collections.unmodifiableList(actionLog);
  }

  // ── Setter — chỉ AdminService gọi ─────────────────────────────────────────

  public void addActionLog(String log) {
    actionLog.add(log);
  }

  @Override
  public void printInfo() {
    System.out.println("=== ADMIN ============================");
    System.out.printf("Username    : %s%n", getUsername());
    System.out.printf("Email       : %s%n", getEmail());
    System.out.printf("Admin level : %d%n", adminLevel);
    System.out.printf("Rating      : %.1f%n", getRating());
    System.out.printf("Status      : %s%n", getAccountStatus());
    System.out.printf("Hành động   : %d lần%n", actionLog.size());
    System.out.println("======================================");
  }
}