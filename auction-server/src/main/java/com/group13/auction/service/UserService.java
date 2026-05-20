package com.group13.auction.service;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.iservice.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý xác thực người dùng (authentication).
 * Trách nhiệm duy nhất: verify danh tính người dùng từ DB.
 */
public class UserService implements IUserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

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

    // Luôn đọc từ DB khi login để có roles/balance/status mới nhất (kể cả SELLER đã duyệt).
    // Không dùng bản in-memory cũ — tránh "chưa là seller" / số dư ví lệch sau khi duyệt role.
    NormalUser user = userDAO.findUserCoreByUsername(username);

    // -- Không tìm thấy --
    if (user == null) {
      log.warn("Login failed: user not found, username={}", username);
      throw new AuthenticationException(Reason.USER_NOT_FOUND);
    }

    // Kiểm tra trạng thái tài khoản
    if (user.getAccountStatus() == AccountStatus.BANNED) {
      log.warn("Login failed: account banned, username={}", username);
      throw new AuthenticationException(Reason.ACCOUNT_BANNED);
    }
    if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
      log.warn("Login failed: account suspended, username={}", username);
      throw new AuthenticationException(Reason.ACCOUNT_SUSPENDED);
    }

    // Kiểm tra mật khẩu
    if (!user.getHashedPassword().equals(User.hashPassword(inputPassword))) {
      log.warn("Login failed: wrong password, username={}", username);
      throw new AuthenticationException(Reason.WRONG_PASSWORD);
    }

    // Đăng nhập thành công — ghi đè bản in-memory bằng dữ liệu vừa load từ DB
    AuctionManager.getInstance().refreshUser(user);
    log.info("Login success: username={}", username);
    return user;
  }
}