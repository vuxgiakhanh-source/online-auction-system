package com.group13.auction.dao;

import com.group13.auction.model.bid.FinancialTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FinancialTransactionDAO {

    public FinancialTransactionDAO() {}

    /**
     * Lưu một giao dịch tài chính vào hệ thống để phục vụ đối soát.
     */
    public boolean saveTransaction(FinancialTransaction tx) {
        String sql = "INSERT INTO financial_transactions (id, sender_id, receiver_id, amount, transaction_type, auction_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tx.getId());
            pstmt.setString(2, tx.getFromUserId());
            pstmt.setString(3, tx.getToUserId());
            pstmt.setLong(4, tx.getAmount());
            pstmt.setString(5, tx.getType().name());
            pstmt.setString(6, tx.getAuctionId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu giao dịch tài chính: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy số tiền cọc đã lock của một user cho một auction (từ audit trail).
     *
     * <p>Dựa trên financial_transactions (transaction_type = 'DEPOSIT_LOCK').
     * Nếu có nhiều bản ghi (retry/bug), trả về tổng (SUM).
     *
     * @return tổng tiền cọc đã lock, hoặc 0 nếu không có.
     */
    public long findLockedDepositAmount(String userId, String auctionId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) AS total " +
                "FROM financial_transactions " +
                "WHERE sender_id = ? AND auction_id = ? AND transaction_type = 'DEPOSIT_LOCK'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, auctionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("total");
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy tiền cọc đã lock: " + e.getMessage());
        }
        return 0L;
    }
}