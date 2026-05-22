package com.group13.auction.model.user;

import com.group13.auction.model.entity.Entity;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lớp abstract người dùng — chỉ lưu data, không chứa nghiệp vụ.
 *
 * <p>Rating được thay đổi, quản lý bởi {@link com.group13.auction.service.RatingService}
 * Không có setter public cho rating.
 * Chỉ {@code RatingService} mới được điều chỉnh rating qua
 * {@code adjustRating(double)}.
 *
 * <p>Một User bình thường (non-admin) có thể mang nhiều role:
 * BIDDER và SELLER cùng lúc.
 * Admin không được mang thêm role khác
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

  private Set<String> joinedAuctionIds;
  private List<String> watchListAuctionIds;
  /** Tập auction user đã rời — không được join lại. Thread-safe nhờ ConcurrentHashMap.newKeySet(). */
  private Set<String> leftAuctionIds;

  // Constructor khai sinh

  /** Khai sinh — hash password tại đây, rating mặc định 3.0. */
  protected User(String username, String password, String email, UserRole role) {
    super();
    this.username = username;
    this.hashedPassword = hashPassword(password);
    this.email = email;
    this.primaryRole = role;
    this.accountStatus = AccountStatus.ACTIVE;
    this.rating = RATING_DEFAULT;
    this.suspendedAt = null;
    this.joinedAuctionIds = ConcurrentHashMap.newKeySet();
    this.watchListAuctionIds = new CopyOnWriteArrayList<>();
    this.leftAuctionIds = ConcurrentHashMap.newKeySet();
  }

  // Constructor hồi sinh

  /**
   * Hồi sinh từ DB — password đã hash
   * Chỉ DAO gọi thông qua {@code reconstitute()} của lớp con
   */
  protected User(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String username,
      String hashedPassword,
      String email,
      UserRole role,
      AccountStatus accountStatus,
      double rating,
      LocalDateTime suspendedAt) {
    super(id, createdAt, updatedAt);
    this.username = username;
    this.hashedPassword = hashedPassword;
    this.email = email;
    this.primaryRole = role;
    this.accountStatus = accountStatus;
    this.rating = rating;
    this.suspendedAt = suspendedAt;
    this.joinedAuctionIds = ConcurrentHashMap.newKeySet();
    this.watchListAuctionIds = new CopyOnWriteArrayList<>();
    this.leftAuctionIds = ConcurrentHashMap.newKeySet();
  }

  // Hash mật khẩu

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

  // Getters

  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public UserRole getPrimaryRole() { return primaryRole; }
  public AccountStatus getAccountStatus() { return accountStatus; }
  /**
   * Trả về rating trong miền [RATING_MIN, RATING_MAX].
   * Clamping được thực hiện tại đây thay vì trong adjustRating(),
   * để tích lũy concurrent delta không bị mất do per-step clamping.
   */
  public synchronized double getRating() {
    return Math.max(RATING_MIN, Math.min(RATING_MAX, rating));
  }
  public String getHashedPassword() { return hashedPassword; }
  public LocalDateTime getSuspendedAt() { return suspendedAt; }

  public Set<String> getJoinedAuctionIds() {
    return Collections.unmodifiableSet(joinedAuctionIds);
  }

  public List<String> getWatchListAuctionIds() {
    return Collections.unmodifiableList(watchListAuctionIds);
  }

  /**
   * Kiểm tra user đã join phiên chưa.
   *
   * @param auctionId id phiên cần kiểm tra
   * @return true nếu đã join
   */
  public boolean hasJoined(String auctionId) {
    return joinedAuctionIds.contains(auctionId);
  }

  /**
   * Đánh dấu user đã join phiên.
   * Chỉ {@link com.group13.auction.service.BidService} gọi — sau khi cọc đã được xử lý.
   *
   * @param auctionId id phiên
   */
  public void addJoinedAuction(String auctionId) {
    joinedAuctionIds.add(auctionId);
  }

  /**
   * Atomic check-and-mark: thêm auctionId vào joinedAuctionIds nếu chưa có.
   * Dùng tính chất của ConcurrentHashMap.newKeySet() — {@code add()} là atomic.
   *
   * @return {@code true} nếu đây là lần đầu join (chưa có trước đó),
   *         {@code false} nếu đã join rồi (duplicate — caller nên bỏ qua).
   */
  public boolean tryMarkJoined(String auctionId) {
    return joinedAuctionIds.add(auctionId);
  }

  /**
   * Rollback tryMarkJoined() khi join thất bại (ineligible, insufficient deposit, v.v.).
   * Cho phép user thử join lại sau khi lỗi được giải quyết.
   */
  public void removeJoinedAuction(String auctionId) {
    joinedAuctionIds.remove(auctionId);
  }

  /**
   * Thêm phiên vào watchList (idempotent).
   * Chỉ {@link com.group13.auction.service.BidService} gọi.
   *
   * @param auctionId id phiên
   */
  public void addToWatchList(String auctionId) {
    if (!watchListAuctionIds.contains(auctionId)) {
      watchListAuctionIds.add(auctionId);
    }
  }

  /**
   * FIX: Xóa phiên khỏi watchList khi user rời phiên (LEAVE hoặc disconnect).
   * Thread-safe nhờ CopyOnWriteArrayList.
   * Chỉ {@link com.group13.auction.service.BidService} gọi.
   *
   * @param auctionId id phiên cần xóa khỏi watchlist
   */
  public void removeFromWatchList(String auctionId) {
    watchListAuctionIds.remove(auctionId);
  }

  /**
   * FIX: Đánh dấu user đã rời phiên — không cho join lại.
   * Gọi trong BidService.leaveAuction() sau khi rời thành công.
   */
  public void addLeftAuction(String auctionId) {
    leftAuctionIds.add(auctionId);
  }

  /** Kiểm tra user đã từng rời phiên này chưa (để chặn rejoin). */
  public boolean hasLeft(String auctionId) {
    return leftAuctionIds.contains(auctionId);
  }

  /** Inject leftAuctionIds từ DB — chỉ DAO gọi sau reconstitute(). */
  public void setLeftAuctionIds(Set<String> ids) {
    this.leftAuctionIds.clear();
    if (ids != null) {
      this.leftAuctionIds.addAll(ids);
    }
  }

  // DAO injection setters

  /**
   * Inject danh sách auctionId đã join từ DB — chỉ DAO gọi sau reconstitute().
   *
   * @param ids tập id từ DB
   */
  public void setJoinedAuctionIds(Set<String> ids) {
    this.joinedAuctionIds.clear();
    if (ids != null) {
      this.joinedAuctionIds.addAll(ids);
    }
  }

  /**
   * Inject watchlist từ DB — chỉ DAO gọi sau reconstitute().
   *
   * @param ids danh sách id từ DB
   */
  public void setWatchListAuctionIds(List<String> ids) {
    this.watchListAuctionIds = ids != null
        ? new CopyOnWriteArrayList<>(ids)
        : new CopyOnWriteArrayList<>();
  }

  // Setter AccountStatus - chỉ AccountService / RatingService gọi

  /**
   * Cập nhật trạng thái tài khoản.
   * Khi chuyển sang SUSPENDED, ghi nhận thời điểm suspend.
   */
  public void setAccountStatus(AccountStatus status) {
    if (status == null) {
      throw new NullPointerException("Status không được null.");
    }
    if (status == AccountStatus.SUSPENDED && this.accountStatus != AccountStatus.SUSPENDED) {
      this.suspendedAt = LocalDateTime.now();
    }
    this.accountStatus = status;
    markUpdated();
  }

  // Rating - KHÔNG có setter public. Chỉ RatingService gọi

  /**
   * Điều chỉnh rating theo delta (> 0 = tăng, < 0 = giảm)
   * Đảm bảo nằm trong miền MIN, MAX (0.0, 5.0)
   *
   * <p><b>Chỉ {@link com.group13.auction.service.RatingService} được gọi method này.</b>
   * Tránh gian lận người dùng tự chỉnh Rating.
   * <p>Admin rating cố định 5.0.
   *
   * @param delta lượng thay đổi (có thể âm)
   */
  /**
   * Điều chỉnh rating theo delta (> 0 = tăng, < 0 = giảm).
   *
   * <p>Raw accumulation — không clamp tại đây.
   * Clamping chỉ thực hiện khi đọc qua {@link #getRating()}.
   * Cách này đảm bảo rằng trong môi trường concurrent, tổng net delta
   * từ tất cả các thread được phản ánh chính xác, thay vì bị mất
   * do per-step boundary clamping (lost-update tại biên).
   *
   * <p><b>Chỉ {@link com.group13.auction.service.RatingService} được gọi method này.</b>
   */
  public synchronized void adjustRating(double delta) {
    this.rating += delta;
    markUpdated();
  }

  /**
   * Kiểm tra user có role nào đó không
   * Admin chỉ có role ADMIN. Bidder có thể là Seller và ngược lại
   * nhưng phải thêm điều kiện trong quá trình đấu giá
   */
  public boolean hasRole(UserRole role) {
    return primaryRole == role;
  }


  /**
   * Thêm role cho user bình thường (admin không).
   * <p>Chỉ UserService / AccountService gọi — sau khi hệ thống phê duyệt.
   * Admin không được addRole thêm.
   *
   * @param role role cần thêm
   */
  public abstract void addRole(UserRole role);

  @Override
  public abstract void printInfo();
}