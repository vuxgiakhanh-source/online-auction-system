package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persist số dư escrow của {@link com.group13.auction.bank.SystemBank} (một dòng singleton). */
public class SystemBankDAO {

  public static final String SYSTEM_BANK_ID = "SYSTEM";

  private static final Logger log = LoggerFactory.getLogger(SystemBankDAO.class);

  /** Đảm bảo có dòng SYSTEM với balance 0 nếu chưa tồn tại. */
  public void ensureRowExists() {
    String sql =
        "INSERT INTO system_bank (id, total_balance) VALUES (?, 0) "
            + "ON DUPLICATE KEY UPDATE id = id";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, SYSTEM_BANK_ID);
      pstmt.executeUpdate();
    } catch (SQLException e) {
      log.error("Failed to ensure system_bank row exists", e);
      throw new IllegalStateException("Không thể khởi tạo bản ghi system_bank", e);
    }
  }

  /** Đọc total_balance từ DB; trả về 0 nếu chưa có dòng. */
  public long loadTotalBalance() {
    String sql = "SELECT total_balance FROM system_bank WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, SYSTEM_BANK_ID);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return Math.max(0L, rs.getLong("total_balance"));
        }
      }
    } catch (SQLException e) {
      log.error("Failed to load system bank balance", e);
      throw new IllegalStateException("Không thể đọc số dư SystemBank từ DB", e);
    }
    return 0L;
  }

  /** Ghi đè total_balance (đồng bộ với RAM sau mỗi thao tác bank). */
  public boolean saveTotalBalance(long totalBalance) {
    ensureRowExists();
    String sql = "UPDATE system_bank SET total_balance = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, Math.max(0L, totalBalance));
      pstmt.setString(2, SYSTEM_BANK_ID);
      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Failed to save system bank balance: totalBalance={}", totalBalance, e);
      return false;
    }
  }
}
