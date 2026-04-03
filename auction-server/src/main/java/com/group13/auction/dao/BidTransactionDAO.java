package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BidTransactionDAO {
    private Connection conn;

    public BidTransactionDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    // Ghi lại lịch sử đặt giá của một người dùng
    public boolean recordBid(int auctionId, int bidderId, double bidAmount) {
        String sql = "INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            pstmt.setInt(2, bidderId);
            pstmt.setDouble(3, bidAmount);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi ghi nhận lịch sử đặt giá: " + e.getMessage());
            return false;
        }
    }
}