package com.group13.auction.service;

import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.User;

/**
 * Hợp đồng quản lý tài khoản: ban, deposit, tạo admin.
 */
public interface IAccountService {

  /**
   * Ban tài khoản với lý do cụ thể.
   *
   * @param admin  admin thực hiện
   * @param target user bị ban
   * @param reason lý do ban
   */
  void banUser(Admin admin, User target, Admin.BanReason reason);

  /**
   * Nạp tiền vào tài khoản Bidder.
   *
   * @param bidder bidder cần nạp
   * @param amount số tiền (phải > 0)
   * @throws IllegalArgumentException nếu amount <= 0
   */
  void deposit(Bidder bidder, double amount);

  /**
   * Admin tạo tài khoản Admin mới.
   *
   * @param creator    admin đang tạo
   * @param username   username admin mới
   * @param password   password
   * @param email      email
   * @param adminLevel cấp độ (không vượt cấp creator)
   * @return Admin mới
   */
  Admin createAdmin(Admin creator, String username,
      String password, String email, int adminLevel);
}