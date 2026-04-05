package com.group13.auction.service.account;

import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;

/**
 * Quản lý trạng thái tài khoản: ban, deposit, tạo admin.
 * Tách khỏi UserService để tuân thủ SRP.
 * TODO: inject UserDAO để persist xuống DB.
 */
public class AccountService implements IAccountService {

  private final IRatingService ratingService;

  /**
   * Nhận {@link IRatingService} qua constructor — không new cứng (DIP).
   * @param ratingService dùng để kiểm tra eligibility trước khi thực hiện
   */
  public AccountService(IRatingService ratingService) {
    this.ratingService = ratingService;
  }

  // ── Ban / Suspend ──────────────────────────────────────────────────────────

  /**
   * Ban tài khoản với lý do cụ thể — chỉ Admin gọi.
   * TODO: userDAO.update(target).
   *
   * @param admin  admin thực hiện
   * @param target user bị ban
   * @param reason lý do ban
   */
  public void banUser(Admin admin, User target, Admin.BanReason reason) {
    target.setAccountStatus(AccountStatus.BANNED);
    String log = String.format("[ACCOUNT] %s ban %s | Lý do: %s",
        admin.getUsername(), target.getUsername(), reason);
    admin.addActionLog(log);
    System.out.println(log);
    // TODO: userDAO.update(target)
  }

  // ── Deposit ────────────────────────────────────────────────────────────────

  /**
   * Nạp tiền vào tài khoản Bidder.
   * TODO: userDAO.update(bidder).
   *
   * @param bidder bidder cần nạp
   * @param amount số tiền nạp (phải > 0)
   * @throws IllegalStateException nếu tài khoản bị khóa hoặc
   * điểm rating quá thấp
   * @throws IllegalArgumentException nếu amount <= 0
   */
  public void deposit(Bidder bidder, double amount) {
    if (!ratingService.isEligible(bidder)) {
      throw new IllegalStateException(
              "Tài khoản không đủ điều kiện thực hiện giao dịch");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
    }
    bidder.setBalance(bidder.getBalance() + amount);
    System.out.printf("[ACCOUNT] %s nạp %.0f | Số dư mới: %.0f%n",
        bidder.getUsername(), amount, bidder.getBalance());
    // TODO: userDAO.update(bidder)
  }

  // ── Admin creation ─────────────────────────────────────────────────────────

  /**
   * Admin tạo tài khoản Admin mới.
   * Admin chỉ được tạo admin có cấp <= cấp của mình.
   * TODO: userDAO.save(newAdmin).
   *
   * @param creator    admin đang tạo
   * @param username   username admin mới
   * @param password   password
   * @param email      email
   * @param adminLevel cấp độ
   * @return Admin mới
   * @throws IllegalArgumentException nếu cấp vượt quá cấp creator
   */
  public Admin createAdmin(Admin creator, String username,
      String password, String email, int adminLevel) {
    if (adminLevel > creator.getAdminLevel()) {
      throw new IllegalArgumentException(
          "Không thể tạo admin có cấp cao hơn cấp của mình.");
    }
    Admin newAdmin = Admin.create(username, password, email, adminLevel);
    String log = String.format("[ACCOUNT] %s tạo admin: %s (level %d)",
        creator.getUsername(), username, adminLevel);
    creator.addActionLog(log);
    System.out.println(log);
    // TODO: userDAO.save(newAdmin)
    return newAdmin;
  }
}