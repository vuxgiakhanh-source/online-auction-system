package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DAO cho bảng {@code account_bans} — lịch sử khóa tài khoản bởi admin/hệ thống. */
public class AccountBanDAO {

  private static final Logger log = LoggerFactory.getLogger(AccountBanDAO.class);

  public record AccountBanRow(
      String id,
      String userId,
      String username,
      String email,
      String adminId,
      String bannedByUsername,
      String reason,
      String note,
      LocalDateTime bannedAt,
      LocalDateTime unbannedAt,
      String unbannedByUsername) {}

  /** Ghi nhận lần khóa mới; đóng mọi bản ghi active cũ của user trước khi insert. */
  public boolean insertBan(
      String userId, String adminId, String bannedByUsername, String reason, String note) {
    closeActiveBans(userId, adminId, bannedByUsername);

    String sql =
        """
        INSERT INTO account_bans
        (id, user_id, admin_id, banned_by_username, reason, note, banned_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
    try {
      return executeInsertBan(userId, adminId, bannedByUsername, reason, note, false, sql);
    } catch (SQLException e) {
      if (hasAdminId(adminId) && isForeignKeyViolationOnColumn(e, "admin_id")) {
        log.warn(
            "Insert account ban failed because admin_id is stale, "
                + "retrying with null admin_id: userId={}, adminId={}",
            userId,
            adminId);
        try {
          return executeInsertBan(userId, adminId, bannedByUsername, reason, note, true, sql);
        } catch (SQLException retryEx) {
          log.error(
              "Failed to insert account ban after retry null admin_id: userId={}, reason={}",
              userId,
              reason,
              retryEx);
          return false;
        }
      }
      log.error("Failed to insert account ban: userId={}, reason={}", userId, reason, e);
      return false;
    }
  }

  private boolean executeInsertBan(
      String userId,
      String adminId,
      String bannedByUsername,
      String reason,
      String note,
      boolean forceNullAdminId,
      String sql)
      throws SQLException {
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, UUID.randomUUID().toString());
      ps.setString(2, userId);
      if (forceNullAdminId) {
        ps.setNull(3, Types.VARCHAR);
      } else {
        ps.setString(3, adminId);
      }
      ps.setString(4, bannedByUsername);
      ps.setString(5, reason);
      ps.setString(6, note);
      ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
      return ps.executeUpdate() > 0;
    }
  }

  /** Đóng mọi bản ghi khóa đang active của user (unban). */
  public boolean closeActiveBans(String userId, String adminId, String unbannedByUsername) {
    String sql =
        """
        UPDATE account_bans
        SET unbanned_at = ?, unbanned_by_admin_id = ?, unbanned_by_username = ?
        WHERE user_id = ? AND unbanned_at IS NULL
        """;
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      LocalDateTime now = LocalDateTime.now();
      ps.setTimestamp(1, Timestamp.valueOf(now));
      ps.setString(2, adminId);
      ps.setString(3, unbannedByUsername);
      ps.setString(4, userId);
      return ps.executeUpdate() >= 0;
    } catch (SQLException e) {
      log.error("Failed to close active bans: userId={}", userId, e);
      return false;
    }
  }

  public Optional<AccountBanRow> findActiveByUserId(String userId) {
    String sql =
        """
        SELECT b.id, b.user_id, u.username, u.email,
               b.admin_id, b.banned_by_username, b.reason, b.note,
               b.banned_at, b.unbanned_at, b.unbanned_by_username
        FROM account_bans b
        JOIN users u ON u.id = b.user_id
        WHERE b.user_id = ? AND b.unbanned_at IS NULL
        ORDER BY b.banned_at DESC
        LIMIT 1
        """;
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
      }
    } catch (SQLException e) {
      log.error("Failed to find active ban: userId={}", userId, e);
      return Optional.empty();
    }
  }

  /** userId → bản ghi khóa đang active (một user tối đa một bản active). */
  public Map<String, AccountBanRow> findAllActiveByUserIds() {
    String sql =
        """
        SELECT b.id, b.user_id, u.username, u.email,
               b.admin_id, b.banned_by_username, b.reason, b.note,
               b.banned_at, b.unbanned_at, b.unbanned_by_username
        FROM account_bans b
        JOIN users u ON u.id = b.user_id
        WHERE b.unbanned_at IS NULL
        ORDER BY b.banned_at DESC
        """;
    Map<String, AccountBanRow> map = new HashMap<>();
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        AccountBanRow row = mapRow(rs);
        map.putIfAbsent(row.userId(), row);
      }
    } catch (SQLException e) {
      log.error("Failed to load active account bans", e);
    }
    return map;
  }

  public List<AccountBanRow> findAllActive() {
    return new ArrayList<>(findAllActiveByUserIds().values());
  }

  private static AccountBanRow mapRow(ResultSet rs) throws SQLException {
    Timestamp bannedAt = rs.getTimestamp("banned_at");
    Timestamp unbannedAt = rs.getTimestamp("unbanned_at");
    return new AccountBanRow(
        rs.getString("id"),
        rs.getString("user_id"),
        rs.getString("username"),
        rs.getString("email"),
        rs.getString("admin_id"),
        rs.getString("banned_by_username"),
        rs.getString("reason"),
        rs.getString("note"),
        bannedAt != null ? bannedAt.toLocalDateTime() : null,
        unbannedAt != null ? unbannedAt.toLocalDateTime() : null,
        rs.getString("unbanned_by_username"));
  }

  private static boolean isForeignKeyViolationOnColumn(SQLException e, String columnName) {
    String sqlState = e.getSQLState();
    String msg = e.getMessage();
    return "23503".equals(sqlState)
        || (msg != null
            && msg.toLowerCase().contains("foreign key")
            && msg.toLowerCase().contains(columnName.toLowerCase()));
  }

  private static boolean hasAdminId(String adminId) {
    return adminId != null && !adminId.isBlank();
  }
}
