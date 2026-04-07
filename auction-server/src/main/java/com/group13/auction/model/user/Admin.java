package com.group13.auction.model.user;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quản trị viên.
 *
 * <p>Phân cấp adminLevel:
 * <ul>
 *   <li>{@value #LEVEL_MASTER} — có thể tạo STAFF hoặc MASTER khác.
 *       Không còn là singleton đặc biệt; chỉ là user bình thường với level MASTER.</li>
 *   <li>{@value #LEVEL_STAFF} — do MASTER tạo ra; không tạo được admin khác.</li>
 * </ul>
 *
 * <p>Admin tự động là globalObserver trong AuctionManager.
 * Khi joinAuction sẽ nhận thêm notify theo phiên như watcher bình thường.
 */
public class Admin extends User {

  public static final String LEVEL_MASTER = "MASTER";
  public static final String LEVEL_STAFF  = "STAFF";

  public enum BanReason {
    LOW_RATING, PAYMENT_VIOLATION, FRAUDULENT_BIDDING, SPAM, OTHER
  }

  public enum CancelReason {
    FRAUDULENT_ITEM, SELLER_REQUEST, DISPUTE, SYSTEM_ERROR,
    /** Phiên kết thúc mà currentLeader chưa đạt reserve price. */
    RESERVE_NOT_MET,
    /** Phiên kết thúc không có ai đặt giá. */
    NO_WINNER,
    OTHER
  }

  private final String       adminLevel;
  private final List<String> actionLog;

  // ── Static factory methods ─────────────────────────────────────────────

  /**
   * Khai sinh Admin mới (STAFF hoặc MASTER).
   *
   * @param username   tên đăng nhập
   * @param password   mật khẩu thô
   * @param email      email
   * @param adminLevel cấp độ quyền ({@value #LEVEL_STAFF} hoặc {@value #LEVEL_MASTER})
   * @return Admin mới
   */
  public static Admin create(String username, String password, String email, String adminLevel) {
    return new Admin(username, password, email, adminLevel);
  }

  public static Admin reconstitute(String id, LocalDateTime createdAt,
                                   LocalDateTime updatedAt, String username, String hashedPassword,
                                   String email, AccountStatus accountStatus, double rating,
                                   String adminLevel, LocalDateTime suspendedAt) {
    return new Admin(id, createdAt, updatedAt, username, hashedPassword,
            email, accountStatus, rating, adminLevel, suspendedAt);
  }

  // ── Constructors, chỉ được new khi tạo SystemAdmin ────────────────────

  public Admin(String username, String password, String email, String adminLevel) {
    super(username, password, email, UserRole.ADMIN);
    this.adminLevel = adminLevel;
    this.actionLog  = new ArrayList<>();
  }

  public Admin(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                String username, String hashedPassword, String email,
                AccountStatus accountStatus, double rating,
                String adminLevel, LocalDateTime suspendedAt) {
    super(id, createdAt, updatedAt, username, hashedPassword, email,
            UserRole.ADMIN, accountStatus, rating, suspendedAt);
    this.adminLevel = adminLevel;
    this.actionLog  = new ArrayList<>();
  }

  // ── Getters ────────────────────────────────────────────────────────────

  public String getAdminLevel() {
    return adminLevel;
  }

  /** @return true nếu đây là admin cấp MASTER. */
  public boolean isMaster() {
    return LEVEL_MASTER.equals(adminLevel);
  }

  /** @return true nếu đây là admin cấp STAFF. */
  public boolean isStaff() {
    return LEVEL_STAFF.equals(adminLevel);
  }

  public List<String> getActionLog() {
    return Collections.unmodifiableList(actionLog);
  }

  public void addActionLog(String log) {
    actionLog.add(log);
  }

  public boolean isSystem() { return false; }
  @Override
  public void printInfo() {
    System.out.println("=== ADMIN ====================================");
    System.out.printf("Username    : %s%n", getUsername());
    System.out.printf("Email       : %s%n", getEmail());
    System.out.printf("Admin level : %s%n", adminLevel);
    System.out.printf("Rating      : %.1f%n", getRating());
    System.out.printf("Status      : %s%n", getAccountStatus());
    System.out.printf("Hành động   : %d lần%n", actionLog.size());
    System.out.println("=============================================");
  }
}