package com.group13.auction.service;

import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;

/**
 * Xử lý xác thực người dùng (authentication).
 * Sau khi tách RatingService và AccountService, UserService chỉ còn
 * trách nhiệm duy nhất: verify danh tính người dùng.
 */
public class UserService implements IUserService {

  /**
   * Xác thực đăng nhập.
   * Ném {@link AuthenticationException} với lý do cụ thể thay vì trả boolean.
   *
   * @param user          user cần xác thực
   * @param inputPassword mật khẩu nhập vào
   * @throws AuthenticationException nếu xác thực thất bại
   */
  public void login(User user, String inputPassword) {
    if (user.getAccountStatus() == AccountStatus.BANNED) {
      throw new AuthenticationException(Reason.ACCOUNT_BANNED);
    }
    if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
      throw new AuthenticationException(Reason.ACCOUNT_SUSPENDED);
    }
    if (!user.getHashedPassword().equals(User.hashPassword(inputPassword))) {
      throw new AuthenticationException(Reason.WRONG_PASSWORD);
    }
  }
}