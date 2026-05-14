package com.group13.auction.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminDAO {
    private static final Logger log = LoggerFactory.getLogger(AdminDAO.class);

    public AdminDAO() {
    }

    /**
     * Kiểm tra xem admin với username đã tồn tại trong bảng admins chưa.
     * Dùng để tránh INSERT trùng khi bootstrap SystemAdmin.
     */
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM admins WHERE username = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check admin existence: username={}", username, e);
            return false;
        }
    }

    /**
     * Tạo Admin mới (STAFF hoặc MASTER)
     */
    public boolean createAdmin(String adminId, String username, String passwordHash, String email, String level) {
        String sql = "INSERT INTO admins (id, username, password_hash, email, level) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, adminId);
            pstmt.setString(2, username);
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, email);
            pstmt.setString(5, level);

            boolean result = pstmt.executeUpdate() > 0;
            if (result) {
                log.debug("Admin created: adminId={}, username={}, level={}", adminId, username, level);
            }
            return result;
        } catch (SQLException e) {
            log.error("Failed to create admin: adminId={}, username={}", adminId, username, e);
            return false;
        }
    }
}