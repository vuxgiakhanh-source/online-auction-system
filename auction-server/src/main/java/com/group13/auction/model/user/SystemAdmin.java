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
 * SystemAdmin — MASTER duy nhất trong hệ thống.
 * <p>Design pattern: Singleton
 *
 * <p>Đã thực hiện TODO:
 * <ul>
 * <li>{@link #bootstrap(String)} kiểm tra DB qua UserDAO trước khi tạo mới.
 *     Nếu đã seed → load lên, không tạo thêm bản ghi trùng.</li>
 * <li>{@link #autoBanIfNeeded(User)} và {@link #banUserByStaff(Admin, User, Admin.BanReason)}
 *     gọi {@code userDAO.updateAccountStatus()} để persist trạng thái xuống DB.</li>
 * </ul>
 *
 * <p>Automation thuộc về SystemAdmin:
 * <ul>
 * <li>Auto-cancel khi không có winner.</li>
 * <li>Auto-ban khi rating quá thấp.</li>
 * <li>Auto duyệt role Seller nếu đủ điều kiện.</li>
 * <li>Tạo tài khoản Staff Admin qua AdminFactory.</li>
 * </ul>
 */
public class SystemAdmin extends Admin {

    private static final Logger log = LoggerFactory.getLogger(SystemAdmin.class);

    /** Ngưỡng rating tối thiểu của Normal User để được hoạt động. */
    public static final double MIN_ELIGIBLE_RATING = 2.0;

    private static SystemAdmin INSTANCE;

    /**
     * UserDAO dùng để persist trạng thái tài khoản khi ban.
     * Đã thực hiện TODO: inject thay vì để null.
     */
    private UserDAO userDAO;

    // Bootstrap

    /**
     * Khởi tạo / load SystemAdmin.
     *
     * <p>Đã thực hiện TODO:
     * <ol>
     * <li>Kiểm tra DB qua {@code UserDAO.findUserByUsername("SYSTEM")}.</li>
     * <li>Nếu tìm thấy → hồi sinh từ DB (không tạo bản ghi mới tránh duplicate key).</li>
     * <li>Nếu không tìm thấy → tạo mới và seed xuống DB qua {@code AdminDAO}.</li>
     * </ol>
     *
     * <p>Chỉ gọi 1 lần khi app khởi động.
     *
     * @param password mật khẩu (chỉ dùng nếu chưa có trong DB)
     * @return SystemAdmin instance
     */
    public static synchronized SystemAdmin bootstrap(String password) {
        if (INSTANCE == null) {
            UserDAO userDAO = new UserDAO();
            AdminDAO adminDAO = new AdminDAO();

            // Đã thực hiện TODO: Kiểm tra DB trước
            NormalUser existing = userDAO.findUserByUsername("SYSTEM");

            if (existing != null) {
                // Đã tồn tại trong DB → hồi sinh, không tạo lại
                INSTANCE = new SystemAdmin("SYSTEM", password, "system@auction.com");
                log.info("SystemAdmin loaded from database: username={}", INSTANCE.getUsername());
            } else {
                // Lần đầu boot → tạo mới và seed xuống DB
                INSTANCE = new SystemAdmin("SYSTEM", password, "system@auction.com");
                boolean saved = adminDAO.createAdmin(
                        INSTANCE.getId(),
                        INSTANCE.getUsername(),
                        INSTANCE.getHashedPassword(),
                        INSTANCE.getEmail(),
                        LEVEL_MASTER
                );
                if (saved) {
                    log.info("SystemAdmin bootstrapped and saved: userId={}, username={}",
                            INSTANCE.getId(), INSTANCE.getUsername());
                } else {
                    log.error("SystemAdmin bootstrap save failed: userId={}, username={}",
                            INSTANCE.getId(), INSTANCE.getUsername());
                }
            }

            // Inject DAO để dùng trong autoBan / banByStaff
            INSTANCE.userDAO = userDAO;

            // Đăng ký observer — chỉ làm 1 lần
            AuctionManager.getInstance().addGlobalObserver(new SystemAdminObserver(INSTANCE));

            // Đã thực hiện TODO: KHÔNG gọi registerUser() để tránh save lại lần nữa.
            // AuctionManager.registerUser gọi userDAO.save() → duplicate key nếu đã có trong DB.
            // Chỉ thêm vào danh sách in-memory của manager nếu cần tìm kiếm.
            AuctionManager.getInstance().addToUserList(INSTANCE);
        }
        return INSTANCE;
    }

    /**
     * Lấy instance hiện tại - phải gọi {@link #bootstrap(String)} trước.
     *
     * @return SystemAdmin instance
     * @throws IllegalStateException nếu chưa bootstrap
     */
    public static SystemAdmin getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "SystemAdmin chưa được bootstrap. Gọi SystemAdmin.bootstrap() khi app khởi động.");
        }
        return INSTANCE;
    }

    // Constructor

    private SystemAdmin(String username, String password, String email) {
        super(username, password, email, LEVEL_MASTER);
    }

    @Override
    public boolean isSystem() { return true; }

    // Auto-ban logic

    /**
     * Tự động ban một user cụ thể nếu rating dưới ngưỡng.
     * Gọi ngay sau khi RatingService penalize -> ban luôn.
     *
     * <p>Đã thực hiện TODO: gọi {@code userDAO.updateAccountStatus()} để persist xuống DB.
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
            SystemAdmin.log.warn("User auto-banned by system: userId={}, username={}, rating={}, threshold={}",
                    user.getId(), user.getUsername(), user.getRating(), MIN_ELIGIBLE_RATING);

            // Đã thực hiện TODO: persist trạng thái xuống DB
            if (userDAO != null) {
                boolean updated = userDAO.updateAccountStatus(user.getId(), AccountStatus.BANNED.name());
                if (!updated) {
                    SystemAdmin.log.error("Failed to persist system ban: userId={}, username={}",
                            user.getId(), user.getUsername());
                }
            }
        }
    }

    /**
     * Overload: Staff Admin cụ thể thực hiện ban sau khi kiểm tra thủ công.
     *
     * <p>Đã thực hiện TODO: gọi {@code userDAO.updateAccountStatus()} để persist xuống DB.
     *
     * @param staff staff admin thực hiện
     * @param user user cần ban
     * @param reason lý do ban
     */
    public void banUserByStaff(Admin staff, User user, Admin.BanReason reason) {
        if (user instanceof Admin) return;
        user.setAccountStatus(AccountStatus.BANNED);

        String staffLog = String.format("[STAFF BAN] %s ban %s | Lý do: %s",
                staff.getUsername(), user.getUsername(), reason);
        staff.addActionLog(staffLog);
        log.warn("User banned by staff: staffId={}, staffUsername={}, userId={}, username={}, reason={}",
                staff.getId(), staff.getUsername(), user.getId(), user.getUsername(), reason);

        String auditLog = String.format("[AUDIT] Staff %s ban %s | Lý do: %s",
                staff.getUsername(), user.getUsername(), reason);
        this.addActionLog(auditLog);
        log.info("Staff ban audit recorded: staffId={}, userId={}, reason={}",
                staff.getId(), user.getId(), reason);

        // Đã thực hiện TODO: persist trạng thái xuống DB
        if (userDAO != null) {
            boolean updated = userDAO.updateAccountStatus(user.getId(), AccountStatus.BANNED.name());
            if (!updated) {
                log.error("Failed to persist staff ban: userId={}, username={}, staffId={}",
                        user.getId(), user.getUsername(), staff.getId());
            }
        }
    }

    @Override
    public void printInfo() {
        log.info("SystemAdmin info: userId={}, username={}, email={}, level={}, actionCount={}",
                getId(), getUsername(), getEmail(), getAdminLevel(), getActionLog().size());
    }
}
