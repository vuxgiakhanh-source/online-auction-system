package com.group13.auction.common.dto.auth;

import com.group13.auction.common.dto.user.UserDTO;

/**
 * Payload của packet {@code LOGIN_SUCCESS} và {@code REGISTER_SUCCESS}.
 *
 * <p>Token dùng để xác thực các request tiếp theo.
 * Client lưu token vào memory và gửi kèm mọi WebSocket message (qua header hoặc trong payload).
 */
public class LoginResponseDTO {

    /** Session token do Server sinh (UUID hoặc JWT). */
    private String token;

    /** Thông tin đầy đủ của user sau khi đăng nhập. */
    private UserDTO user;

    /**
     * true khi tài khoản BANNED/SUSPENDED — client chỉ nên gọi API ví sau login.
     */
    private boolean restrictedMode;

    public LoginResponseDTO() {}

    public LoginResponseDTO(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    public LoginResponseDTO(String token, UserDTO user, boolean restrictedMode) {
        this.token = token;
        this.user = user;
        this.restrictedMode = restrictedMode;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UserDTO getUser() { return user; }
    public void setUser(UserDTO user) { this.user = user; }

    public boolean isRestrictedMode() { return restrictedMode; }
    public void setRestrictedMode(boolean restrictedMode) { this.restrictedMode = restrictedMode; }
}