package com.group13.auction.dao;

import com.group13.auction.model.auction.SecondChanceOffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class SecondChanceOfferDAO {

    public SecondChanceOfferDAO() {}

    /**
     * Lưu một đề nghị Second Chance mới xuống DB
     */
    public boolean saveOffer(SecondChanceOffer offer) {
        String sql = "INSERT INTO second_chance_offers (id, auction_id, runner_up_id, offer_price, deposit_paid, status, deadline) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, offer.getId());
            pstmt.setString(2, offer.getAuctionId());
            pstmt.setString(3, offer.getRunnerUp().getId());
            pstmt.setDouble(4, offer.getOfferPrice());
            pstmt.setDouble(5, offer.getDepositPaid());
            pstmt.setString(6, offer.getStatus().name());
            pstmt.setTimestamp(7, Timestamp.valueOf(offer.getDeadline()));

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu Second Chance Offer: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái đề nghị (PENDING, ACCEPTED, DECLINED, EXPIRED)
     */
    public boolean updateOfferStatus(String offerId, String status) {
        String sql = "UPDATE second_chance_offers SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, offerId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái Second Chance Offer: " + e.getMessage());
            return false;
        }
    }
}
