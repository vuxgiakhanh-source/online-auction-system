package com.group13.auction.viewmodel.auth;

import java.util.Optional;

/**
 * Dữ liệu form đăng nhập đã tách khỏi JavaFX controller.
 *
 * <p>Controller chỉ đọc dữ liệu từ ô nhập, còn validate cơ bản được đặt ở đây để dễ test và dễ tái
 * sử dụng.
 */
public final class LoginFormState {

    private final String username;
    private final String password;

    /** Tạo form rỗng. */
    public LoginFormState() {
        this("", "");
    }

    /**
     * Tạo form đăng nhập.
     *
     * @param username tên đăng nhập
     * @param password mật khẩu
     */
    public LoginFormState(String username, String password) {
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
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

    /**
     * Validate dữ liệu đăng nhập ở phía client.
     *
     * @return optional chứa lỗi nếu form chưa hợp lệ
     */
    public Optional<String> validate() {
        if (normalizedUsername().isBlank()) {
            return Optional.of("Bạn chưa nhập tên đăng nhập.");
        }

        if (password.isBlank()) {
            return Optional.of("Bạn chưa nhập mật khẩu.");
        }

        return Optional.empty();
    }
}