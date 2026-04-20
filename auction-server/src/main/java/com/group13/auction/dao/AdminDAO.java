package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class AdminDAO {

    public AdminDAO() {
    }

    /**
     * Tạo Admin mới (STAFF hoặc MASTER)
     */
    public boolean createAdmin(String adminId, String username, String passwordHash, String email, String level) {
        // Không sinh UUID ở đây nữa
        String sql = "INSERT INTO admins (id, username, password_hash, email, level) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, adminId);
            pstmt.setString(2, username);
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, email);
            pstmt.setString(5, level);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi tạo Admin: " + e.getMessage());
            return false;
        }
    }
}
