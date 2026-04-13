package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SellerDAO {

    public SellerDAO() {}

    /**
     * User gửi yêu cầu làm Seller (Tạo record trạng thái PENDING)
     */
    public boolean requestSellerRole(String userId) {
        String sql = "INSERT INTO sellers (user_id, approval_status) VALUES (?, 'PENDING')";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi yêu cầu role Seller: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin duyệt Role Seller (Chuyển thành APPROVED và cập nhật thời gian)
     */
    public boolean approveSellerRole(String userId) {
        String sql = "UPDATE sellers SET approval_status = 'APPROVED', approved_date = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi duyệt role Seller: " + e.getMessage());
            return false;
        }
    }
}