package com.group13.auction.service.admin;

import com.group13.auction.core.context.AppContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Service nhỏ dùng cho dashboard và guard của khu vực Admin.
 *
 * <p>Server hiện chưa có API dashboard statistics riêng, nên service này không tạo số liệu giả.
 * Dashboard chỉ hiển thị các entry điều hướng và thông báo trạng thái hỗ trợ backend.
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
   * Lấy danh sách module admin có backend support ở source hiện tại.
   *
   * @return danh sách tên module
   */
  public List<String> getSupportedAdminModules() {
    List<String> modules =
        new ArrayList<>(
            List.of(
                "User moderation",
                "Auction moderation",
                "Seller role approval",
                "Quality report review"));

    if (currentUserIsMasterAdmin()) {
      modules.add("Staff Admin management");
    }

    return List.copyOf(modules);
  }

  /**
   * Thông báo rõ ràng cho dashboard khi chưa có API thống kê tổng quan.
   *
   * @return nội dung empty state cho phần statistics
   */
  public String getStatisticsUnavailableMessage() {
    return "Dashboard statistics API is not available yet. Use the moderation cards below.";
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
