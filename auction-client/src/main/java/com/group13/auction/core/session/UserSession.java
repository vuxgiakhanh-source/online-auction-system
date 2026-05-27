package com.group13.auction.core.session;

import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.user.UserDTO;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Session hiện tại của người dùng đã đăng nhập ở phía client.
 *
 * <p>Session chỉ lưu dữ liệu cần thiết cho UI và request tiếp theo. Không lưu mật khẩu.
 */
public final class UserSession {

  private final String token;
  private final String userId;
  private final String username;
  private final String email;
  private final List<String> roles;
  private final String accountStatus;
  private final String adminType;

  private UserSession(
      String token,
      String userId,
      String username,
      String email,
      List<String> roles,
      String accountStatus,
      String adminType) {
    this.token = token;
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.roles = List.copyOf(roles == null ? Collections.emptyList() : roles);
    this.accountStatus = accountStatus;
    this.adminType = adminType;
  }

  /**
   * Tạo session từ response đăng nhập/đăng ký server trả về.
   *
   * @param response response chứa token và user DTO
   * @return session phía client
   */
  public static UserSession from(LoginResponseDTO response) {
    Objects.requireNonNull(response, "response must not be null");
    return from(response.getToken(), response.getUser());
  }

  /**
   * Tạo session từ token và {@link UserDTO}.
   *
   * @param token session token
   * @param user thông tin người dùng từ common DTO
   * @return session phía client
   */
  public static UserSession from(String token, UserDTO user) {
    Objects.requireNonNull(user, "user must not be null");

    return new UserSession(
        requireText(token, "token"),
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getRoles(),
        user.getAccountStatus(),
        user.getAdminType());
  }

  /**
   * Tạo session thủ công, hữu ích cho test hoặc mock UI tạm thời.
   *
   * @param token session token
   * @param userId id người dùng
   * @param username tên đăng nhập
   * @param email email người dùng
   * @param roles danh sách role
   * @param accountStatus trạng thái tài khoản
   * @return session phía client
   */
  public static UserSession of(
      String token,
      String userId,
      String username,
      String email,
      List<String> roles,
      String accountStatus) {
    return of(token, userId, username, email, roles, accountStatus, null);
  }

  /**
   * Tạo session thủ công kèm loại Admin, hữu ích cho test hoặc dữ liệu server đã map sẵn.
   *
   * @param token session token
   * @param userId id người dùng
   * @param username tên đăng nhập
   * @param email email người dùng
   * @param roles danh sách role
   * @param accountStatus trạng thái tài khoản
   * @param adminType loại Admin: {@code MASTER}, {@code STAFF}, hoặc null nếu không phải Admin
   * @return session phía client
   */
  public static UserSession of(
      String token,
      String userId,
      String username,
      String email,
      List<String> roles,
      String accountStatus,
      String adminType) {
    return new UserSession(
        requireText(token, "token"), userId, username, email, roles, accountStatus, adminType);
  }

  public String getToken() {
    return token;
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

  public List<String> getRoles() {
    return roles;
  }

  public String getAccountStatus() {
    return accountStatus;
  }

  public String getAdminType() {
    return adminType;
  }

  /**
   * Kiểm tra user hiện tại có role nhất định hay không.
   *
   * @param role role cần kiểm tra, ví dụ {@code BIDDER}, {@code SELLER}, {@code ADMIN}
   * @return true nếu session có role này
   */
  public boolean hasRole(String role) {
    if (role == null || role.isBlank()) {
      return false;
    }

    return roles.stream().anyMatch(currentRole -> currentRole.equalsIgnoreCase(role));
  }

  public boolean isAdmin() {
    return hasRole("ADMIN") || hasAdminType();
  }

  public boolean isMasterAdmin() {
    return isAdmin() && "MASTER".equalsIgnoreCase(adminType);
  }

  public boolean isStaffAdmin() {
    return isAdmin() && "STAFF".equalsIgnoreCase(adminType);
  }

  public boolean hasAdminType() {
    return adminType != null && !adminType.isBlank();
  }

  public boolean isSeller() {
    return hasRole("SELLER") || hasRole("BIDDER_SELLER");
  }

  public boolean isBidder() {
    return hasRole("BIDDER") || hasRole("BIDDER_SELLER");
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
