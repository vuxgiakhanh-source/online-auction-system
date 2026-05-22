package com.group13.auction.viewmodel.admin;

/**
 * View model hiển thị một tài khoản Staff Admin trong màn System Admin.
 */
public final class StaffAdminViewModel {

  private final String adminId;
  private final String username;
  private final String email;
  private final String adminType;
  private final String status;

  /**
   * Tạo dữ liệu hiển thị cho Staff Admin.
   *
   * @param adminId mã tài khoản Admin
   * @param username tên đăng nhập
   * @param email email
   * @param adminType loại Admin
   * @param status trạng thái tài khoản
   */
  public StaffAdminViewModel(
      String adminId, String username, String email, String adminType, String status) {
    this.adminId = adminId;
    this.username = username;
    this.email = email;
    this.adminType = adminType;
    this.status = status;
  }

  public String getAdminId() {
    return adminId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getAdminType() {
    return adminType;
  }

  public String getStatus() {
    return status;
  }
}