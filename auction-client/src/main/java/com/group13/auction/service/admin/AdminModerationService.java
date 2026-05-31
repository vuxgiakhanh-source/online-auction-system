package com.group13.auction.service.admin;

import com.group13.auction.core.context.AppContext;

/**
 * Service nhỏ dùng cho dashboard và guard của khu vực Admin.
 */
public final class AdminModerationService {

  /**
   * Kiểm tra user hiện tại có quyền Admin hay không.
   *
   * @return true nếu user hiện tại là Admin
   */
  public boolean currentUserIsAdmin() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(session -> session.isAdmin())
        .orElse(false);
  }

  /**
   * Kiểm tra user hiện tại có quyền System Admin hay không.
   *
   * @return true nếu user hiện tại là MASTER Admin
   */
  public boolean currentUserIsMasterAdmin() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(session -> session.isMasterAdmin())
        .orElse(false);
  }

  /**
   * Lấy tên cấp Admin hiện tại để hiển thị trên dashboard.
   *
   * @return nhãn cấp quyền Admin
   */
  public String getCurrentAdminAccessLabel() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(
            session -> {
              if (session.isMasterAdmin()) {
                return "Tài khoản hiện tại có quyền System Admin.";
              }
              if (session.isAdmin()) {
                return "Tài khoản hiện tại có quyền Staff Admin.";
              }
              return "Bạn không có quyền truy cập khu vực Admin.";
            })
        .orElse("Bạn không có quyền truy cập khu vực Admin.");
  }

  /**
   * Tạo thông báo lỗi quyền truy cập thống nhất.
   *
   * @return thông báo không đủ quyền
   */
  public String getAccessDeniedMessage() {
    return "You do not have permission to access the Admin area.";
  }
}
