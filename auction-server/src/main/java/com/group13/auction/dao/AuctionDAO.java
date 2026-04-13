package com.group13.auction.dao;

import com.group13.auction.model.auction.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class AuctionDAO {

    public AuctionDAO() {
        // Constructor rỗng, lấy Connection cục bộ trong từng hàm
    }

    /**
     * Lưu phiên đấu giá mới vào DB.
     * ID được sinh từ tầng Entity (Java) và truyền xuống.
     */
    public boolean createAuction(Auction auction) {
        String sql = "INSERT INTO auctions (id, item_id, start_time, end_time, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auction.getId());
            pstmt.setString(2, auction.getItem().getId());
            pstmt.setTimestamp(3, Timestamp.valueOf(auction.getStartTime()));
            pstmt.setTimestamp(4, Timestamp.valueOf(auction.getEndTime()));
            pstmt.setString(5, auction.getStatus().name());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi tạo phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái của phiên (OPEN -> RUNNING, FINISHED, CANCELED...)
     */
    public boolean updateAuctionStatus(String auctionId, String status) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật toàn bộ kết quả khi phiên kết thúc.
     * (Lưu trạng thái, giá cao nhất hiện tại và ID người chiến thắng nếu có).
     */
    public boolean updateAuctionResult(Auction auction) {
        String sql = "UPDATE auctions SET status = ?, current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auction.getStatus().name());
            pstmt.setDouble(2, auction.getCurrentPrice()); // Bảng DB là BIGINT, JDBC setDouble sẽ ép kiểu tự động

            // Xử lý trường hợp không có người chiến thắng (NULL)
            if (auction.getCurrentLeader() != null) {
                pstmt.setString(3, auction.getCurrentLeader().getId());
            } else {
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            }

            pstmt.setString(4, auction.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật kết quả phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật giá cao nhất khi có người đặt giá hợp lệ (Bid).
     * Hàm này bạn đã có, tôi chỉ sửa lại kiểu dữ liệu String cho khớp UUID.
     */
    public boolean updateHighestPrice(String auctionId, double newPrice, String bidderId) {
        String sql = "UPDATE auctions SET current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, newPrice);
            pstmt.setString(2, bidderId);
            pstmt.setString(3, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật giá đấu: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật số lượng người theo dõi (viewer_count) của phiên đấu giá
     */
    public boolean updateViewerCount(String auctionId, int count) {
        String sql = "UPDATE auctions SET viewer_count = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, count);
            pstmt.setString(2, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số lượt xem phiên đấu giá: " + e.getMessage());
            return false;
        }
    }
}