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
 * <p>Lớp này hỗ trợ dual-role theo yêu cầu nghiệp vụ.
 * BIDDER có state riêng SELLER có state riêng.
 */
public class NormalUser extends User {

    // Bidder state
    private double balance;
    /** Số tiền bị khóa làm cọc cho các phiên đang tham gia. */
    private double lockedDeposit;
    private List<BidTransaction> bidHistory;
    private Set<String> joinedAuctionIds;
    private List<String> watchListAuctionIds;

    // Seller state
    private List<Item> listedItems;
    private List<String> allAuctionIds;
    /** Đánh dấu seller đã từng bị trừ rating chưa — dùng để kiểm tra duyệt role Seller. */
    private boolean hasEverBeenPenalized;
    /**
     * Đánh dấu tài khoản đã từng được auto-restore sau khi bị SUSPENDED.
     * Cơ chế restore chỉ xảy ra 1 lần duy nhất trên mỗi tài khoản.
     */
    private boolean hasEverBeenRestored;

    /** Các role hiện tại của user. Mặc định khi tạo tài khoản là BIDDER. */
    private final Set<UserRole> roles;

    // Static factory methods

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
     * !!! Vấn đề: Chưa xử lý việc quá nhiều tham số khởi tạo
     * DAO phải gọi thêm các setter tương ứng sau khi reconstitute để nạp dữ liệu lịch sử.
     *
     * <p>TODO: UserDAO — sau khi gọi reconstitute()
     * setBidHistory(UserDAO.findBidHistoryByUserId(id))
     * setJoinedAuctionIds(UserDAO.findJoinedAuctionIdsByUserId(id))
     * setWatchListAuctionIds(UserDAO.findWatchListByUserId(id))
     * setListedItems(ItemDAO.findItemsBySellerId(id))
     * setAllAuctionIds(AuctionDAO.findAuctionIdsBySellerId(id))
     */
    public static NormalUser reconstitute(
            String id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String username,
            String hashedPassword,
            String email,
            AccountStatus accountStatus,
            double rating,
            double balance,
            double lockedDeposit,
            Set<UserRole> roles,
            boolean hasEverBeenPenalized,
            boolean hasEverBeenRestored,
            LocalDateTime suspendedAt) {
        return new NormalUser(
                id, createdAt, updatedAt, username, hashedPassword, email,
                accountStatus, rating, balance, lockedDeposit, roles,
                hasEverBeenPenalized, hasEverBeenRestored, suspendedAt);
    }

    // Private constructors

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
        this.hasEverBeenRestored = false;
    }

    /**
     * Constructor reconstitute: các list lịch sử được khởi tạo rỗng.
     * DAO chịu trách nhiệm nạp dữ liệu
     */
    private NormalUser(
            String id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String username,
            String hashedPassword,
            String email,
            AccountStatus accountStatus,
            double rating,
            double balance,
            double lockedDeposit,
            Set<UserRole> roles,
            boolean hasEverBeenPenalized,
            boolean hasEverBeenRestored,
            LocalDateTime suspendedAt) {
        super(id, createdAt, updatedAt, username, hashedPassword, email,
                UserRole.BIDDER, accountStatus, rating, suspendedAt);
        this.balance = balance;
        this.lockedDeposit = lockedDeposit;
        // Khởi tạo rỗng; DAO sẽ inject dữ liệu thực sau khi gọi reconstitute()
        this.bidHistory = new ArrayList<>();
        this.joinedAuctionIds = new HashSet<>();
        this.watchListAuctionIds = new ArrayList<>();
        this.listedItems = new ArrayList<>();
        this.allAuctionIds = new ArrayList<>();
        this.roles = EnumSet.copyOf(roles);
        this.hasEverBeenPenalized = hasEverBeenPenalized;
        this.hasEverBeenRestored = hasEverBeenRestored;
    }

    // Role management

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

    // Bidder getters / setters

    public double getBalance() {
        return balance;
    }

    public double getLockedDeposit() {
        return lockedDeposit;
    }

    /** Số dư khả dụng (không bị khóa). */
    public double getAvailableBalance() {
        return balance - lockedDeposit;
    }

    public boolean isHasEverBeenPenalized() {
        return hasEverBeenPenalized;
    }

    /**
     * Kiểm tra tài khoản đã từng được auto-restore chưa.
     * Dùng bởi {@link com.group13.auction.service.RatingService#checkAndRestoreSuspended}.
     *
     * @return true nếu đã được restore 1 lần
     */
    public boolean isHasEverBeenRestored() {
        return hasEverBeenRestored;
    }

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
     * Khóa một khoản cọc - gọi khi joinAuction thành công.
     * <= số dư khả dụng.
     * Không được tự ý gọi, chỉ được gọi trong WalletService.
     *
     * @param amount số tiền cần khóa
     */
    public void lockDeposit(double amount) {
        this.lockedDeposit += amount;
        markUpdated();
    }

    /**
     * Giải phóng khoản cọc — gọi khi phiên kết thúc.
     * Không được tự ý gọi, chỉ được gọi trong WalletService.
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

    /**
     * Đánh dấu tài khoản đã được auto-restore 1 lần.
     * Chỉ {@link com.group13.auction.service.RatingService} gọi.
     * Sau khi đánh dấu, tài khoản sẽ không được auto-restore nựa.
     */
    public void markRestored() {
        this.hasEverBeenRestored = true;
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

    // DAO injection setters (package-private — chỉ DAO trong cùng package hoặc DAO được phép gọi)

    /**
     * Inject lịch sử bid từ DB — chỉ UserDAO gọi sau reconstitute().
     * TODO: UserDAO.findBidHistoryByUserId(id) → gọi setBidHistory()
     */
    public void setBidHistory(List<BidTransaction> bidHistory) {
        this.bidHistory = bidHistory != null ? new ArrayList<>(bidHistory) : new ArrayList<>();
    }

    /**
     * Inject danh sách auctionId đã join từ DB — chỉ UserDAO gọi sau reconstitute().
     * TODO: UserDAO.findJoinedAuctionIdsByUserId(id) → gọi setJoinedAuctionIds()
     */
    public void setJoinedAuctionIds(Set<String> joinedAuctionIds) {
        this.joinedAuctionIds = joinedAuctionIds != null ? new HashSet<>(joinedAuctionIds) : new HashSet<>();
    }

    /**
     * Inject watchlist từ DB — chỉ UserDAO gọi sau reconstitute().
     * TODO: UserDAO.findWatchListByUserId(id) → gọi setWatchListAuctionIds()
     */
    public void setWatchListAuctionIds(List<String> watchListAuctionIds) {
        this.watchListAuctionIds = watchListAuctionIds != null
                ? new ArrayList<>(watchListAuctionIds) : new ArrayList<>();
    }

    // Seller getters / setters

    /** @return Collections ở dạng read-only */
    public List<Item> getListedItems() {
        return Collections.unmodifiableList(listedItems);
    }

    /** @return Collections ở dạng read-only */
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

    /**
     * Lọc ra các phiên đấu giá của Seller đang ở trạng thái OPEN hoặc RUNNING.
     *
     * <p>Dữ liệu được filter từ {@code allAuctionIds} đã được AuctionManager load vào memory.
     * Trong môi trường thực tế, nên ưu tiên query thẳng DB để tránh load toàn bộ phiên.
     *
     * <p>TODO: Thay thế bằng AuctionDAO.findUnfinishedAuctionIdsBySellerId(getId())
     * để query trực tiếp DB (WHERE seller_id = ? AND status IN ('OPEN','RUNNING'))
     * thay vì filter in-memory — đặc biệt quan trọng khi allAuctionIds chưa được inject đủ.
     *
     * @param auctionLookup hàm tìm Auction theo id (thường là AuctionManager::findAuctionById)
     * @return danh sách auctionId có trạng thái OPEN hoặc RUNNING (read-only)
     */
    public List<String> getUnfinishedAuctionIds(
            java.util.function.Function<String, com.group13.auction.model.auction.Auction> auctionLookup) {
        List<String> result = new ArrayList<>();
        for (String auctionId : allAuctionIds) {
            com.group13.auction.model.auction.Auction auction = auctionLookup.apply(auctionId);
            if (auction != null) {
                com.group13.auction.model.auction.Auction.AuctionStatus status = auction.getStatus();
                if (status == com.group13.auction.model.auction.Auction.AuctionStatus.OPEN
                        || status == com.group13.auction.model.auction.Auction.AuctionStatus.RUNNING) {
                    result.add(auctionId);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    // Delete account

    /**
     * Đánh dấu tài khoản đã bị xóa (soft-delete).
     *
     * <p>Bên DB chia 2 bảng: 1 bảng bị BANNED do Rating,
     * 1 bảng bị BANNED do xóa tài khoản (không cho đăng nhập).
     * (2 bảng đều giữ Rating của người dùng).
     *
     * <p>Khi người dùng đăng kí lại → Giữ Rating cũ của họ.
     */
    public void markDeleted() {
        setAccountStatus(AccountStatus.BANNED);
        markUpdated();
    }

    @Override
    public void printInfo() {
        System.out.println("THÔNG TIN NORMAL USER");
        System.out.printf("Username  : %s%n", getUsername());
        System.out.printf("Email     : %s%n", getEmail());
        System.out.printf("Roles     : %s%n", getRoles());
        System.out.printf("Balance   : %.0f%n", balance);
        System.out.printf("Locked    : %.0f%n", lockedDeposit);
        System.out.printf("Available : %.0f%n", getAvailableBalance());
        System.out.printf("Rating    : %.1f%n", getRating());
        System.out.printf("Status    : %s%n", getAccountStatus());
        System.out.printf("Penalized : %s%n", hasEverBeenPenalized);
        System.out.printf("Restored  : %s%n", hasEverBeenRestored);
        System.out.println("======================================");
    }
}