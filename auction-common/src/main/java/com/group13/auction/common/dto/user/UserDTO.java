package com.group13.auction.common.dto.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO truyền thông tin người dùng qua mạng.
 *
 * <p>Không bao giờ chứa hashedPassword.
 * Balance chỉ gửi về đúng chủ tài khoản hoặc Admin.
 */
public class UserDTO {

    private String id;
    private String username;
    private String email;

    /** "BIDDER", "SELLER", "BIDDER_SELLER" */
    private List<String> roles;

    /** "ACTIVE", "SUSPENDED", "BANNED", "DELETED" */
    private String accountStatus;

    private double rating;
    private long balance;
    private long lockedDeposit;
    private long availableBalance;

    /** true nếu user này đã từng bị penalize (ảnh hưởng auto-approve Seller). */
    private boolean hasEverBeenPenalized;

    /** Số lần tài khoản được auto-restore sau khi bị SUSPENDED. */
    private int timesRestored;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Loại tài khoản admin: null nếu là NormalUser, "MASTER" hoặc "STAFF" nếu là Admin. */
    private String adminType;

    public UserDTO() {}

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }

    public long getLockedDeposit() { return lockedDeposit; }
    public void setLockedDeposit(long lockedDeposit) { this.lockedDeposit = lockedDeposit; }

    public long getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(long availableBalance) { this.availableBalance = availableBalance; }

    public boolean isHasEverBeenPenalized() { return hasEverBeenPenalized; }
    public void setHasEverBeenPenalized(boolean hasEverBeenPenalized) {
        this.hasEverBeenPenalized = hasEverBeenPenalized;
    }

    public int getTimesRestored() { return timesRestored; }
    public void setTimesRestored(int timesRestored) { this.timesRestored = timesRestored; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getAdminType() { return adminType; }
    public void setAdminType(String adminType) { this.adminType = adminType; }
}