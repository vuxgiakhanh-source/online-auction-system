package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminDAO {

  /** Bản ghi admin đọc từ bảng {@code admins} (dùng cho đăng nhập). */
  public record AdminRow(
      String id,
      String username,
      String passwordHash,
      String email,
      String level,
      LocalDateTime createdAt) {}

  private static final Logger log = LoggerFactory.getLogger(AdminDAO.class);

  public AdminDAO() {}

  /**
   * Kiểm tra xem admin với username đã tồn tại trong bảng admins chưa. Dùng để tránh INSERT trùng
   * khi bootstrap SystemAdmin.
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
   * Tìm admin theo username — dùng khi đăng nhập (bảng {@code admins}, không phải {@code users}).
   */
  public Optional<AdminRow> findByUsername(String username) {
    String sql =
        """
        SELECT id, username, password_hash, email, level, created_at
        FROM admins WHERE username = ? LIMIT 1
        """;
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, username);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        Timestamp created = rs.getTimestamp("created_at");
        return Optional.of(
            new AdminRow(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("email"),
                rs.getString("level"),
                created != null ? created.toLocalDateTime() : LocalDateTime.now()));
      }
    } catch (SQLException e) {
      log.error("Failed to find admin by username: username={}", username, e);
      return Optional.empty();
    }
  }

  /** Lấy toàn bộ admin từ bảng {@code admins} (nguồn dữ liệu cho danh sách Staff Admin). */
  public List<AdminRow> findAll() {
    String sql =
        """
        SELECT id, username, password_hash, email, level, created_at
        FROM admins
        ORDER BY username
        """;
    List<AdminRow> rows = new ArrayList<>();
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        Timestamp created = rs.getTimestamp("created_at");
        rows.add(
            new AdminRow(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("email"),
                rs.getString("level"),
                created != null ? created.toLocalDateTime() : LocalDateTime.now()));
      }
    } catch (SQLException e) {
      log.error("Failed to list admins", e);
    }
    return rows;
  }

  /** Tạo Admin mới (STAFF hoặc MASTER) */
  public boolean createAdmin(
      String adminId, String username, String passwordHash, String email, String level) {
    String sql =
        "INSERT INTO admins (id, username, password_hash, email, level) VALUES (?, ?, ?, ?, ?)";

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
