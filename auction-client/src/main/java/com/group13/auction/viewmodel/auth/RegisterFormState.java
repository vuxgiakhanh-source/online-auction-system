package com.group13.auction.viewmodel.auth;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Dữ liệu form đăng ký đã tách khỏi JavaFX controller.
 *
 * <p>Form state chỉ validate dữ liệu nhập cơ bản. Việc kiểm tra username/email đã tồn tại hay chưa
 * vẫn thuộc trách nhiệm của server.
 */
public final class RegisterFormState {

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final String email;
    private final String username;
    private final String password;
    private final String confirmPassword;

    /** Tạo form rỗng. */
    public RegisterFormState() {
        this("", "", "", "");
    }

    /**
     * Tạo form đăng ký khi không cần kiểm tra lại mật khẩu.
     *
     * <p>Constructor này giữ tương thích với code cũ. Flow UI nên dùng constructor có
     * {@code confirmPassword} để validate đầy đủ hơn.
     *
     * @param email email người dùng
     * @param username tên đăng nhập
     * @param password mật khẩu
     */
    public RegisterFormState(String email, String username, String password) {
        this(email, username, password, password);
    }

    /**
     * Tạo form đăng ký đầy đủ.
     *
     * @param email email người dùng
     * @param username tên đăng nhập
     * @param password mật khẩu
     * @param confirmPassword mật khẩu nhập lại
     */
    public RegisterFormState(
            String email, String username, String password, String confirmPassword) {
        this.email = email == null ? "" : email;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.confirmPassword = confirmPassword == null ? "" : confirmPassword;
    }

    public String email() {
        return email;
    }

    public String normalizedEmail() {
        return email.trim();
    }

    public String username() {
        return username;
    }

    public String normalizedUsername() {
        return username.trim();
    }

    public String password() {
        return password;
    }

    public String confirmPassword() {
        return confirmPassword;
    }

    /**
     * Validate dữ liệu đăng ký ở phía client.
     *
     * @return optional chứa lỗi nếu form chưa hợp lệ
     */
    public Optional<String> validate() {
        if (normalizedEmail().isBlank()) {
            return Optional.of("Bạn chưa nhập email.");
        }

        if (!EMAIL_PATTERN.matcher(normalizedEmail()).matches()) {
            return Optional.of("Email chưa đúng định dạng.");
        }

        if (normalizedUsername().length() < MIN_USERNAME_LENGTH) {
            return Optional.of("Tên đăng nhập cần có ít nhất 3 ký tự.");
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            return Optional.of("Mật khẩu cần có ít nhất 6 ký tự.");
        }

        if (confirmPassword.isBlank()) {
            return Optional.of("Bạn chưa nhập lại mật khẩu.");
        }

        if (!password.equals(confirmPassword)) {
            return Optional.of("Mật khẩu nhập lại chưa khớp.");
        }

        return Optional.empty();
    }
}