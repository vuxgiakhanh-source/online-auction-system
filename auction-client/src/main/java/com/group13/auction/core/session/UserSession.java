package com.group13.auction.core.session;

import com.group13.auction.common.dto.user.UserDTO;
import java.util.List;
import java.util.Objects;

/** Thông tin người dùng đang đăng nhập trong ứng dụng client. */
public final class UserSession {

    private final String token;
    private final UserDTO user;

    /**
     * Khởi tạo UserSession với dữ liệu cần thiết cho module client.
     */
    public UserSession(String token, UserDTO user) {
        this.token = Objects.requireNonNullElse(token, "demo-token");
        this.user = Objects.requireNonNull(user);
    }

    /**
     * Trả về giá trị token dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị token
     */
    public String getToken() {
        return token;
    }

    /**
     * Trả về giá trị user dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị user
     */
    public UserDTO getUser() {
        return user;
    }

    /**
     * Trả về giá trị userId dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị userId
     */
    public String getUserId() {
        return user.getId();
    }

    /**
     * Trả về giá trị username dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị username
     */
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * Trả về giá trị roles dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị roles
     */
    public List<String> getRoles() {
        return user.getRoles();
    }

    /**
     * Thực thi thao tác hasRole của thành phần client.
     */
    public boolean hasRole(String role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }
}
