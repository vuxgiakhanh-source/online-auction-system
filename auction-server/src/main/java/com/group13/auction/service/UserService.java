package com.group13.auction.service;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.iservice.IUserService;

/**
 * Xử lý xác thực người dùng (authentication).
 * Trách nhiệm duy nhất: verify danh tính người dùng từ DB.
 */
public class UserService implements IUserService {

  private final UserDAO userDAO;

  // Tiêm UserDAO vào qua constructor
  public UserService(UserDAO userDAO) {
    this.userDAO = userDAO;
  }

  /**
   * Xác thực đăng nhập bằng username và mật khẩu.
   *
   * @param username tên đăng nhập
   * @param inputPassword mật khẩu nhập vào
   * @return Đối tượng User nếu đăng nhập thành công
   * @throws AuthenticationException nếu xác thực thất bại
   */
  @Override
  public User login(String username, String inputPassword) {

    // 1. Tìm user trong Database
    NormalUser user = userDAO.findUserByUsername(username);

    // 2. Nếu không tìm thấy trong DB -> Ném exception
    if (user == null) {
      // Lưu ý: Bạn cần thêm USER_NOT_FOUND vào enum Reason trong class AuthenticationException nhé
      throw new AuthenticationException(Reason.USER_NOT_FOUND);
    }

    // 3. Kiểm tra trạng thái tài khoản
    if (user.getAccountStatus() == AccountStatus.BANNED) {
      throw new AuthenticationException(Reason.ACCOUNT_BANNED);
    }
    if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
      throw new AuthenticationException(Reason.ACCOUNT_SUSPENDED);
    }

    // 4. Kiểm tra mật khẩu
    if (!user.getHashedPassword().equals(User.hashPassword(inputPassword))) {
      throw new AuthenticationException(Reason.WRONG_PASSWORD);
    }

    // 5. Đăng nhập thành công
    return user;
  }
}