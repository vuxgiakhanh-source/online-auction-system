package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persists the singleton SystemBank balance. */
public class SystemBankDAO {

  public static final String SYSTEM_BANK_ID = "SYSTEM";

  private static final Logger log = LoggerFactory.getLogger(SystemBankDAO.class);

  /** Ensures the singleton row exists for normal DAO callers. */
  public void ensureRowExists() {
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      ensureTableExists(conn);
      ensureRowExists(conn);
    } catch (SQLException e) {
      log.error("Failed to ensure system_bank row exists", e);
      throw new IllegalStateException("Khong the khoi tao ban ghi system_bank", e);
    }
  }

  /** Ensures the singleton row exists inside the caller transaction. */
  public void ensureRowExists(Connection conn) throws SQLException {
    String sql =
        "INSERT INTO system_bank (id, total_balance) VALUES (?, 0) "
            + "ON DUPLICATE KEY UPDATE id = id";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, SYSTEM_BANK_ID);
      pstmt.executeUpdate();
    }
  }

  /** Reads total_balance from DB. Returns 0 if the row is missing. */
  public long loadTotalBalance() {
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      ensureTableExists(conn);
      return loadTotalBalance(conn);
    } catch (SQLException e) {
      log.error("Failed to load system bank balance", e);
      throw new IllegalStateException("Khong the doc so du SystemBank tu DB", e);
    }
  }

  /** Reads total_balance using an existing connection. */
  public long loadTotalBalance(Connection conn) throws SQLException {
    ensureRowExists(conn);
    String sql = "SELECT total_balance FROM system_bank WHERE id = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, SYSTEM_BANK_ID);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return Math.max(0L, rs.getLong("total_balance"));
        }
      }
    }
    return 0L;
  }

  /** Reads and locks the singleton row for a money movement transaction. */
  public long loadTotalBalanceForUpdate(Connection conn) throws SQLException {
    ensureRowExists(conn);
    String sql = "SELECT total_balance FROM system_bank WHERE id = ? FOR UPDATE";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, SYSTEM_BANK_ID);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return Math.max(0L, rs.getLong("total_balance"));
        }
      }
    }
    return 0L;
  }

  /** Reads the last update time for the singleton row. */
  public java.time.LocalDateTime loadUpdatedAt() {
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      ensureTableExists(conn);
      return loadUpdatedAt(conn);
    } catch (SQLException e) {
      log.error("Failed to load system bank updatedAt", e);
    }
    return java.time.LocalDateTime.now();
  }

  /** Reads updated_at using an existing connection. */
  public java.time.LocalDateTime loadUpdatedAt(Connection conn) throws SQLException {
    ensureRowExists(conn);
    String sql = "SELECT updated_at FROM system_bank WHERE id = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, SYSTEM_BANK_ID);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
          if (updatedAt != null) {
            return updatedAt.toLocalDateTime();
          }
        }
      }
    }
    return java.time.LocalDateTime.now();
  }

  /** Saves total_balance for normal DAO callers. */
  public boolean saveTotalBalance(long totalBalance) {
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      ensureTableExists(conn);
      return saveTotalBalance(conn, totalBalance);
    } catch (SQLException e) {
      log.error("Failed to save system bank balance: totalBalance={}", totalBalance, e);
      return false;
    }
  }

  /** Saves total_balance inside the caller transaction. */
  public boolean saveTotalBalance(Connection conn, long totalBalance) throws SQLException {
    ensureRowExists(conn);
    String sql = "UPDATE system_bank SET total_balance = ? WHERE id = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, Math.max(0L, totalBalance));
      pstmt.setString(2, SYSTEM_BANK_ID);
      return pstmt.executeUpdate() > 0;
    }
  }

  private void ensureTableExists(Connection conn) throws SQLException {
    String sql =
        "CREATE TABLE IF NOT EXISTS system_bank ("
            + "id VARCHAR(36) PRIMARY KEY, "
            + "total_balance BIGINT NOT NULL DEFAULT 0, "
            + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
            + ")";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.executeUpdate();
    }
  }
}
