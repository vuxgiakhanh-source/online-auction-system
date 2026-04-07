package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {
    private Connection conn;

    public UserDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    public boolean registerUser(String username, String passwordHash, String role, String email) {
        String sql = "INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, email);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký người dùng: " + e.getMessage());
            return false;
        }
    }

    // Hàm lấy ID người dùng khi đăng nhập thành công (Trả về -1 nếu thất bại)
    public int authenticateAndGetId(String username, String passwordHash) {
        String sql = "SELECT id FROM users WHERE username = ? AND password_hash = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi xác thực: " + e.getMessage());
        }
        return -1;
    }
}