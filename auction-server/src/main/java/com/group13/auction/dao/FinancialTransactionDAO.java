package com.group13.auction.dao;

import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinancialTransactionDAO {
  private static final Logger log = LoggerFactory.getLogger(FinancialTransactionDAO.class);

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 200;

  public FinancialTransactionDAO() {}

  /** Lưu một giao dịch tài chính vào hệ thống để phục vụ đối soát. */
  public boolean saveTransaction(FinancialTransaction tx) {
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      return saveTransaction(conn, tx);
    } catch (SQLException e) {
      log.error(
          "Failed to save financial transaction: txId={}, type={}, auctionId={}",
          tx.getId(),
          tx.getType(),
          tx.getAuctionId(),
          e);
      return false;
    }
  }

  /** Save a financial transaction using the caller transaction. */
  public boolean saveTransaction(Connection conn, FinancialTransaction tx) throws SQLException {
    String sql =
        "INSERT INTO financial_transactions (id, sender_id, receiver_id, amount, transaction_type,"
            + " auction_id) VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, tx.getId());
      pstmt.setString(2, tx.getFromUserId());
      pstmt.setString(3, tx.getToUserId());
      pstmt.setLong(4, tx.getAmount());
      pstmt.setString(5, tx.getType().name());
      pstmt.setString(6, tx.getAuctionId());

      boolean result = pstmt.executeUpdate() > 0;
      if (result) {
        log.debug(
            "Financial transaction saved: txId={}, type={}, amount={}, auctionId={}, from={},"
                + " to={}",
            tx.getId(),
            tx.getType(),
            tx.getAmount(),
            tx.getAuctionId(),
            tx.getFromUserId(),
            tx.getToUserId());
      }
      return result;
    }
  }

  /**
   * Lấy số tiền cọc đã lock của một user cho một auction (từ audit trail).
   *
   * <p>Dựa trên financial_transactions (transaction_type = 'DEPOSIT_LOCK'). Nếu có nhiều bản ghi
   * (retry/bug), trả về tổng (SUM).
   *
   * @return tổng tiền cọc đã lock, hoặc 0 nếu không có.
   */
  public long findLockedDepositAmount(String userId, String auctionId) {
    String sql =
        "SELECT COALESCE(SUM(amount), 0) AS total "
            + "FROM financial_transactions "
            + "WHERE sender_id = ? AND auction_id = ? AND transaction_type = 'DEPOSIT_LOCK'";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, userId);
      pstmt.setString(2, auctionId);

      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          long total = rs.getLong("total");
          log.debug(
              "Locked deposit found: userId={}, auctionId={}, total={}", userId, auctionId, total);
          return total;
        }
      }
    } catch (SQLException e) {
      log.error("Failed to find locked deposit: userId={}, auctionId={}", userId, auctionId, e);
    }
    return 0L;
  }

  /** Lấy danh sách giao dịch tài chính gần nhất, có lọc theo loại và phiên nếu cần. */
  public List<FinancialTransaction> findTransactions(
      String transactionType, String auctionId, int page, int pageSize) {
    int safePage = normalizePage(page);
    int safePageSize = normalizePageSize(pageSize);
    int offset = (safePage - 1) * safePageSize;

    StringBuilder sql = new StringBuilder("SELECT * FROM financial_transactions");
    List<Object> params = new ArrayList<>();
    appendFilters(sql, params, transactionType, auctionId);
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    params.add(safePageSize);
    params.add(offset);

    List<FinancialTransaction> transactions = new ArrayList<>();
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
      bindParams(pstmt, params);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          transactions.add(mapTransaction(rs));
        }
      }
    } catch (SQLException e) {
      log.error(
          "Failed to query financial transactions: type={}, auctionId={}",
          transactionType,
          auctionId,
          e);
    }
    return transactions;
  }

  /** Đếm số giao dịch tài chính phù hợp với filter hiện tại. */
  public int countTransactions(String transactionType, String auctionId) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total FROM financial_transactions");
    List<Object> params = new ArrayList<>();
    appendFilters(sql, params, transactionType, auctionId);

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
      bindParams(pstmt, params);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getInt("total");
        }
      }
    } catch (SQLException e) {
      log.error(
          "Failed to count financial transactions: type={}, auctionId={}",
          transactionType,
          auctionId,
          e);
    }
    return 0;
  }

  /** Tính tổng amount theo một loại giao dịch. */
  public long sumAmountByType(TransactionType type) {
    String sql =
        "SELECT COALESCE(SUM(amount), 0) AS total "
            + "FROM financial_transactions WHERE transaction_type = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, type.name());
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("total");
        }
      }
    } catch (SQLException e) {
      log.error("Failed to sum financial transactions by type={}", type, e);
    }
    return 0L;
  }

  /** Tính tổng amount theo nhiều loại giao dịch. */
  public long sumAmountByTypes(TransactionType... types) {
    if (types == null || types.length == 0) {
      return 0L;
    }

    StringBuilder sql =
        new StringBuilder("SELECT COALESCE(SUM(amount), 0) AS total FROM financial_transactions ");
    sql.append("WHERE transaction_type IN (");
    for (int i = 0; i < types.length; i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append('?');
    }
    sql.append(')');

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
      for (int i = 0; i < types.length; i++) {
        pstmt.setString(i + 1, types[i].name());
      }
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("total");
        }
      }
    } catch (SQLException e) {
      log.error("Failed to sum financial transactions by types", e);
    }
    return 0L;
  }

  private void appendFilters(
      StringBuilder sql, List<Object> params, String transactionType, String auctionId) {
    List<String> conditions = new ArrayList<>();
    if (transactionType != null && !transactionType.isBlank()) {
      conditions.add("transaction_type = ?");
      params.add(transactionType.trim());
    }
    if (auctionId != null && !auctionId.isBlank()) {
      conditions.add("auction_id = ?");
      params.add(auctionId.trim());
    }
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }

  private void bindParams(PreparedStatement pstmt, List<Object> params) throws SQLException {
    for (int i = 0; i < params.size(); i++) {
      Object value = params.get(i);
      if (value instanceof Integer intValue) {
        pstmt.setInt(i + 1, intValue);
      } else {
        pstmt.setString(i + 1, String.valueOf(value));
      }
    }
  }

  private FinancialTransaction mapTransaction(ResultSet rs) throws SQLException {
    Timestamp createdTs = rs.getTimestamp("created_at");
    LocalDateTime createdAt = createdTs != null ? createdTs.toLocalDateTime() : LocalDateTime.now();
    TransactionType type = TransactionType.valueOf(rs.getString("transaction_type"));

    return FinancialTransaction.reconstitute(
        rs.getString("id"),
        createdAt,
        createdAt,
        rs.getString("sender_id"),
        rs.getString("receiver_id"),
        rs.getLong("amount"),
        type,
        rs.getString("auction_id"));
  }

  private int normalizePage(int page) {
    return Math.max(DEFAULT_PAGE, page);
  }

  private int normalizePageSize(int pageSize) {
    if (pageSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }
}
