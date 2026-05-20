package com.group13.auction.model.user;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.item.Item;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Người dùng bình thường - có thể đảm nhận cả vai Bidder và Seller.
 *
 * <p>Khi mới tạo, mặc định có role BIDDER.
 * Muốn trở thành Seller, user phải gửi yêu cầu và được hệ thống phê duyệt
 * qua {@link com.group13.auction.service.AccountService}.
 *
 * <p>Lớp này hỗ trợ dual-role theo yêu cầu nghiệp vụ.
 * BIDDER có state riêng SELLER có state riêng.
 */
public class NormalUser extends User {

    private static final Logger log = LoggerFactory.getLogger(NormalUser.class);

    // Bidder state (FIX: Dùng AtomicLong để thread-safe)
    private final AtomicLong balance;
    /** Số tiền bị khóa làm cọc cho các phiên đang tham gia. */
    private final AtomicLong lockedDeposit;

    // FIX: Dùng Concurrent Collections để tránh ConcurrentModificationException
    private List<BidTransaction> bidHistory;

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
    protected static NormalUser create(String username, String password, String email) {
        return new NormalUser(username, password, email);
    }

    /**
     * Hồi sinh NormalUser từ DB — CHÚ Ý: chỉ DAO được gọi method này.
     *
     * <p>Sau khi gọi reconstitute(), DAO phải inject thêm dữ liệu lịch sử
     * bằng các setter tương ứng. Tất cả các hàm DAO đã được triển khai đầy đủ:
     *
     * <pre>
     *   NormalUser user = NormalUser.reconstitute(...);
     *   // Đã thực hiện TODO — tự động inject trong UserDAO.findNormalUserById():
     *   user.setJoinedAuctionIds(userDAO.findJoinedAuctionIdsByUserId(id));
     *   user.setWatchListAuctionIds(userDAO.findWatchListByUserId(id));
     *   // Đã thực hiện TODO — caller inject thủ công nếu cần (tránh đệ quy):
     *   user.setBidHistory(userDAO.findBidHistoryByUserId(id));
     *   user.setListedItems(itemDAO.findItemsBySellerId(id));
     *   user.setAllAuctionIds(auctionDAO.findAuctionIdsBySellerId(id));
     * </pre>
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
            long balance,
            long lockedDeposit,
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
        this.balance = new AtomicLong(0L);
        this.lockedDeposit = new AtomicLong(0L);
        this.bidHistory = new CopyOnWriteArrayList<>();
        this.listedItems = new CopyOnWriteArrayList<>();
        this.allAuctionIds = new CopyOnWriteArrayList<>();
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
            long balance,
            long lockedDeposit,
            Set<UserRole> roles,
            boolean hasEverBeenPenalized,
            boolean hasEverBeenRestored,
            LocalDateTime suspendedAt) {
        super(id, createdAt, updatedAt, username, hashedPassword, email,
                UserRole.BIDDER, accountStatus, rating, suspendedAt);
        this.balance = new AtomicLong(balance);
        this.lockedDeposit = new AtomicLong(lockedDeposit);
        this.bidHistory = new CopyOnWriteArrayList<>();
        this.listedItems = new CopyOnWriteArrayList<>();
        this.allAuctionIds = new CopyOnWriteArrayList<>();
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

    public long getBalance() {
        return balance.get();
    }

    public long getLockedDeposit() {
        return lockedDeposit.get();
    }

    /** Số dư khả dụng (không bị khóa). */
    public long getAvailableBalance() {
        return balance.get() - lockedDeposit.get();
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

    public void setBalance(long balance) {
        this.balance.set(balance);
        markUpdated();
    }

    /**
     * Cộng delta vào balance atomic (AtomicLong.addAndGet).
     */
    public long addBalance(long delta) {
        markUpdated();
        return this.balance.addAndGet(delta);
    }

    /**
     * Khóa một khoản cọc - gọi khi joinAuction thành công.
     * <= số dư khả dụng.
     * Không được tự ý gọi, chỉ được gọi trong WalletService.
     *
     * @param amount số tiền cần khóa
     */
    public void lockDeposit(long amount) {
        this.lockedDeposit.addAndGet(amount);
        markUpdated();
    }

    /**
     * Giải phóng khoản cọc - gọi khi phiên kết thúc.
     * Không được tự ý gọi, chỉ được gọi trong WalletService.
     *
     * @param amount số tiền giải phóng
     */
    public void unlockDeposit(long amount) {
        // Đảm bảo không bao giờ âm lockedDeposit
        while (true) {
            long current = lockedDeposit.get();
            long next = Math.max(0L, current - amount);
            if (lockedDeposit.compareAndSet(current, next)) {
                break;
            }
        }
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

    // DAO injection setters (DAO được phép gọi)

    /**
     * Inject lịch sử bid từ DB — chỉ UserDAO gọi sau reconstitute().
     * Đã thực hiện TODO: UserDAO.findBidHistoryByUserId(id) đã được triển khai.
     * Lưu ý: UserDAO.findNormalUserById() không tự inject để tránh đệ quy;
     * caller cần gọi thủ công nếu cần đầy đủ lịch sử bid.
     */
    public void setBidHistory(List<BidTransaction> bidHistory) {
        this.bidHistory = bidHistory != null ? new CopyOnWriteArrayList<>(bidHistory) : new CopyOnWriteArrayList<>();
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
     * Inject danh sách auctionId của seller từ DB — chỉ DAO gọi sau reconstitute().
     * Thiếu setter này khiến allAuctionIds luôn rỗng sau server restart.
     */
    public void setAllAuctionIds(java.util.List<String> auctionIds) {
        this.allAuctionIds = auctionIds != null
                ? new CopyOnWriteArrayList<>(auctionIds)
                : new CopyOnWriteArrayList<>();
        markUpdated();
    }

    /** Chỉ được gọi trong WalletService hỗ trợ quá trình Rollback */
    public void restoreBalances(long balance, long lockedDeposit) {
        this.balance.set(balance);
        this.lockedDeposit.set(lockedDeposit);
        markUpdated();
    }

    /**
     * Lọc ra các phiên đấu giá của Seller đang ở trạng thái OPEN hoặc RUNNING.
     *
     * <p>Dữ liệu được filter từ {@code allAuctionIds} đã được AuctionManager load vào memory.
     * Trong môi trường thực tế, nên ưu tiên query thẳng DB để tránh load toàn bộ phiên.
     *
     * <p>Đã thực hiện TODO: Hàm {@code AuctionDAO.findUnfinishedAuctionIdsBySellerId(id)}
     * đã được thêm vào AuctionDAO để query trực tiếp DB
     * (WHERE item_id IN (SELECT id FROM items WHERE seller_id = ?) AND status IN ('OPEN','RUNNING')).
     * Nên gọi hàm đó thay vì filter in-memory khi allAuctionIds chưa được inject đủ.
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

    @Override
    public void printInfo() {
        log.info("THÔNG TIN NORMAL USER");
        log.info("Username  : {}", getUsername());
        log.info("Email     : {}", getEmail());
        log.info("Roles     : {}", getRoles());
        log.info("Balance   : {}", balance.get());
        log.info("Locked    : {}", lockedDeposit.get());
        log.info("Available : {}", getAvailableBalance());
        log.info("Rating    : {}", getRating());
        log.info("Status    : {}", getAccountStatus());
        log.info("Penalized : {}", hasEverBeenPenalized);
        log.info("Restored  : {}", hasEverBeenRestored);
        log.info("======================================");
    }
}