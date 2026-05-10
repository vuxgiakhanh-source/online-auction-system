package com.group13.auction.dao;

import com.group13.auction.model.auction.AuctionWinner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AuctionWinnerDAO {
    private static final Logger log = LoggerFactory.getLogger(AuctionWinnerDAO.class);

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
            pstmt.setLong(4, winner.getFinalPrice());
            pstmt.setLong(5, winner.getDepositPaid());
            pstmt.setString(6, winner.getPaymentStatus().name());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi lưu thông tin người thắng cuộc: winnerId={}, auctionId={}",
                    winner != null ? winner.getId() : null,
                    winner != null ? winner.getAuctionId() : null,
                    e);
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
            log.error("Lỗi cập nhật trạng thái thanh toán: winnerId={}, status={}", winnerId, status, e);
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
            log.error("Lỗi kiểm tra pending payment: userId={}", userId, e);
        }
        return false;
    }
}