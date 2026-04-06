package com.group13.auction.model.user;

import com.group13.auction.model.entity.Entity;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Lớp abstract người dùng — chỉ lưu data, không chứa nghiệp vụ.
 *
 * <p>Rating được quản lý hoàn toàn bởi {@link com.group13.auction.service.RatingService}.
 * Không có setter public cho rating
 * Chỉ {@code RatingService} mới được điều chỉnh rating qua
 * {@code adjustRating(double)}.
 */
public abstract class User extends Entity {

  public enum UserRole { BIDDER, SELLER, ADMIN }

  public enum AccountStatus { ACTIVE, BANNED, SUSPENDED }

  private static final double RATING_DEFAULT = 3.0;
  private static final double RATING_MIN     = 0.0;
  private static final double RATING_MAX     = 5.0;

  private final String username;
  private final String hashedPassword;
  private final String email;
  private final UserRole role;
  private AccountStatus accountStatus;
  private double rating;

  // ── Constructor khai sinh ──────────────────────────────────────────────────

  /** Khai sinh — hash password ngay tại đây, rating mặc định 3.0. */
  protected User(String username, String password,
      String email, UserRole role) {
    super();
    this.username = username;
    this.hashedPassword = hashPassword(password);
    this.email = email;
    this.role = role;
    this.accountStatus = AccountStatus.ACTIVE;
    this.rating = RATING_DEFAULT;
  }

  // ── Constructor hồi sinh ──────────────────────────────────────────────────

  /**
   * Hồi sinh từ DB — password đã hash, không hash lại.
   * Chỉ DAO gọi thông qua {@code reconstitute()} của lớp con.
   */
  protected User(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String username, String hashedPassword, String email,
      UserRole role, AccountStatus accountStatus, double rating) {
    super(id, createdAt, updatedAt);
    this.username = username;
    this.hashedPassword = hashedPassword;
    this.email = email;
    this.role = role;
    this.accountStatus = accountStatus;
    this.rating = rating;
  }

  // ── Hash utility ───────────────────────────────────────────────────────────

  /** Hash mật khẩu SHA-256. Public vì UserService dùng khi verify login. */
  public static String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(password.getBytes());
      StringBuilder hex = new StringBuilder();
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 không khả dụng.", e);
    }
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getUsername()          { return username; }
  public String getEmail()             { return email; }
  public UserRole getRole()            { return role; }
  public AccountStatus getAccountStatus() { return accountStatus; }
  public double getRating()            { return rating; }
  public String getHashedPassword()    { return hashedPassword; }

  // ── Package-level setter cho AccountStatus — chỉ AccountService gọi ────────

  public void setAccountStatus(AccountStatus status) {
    this.accountStatus = status;
    markUpdated();
  }

  // ── Rating — KHÔNG có setter public. Chỉ RatingService gọi adjustRating() ──

  /**
   * Điều chỉnh rating theo delta (dương = tăng, âm = giảm).
   * Được clamp tự động trong [{@value #RATING_MIN}, {@value #RATING_MAX}].
   *
   * <p><b>Chỉ {@link com.group13.auction.service.RatingService} được gọi method này.</b>
   * Tránh nhầm lẫn việc người dùng tự set rating cho bản thân
   *
   * @param delta lượng thay đổi (có thể âm)
   */
  public void adjustRating(double delta) {
    this.rating = Math.max(RATING_MIN, Math.min(RATING_MAX, this.rating + delta));
    markUpdated();
  }

  @Override
  public abstract void printInfo();
}