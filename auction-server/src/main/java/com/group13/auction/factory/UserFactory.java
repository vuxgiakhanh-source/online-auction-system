package com.group13.auction.factory;

import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.Seller;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.UserRole;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory tạo User — tập trung validate và khởi tạo (lỗi #24, #25).
 * ID được sinh bởi Entity (UUID) — không cần generateId() ở đây.
 */
public class UserFactory {

  /**
   * Lưu username đã dùng để kiểm tra trùng (lỗi #24).
   * TODO: sau này thay bằng truy vấn DB qua UserDAO.
   */
  private static final Set<String> usedUsernames = new HashSet<>();

  /** Utility class — không cho khởi tạo. */
  private UserFactory() {}

  /**
   * Tạo User theo role với validate đầu vào.
   *
   * @param role     vai trò người dùng
   * @param username tên đăng nhập (tối thiểu 8 ký tự, không trùng)
   * @param password mật khẩu thô (tối thiểu 8 ký tự)
   * @param email    địa chỉ email hợp lệ
   * @return User mới, id do Entity tự sinh UUID
   * @throws IllegalArgumentException nếu dữ liệu không hợp lệ
   */
  public static User createUser(UserRole role, String username,
      String password, String email) {
    validateUsername(username);
    validatePassword(password);
    validateEmail(email);

    usedUsernames.add(username);

    switch (role) {
      case BIDDER:
        return new Bidder(username, password, email);
      case SELLER:
        return new Seller(username, password, email);
      case ADMIN:
        // Admin chỉ được tạo bởi Admin khác (lỗi #9)
        // Tại đây chỉ dùng để seed admin đầu tiên từ hệ thống
        return new Admin(username, password, email, 1);
      default:
        throw new IllegalArgumentException("Role không hợp lệ: " + role);
    }
  }

  // ── Validation methods ─────────────────────────────────────────────────────

  private static void validateUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Username không được để trống.");
    }
    if (username.length() < 8) {
      throw new IllegalArgumentException("Username phải từ 8 ký tự trở lên.");
    }
    if (usedUsernames.contains(username)) {
      // Không lộ thông tin nhạy cảm — chỉ báo "không hợp lệ" (lỗi #24)
      throw new IllegalArgumentException("Thông tin đăng ký không hợp lệ.");
    }
  }

  private static void validatePassword(String password) {
    if (password == null || password.length() < 8) {
      throw new IllegalArgumentException("Password phải từ 8 ký tự trở lên.");
    }
  }

  /**
   * Validate email bằng regex cơ bản (lỗi #25).
   * Không thể verify email tồn tại thật từ phía server —
   * cần gửi email xác nhận (OTP/verification link) sau khi đăng ký.
   *
   * @param email địa chỉ email cần kiểm tra
   */
  private static void validateEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email không được để trống.");
    }
    // Regex cơ bản: có @ và ít nhất 1 ký tự trước/sau, có dấu chấm sau @
    String emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    if (!email.matches(emailRegex)) {
      throw new IllegalArgumentException("Email không đúng định dạng.");
    }
    // Lưu ý (lỗi #25): để verify email thật sự tồn tại,
    // cần gửi verification email sau khi đăng ký thành công.
    // TODO: tích hợp EmailService.sendVerification(email)
  }
}