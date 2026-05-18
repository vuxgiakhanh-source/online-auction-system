package com.group13.auction.viewmodel.admin;

/**
 * View model hiển thị một người dùng trong màn Admin User Moderation.
 */
public class UserModerationViewModel {

    private final String userId;
    private final String username;
    private final String email;
    private final String role;
    private final String status;
    private final boolean banned;

    /**
     * Tạo dữ liệu hiển thị cho một người dùng.
     *
     * @param userId mã người dùng
     * @param username tên đăng nhập
     * @param email email
     * @param role vai trò
     * @param status trạng thái hiển thị
     * @param banned true nếu user đang bị khóa/cấm
     */
    public UserModerationViewModel(
            String userId, String username, String email, String role, String status, boolean banned) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.status = status;
        this.banned = banned;
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

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public boolean isBanned() {
        return banned;
    }
}