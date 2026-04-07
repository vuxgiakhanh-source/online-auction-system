package com.group13.auction.model.user;

import com.group13.auction.model.entity.Entity;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Lớp abstract người dùng — chỉ lưu data, không chứa nghiệp vụ.
 *
 * <p>Rating được quản lý hoàn toàn bởi {@link com.group13.auction.service.RatingService}.
 * Không có setter public cho rating.
 * Chỉ {@code RatingService} mới được điều chỉnh rating qua
 * {@code adjustRating(double)}.
 *
 * <p>Một User bình thường (non-admin) có thể mang nhiều role:
 * BIDDER và SELLER cùng lúc (xem {@link #addRole}).
 * Admin không được mang thêm role khác.
 */
public abstract class User extends Entity {

  public enum UserRole { BIDDER, SELLER, ADMIN }
  public enum AccountStatus { ACTIVE, BANNED, SUSPENDED }

  private static final double RATING_DEFAULT = 3.0;
  private static final double RATING_MIN = 0.0;
  private static final double RATING_MAX = 5.0;

  /**
   * Ngưỡng rating bị đình chỉ tự động.
   * Khi rating <= ngưỡng này tài khoản chuyển sang SUSPENDED.
   */
  public static final double RATING_SUSPEND_THRESHOLD = 1.5;

  private final String username;
  private final String hashedPassword;
  private final String email;
  private UserRole primaryRole;
  private AccountStatus accountStatus;
  private double rating;

  /**
   * Thời điểm tài khoản bị đình chỉ gần nhất.
   * Dùng để tính 6 tháng auto-restore rating.
   * null nếu chưa từng bị suspend.
   */
  private LocalDateTime suspendedAt;

  // ── Constructor khai sinh ──────────────────────────────────────────────────

  /** Khai sinh — hash password ngay tại đây, rating mặc định 3.0. */
  protected User(String username, String password,
                 String email, UserRole role) {
    super();
    this.username = username;
    this.hashedPassword = hashPassword(password);
    this.email = email;
    this.primaryRole = role;
    this.accountStatus = AccountStatus.ACTIVE;
    this.rating = RATING_DEFAULT;
    this.suspendedAt = null;
  }

  // ── Constructor hồi sinh ──────────────────────────────────────────────────

  /**
   * Hồi sinh từ DB — password đã hash, không hash lại.
   * Chỉ DAO gọi thông qua {@code reconstitute()} của lớp con.
   */
  protected User(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                 String username, String hashedPassword, String email,
                 UserRole role, AccountStatus accountStatus, double rating,
                 LocalDateTime suspendedAt) {
    super(id, createdAt, updatedAt);
    this.username = username;
    this.hashedPassword = hashedPassword;
    this.email = email;
    this.primaryRole = role;
    this.accountStatus = accountStatus;
    this.rating = rating;
    this.suspendedAt = suspendedAt;
  }

  // ── Hash utility ───────────────────────────────────────────────────────────

  /** Hash mật khẩu SHA-256. Public vì UserService dùng khi verify login. */
  public static String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(password.getBytes());
      StringBuilder hex = new StringBuilder();
      for (byte b : bytes) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 không khả dụng.", e);
    }
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public UserRole getPrimaryRole() { return primaryRole; }
  public AccountStatus getAccountStatus() { return accountStatus; }
  public double getRating() { return rating; }
  public String getHashedPassword() { return hashedPassword; }
  public LocalDateTime getSuspendedAt() { return suspendedAt; }

  /**
   * Kiểm tra user có role cụ thể không.
   * Admin chỉ có role ADMIN. Bidder/Seller có thể có cả hai.
   */
  public boolean hasRole(UserRole role) {
    if (primaryRole == role) return true;
    // Bidder có thể được thêm role SELLER và ngược lại
    return false; // override trong subclass nếu cần
  }

  // ── Setter AccountStatus — chỉ AccountService / RatingService gọi ─────────

  /**
   * Cập nhật trạng thái tài khoản.
   * Khi chuyển sang SUSPENDED, ghi nhận thời điểm suspend.
   */
  public void setAccountStatus(AccountStatus status) {
    if (status == AccountStatus.SUSPENDED && this.accountStatus != AccountStatus.SUSPENDED) {
      this.suspendedAt = LocalDateTime.now();
    }
    this.accountStatus = status;
    markUpdated();
  }

  // ── Rating — KHÔNG có setter public. Chỉ RatingService gọi ──────────────

  /**
   * Điều chỉnh rating theo delta (dương = tăng, âm = giảm).
   * Được clamp tự động trong [{@value #RATING_MIN}, {@value #RATING_MAX}].
   *
   * <p><b>Chỉ {@link com.group13.auction.service.RatingService} được gọi method này.</b>
   * Tránh nhầm lẫn việc người dùng tự set rating cho bản thân.
   * Admin override method này để không làm gì (rating cố định 5.0).
   *
   * @param delta lượng thay đổi (có thể âm)
   */
  public void adjustRating(double delta) {
    this.rating = Math.max(RATING_MIN, Math.min(RATING_MAX, this.rating + delta));
    markUpdated();
  }

  /**
   * Thêm role cho user bình thường (non-admin).
   * Chỉ UserService / AccountService gọi — sau khi hệ thống phê duyệt.
   * Admin không được addRole thêm.
   *
   * @param role role cần thêm
   */
  public void addRole(UserRole role) {
    // Subclass override để lưu thêm danh sách roles nếu cần.
    // Base: không làm gì — override ở NormalUser.
  }

  @Override
  public abstract void printInfo();
}