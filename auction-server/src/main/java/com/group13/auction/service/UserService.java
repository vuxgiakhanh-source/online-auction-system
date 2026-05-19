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

    // FIX LOGIN SLOWNESS:
    // Bước 1: Tìm trong AuctionManager (in-memory ConcurrentHashMap) trước.
    //   Nếu user đã login trước đó hoặc đã load khi server start → O(n) scan nhưng không có DB I/O.
    //   AuctionManager.findUserByUsername() gọi DB nếu không tìm thấy, nhưng đó là fallback.
    //
    // Bước 2: Nếu không có trong memory, dùng findUserCoreByUsername() — 1 query duy nhất,
    //   KHÔNG load joinedAuctionIds + watchListAuctionIds (giảm từ 3 queries xuống 1).
    //
    // Bước 3: Sau khi xác thực xong, nếu user chỉ có core data (từ bước 2),
    //   trả về nó — caller (AuthHandler) sẽ tự update AuctionManager.

    // -- Bước 1: in-memory lookup --
    NormalUser user = null;
    User memUser = AuctionManager.getInstance().findUserByUsernameInMemoryOnly(username);
    if (memUser instanceof NormalUser) {
      user = (NormalUser) memUser;
      log.debug("Login: user found in-memory, skip DB query. username={}", username);
    }

    // -- Bước 2: lightweight DB query nếu không có trong memory --
    if (user == null) {
      user = userDAO.findUserCoreByUsername(username);
    }

    // -- Bước 3: Không tìm thấy --
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

    // Đăng nhập thành công — đảm bảo user được lưu vào AuctionManager
    AuctionManager.getInstance().addToUserList(user);
    log.info("Login success: username={}", username);
    return user;
  }
}