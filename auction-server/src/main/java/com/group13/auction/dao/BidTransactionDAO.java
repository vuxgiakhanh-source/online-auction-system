package com.group13.auction.dao;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.auction.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BidTransactionDAO {

    public BidTransactionDAO() {}

    /**
     * 1. Lưu lại lịch sử một lượt đặt giá vào Database
     */
    public boolean saveTransaction(BidTransaction tx) {
        String sql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, result) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, tx.getId());
            pstmt.setString(2, tx.getAuctionId());
            pstmt.setString(3, tx.getBidder().getId());
            pstmt.setDouble(4, tx.getAmount());
            pstmt.setString(5, tx.getResult().name());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi lưu lịch sử Bid: " + e.getMessage());
            return false;
        }
    }

    /**
     * 2. Tìm danh sách tất cả những người (bidders) đã tham gia đặt giá hợp lệ trong một phiên.
     */
    public List<NormalUser> findBiddersByAuction(String auctionId) {
        List<NormalUser> bidders = new ArrayList<>();
        // Chỉ lấy những người có bid được ACCEPTED
        String sql = "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = ? AND result != 'REJECTED'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                UserDAO userDAO = new UserDAO(); // Dùng UserDAO để lấy thông tin chi tiết của User

                while (rs.next()) {
                    String bidderId = rs.getString("bidder_id");
                    NormalUser user = userDAO.findNormalUserById(bidderId);
                    if (user != null) {
                        bidders.add(user);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách bidder: " + e.getMessage());
        }
        return bidders;
    }

    /**
     * 3. Tìm lượt đặt giá HỢP LỆ cao nhất, ngoại trừ người thắng cuộc (winner).
     * Dùng để tìm Runner-up (người về nhì) cho Second Chance Offer.
     */
    public BidTransaction findHighestValidBidExcept(String auctionId, String excludedBidderId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? AND bidder_id != ? AND result = 'ACCEPTED' ORDER BY bid_amount DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            // Xử lý trường hợp không có winner (truyền chuỗi rỗng để SQL vẫn chạy đúng)
            pstmt.setString(2, excludedBidderId != null ? excludedBidderId : "");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 1. Rút trích dữ liệu thô từ Database
                    String id = rs.getString("id");
                    String fetchedAuctionId = rs.getString("auction_id");
                    String bidderId = rs.getString("bidder_id");
                    double amount = rs.getDouble("bid_amount");
                    String resultStr = rs.getString("result");

                    // Lấy thời gian (nếu DB lưu là TIMESTAMP)
                    java.sql.Timestamp bidTimeTs = rs.getTimestamp("bid_time");
                    java.time.LocalDateTime bidTime = (bidTimeTs != null) ?
                            bidTimeTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    // 2. Lấy đối tượng NormalUser từ Database
                    // 2. Lấy đối tượng NormalUser từ Database
                    UserDAO userDAO = new UserDAO();
                    NormalUser bidder = userDAO.findNormalUserById(bidderId);

                    // KHÔNG CẦN gọi AuctionDAO nữa để tránh rườm rà

                    // 3. Gọi hàm HỒI SINH (reconstitute)
                    return BidTransaction.reconstitute(
                            id,
                            bidTime,
                            bidTime,
                            bidder,
                            null,  // <-- TRUYỀN NULL VÀO ĐÂY LÀ XONG!
                            amount,
                            bidTime,
                            BidTransaction.BidResult.valueOf(resultStr)
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm Runner-up: " + e.getMessage());
        }
        return null;
    }
}