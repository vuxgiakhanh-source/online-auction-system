package com.group13.auction.dao;

import com.group13.auction.model.bid.FinancialTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
            pstmt.setDouble(4, tx.getAmount());
            pstmt.setString(5, tx.getType().name());
            pstmt.setString(6, tx.getAuctionId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu giao dịch tài chính: " + e.getMessage());
            return false;
        }
    }
}