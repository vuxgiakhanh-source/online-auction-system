package com.group13.auction.service.iservice;

import com.group13.auction.model.user.User;

/**
 * Hợp đồng xác thực người dùng (đảm nhiệm quản lý khâu login)
 * Tách interface để tầng trên (Controller) phụ thuộc vào abstraction,
 * không phụ thuộc vào implementation cụ thể.
 */
public interface IUserService {

  /**
   * Xác thực đăng nhập.
   *
   * @param username tên username cần xác thực
   * @param inputPassword mật khẩu nhập vào
   * @throws com.group13.auction.exception.AuthenticationException nếu thất bại
   */
  User login(String username, String inputPassword);
}