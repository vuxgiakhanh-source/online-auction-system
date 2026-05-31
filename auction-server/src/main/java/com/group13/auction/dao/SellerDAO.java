package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerDAO {
  private static final Logger log = LoggerFactory.getLogger(SellerDAO.class);

  public SellerDAO() {}

  /**
   * User gửi yêu cầu làm Seller (Tạo record trạng thái PENDING). Dùng INSERT IGNORE để idempotent —
   * không báo lỗi nếu record đã tồn tại.
   */
  public boolean requestSellerRole(String userId) {
    String sql = "INSERT IGNORE INTO sellers (user_id, approval_status) VALUES (?, 'PENDING')";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, userId);
      pstmt.executeUpdate(); // affectedRows có thể 0 nếu đã tồn tại — vẫn OK
      return true;
    } catch (SQLException e) {
      log.error("Lỗi yêu cầu role Seller", e);
      return false;
    }
  }

  // Dùng UPSERT để luôn có bản ghi sellers khi duyệt role (tránh UPDATE 0 dòng).

  /**
   * Duyệt Role Seller — UPSERT: tạo record nếu chưa có, cập nhật nếu đã có.
   *
   * <p>Trước: {@code UPDATE sellers SET approval_status = 'APPROVED' WHERE user_id = ?} → silent
   * fail nếu không có record (0 rows updated, trả về false).
   *
   * <p>Sau: INSERT ... ON DUPLICATE KEY UPDATE → đảm bảo luôn có record APPROVED dù
   * requestSellerRole() có được gọi trước hay không.
   */
  public boolean approveSellerRole(String userId) {
    String sql =
        "INSERT INTO sellers (user_id, approval_status, approved_date) "
            + "VALUES (?, 'APPROVED', CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE "
            + "  approval_status = 'APPROVED', "
            + "  approved_date = CURRENT_TIMESTAMP";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, userId);
      pstmt.executeUpdate();
      return true;
    } catch (SQLException e) {
      log.error("Lỗi duyệt role Seller (upsert)", e);
      return false;
    }
  }
}
