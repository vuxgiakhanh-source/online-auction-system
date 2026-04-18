package com.group13.auction.dao;

import com.group13.auction.model.auction.AuctionWinner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AuctionWinnerDAO {

    public AuctionWinnerDAO() {}

    /**
     * Lưu thông tin người thắng cuộc mới vào bảng auction_winners
     */
    public boolean saveWinner(AuctionWinner winner) {
        String sql = "INSERT INTO auction_winners (id, auction_id, winner_id, final_price, deposit_paid, payment_status) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, winner.getId());
            pstmt.setString(2, winner.getAuctionId());
            pstmt.setString(3, winner.getWinner().getId());
            pstmt.setDouble(4, winner.getFinalPrice());
            pstmt.setDouble(5, winner.getDepositPaid());
            pstmt.setString(6, winner.getPaymentStatus().name());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu thông tin người thắng cuộc: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái thanh toán (PENDING, COMPLETED, EXPIRED)
     */
    public boolean updatePaymentStatus(String winnerId, String status) {
        String sql = "UPDATE auction_winners SET payment_status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, winnerId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái thanh toán: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra user có đang là winner/runner-up với trạng thái PENDING không.
     * Đã thực hiện TODO trong AccountService.deleteAccount():
     * chặn xóa tài khoản khi user chưa hoàn tất thanh toán phiên đấu giá.
     *
     * @param userId ID của user cần kiểm tra
     * @return true nếu có ít nhất 1 AuctionWinner ở trạng thái PENDING
     */
    public boolean hasPendingPayment(String userId) {
        String sql = "SELECT COUNT(*) FROM auction_winners WHERE winner_id = ? AND payment_status = 'PENDING'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra pending payment: " + e.getMessage());
        }
        return false;
    }
}