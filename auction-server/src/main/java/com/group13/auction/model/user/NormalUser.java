package com.group13.auction.model.user;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.item.Item;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Người dùng bình thường — có thể đảm nhận cả vai Bidder và Seller.
 *
 * <p>Khi mới tạo, mặc định có role BIDDER.
 * Muốn trở thành Seller, user phải gửi yêu cầu và được hệ thống phê duyệt
 * qua {@link com.group13.auction.service.AccountService}.
 *
 * <p>Lớp này thay thế các lớp Bidder và Seller riêng biệt để hỗ trợ
 * dual-role theo yêu cầu nghiệp vụ.
 */
public class NormalUser extends User {

    // ── Bidder state ───────────────────────────────────────────────────────────
    private double balance;
    /** Số tiền bị khóa làm cọc cho các phiên đang tham gia. */
    private double lockedDeposit;
    private final List<BidTransaction> bidHistory;
    private final Set<String> joinedAuctionIds;
    private final List<String> watchListAuctionIds;

    // ── Seller state ───────────────────────────────────────────────────────────
    private final List<Item> listedItems;
    private final List<String> allAuctionIds;
    /** Đánh dấu seller đã từng bị trừ rating chưa — dùng để kiểm tra duyệt role Seller. */
    private boolean hasEverBeenPenalized;

    /** Các role hiện tại của user. Mặc định = {BIDDER}. */
    private final Set<UserRole> roles;

    // ── Static factory methods ─────────────────────────────────────────────────

    /**
     * Khai sinh NormalUser mới — mặc định có role BIDDER, balance = 0.
     *
     * @param username tên đăng nhập
     * @param password mật khẩu thô
     * @param email địa chỉ email
     * @return NormalUser mới
     */
    public static NormalUser create(String username, String password, String email) {
        return new NormalUser(username, password, email);
    }

    /**
     * Hồi sinh NormalUser từ DB — CHÚ Ý: chỉ DAO được gọi method này.
     */
    public static NormalUser reconstitute(String id, LocalDateTime createdAt,
                                          LocalDateTime updatedAt, String username, String hashedPassword,
                                          String email, AccountStatus accountStatus, double rating,
                                          double balance, double lockedDeposit, Set<UserRole> roles,
                                          boolean hasEverBeenPenalized, LocalDateTime suspendedAt) {
        return new NormalUser(id, createdAt, updatedAt, username, hashedPassword,
                email, accountStatus, rating, balance, lockedDeposit, roles,
                hasEverBeenPenalized, suspendedAt);
    }

    // ── Private constructors ───────────────────────────────────────────────────

    private NormalUser(String username, String password, String email) {
        super(username, password, email, UserRole.BIDDER);
        this.balance = 0.0;
        this.lockedDeposit = 0.0;
        this.bidHistory = new ArrayList<>();
        this.joinedAuctionIds = new HashSet<>();
        this.watchListAuctionIds = new ArrayList<>();
        this.listedItems = new ArrayList<>();
        this.allAuctionIds = new ArrayList<>();
        this.roles = EnumSet.of(UserRole.BIDDER);
        this.hasEverBeenPenalized = false;
    }

    private NormalUser(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                       String username, String hashedPassword, String email,
                       AccountStatus accountStatus, double rating,
                       double balance, double lockedDeposit,
                       Set<UserRole> roles, boolean hasEverBeenPenalized, LocalDateTime suspendedAt) {
        super(id, createdAt, updatedAt, username, hashedPassword, email,
                UserRole.BIDDER, accountStatus, rating, suspendedAt);
        this.balance = balance;
        this.lockedDeposit = lockedDeposit;
        this.bidHistory = new ArrayList<>();
        this.joinedAuctionIds = new HashSet<>();
        this.watchListAuctionIds = new ArrayList<>();
        this.listedItems = new ArrayList<>();
        this.allAuctionIds = new ArrayList<>();
        this.roles = EnumSet.copyOf(roles);
        this.hasEverBeenPenalized = hasEverBeenPenalized;
    }

    // ── Role management ────────────────────────────────────────────────────────

    @Override
    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    /**
     * Thêm role cho user — chỉ AccountService gọi sau khi phê duyệt.
     * Admin không được addRole thêm (guard ở AccountService).
     */
    @Override
    public void addRole(UserRole role) {
        roles.add(role);
        markUpdated();
    }

    public Set<UserRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    // ── Bidder getters / setters ───────────────────────────────────────────────

    public double getBalance() { return balance; }
    public double getLockedDeposit() { return lockedDeposit; }

    /** Số dư khả dụng (không bị khóa). */
    public double getAvailableBalance() { return balance - lockedDeposit; }

    public boolean isHasEverBeenPenalized() { return hasEverBeenPenalized; }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public Set<String> getJoinedAuctionIds() {
        return Collections.unmodifiableSet(joinedAuctionIds);
    }

    public List<String> getWatchListAuctionIds() {
        return Collections.unmodifiableList(watchListAuctionIds);
    }

    public boolean hasJoined(String auctionId) {
        return joinedAuctionIds.contains(auctionId);
    }

    public void setBalance(double balance) {
        this.balance = balance;
        markUpdated();
    }

    /**
     * Khóa một khoản cọc — gọi khi joinAuction thành công.
     * Không được vượt quá số dư khả dụng.
     * Không được tự ý gọi, chỉ được gọi trong WalletService
     *
     * @param amount số tiền cần khóa
     */
    public void lockDeposit(double amount) {
        this.lockedDeposit += amount;
        markUpdated();
    }

    /**
     * Giải phóng khoản cọc — gọi khi phiên kết thúc.
     * Không được tự ý gọi, chỉ được gọi trong WalletService
     *
     * @param amount số tiền giải phóng
     */
    public void unlockDeposit(double amount) {
        this.lockedDeposit = Math.max(0, this.lockedDeposit - amount);
        markUpdated();
    }

    /**
     * Đánh dấu user đã từng bị phạt rating.
     * Chỉ RatingService gọi — dùng để kiểm tra điều kiện duyệt role Seller.
     */
    public void markPenalized() {
        this.hasEverBeenPenalized = true;
        markUpdated();
    }

    public void addBidToHistory(BidTransaction tx) {
        bidHistory.add(tx);
    }

    public void addJoinedAuction(String auctionId) {
        joinedAuctionIds.add(auctionId);
    }

    public void addToWatchList(String auctionId) {
        if (!watchListAuctionIds.contains(auctionId)) {
            watchListAuctionIds.add(auctionId);
        }
    }

    // ── Seller getters / setters ───────────────────────────────────────────────

    public List<Item> getListedItems() {
        return Collections.unmodifiableList(listedItems);
    }

    public List<String> getAllAuctionIds() {
        return Collections.unmodifiableList(allAuctionIds);
    }

    public void addListedItem(Item item) {
        listedItems.add(item);
        markUpdated();
    }

    public void removeListedItem(Item item) {
        listedItems.remove(item);
        markUpdated();
    }

    public void addAuctionId(String auctionId) {
        allAuctionIds.add(auctionId);
        markUpdated();
    }

    // ── Delete account ─────────────────────────────────────────────────────────

    /** Đánh dấu tài khoản đã bị xóa (soft-delete). */
    public void markDeleted() {
        setAccountStatus(AccountStatus.BANNED);
        markUpdated();
    }

    @Override
    public void printInfo() {
        System.out.println("=== NORMAL USER ======================");
        System.out.printf("Username : %s%n", getUsername());
        System.out.printf("Email : %s%n", getEmail());
        System.out.printf("Roles : %s%n", getRoles());
        System.out.printf("Balance : %.0f%n", balance);
        System.out.printf("Locked : %.0f%n", lockedDeposit);
        System.out.printf("Available : %.0f%n", getAvailableBalance());
        System.out.printf("Rating : %.1f%n", getRating());
        System.out.printf("Status : %s%n", getAccountStatus());
        System.out.printf("Penalized : %s%n", hasEverBeenPenalized);
        System.out.println("======================================");
    }
}