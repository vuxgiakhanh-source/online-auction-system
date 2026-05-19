package com.group13.auction.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SellerDAO {
    private static final Logger log = LoggerFactory.getLogger(SellerDAO.class);

    public SellerDAO() {}

    /**
     * User gửi yêu cầu làm Seller (Tạo record trạng thái PENDING).
     * Dùng INSERT IGNORE để idempotent — không báo lỗi nếu record đã tồn tại.
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

    // ═══════════════════════════════════════════════════════════════════════
    // BUG FIX #2 — SellerDAO.approveSellerRole() dùng UPDATE thuần
    //
    // Vấn đề: AccountService.autoApproveSellerRole() gọi thẳng approveSellerRole()
    // mà không đảm bảo record trong bảng sellers đã tồn tại trước đó.
    // Nếu user chưa từng gọi requestSellerRole() (hoặc record bị thiếu),
    // câu UPDATE sẽ match 0 rows → silent fail → role SELLER KHÔNG được lưu DB.
    // In-memory thì user có SELLER role (vì user.addRole() đã gọi),
    // nhưng sau khi server restart hoặc login lại → mất role.
    //
    // Fix: dùng INSERT ... ON DUPLICATE KEY UPDATE (MySQL UPSERT) để đảm bảo
    // record luôn tồn tại và được set APPROVED, bất kể trước đó có record chưa.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Duyệt Role Seller — UPSERT: tạo record nếu chưa có, cập nhật nếu đã có.
     *
     * <p>Trước: {@code UPDATE sellers SET approval_status = 'APPROVED' WHERE user_id = ?}
     * → silent fail nếu không có record (0 rows updated, trả về false).</p>
     *
     * <p>Sau: INSERT ... ON DUPLICATE KEY UPDATE → đảm bảo luôn có record APPROVED
     * dù requestSellerRole() có được gọi trước hay không.</p>
     */
    public boolean approveSellerRole(String userId) {
        String sql = "INSERT INTO sellers (user_id, approval_status, approved_date) " +
                "VALUES (?, 'APPROVED', CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE " +
                "  approval_status = 'APPROVED', " +
                "  approved_date = CURRENT_TIMESTAMP";
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