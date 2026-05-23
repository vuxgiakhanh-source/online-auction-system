package com.group13.auction.model.user;

import com.group13.auction.dao.AccountBanDAO;
import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.observer.SystemAdminObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SystemAdmin — MASTER duy nhất trong hệ thống (Singleton).
 *
 * <h3>Cải tiến v2:</h3>
 * <ul>
 *   <li>Logging chuẩn SLF4J (xóa System.out/err.println).</li>
 *   <li>printInfo() dùng log.info thay vì System.out.</li>
 * </ul>
 *
 * <h3>Fix v3:</h3>
 * <ul>
 *   <li>bootstrap() kiểm tra sự tồn tại của SYSTEM trong bảng {@code admins}
 *       (thay vì bảng {@code users}) để tránh Duplicate entry khi restart.</li>
 * </ul>
 *
 * <h3>Fix v4 (Qodana):</h3>
 * <ul>
 *   <li>[Non-distinguishable logging] Hai log.error trong autoBanIfNeeded() và
 *       banUserByStaff() có message giống nhau, Qodana không phân biệt được nguồn.
 *       Đã thêm context "source=autoBan" / "source=staffBan" để phân biệt.</li>
 * </ul>
 */
public class SystemAdmin extends Admin {

    private static final Logger log = LoggerFactory.getLogger(SystemAdmin.class);

    public static final double MIN_ELIGIBLE_RATING = 2.0;

    private static SystemAdmin INSTANCE;

    private UserDAO userDAO;
    private AccountBanDAO accountBanDAO;

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    public static synchronized SystemAdmin bootstrap(String password) {
        if (INSTANCE == null) {
            UserDAO userDAO   = new UserDAO();
            AdminDAO adminDAO = new AdminDAO();

            boolean existsInDb = adminDAO.existsByUsername("SYSTEM");

            INSTANCE = new SystemAdmin("SYSTEM", password, "system@auction.com");

            if (existsInDb) {
                log.info("SystemAdmin đã tồn tại trong DB — load lên bộ nhớ.");
            } else {
                boolean saved = adminDAO.createAdmin(
                        INSTANCE.getId(),
                        INSTANCE.getUsername(),
                        INSTANCE.getHashedPassword(),
                        INSTANCE.getEmail(),
                        LEVEL_MASTER
                );
                if (saved) {
                    log.info("SystemAdmin khởi tạo lần đầu và đã lưu vào DB.");
                } else {
                    log.warn("Không thể lưu SystemAdmin vào DB!");
                }
            }

            INSTANCE.userDAO = userDAO;
            INSTANCE.accountBanDAO = new AccountBanDAO();
            AuctionManager.getInstance().addGlobalObserver(new SystemAdminObserver(INSTANCE));
            AuctionManager.getInstance().addToUserList(INSTANCE);
        }
        return INSTANCE;
    }

    public static SystemAdmin getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "SystemAdmin chưa được bootstrap. Gọi SystemAdmin.bootstrap() khi app khởi động.");
        }
        return INSTANCE;
    }

    private SystemAdmin(String username, String password, String email) {
        super(username, password, email, LEVEL_MASTER);
    }

    @Override
    public boolean isSystem() { return true; }

    // ── Auto-ban logic ────────────────────────────────────────────────────────

    /**
     * Tự động ban user nếu rating dưới ngưỡng.
     */
    public void autoBanIfNeeded(User user) {
        if (user instanceof Admin) return;
        if (user.getAccountStatus() != AccountStatus.ACTIVE) return;
        if (user.getRating() < MIN_ELIGIBLE_RATING) {
            user.setAccountStatus(AccountStatus.BANNED);
            String msg = String.format("[SYSTEM AUTO-BAN] %s bị ban — rating %.1f < %.1f",
                    user.getUsername(), user.getRating(), MIN_ELIGIBLE_RATING);
            addActionLog(msg);
            log.warn("AUTO-BAN: username={} rating={}", user.getUsername(), user.getRating());

            if (userDAO != null) {
                boolean updated = userDAO.updateAccountStatus(user.getId(), AccountStatus.BANNED.name());
                if (!updated) {
                    // FIX QODANA [Non-distinguishable logging]: thêm source=autoBan
                    // để phân biệt với log.error cùng nội dung trong banUserByStaff().
                    log.error("Không thể persist ban cho user: username={} source=autoBan",
                            user.getUsername());
                } else if (accountBanDAO != null) {
                    accountBanDAO.insertBan(
                            user.getId(), null, "SYSTEM",
                            BanReason.SYSTEM_AUTO.name(), null);
                }
            }
        }
    }

    /**
     * Staff Admin thực hiện ban user.
     */
    public void banUserByStaff(Admin staff, User user, Admin.BanReason reason) {
        if (user instanceof Admin) return;
        user.setAccountStatus(AccountStatus.BANNED);

        String staffLog = String.format("[STAFF BAN] %s ban %s | Lý do: %s",
                staff.getUsername(), user.getUsername(), reason);
        staff.addActionLog(staffLog);
        log.info("STAFF BAN: staff={} target={} reason={}", staff.getUsername(), user.getUsername(), reason);

        String auditLog = String.format("[AUDIT] Staff %s ban %s | Lý do: %s",
                staff.getUsername(), user.getUsername(), reason);
        this.addActionLog(auditLog);

        if (userDAO != null) {
            boolean updated = userDAO.updateAccountStatus(user.getId(), AccountStatus.BANNED.name());
            if (!updated) {
                // FIX QODANA [Non-distinguishable logging]: thêm source=staffBan và staff info
                // để phân biệt với log.error cùng nội dung trong autoBanIfNeeded().
                log.error("Không thể persist ban cho user: username={} source=staffBan staff={}",
                        user.getUsername(), staff.getUsername());
            } else if (accountBanDAO != null) {
                accountBanDAO.insertBan(
                        user.getId(), staff.getId(), staff.getUsername(),
                        reason.name(), null);
            }
        }
    }

    @Override
    public void printInfo() {
        log.info("=== SYSTEM ADMIN INFO === username={} email={} level={} actionCount={}",
                getUsername(), getEmail(), getAdminLevel(), getActionLog().size());
    }
}