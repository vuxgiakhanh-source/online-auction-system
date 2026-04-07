package com.group13.auction.service;

import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.AdminFactory;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.model.user.UserFactory;
import com.group13.auction.observer.AdminObserver;

/**
 * Quản lý trạng thái tài khoản: ban, deposit, tạo admin STAFF, quản lý role.
 * Tách khỏi UserService để tuân thủ SRP.
 *
 * <p>Chỉ SystemAdmin (MASTER) mới được tạo admin STAFF qua
 * {@link #createStaffAdmin}. Không có overload nào cho phép tạo MASTER —
 * MASTER duy nhất là {@link SystemAdmin}, được seed sẵn khi bootstrap.
 *
 * TODO: inject UserDAO để persist xuống DB.
 */
public class AccountService implements IAccountService {

  private final IRatingService ratingService;
  private final AdminFactory   adminFactory;

  /**
   * Nhận {@link IRatingService} qua constructor — không new cứng (DIP).
   *
   * @param ratingService dùng để kiểm tra eligibility trước khi thực hiện
   */
  public AccountService(IRatingService ratingService) {
    this.ratingService = ratingService;
    this.adminFactory  = new AdminFactory();
  }

  // ── Ban ────────────────────────────────────────────────────────────────

  /**
   * Ban tài khoản với lý do cụ thể — chỉ Admin gọi.
   * TODO: userDAO.update(target).
   *
   * @param admin  admin thực hiện
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
    // TODO: userDAO.update(target)
  }

  // ── Deposit ────────────────────────────────────────────────────────────

  /**
   * Nạp tiền vào tài khoản NormalUser.
   * TODO: userDAO.update(user).
   *
   * @param user   user cần nạp
   * @param amount số tiền nạp (phải > 0)
   * @throws IllegalStateException    nếu tài khoản bị khóa hoặc rating quá thấp
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
    // TODO: userDAO.update(user)
  }

  // ── Admin STAFF creation ───────────────────────────────────────────────

  /**
   * Tạo tài khoản Admin STAFF mới — chỉ SystemAdmin gọi method này.
   *
   * <p>Điều kiện:
   * <ol>
   *   <li>SystemAdmin phải đã được bootstrap (guard qua {@link SystemAdmin#getInstance()}).</li>
   *   <li>Email không được dùng trước bởi NormalUser (Bidder/Seller).
   *       Nếu đã dùng → ném exception, yêu cầu xóa tài khoản trước.</li>
   *   <li>Admin tạo ra luôn là STAFF — không thể tạo MASTER qua đây.</li>
   * </ol>
   *
   * TODO: userDAO.save(newAdmin).
   *
   * @param username username admin STAFF mới
   * @param password password
   * @param email    email chưa từng dùng cho NormalUser
   * @return Admin STAFF mới
   * @throws IllegalArgumentException nếu email đã tồn tại cho NormalUser
   * @throws IllegalStateException    nếu SystemAdmin chưa được bootstrap
   */
  @Override
  public Admin createStaffAdmin(String username, String password, String email) {
    // Guard: đảm bảo SystemAdmin đã tồn tại
    SystemAdmin system = SystemAdmin.getInstance();

    if (UserFactory.isEmailAlreadyUsed(email)) {
      throw new IllegalArgumentException(
              "Email này đã được dùng để đăng ký Bidder/Seller. "
                      + "Phải xóa tài khoản đó trước khi đăng ký Admin.");
    }

    Admin newAdmin = (Admin) adminFactory.createUser(username, password, email);
    // Thêm global observer cho admin mới
    AuctionManager.getInstance().addGlobalObserver(new AdminObserver(newAdmin));
    AuctionManager.getInstance().registerUser(newAdmin);

    String log = String.format("[SYSTEM] Tạo admin STAFF: %s", username);
    system.addActionLog(log);
    System.out.println(log);
    // TODO: userDAO.save(newAdmin)
    return newAdmin;
  }

  // ── Seller role approval ───────────────────────────────────────────────

  /**
   * Admin (STAFF hoặc SYSTEM) phê duyệt yêu cầu thêm role Seller.
   * Điều kiện: user đang ACTIVE và có rating đủ.
   * TODO: userDAO.update(user).
   *
   * @param admin admin phê duyệt
   * @param user  user muốn trở thành seller
   * @throws IllegalStateException nếu user không đủ điều kiện
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
    String log = String.format("[ACCOUNT] %s phê duyệt role Seller cho: %s",
            admin.getUsername(), user.getUsername());
    admin.addActionLog(log);
    System.out.println(log);
    // TODO: userDAO.update(user)
  }

  // ── Delete account ─────────────────────────────────────────────────────

  /**
   * User tự xóa tài khoản của mình (soft-delete).
   * Giải phóng username/email để có thể đăng ký Admin sau này.
   * TODO: userDAO.delete(user).
   *
   * @param user user muốn xóa tài khoản
   */
  @Override
  public void deleteAccount(NormalUser user) {
    user.markDeleted();
    UserFactory.releaseUserIdentity(user.getUsername(), user.getEmail());
    AuctionManager.getInstance().removeUser(user);
    System.out.printf("[ACCOUNT] Tài khoản %s đã bị xóa.%n", user.getUsername());
    // TODO: userDAO.delete(user)
  }
}