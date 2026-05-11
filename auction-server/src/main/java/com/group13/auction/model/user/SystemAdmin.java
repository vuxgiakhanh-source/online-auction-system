package com.group13.auction.model.user;

import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.observer.SystemAdminObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SystemAdmin — MASTER duy nhất trong hệ thống (Singleton).
 *
 * <h3>Cải tiến v2:</h3>
 * <ul>
 *   <li>Logging chuẩn SLF4J (xóa System.out/err.println).</li>
 *   <li>printInfo() dùng log.info thay vì System.out.</li>
 * </ul>
 */
public class SystemAdmin extends Admin {

    private static final Logger log = LoggerFactory.getLogger(SystemAdmin.class);

    public static final double MIN_ELIGIBLE_RATING = 2.0;

    private static SystemAdmin INSTANCE;

    private UserDAO userDAO;

    // ── Bootstrap ─────────────────────────────────────────────────────────────

    public static synchronized SystemAdmin bootstrap(String password) {
        if (INSTANCE == null) {
            UserDAO userDAO = new UserDAO();
            AdminDAO adminDAO = new AdminDAO();

            NormalUser existing = userDAO.findUserByUsername("SYSTEM");

            if (existing != null) {
                INSTANCE = new SystemAdmin("SYSTEM", password, "system@auction.com");
                log.info("SystemAdmin đã tồn tại trong DB — load lên bộ nhớ.");
            } else {
                INSTANCE = new SystemAdmin("SYSTEM", password, "system@auction.com");
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
                    log.error("Không thể persist ban cho user: username={}", user.getUsername());
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
                log.error("Không thể persist ban cho user: username={}", user.getUsername());
            }
        }
    }

    @Override
    public void printInfo() {
        log.info("=== SYSTEM ADMIN INFO === username={} email={} level={} actionCount={}",
                getUsername(), getEmail(), getAdminLevel(), getActionLog().size());
    }
}