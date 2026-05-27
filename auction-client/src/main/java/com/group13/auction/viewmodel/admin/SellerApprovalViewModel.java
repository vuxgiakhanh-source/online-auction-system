package com.group13.auction.viewmodel.admin;

/** View model hiển thị người dùng trong màn duyệt quyền Seller. */
public class SellerApprovalViewModel {

  private final String userId;
  private final String username;
  private final String email;
  private final String role;
  private final String note;
  private final boolean approvable;

  /**
   * Tạo dữ liệu hiển thị cho một yêu cầu/candidate Seller.
   *
   * @param userId mã người dùng
   * @param username tên đăng nhập
   * @param email email
   * @param role vai trò hiện tại
   * @param note ghi chú trạng thái
   * @param approvable true nếu có thể approve bằng API hiện có
   */
  public SellerApprovalViewModel(
      String userId, String username, String email, String role, String note, boolean approvable) {
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.role = role;
    this.note = note;
    this.approvable = approvable;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }

  public String getNote() {
    return note;
  }

  public boolean isApprovable() {
    return approvable;
  }
}
