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
 * <li>{@value #LEVEL_MASTER} — SystemAdmin duy nhất, được seed sẵn trong DB.
 * Không thể tạo thêm MASTER qua Factory. Toàn bộ automation thuộc về đây.</li>
 * <li>{@value #LEVEL_STAFF} — do SystemAdmin tạo ra qua AdminFactory; không tạo được admin khác.</li>
 * </ul>
 *
 * <p>Rating Admin luôn là 5.0 — không tăng, không giảm.
 *
 * <p>Staff Admin tự động là staffObserver trong AuctionManager.
 * SystemAdmin tự động là globalObserver.
 * Khi admin joinAuction sẽ nhận thêm notify theo phiên như watcher bình thường.
 */
public class Admin extends User {

  public static final String LEVEL_MASTER = "MASTER";
  public static final String LEVEL_STAFF = "STAFF";

  /** Rating cố định cho Admin — không thay đổi. */
  private static final double ADMIN_FIXED_RATING = 5.0;

  /**
   * Lý do ban tài khoản — chỉ giữ những lý do được dùng thực sự trong code.
   */
  public enum BanReason {
    /** Rating xuống dưới ngưỡng tối thiểu. */
    LOW_RATING,
    /** Seller không thanh toán thủ tục hoàn tiền cho winner. */
    SELLER_REFUND_DEFAULT
  }

  /**
   * Lý do hủy phiên — chỉ giữ những lý do được dùng thực sự trong code.
   */
  public enum CancelReason {
    /** Phiên kết thúc không có ai đặt giá. */
    NO_WINNER,
    /** Phiên kết thúc với giá cao nhất chưa đạt reserve price. */
    RESERVE_NOT_MET,
    /** Seller yêu cầu hủy — phải qua Staff Admin xem xét. */
    SELLER_REQUEST,
    /** Lỗi hệ thống. */
    SYSTEM_ERROR,
    /** Item gian lận hoặc hàng giả. */
    FRAUDULENT_ITEM
  }

  private final String adminLevel;
  private final List<String> actionLog;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh Admin mới (STAFF).
   * Chỉ được gọi từ AdminFactory — không được gọi trực tiếp.
   *
   * @param username tên đăng nhập
   * @param password mật khẩu thô
   * @param email email
   * @param adminLevel cấp độ quyền ({@value #LEVEL_STAFF})
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

  // ── Constructors, chỉ được new khi tạo SystemAdmin ────────────────────────

  public Admin(String username, String password, String email, String adminLevel) {
    super(username, password, email, UserRole.ADMIN);
    this.adminLevel = adminLevel;
    this.actionLog = new ArrayList<>();
    // Admin luôn được set rating = 5.0 ngay khi tạo
    super.adjustRating(ADMIN_FIXED_RATING - this.getRating());
  }

  public Admin(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
               String username, String hashedPassword, String email,
               AccountStatus accountStatus, double rating,
               String adminLevel, LocalDateTime suspendedAt) {
    super(id, createdAt, updatedAt, username, hashedPassword, email,
            UserRole.ADMIN, accountStatus, rating, suspendedAt);
    this.adminLevel = adminLevel;
    this.actionLog = new ArrayList<>();
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getAdminLevel() {
    return adminLevel;
  }

  /** @return true nếu đây là admin cấp MASTER (SystemAdmin). */
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

  /**
   * Rating Admin luôn cố định 5.0 — override để ngăn thay đổi.
   * Không tăng, không giảm, kệ nó.
   *
   * @param delta (bị bỏ qua)
   */
  @Override
  public void adjustRating(double delta) {
    // Admin rating không thay đổi — intentionally no-op
  }

  public boolean isSystem() { return false; }

  @Override
  public void printInfo() {
    System.out.println("=== ADMIN ====================================");
    System.out.printf("Username : %s%n", getUsername());
    System.out.printf("Email : %s%n", getEmail());
    System.out.printf("Admin level : %s%n", adminLevel);
    System.out.printf("Rating : %.1f (cố định)%n", getRating());
    System.out.printf("Status : %s%n", getAccountStatus());
    System.out.printf("Hành động : %d lần%n", actionLog.size());
    System.out.println("=============================================");
  }
}