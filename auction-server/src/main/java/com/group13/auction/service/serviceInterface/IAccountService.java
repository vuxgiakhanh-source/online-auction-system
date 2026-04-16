package com.group13.auction.service.serviceInterface;

import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;

/**
 * Hợp đồng quản lý tài khoản: ban, deposit, withdraw, tạo admin STAFF, quản lý role.
 */
public interface IAccountService {

  /**
   * Ban tài khoản với lý do cụ thể — chỉ Admin gọi.
   *
   * @param admin admin thực hiện
   * @param target user bị ban
   * @param reason lý do ban
   */
  void banUser(Admin admin, User target, Admin.BanReason reason);

  /**
   * Nạp tiền vào tài khoản NormalUser.
   *
   * @param user user cần nạp
   * @param amount số tiền (phải > 0)
   * @throws IllegalArgumentException nếu amount <= 0
   */
  void deposit(NormalUser user, double amount);

  /**
   * Rút tiền từ tài khoản NormalUser.
   *
   * <p>Chỉ rút được phần availableBalance (không rút tiền đang khóa cọc).
   * Bắt buộc rút hết trước khi xóa tài khoản.
   *
   * @param user user cần rút
   * @param amount số tiền rút (phải > 0)
   * @throws IllegalArgumentException nếu amount <= 0 hoặc vượt số dư khả dụng
   * @throws IllegalStateException nếu tài khoản không đủ điều kiện
   */
  void withdraw(NormalUser user, double amount);

  /**
   * SystemAdmin tạo tài khoản Admin STAFF mới.
   * Chỉ SystemAdmin (MASTER) mới được tạo admin.
   * AdminFactory tuyệt đối chỉ được cấp bởi System.
   *
   * @param username username admin mới
   * @param password password
   * @param email email (chưa được dùng cho NormalUser)
   * @return Admin STAFF mới
   * @throws IllegalArgumentException nếu email đã tồn tại cho NormalUser
   */
  Admin createStaffAdmin(String username, String password, String email);

  /**
   * Hệ thống tự động duyệt role Seller nếu user chưa từng bị trừ rating.
   *
   * @param user user muốn trở thành seller
   * @throws IllegalStateException nếu user đã từng bị penalize
   */
  void autoApproveSellerRole(NormalUser user);

  /**
   * User tự xóa tài khoản của mình.
   * Soft-delete: ban + giữ rating cũ.
   * Bắt buộc số dư phải bằng 0 và không còn cọc đang khóa.
   *
   * @param user user muốn xóa tài khoản
   * @throws IllegalStateException nếu còn số dư hoặc cọc đang khóa
   */
  void deleteAccount(NormalUser user);
}