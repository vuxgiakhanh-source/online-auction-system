package com.group13.auction.model.user;

import com.group13.auction.dao.UserDAO;

/**
 * Factory tạo User - tập trung validate và khởi tạo. ID được sinh bởi Entity (UUID).
 *
 * <p>Design Pattern: Factory Method.
 *
 * <p>Đã thực hiện TODO: Bỏ Set in-memory usedUsernames/usedEmails, thay bằng truy vấn DB qua
 * UserDAO để đảm bảo tính nhất quán khi restart server.
 */
public abstract class UserFactory<T extends User> {

  /**
   * DAO dùng để kiểm tra username/email đã tồn tại trong DB chưa. Inject qua setUserDAO() hoặc
   * constructor của lớp con.
   *
   * <p>Khi userDAO == null, factory bỏ qua kiểm tra unique với DB - chỉ chấp nhận trong môi trường
   * test.
   */
  private UserDAO userDAO;

  /** Constructor mặc định — không inject DAO. */
  protected UserFactory() {
    this.userDAO = null;
  }

  /**
   * Constructor inject UserDAO - nên dùng trong production.
   *
   * @param userDAO DAO để kiểm tra unique username/email với DB
   */
  protected UserFactory(UserDAO userDAO) {
    this.userDAO = userDAO;
  }

  /**
   * Setter injection - dùng khi factory được tạo trước khi DAO sẵn sàng.
   *
   * @param userDAO DAO để kiểm tra unique username/email với DB
   */
  public void setUserDAO(UserDAO userDAO) {
    this.userDAO = userDAO;
  }

  /**
   * Tạo User theo role với validate đầu vào.
   *
   * @param username tên đăng nhập (tối thiểu 8 ký tự, không trùng)
   * @param password mật khẩu thô (tối thiểu 8 ký tự)
   * @param email địa chỉ email hợp lệ (không trùng)
   * @param args các tham số bổ sung tùy theo loại User
   * @return User mới, id do Entity tự sinh UUID
   * @throws IllegalArgumentException nếu dữ liệu không hợp lệ hoặc đã tồn tại
   */
  public T createUser(String username, String password, String email, Object... args) {
    validateUsername(username);
    validatePassword(password);
    validateEmail(email);
    return createProduct(username, password, email, args);
  }

  /**
   * Kiểm tra email đã dùng chưa - truy vấn DB.
   *
   * @param email email cần kiểm tra
   * @return true nếu email đã tồn tại
   */
  public boolean isEmailAlreadyUsed(String email) {
    if (userDAO == null) {
      return false;
    }
    return userDAO.existsByEmail(email);
  }

  /** Factory Method để các subclass tự khởi tạo instance cụ thể. */
  protected abstract T createProduct(
      String username, String password, String email, Object... args);

  // Validation

  private void validateUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Username không được để trống.");
    }
    if (username.length() < 8) {
      throw new IllegalArgumentException("Username phải từ 8 ký tự trở lên.");
    }
    // Đã thực hiện TODO: Bỏ Set in-memory, truy vấn DB
    if (userDAO != null && userDAO.existsByUsername(username)) {
      throw new IllegalArgumentException("Thông tin đăng ký không hợp lệ.");
    }
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8) {
      throw new IllegalArgumentException("Password phải từ 8 ký tự trở lên.");
    }
  }

  private void validateEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email không được để trống.");
    }
    String emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    if (!email.matches(emailRegex)) {
      throw new IllegalArgumentException("Email không đúng định dạng.");
    }
    // Đã thực hiện TODO: Bỏ Set in-memory, truy vấn DB
    if (userDAO != null && userDAO.existsByEmail(email)) {
      throw new IllegalArgumentException("Email đã được sử dụng.");
    }
  }
}
