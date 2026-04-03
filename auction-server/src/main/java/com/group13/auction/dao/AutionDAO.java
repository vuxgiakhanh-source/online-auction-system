package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class AuctionDAO {
    private Connection conn;

    public AuctionDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    // Mở một phiên đấu giá mới
    public boolean createAuction(int itemId, LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "INSERT INTO auctions (item_id, start_time, end_time, status) VALUES (?, ?, ?, 'OPEN')";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setTimestamp(2, Timestamp.valueOf(startTime));
            pstmt.setTimestamp(3, Timestamp.valueOf(endTime));

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi tạo phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    // Cập nhật giá cao nhất khi có người đặt giá hợp lệ
    public boolean updateHighestPrice(int auctionId, double newPrice, int bidderId) {
        String sql = "UPDATE auctions SET current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newPrice);
            pstmt.setInt(2, bidderId);
            pstmt.setInt(3, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật giá đấu: " + e.getMessage());
            return false;
        }
    }
}