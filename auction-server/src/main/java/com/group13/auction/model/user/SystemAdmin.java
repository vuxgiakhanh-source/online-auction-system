package com.group13.auction.model.user;

import com.group13.auction.manager.AuctionManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin hệ thống — tài khoản đặc biệt duy nhất được seed sẵn trong DB.
 *
 * <p>Trách nhiệm tự động (không cần con người điều khiển):
 * <ul>
 *   <li>Auto-ban tài khoản có rating dưới ngưỡng tối thiểu ({@value #MIN_ELIGIBLE_RATING}).</li>
 *   <li>Auto-cancel phiên không có winner hoặc reserve not met — ghi log vào chính mình.</li>
 * </ul>
 *
 * <p>Singleton: chỉ tồn tại duy nhất một instance trong toàn hệ thống.
 * Được khởi tạo qua {@link #"bootstrap"()} khi ứng dụng start.
 *
 * <p>Email cài sẵn: {@value #SYSTEM_EMAIL} — không thể thay đổi.
 * Username cài sẵn: {@value #SYSTEM_USERNAME}.
 */
public final class SystemAdmin extends Admin {

    // ── Hằng số hệ thống ──────────────────────────────────────────────────

    /** Email cài sẵn của tài khoản hệ thống — không được dùng cho user khác. */
    public static final String SYSTEM_EMAIL    = "system@auction.internal";

    /** Username cài sẵn. */
    public static final String SYSTEM_USERNAME = "SYSTEM";

    /**
     * Rating tối thiểu để tài khoản hoạt động.
     * Tài khoản có rating < ngưỡng này sẽ bị auto-ban.
     */
    private static final double MIN_ELIGIBLE_RATING = 2.0;

    // ── Singleton ─────────────────────────────────────────────────────────

    private static SystemAdmin INSTANCE;

    // ── Static factory / bootstrap ────────────────────────────────────────

    /**
     * Khởi tạo SystemAdmin lần đầu khi ứng dụng start (seed).
     * Chỉ được gọi một lần duy nhất — thường từ tầng bootstrap/config.
     *
     * @param rawPassword mật khẩu thô cho tài khoản system
     * @return SystemAdmin instance
     * @throws IllegalStateException nếu đã gọi bootstrap trước đó
     */
    public static synchronized SystemAdmin bootstrap(String rawPassword) {
        if (INSTANCE != null) {
            throw new IllegalStateException(
                    "SystemAdmin đã được khởi tạo. Chỉ gọi bootstrap() một lần.");
        }
        INSTANCE = new SystemAdmin(rawPassword);
        // Đăng ký vào AuctionManager như một global observer
        AuctionManager.getInstance().registerUser(INSTANCE);
        System.out.println("[SYSTEM] SystemAdmin khởi tạo thành công.");
        return INSTANCE;
    }

    /**
     * Hồi sinh SystemAdmin từ DB — chỉ DAO gọi khi load lại.
     *
     * @param id              id gốc từ DB
     * @param createdAt       thời gian tạo gốc
     * @param updatedAt       thời gian cập nhật gốc
     * @param hashedPassword  password đã hash
     * @param accountStatus   trạng thái
     * @param rating          rating
     * @return SystemAdmin instance
     * @throws IllegalStateException nếu đã gọi bootstrap trước đó
     */
    public static synchronized SystemAdmin reconstitute(String id,
                                                        LocalDateTime createdAt, LocalDateTime updatedAt,
                                                        String hashedPassword, AccountStatus accountStatus,
                                                        double rating, LocalDateTime suspendedAt) {
        if (INSTANCE != null) {
            throw new IllegalStateException(
                    "SystemAdmin đã được khởi tạo.");
        }
        INSTANCE = new SystemAdmin(id, createdAt, updatedAt,
                hashedPassword, accountStatus, rating, suspendedAt);
        AuctionManager.getInstance().registerUser(INSTANCE);
        return INSTANCE;
    }

    /**
     * Lấy instance SystemAdmin đã được bootstrap.
     *
     * @return SystemAdmin instance
     * @throws IllegalStateException nếu chưa bootstrap
     */
    public static SystemAdmin getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "SystemAdmin chưa được khởi tạo. Gọi bootstrap() trước.");
        }
        return INSTANCE;
    }

    // ── Private constructors ───────────────────────────────────────────────

    private SystemAdmin(String rawPassword) {
        super(SYSTEM_USERNAME, rawPassword, SYSTEM_EMAIL, LEVEL_MASTER);
    }

    private SystemAdmin(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                        String hashedPassword, AccountStatus accountStatus,
                        double rating, LocalDateTime suspendedAt) {
        super(id, createdAt, updatedAt, SYSTEM_USERNAME, hashedPassword,
                SYSTEM_EMAIL, accountStatus, rating, LEVEL_MASTER, suspendedAt);
    }

    // ── Identity override ──────────────────────────────────────────────────

    @Override
    public boolean isSystem() { return true; }

    // ── Auto-ban logic ─────────────────────────────────────────────────────

    /**
     * Quét tất cả user trong hệ thống và tự động ban những tài khoản
     * có rating dưới ngưỡng tối thiểu ({@value #MIN_ELIGIBLE_RATING}).
     *
     * <p>Thường được gọi bởi scheduler định kỳ hoặc sau mỗi thao tác
     * thay đổi rating.
     *
     * <p>Chỉ ban tài khoản đang ACTIVE — SUSPENDED / BANNED không xử lý lại.
     *
     * @param users danh sách user cần kiểm tra (thường từ AuctionManager.getAllUsers())
     */
    public void autoBanLowRatingUsers(List<User> users) {
        if (users == null || users.isEmpty()) return;

        for (User user : users) {
            if (user instanceof Admin) continue; // không ban admin
            if (user.getAccountStatus() != AccountStatus.ACTIVE) continue;
            if (user.getRating() < MIN_ELIGIBLE_RATING) {
                user.setAccountStatus(AccountStatus.BANNED);
                String log = String.format(
                        "[SYSTEM AUTO-BAN] %s bị ban — rating %.1f < %.1f",
                        user.getUsername(), user.getRating(), MIN_ELIGIBLE_RATING);
                addActionLog(log);
                System.out.println(log);
                // TODO: userDAO.update(user)
            }
        }
    }

    /**
     * Tự động ban một user cụ thể nếu rating dưới ngưỡng.
     * Tiện dụng để gọi ngay sau khi RatingService penalize.
     *
     * @param user user cần kiểm tra
     */
    public void autoBanIfNeeded(User user) {
        if (user instanceof Admin) return;
        if (user.getAccountStatus() != AccountStatus.ACTIVE) return;
        if (user.getRating() < MIN_ELIGIBLE_RATING) {
            user.setAccountStatus(AccountStatus.BANNED);
            String log = String.format(
                    "[SYSTEM AUTO-BAN] %s bị ban — rating %.1f < %.1f",
                    user.getUsername(), user.getRating(), MIN_ELIGIBLE_RATING);
            addActionLog(log);
            System.out.println(log);
            // TODO: userDAO.update(user)
        }
    }

    @Override
    public void printInfo() {
        System.out.println("=== SYSTEM ADMIN =====================");
        System.out.printf("Username    : %s%n", getUsername());
        System.out.printf("Email       : %s%n", getEmail());
        System.out.printf("Level       : %s [SYSTEM — DUY NHẤT]%n", getAdminLevel());
        System.out.printf("Hành động   : %d lần%n", getActionLog().size());
        System.out.println("======================================");
    }
}
