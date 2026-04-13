package com.group13.auction.service;

import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.SellerDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.AdminFactory;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.model.user.UserFactory;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.StaffObserver;
import com.group13.auction.service.serviceInterface.IAccountService;
import com.group13.auction.service.serviceInterface.IRatingService;

/**
 * Quản lý trạng thái tài khoản: ban, deposit, tạo admin STAFF, quản lý role.
 * Tách khỏi UserService để tuân thủ SRP.
 *
 * <p>Chỉ SystemAdmin (MASTER) mới được tạo admin STAFF qua
 * {@link #createStaffAdmin}. Không có overload nào cho phép tạo MASTER —
 * MASTER duy nhất là {@link SystemAdmin}, được seed sẵn khi bootstrap.
 *
 * <p>Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating.
 */
public class AccountService implements IAccountService {

  private final IRatingService ratingService;
  private final AdminFactory adminFactory;

  private final UserDAO userDAO;
  private final SellerDAO sellerDAO;
  private final AdminDAO adminDAO;

  /**
   * Nhận dependencies qua constructor (DIP).
   */
  public AccountService(IRatingService ratingService, UserDAO userDAO, SellerDAO sellerDAO, AdminDAO adminDAO) {
    this.ratingService = ratingService;
    this.adminFactory = new AdminFactory();
    this.userDAO = userDAO;
    this.sellerDAO = sellerDAO;
    this.adminDAO = adminDAO;
  }

  // ── Ban ────────────────────────────────────────────────────────────────────

  /**
   * Ban tài khoản với lý do cụ thể — chỉ Admin gọi.
   *
   * @param admin admin thực hiện
   * @param target user bị ban
   * @param reason lý do ban
   */
  @Override
  public void banUser(Admin admin, User target, Admin.BanReason reason) {
    target.setAccountStatus(AccountStatus.BANNED);
    String log = String.format("[ACCOUNT] %s ban %s | Lý do: %s",
            admin.getUsername(), target.getUsername(), reason);
    admin.addActionLog(log);
    System.out.println(log);

    // Gọi DAO để cập nhật DB
    userDAO.updateAccountStatus(target.getId(), AccountStatus.BANNED.name());
  }

  // ── Deposit ────────────────────────────────────────────────────────────────

  /**
   * Nạp tiền vào tài khoản NormalUser.
   *
   * @param user user cần nạp
   * @param amount số tiền nạp (phải > 0)
   * @throws IllegalStateException nếu tài khoản bị khóa hoặc rating quá thấp
   * @throws IllegalArgumentException nếu amount <= 0
   */
  @Override
  public void deposit(NormalUser user, double amount) {
    if (!ratingService.isEligible(user)) {
      throw new IllegalStateException(
              "Tài khoản không đủ điều kiện thực hiện giao dịch.");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
    }
    user.setBalance(user.getBalance() + amount);
    System.out.printf("[ACCOUNT] %s nạp %.0f | Số dư mới: %.0f%n",
            user.getUsername(), amount, user.getBalance());

    // Gọi DAO để cộng tiền dưới DB
    userDAO.addBalance(user.getId(), amount);
  }

  // ── Admin STAFF creation ───────────────────────────────────────────────────

  /**
   * Tạo tài khoản Admin STAFF mới — chỉ SystemAdmin gọi method này.
   * AdminFactory tuyệt đối chỉ được cấp bởi System — không được tạo ở ngoài.
   */
  @Override
  public Admin createStaffAdmin(String username, String password, String email) {
    SystemAdmin system = SystemAdmin.getInstance();

    if (UserFactory.isEmailAlreadyUsed(email)) {
      throw new IllegalArgumentException("Email này đã được dùng...");
    }

    // 1. Khai sinh Object trên RAM trước (Entity sẽ tự động sinh UUID cho biến id final)
    Admin newAdmin = (Admin) adminFactory.createUser(username, password, email);

    // 2. Lấy ID và thông tin vừa tạo lưu xuống Database
    boolean success = adminDAO.createAdmin(
            newAdmin.getId(),       // Lấy UUID do Java tự sinh
            newAdmin.getUsername(),
            newAdmin.getHashedPassword(), // Đảm bảo lấy pass đã hash từ Object
            newAdmin.getEmail(),
            "STAFF"
    );

    if (!success) {
      throw new RuntimeException("Hệ thống lỗi: Không thể tạo Admin trong cơ sở dữ liệu.");
    }

    // 3. Đăng ký Observer...
    AuctionManager.getInstance().addStaffObserver(new StaffObserver(newAdmin));
    AuctionManager.getInstance().registerUser(newAdmin);

    String log = String.format("[SYSTEM] Tạo admin STAFF: %s", username);
    system.addActionLog(log);
    System.out.println(log);

    return newAdmin;
  }

  // ── Seller role approval ───────────────────────────────────────────────────

  /**
   * Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating.
   */
  @Override
  public void autoApproveSellerRole(NormalUser user) {
    if (!ratingService.isEligible(user)) {
      throw new IllegalStateException(
              "User không đủ điều kiện để thêm role Seller (tài khoản bị khóa hoặc rating thấp).");
    }
    if (user.isHasEverBeenPenalized()) {
      throw new IllegalStateException(
              "User đã từng bị trừ rating — không đủ điều kiện tự động duyệt role Seller. "
                      + "Cần Admin xem xét thủ công.");
    }
    if (user.hasRole(User.UserRole.SELLER)) {
      System.out.printf("[ACCOUNT] %s đã có role Seller.%n", user.getUsername());
      return;
    }

    user.addRole(User.UserRole.SELLER);
    String log = String.format("[SYSTEM AUTO-APPROVE] Duyệt role Seller cho: %s", user.getUsername());
    SystemAdmin.getInstance().addActionLog(log);
    System.out.println(log);

    // Gọi DAO để cập nhật DB
    sellerDAO.approveSellerRole(user.getId());
  }

  /**
   * Admin STAFF duyệt thủ công role Seller (dùng khi user đã từng bị penalize).
   */
  @Override
  public void approveSellerRole(Admin admin, NormalUser user) {
    if (!ratingService.isEligible(user)) {
      throw new IllegalStateException(
              "User không đủ điều kiện để thêm role Seller.");
    }
    if (user.hasRole(User.UserRole.SELLER)) {
      System.out.printf("[ACCOUNT] %s đã có role Seller.%n", user.getUsername());
      return;
    }

    user.addRole(User.UserRole.SELLER);
    String log = String.format("[ACCOUNT] %s phê duyệt thủ công role Seller cho: %s",
            admin.getUsername(), user.getUsername());
    admin.addActionLog(log);
    System.out.println(log);

    // Gọi DAO để cập nhật DB
    sellerDAO.approveSellerRole(user.getId());
  }

  // ── Seller cancel request ──────────────────────────────────────────────────

  /**
   * Seller gửi yêu cầu hủy phiên đấu giá lên hệ thống.
   */
  public void requestCancelAuction(NormalUser seller, Auction auction, String reason) {
    if (!seller.hasRole(User.UserRole.SELLER)) {
      throw new IllegalArgumentException("Chỉ Seller mới có thể yêu cầu hủy phiên.");
    }
    if (!seller.getAllAuctionIds().contains(auction.getId())) {
      throw new IllegalArgumentException("Seller không sở hữu phiên đấu giá này.");
    }
    if (auction.getStatus() != Auction.AuctionStatus.OPEN
            && auction.getStatus() != Auction.AuctionStatus.RUNNING) {
      throw new IllegalStateException("Phiên đấu giá không thể hủy ở trạng thái: " + auction.getStatus());
    }

    // Gửi thông báo đến Staff Admin để xem xét
    AuctionEvent cancelRequestEvent = new AuctionEvent(
            AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
            auction, null, 0,
            String.format("Seller %s yêu cầu hủy: %s", seller.getUsername(), reason));
    AuctionManager.getInstance().notifyStaffObservers(cancelRequestEvent);
    AuctionManager.getInstance().notifyGlobalObservers(cancelRequestEvent);

    System.out.printf("[ACCOUNT] Seller %s gửi yêu cầu hủy phiên %s | Lý do: %s%n",
            seller.getUsername(), auction.getId(), reason);
  }

  // ── Delete account ─────────────────────────────────────────────────────────

  /**
   * User tự xóa tài khoản của mình (soft-delete).
   * Giải phóng username/email để có thể đăng ký Admin sau này.
   */
  @Override
  public void deleteAccount(NormalUser user) {
    user.markDeleted();
    UserFactory.releaseUserIdentity(user.getUsername(), user.getEmail());
    AuctionManager.getInstance().removeUser(user);
    System.out.printf("[ACCOUNT] Tài khoản %s đã bị xóa.%n", user.getUsername());

    // Đánh dấu status = 'DELETED' dưới Database
    userDAO.updateAccountStatus(user.getId(), "DELETED");
  }
}