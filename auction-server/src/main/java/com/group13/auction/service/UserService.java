package com.group13.auction.service;

import com.group13.auction.dao.AdminDAO;
import com.group13.auction.dao.AdminDAO.AdminRow;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.iservice.IUserService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý xác thực người dùng (authentication). Trách nhiệm duy nhất: verify danh tính người dùng từ
 * DB.
 */
public class UserService implements IUserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserDAO userDAO;
  private final AdminDAO adminDAO;

  public UserService(UserDAO userDAO) {
    this(userDAO, new AdminDAO());
  }

  public UserService(UserDAO userDAO, AdminDAO adminDAO) {
    this.userDAO = userDAO;
    this.adminDAO = adminDAO;
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

    // Ưu tiên bảng admins: username có thể trùng giữa users và admins (vd. staffadmin test),
    // nếu không kiểm tra trước sẽ đăng nhập nhầm NormalUser (BIDDER) thay vì Staff Admin.
    if (adminDAO.findByUsername(username).isPresent()) {
      return loginAdmin(username, inputPassword);
    }

    // Luôn đọc từ DB khi login để có roles/balance/status mới nhất (kể cả SELLER đã duyệt).
    NormalUser user = userDAO.findUserCoreByUsername(username);
    if (user == null) {
      log.warn("Login failed: user not found, username={}", username);
      throw new AuthenticationException(Reason.USER_NOT_FOUND);
    }

    // BANNED/SUSPENDED vẫn đăng nhập được (restricted mode — chỉ ví); kiểm tra mật khẩu bên dưới.

    // Kiểm tra mật khẩu
    if (!user.getHashedPassword().equals(User.hashPassword(inputPassword))) {
      log.warn("User login failed: wrong password, username={}", username);
      throw new AuthenticationException(Reason.WRONG_PASSWORD);
    }

    // Đăng nhập thành công — ghi đè bản in-memory bằng dữ liệu vừa load từ DB
    AuctionManager.getInstance().refreshUser(user);
    log.info("Login success: username={}", username);
    return user;
  }

  private User loginAdmin(String username, String inputPassword) {
    Optional<AdminRow> rowOpt = adminDAO.findByUsername(username);
    if (rowOpt.isEmpty()) {
      log.warn("Login failed: user not found, username={}", username);
      throw new AuthenticationException(Reason.USER_NOT_FOUND);
    }

    AdminRow row = rowOpt.get();
    if (!row.passwordHash().equals(User.hashPassword(inputPassword))) {
      log.warn("Admin login failed: wrong password, username={}", username);
      throw new AuthenticationException(Reason.WRONG_PASSWORD);
    }

    User admin = resolveAdminFromRow(row);
    AuctionManager.getInstance().refreshUser(admin);
    log.info("Admin login success: username={}, level={}", username, row.level());
    return admin;
  }

  private static User resolveAdminFromRow(AdminRow row) {
    if (Admin.LEVEL_MASTER.equals(row.level()) && isSystemAdminAccount(row.username())) {
      try {
        SystemAdmin system = SystemAdmin.getInstance();
        if (system.getUsername().equalsIgnoreCase(row.username())) {
          return system;
        }
      } catch (IllegalStateException ignored) {
        // Chưa bootstrap (một số unit test) — dùng reconstitute bên dưới
      }
    }

    LocalDateTime created = row.createdAt() != null ? row.createdAt() : LocalDateTime.now();
    return Admin.reconstitute(
        row.id(),
        created,
        created,
        row.username(),
        row.passwordHash(),
        row.email(),
        AccountStatus.ACTIVE,
        5.0,
        row.level(),
        null);
  }

  private static boolean isSystemAdminAccount(String username) {
    return "SYSTEM".equalsIgnoreCase(username);
  }
}
