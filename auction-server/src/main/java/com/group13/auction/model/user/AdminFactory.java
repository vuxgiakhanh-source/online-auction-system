package com.group13.auction.model.user;

/**
 * Factory chuyên tạo Admin STAFF.
 *
 * <p>Lưu ý: {@link SystemAdmin} KHÔNG đi qua Factory này SystemAdmin được khởi tạo qua {@link
 * SystemAdmin#bootstrap(String)}. Factory này để SystemAdmin tạo tài khoản admin STAFF, không được
 * tạo ở ngoài.
 *
 * <p>args[0] bị bỏ qua - tất cả admin tạo qua Factory đều là STAFF.
 */
public class AdminFactory extends UserFactory<Admin> {

  /**
   * Tạo Admin STAFF mới.
   *
   * @param username tên đăng nhập
   * @param password mật khẩu thô
   * @param email email
   * @param args (bỏ qua - level cố định là STAFF)
   * @return Admin STAFF mới
   */
  @Override
  protected Admin createProduct(String username, String password, String email, Object... args) {
    return Admin.create(username, password, email, Admin.LEVEL_STAFF);
  }
}
