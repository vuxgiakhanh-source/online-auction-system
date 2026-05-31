package com.group13.auction.service;

import com.group13.auction.dao.AccountBanDAO;
import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.dao.SellerDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.AdminFactory;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.StaffObserver;
import com.group13.auction.service.iservice.IAccountService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.dao.AdminDAO.AdminRow;
import com.group13.auction.service.seller.SellerSanctionCoordinator;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quản lý trạng thái tài khoản: ban, deposit, tạo admin STAFF, quản lý role.
 *
 * <p>Chỉ SystemAdmin (MASTER) mới được tạo admin STAFF qua {@link #createStaffAdmin}. MASTER duy
 * nhất là {@link SystemAdmin}, được seed sẵn khi bootstrap.
 *
 * <p>Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating.
 */
public class AccountService implements IAccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);

  private final IRatingService ratingService;
  private final AdminFactory adminFactory;
  private final WalletService walletService;

  private final UserDAO userDAO;
  private final SellerDAO sellerDAO;
  private final AdminDAO adminDAO;
  private final AuctionDAO auctionDAO;
  private final AuctionWinnerDAO auctionWinnerDAO;
  private final NotificationDAO notificationDAO;
  private final AccountBanDAO accountBanDAO;

  /** Khởi tạo service với DAO mặc định cho notification và account ban. */
  public AccountService(
      IRatingService ratingService,
      UserDAO userDAO,
      SellerDAO sellerDAO,
      AdminDAO adminDAO,
      AuctionDAO auctionDAO,
      AuctionWinnerDAO auctionWinnerDAO) {
    this(
        ratingService,
        userDAO,
        sellerDAO,
        adminDAO,
        auctionDAO,
        auctionWinnerDAO,
        new NotificationDAO(),
        new AccountBanDAO());
  }

  /** Khởi tạo service với NotificationDAO tùy chỉnh và AccountBanDAO mặc định. */
  public AccountService(
      IRatingService ratingService,
      UserDAO userDAO,
      SellerDAO sellerDAO,
      AdminDAO adminDAO,
      AuctionDAO auctionDAO,
      AuctionWinnerDAO auctionWinnerDAO,
      NotificationDAO notificationDAO) {
    this(
        ratingService,
        userDAO,
        sellerDAO,
        adminDAO,
        auctionDAO,
        auctionWinnerDAO,
        notificationDAO,
        new AccountBanDAO());
  }

  /** Khởi tạo service đầy đủ dependency để thuận tiện cho test/integration. */
  public AccountService(
      IRatingService ratingService,
      UserDAO userDAO,
      SellerDAO sellerDAO,
      AdminDAO adminDAO,
      AuctionDAO auctionDAO,
      AuctionWinnerDAO auctionWinnerDAO,
      NotificationDAO notificationDAO,
      AccountBanDAO accountBanDAO) {
    this.ratingService = ratingService;
    this.adminFactory = new AdminFactory();
    this.userDAO = userDAO;
    this.sellerDAO = sellerDAO;
    this.adminDAO = adminDAO;
    this.auctionDAO = auctionDAO;
    this.auctionWinnerDAO = auctionWinnerDAO;
    this.notificationDAO = notificationDAO;
    this.accountBanDAO = accountBanDAO;
    this.walletService = new WalletService(new FinancialTransactionDAO(), userDAO, ratingService);
  }

  public void deposit(NormalUser user, long amount) {
    walletService.deposit(user, amount);
  }

  public void withdraw(NormalUser user, long amount) {
    walletService.withdraw(user, amount);
  }

  // ── Ban ───────────────────────────────────────────────────────────────────

  /** Ban tài khoản với lý do cụ thể — chỉ Admin gọi. */
  @Override
  public void banUser(Admin admin, User target, Admin.BanReason reason) {
    if (reason == null) {
      throw new IllegalArgumentException("Lí do ban không được null");
    }
    target.setAccountStatus(AccountStatus.BANNED);
    String entry =
        String.format(
            "[ACCOUNT] %s ban %s | Lý do: %s", admin.getUsername(), target.getUsername(), reason);
    admin.addActionLog(entry);
    log.info(
        "Ban user: admin={} target={} reason={}",
        admin.getUsername(),
        target.getUsername(),
        reason);

    userDAO.updateAccountStatus(target.getId(), AccountStatus.BANNED.name());

    accountBanDAO.insertBan(
        target.getId(), admin.getId(), admin.getUsername(), reason.name(), null);

    saveNotification(
        target.getId(),
        null,
        "Tài khoản bị khoá",
        String.format("Tài khoản của bạn đã bị khoá bởi quản trị viên. Lý do: %s.", reason));

    SellerSanctionCoordinator coordinator = SellerSanctionCoordinator.getInstance();
    if (coordinator != null) {
      coordinator.onAccountSanctioned(target, AccountStatus.BANNED);
    }
  }

  /** Mở khóa tài khoản — chỉ Admin gọi; đóng bản ghi {@code account_bans} active. */
  public void unbanUser(Admin admin, User target) {
    if (admin == null || target == null) {
      throw new IllegalArgumentException("Admin và target không được null");
    }
    target.setAccountStatus(AccountStatus.ACTIVE);
    userDAO.updateAccountStatus(target.getId(), AccountStatus.ACTIVE.name());
    accountBanDAO.closeActiveBans(target.getId(), admin.getId(), admin.getUsername());

    String entry =
        String.format("[ACCOUNT] %s unban %s", admin.getUsername(), target.getUsername());
    admin.addActionLog(entry);
    log.info("Unban user: admin={} target={}", admin.getUsername(), target.getUsername());

    saveNotification(
        target.getId(),
        null,
        "Tài khoản được mở khóa",
        "Tài khoản của bạn đã được quản trị viên mở khóa.");
  }

  /** Ghi nhận khóa tự động bởi hệ thống (không có admin). */
  public void recordSystemBan(User target, Admin.BanReason reason) {
    if (target == null || reason == null) {
      return;
    }
    accountBanDAO.insertBan(target.getId(), null, "SYSTEM", reason.name(), null);
  }

  public AccountBanDAO accountBanDAO() {
    return accountBanDAO;
  }

  // ── Admin STAFF ───────────────────────────────────────────────────────────

  /** Tạo tài khoản Admin STAFF mới — chỉ SystemAdmin gọi method này. */
  @Override
  public Admin createStaffAdmin(String username, String password, String email) {
    final SystemAdmin system = SystemAdmin.getInstance();

    Admin newAdmin = (Admin) adminFactory.createUser(username, password, email);

    boolean success =
        adminDAO.createAdmin(
            newAdmin.getId(),
            newAdmin.getUsername(),
            newAdmin.getHashedPassword(),
            newAdmin.getEmail(),
            "STAFF");

    if (!success) {
      throw new RuntimeException("Hệ thống lỗi: Không thể tạo Admin trong cơ sở dữ liệu.");
    }

    AuctionManager.getInstance().addStaffObserver(new StaffObserver(newAdmin));
    AuctionManager.getInstance().registerUser(newAdmin);

    String entry = String.format("[SYSTEM] Tạo admin STAFF: %s", username);
    system.addActionLog(entry);
    log.info("Tạo admin STAFF: username={}", username);

    return newAdmin;
  }

  /** Lấy toàn bộ Staff Admin từ DB — không phụ thuộc bộ nhớ in-memory. */
  @Override
  public List<Admin> getAllStaffAdmins() {
    return adminDAO.findAll().stream()
        .filter(row -> Admin.LEVEL_STAFF.equals(row.level()))
        .map(AccountService::adminFromRow)
        .toList();
  }

  private static Admin adminFromRow(AdminRow row) {
    LocalDateTime created = row.createdAt() != null ? row.createdAt() : LocalDateTime.now();
    return Admin.reconstitute(
        row.id(),
        created,
        created,
        row.username(),
        row.passwordHash(),
        row.email(),
        AccountStatus.ACTIVE,
        5.0,
        row.level(),
        null);
  }

  // ── Seller role ───────────────────────────────────────────────────────────

  /** Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating. */
  @Override
  public void autoApproveSellerRole(NormalUser user) {
    if (!ratingService.isEligible(user)) {
      throw new IllegalStateException(
          "User không đủ điều kiện để thêm role Seller (tài khoản bị khóa hoặc rating thấp).");
    }
    if (user.isHasEverBeenPenalized()) {
      throw new IllegalStateException(
          "User đã từng bị trừ rating — không đủ điều kiện tự động duyệt role Seller.");
    }
    if (user.hasRole(User.UserRole.SELLER)) {
      log.info("{} đã có role Seller.", user.getUsername());
      return;
    }

    user.addRole(User.UserRole.SELLER);
    String entry =
        String.format("[SYSTEM AUTO-APPROVE] Duyệt role Seller cho: %s", user.getUsername());
    SystemAdmin.getInstance().addActionLog(entry);
    log.info("Auto-approve role Seller: user={}", user.getUsername());

    sellerDAO.approveSellerRole(user.getId());

    saveNotification(
        user.getId(),
        null,
        "Đăng ký Seller thành công",
        "Yêu cầu trở thành Seller của bạn đã được duyệt. Bạn có thể tạo phiên đấu giá ngay bây"
            + " giờ.");
  }

  // ── Seller cancel request ─────────────────────────────────────────────────

  /** Seller gửi yêu cầu hủy phiên đấu giá lên hệ thống. */
  public void requestCancelAuction(NormalUser seller, Auction auction, String reason) {
    if (!seller.hasRole(User.UserRole.SELLER)) {
      throw new IllegalArgumentException("Chỉ Seller mới có thể yêu cầu hủy phiên.");
    }
    if (auction.getItem() == null
        || auction.getItem().getSeller() == null
        || !auction.getItem().getSeller().getId().equals(seller.getId())) {
      throw new IllegalArgumentException("Seller không sở hữu phiên đấu giá này.");
    }
    if (auction.getStatus() != Auction.AuctionStatus.OPEN) {
      throw new IllegalStateException(
          "Phiên đấu giá không thể yêu cầu hủy ở trạng thái: " + auction.getStatus());
    }

    AuctionEvent cancelRequestEvent =
        new AuctionEvent(
            AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
            auction,
            null,
            0L,
            String.format("Seller %s yêu cầu hủy: %s", seller.getUsername(), reason));
    AuctionManager.getInstance().notifyStaffObservers(cancelRequestEvent);
    AuctionManager.getInstance().notifyGlobalObservers(cancelRequestEvent);

    log.info(
        "Seller gửi yêu cầu hủy phiên: seller={} auctionId={} reason={}",
        seller.getUsername(),
        auction.getId(),
        reason);
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private void saveNotification(String userId, String auctionId, String title, String body) {
    try {
      notificationDAO.save(Notification.create(userId, auctionId, title, body));
    } catch (Exception e) {
      log.warn("Không thể lưu notification: userId={}, title={}", userId, title, e);
    }
  }
}
