package com.group13.auction.service.serviceInterface;

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
   * @param user user cần xác thực
   * @param inputPassword mật khẩu nhập vào
   * @throws com.group13.auction.exception.AuthenticationException nếu thất bại
   */
  void login(User user, String inputPassword);
}