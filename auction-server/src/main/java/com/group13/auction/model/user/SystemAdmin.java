package com.group13.auction.model.user;

import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.observer.SystemAdminObserver;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SystemAdmin — MASTER duy nhất trong hệ thống.
 * <p>Design pattern: Singleton
 *
 * <p>Nếu SystemAdmin đã được seed trong DB,
 * {@link #bootstrap(String)} sẽ load từ DB qua DAO rồi gán vào instance -
 * không tạo object mới. Chỉ tạo mới nếu chưa có trong DB (lần đầu boot).
 * bootstrap() gọi 1 lần duy nhất khi app khởi động.
 *
 * <p>Automation thuộc về SystemAdmin:
 * <ul>
 * <li>Auto-cancel khi không có winner.</li>
 * <li>Auto-ban khi rating quá thấp.</li>
 * <li>Auto duyệt role Seller nếu đủ điều kiện.</li>
 * <li>Tạo tài khoản Staff Admin qua AdminFactory.</li>
 * </ul>
 *
 * <p>Overload method có tham số Staff Admin để lỡ có việc cần người cụ thể đi kiểm tra.
 */
public class SystemAdmin extends Admin {

    /** Ngưỡng rating tối thiểu của Normal User để được hoạt động. */
    public static final double MIN_ELIGIBLE_RATING = 2.0;

    private static SystemAdmin INSTANCE;

    // Bootstrap

    /**
     * Khởi tạo / load SystemAdmin.
     * Nếu đã seed trong DB -> load lên (không tạo mới).
     * Nếu chưa có -> tạo mới và seed vào DB.
     *
     * <p>Chỉ gọi 1 lần khi app khởi động.
     *
     * @param password mật khẩu (chỉ dùng nếu chưa có trong DB)
     * @return SystemAdmin instance
     */
    public static synchronized SystemAdmin bootstrap(String password) {
        if (INSTANCE == null) {
            // TODO: Kiểm tra DB qua UserDAO.findByUsername("system")
            // Nếu tìm thấy -> reconstitute từ DB, gán vào INSTANCE
            // Nếu không tìm thấy -> tạo mới.
            INSTANCE = new SystemAdmin("SYSTEM", password, "system@auction.com");
            System.out.println("[SYSTEM] SystemAdmin khởi tạo lần đầu.");
            // TODO: userDAO.save(INSTANCE)

            // Đăng ký SystemAdmin làm global observer
            AuctionManager.getInstance().addGlobalObserver(new SystemAdminObserver(INSTANCE));
            AuctionManager.getInstance().registerUser(INSTANCE);
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

    // Constructor - chỉ bootstrap() được gọi

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
            // SystemAdmin chưa inject UserDAO — cần refactor bootstrap() để nhận UserDAO.
        }
    }

    /**
     * Overload: Staff Admin cụ thể thực hiện ban sau khi kiểm tra thủ công.
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
        System.out.println(staffLog);

        String auditLog = String.format("[AUDIT] Staff %s ban %s | Lý do: %s",
                staff.getUsername(), user.getUsername(), reason);
        this.addActionLog(auditLog);
        System.out.println(auditLog);
        // TODO: userDAO.update(user)
        // Tương tự autoBanIfNeeded() — cần inject UserDAO
    }

    @Override
    public void printInfo() {
        System.out.println("THÔNG TIN SYSTEM ADMIN");
        System.out.printf("Username : %s%n", getUsername());
        System.out.printf("Email : %s%n", getEmail());
        System.out.printf("Level : %s [SYSTEM — DUY NHẤT]%n", getAdminLevel());
        System.out.printf("Hành động : %d lần%n", getActionLog().size());
        System.out.println("======================================");
    }
}