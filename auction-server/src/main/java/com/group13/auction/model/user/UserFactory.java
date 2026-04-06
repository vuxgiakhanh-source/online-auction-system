package com.group13.auction.model.user;

import java.util.HashSet;
import java.util.Set;

/**
 * Factory tạo User — tập trung validate và khởi tạo.
 * ID được sinh bởi Entity (UUID).
 * Triển khai theo Factory Method Pattern.
 */
public abstract class UserFactory {

    /**
     * Lưu username đã dùng để kiểm tra trùng
     * TODO: sau này thay bằng truy vấn DB qua UserDAO.
     */
    private static final Set<String> usedUsernames = new HashSet<>();

    /**
     * Tạo User theo role với validate đầu vào.
     *
     * @param username tên đăng nhập (tối thiểu 8 ký tự, không trùng)
     * @param password mật khẩu thô (tối thiểu 8 ký tự)
     * @param email    địa chỉ email hợp lệ
     * @param args     các tham số bổ sung tùy theo loại User
     * @return User mới, id do Entity tự sinh UUID
     * @throws IllegalArgumentException nếu dữ liệu không hợp lệ
     */
    public User createUser(String username, String password, String email, Object... args) {
        validateUsername(username);
        validatePassword(password);
        validateEmail(email);

        User user = createProduct(username, password, email, args);
        usedUsernames.add(username);
        return user;
    }

    /**
     * Factory Method để các subclass tự khởi tạo instance cụ thể.
     */
    protected abstract User createProduct(String username, String password, String email, Object... args);

    // ── Validation methods ─────────────────────────────────────────────────────

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username không được để trống.");
        }
        if (username.length() < 8) {
            throw new IllegalArgumentException("Username phải từ 8 ký tự trở lên.");
        }
        if (usedUsernames.contains(username)) {
            // Không lộ thông tin nhạy cảm — chỉ báo "không hợp lệ"
            throw new IllegalArgumentException("Thông tin đăng ký không hợp lệ.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password phải từ 8 ký tự trở lên.");
        }
    }

    /**
     * Validate email bằng regex cơ bản
     * Không thể verify email tồn tại thật từ phía server -
     * cần gửi email xác nhạn (OTP) sau khi đăng kí
     *
     * @param email địa chỉ email cần kiểm tra
     */
    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống.");
        }
        String emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
        if (!email.matches(emailRegex)) {
            throw new IllegalArgumentException("Email không đúng định dạng.");
        }
    }
}