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
    private final String banReason;
    private final String bannedBy;
    private final String bannedAt;

    public UserModerationViewModel(
            String userId,
            String username,
            String email,
            String role,
            String status,
            boolean banned,
            String banReason,
            String bannedBy,
            String bannedAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.status = status;
        this.banned = banned;
        this.banReason = banReason;
        this.bannedBy = bannedBy;
        this.bannedAt = bannedAt;
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

    public String getBanReason() {
        return banReason;
    }

    public String getBannedBy() {
        return bannedBy;
    }

    public String getBannedAt() {
        return bannedAt;
    }
}
