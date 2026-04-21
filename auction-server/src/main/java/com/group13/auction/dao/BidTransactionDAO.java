package com.group13.auction.dao;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BidTransactionDAO {

    public BidTransactionDAO() {}

    /**
     * 1. Lưu lại lịch sử một lượt đặt giá vào Database
     */
    public boolean saveTransaction(BidTransaction tx) {
        String sql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, result) "
                + "VALUES (?, ?, ?, ?, ?)";

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
        String sql = "SELECT DISTINCT bidder_id FROM bid_transactions "
                + "WHERE auction_id = ? AND result != 'REJECTED'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                UserDAO userDAO = new UserDAO();
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
        String sql = "SELECT * FROM bid_transactions "
                + "WHERE auction_id = ? AND bidder_id != ? AND result = 'ACCEPTED' "
                + "ORDER BY bid_amount DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            pstmt.setString(2, excludedBidderId != null ? excludedBidderId : "");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm Runner-up: " + e.getMessage());
        }
        return null;
    }

    /**
     * 4. Lấy toàn bộ lịch sử bid ACCEPTED/ACCEPTED_RESERVE_NOT_MET của một phiên,
     *    sắp xếp theo thời gian tăng dần — dùng để vẽ Bid History Chart.
     *
     * <p>BidHandler.handleGetBidHistory() gọi method này để trả về danh sách
     * BidChartPointDTO cho client hiển thị line chart giá theo thời gian.
     *
     * @param auctionId ID phiên đấu giá
     * @return list BidTransaction theo thứ tự thời gian, rỗng nếu chưa có bid nào
     */
    public List<BidTransaction> findBidHistoryByAuction(String auctionId) {
        List<BidTransaction> history = new ArrayList<>();
        // Chỉ lấy bid được chấp nhận (cả khi chưa đạt reserve), bỏ qua REJECTED/OUTBID
        String sql = "SELECT * FROM bid_transactions "
                + "WHERE auction_id = ? AND result IN ('ACCEPTED', 'ACCEPTED_RESERVE_NOT_MET') "
                + "ORDER BY bid_time ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    BidTransaction tx = mapRow(rs);
                    if (tx != null) {
                        history.add(tx);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy bid history: " + e.getMessage());
        }
        return history;
    }

    // ── Private helper ────────────────────────────────────────────────────────

    /**
     * Map một ResultSet row thành BidTransaction.
     * Dùng chung cho findHighestValidBidExcept và findBidHistoryByAuction.
     */
    private BidTransaction mapRow(ResultSet rs) throws SQLException {
        String id            = rs.getString("id");
        String fetchedAuctionId = rs.getString("auction_id");
        String bidderId      = rs.getString("bidder_id");
        double amount        = rs.getDouble("bid_amount");
        String resultStr     = rs.getString("result");

        Timestamp bidTimeTs  = rs.getTimestamp("bid_time");
        LocalDateTime bidTime = (bidTimeTs != null)
                ? bidTimeTs.toLocalDateTime()
                : LocalDateTime.now();

        UserDAO userDAO = new UserDAO();
        NormalUser bidder = userDAO.findNormalUserById(bidderId);

        return BidTransaction.reconstitute(
                id,
                bidTime,
                bidTime,
                bidder,
                fetchedAuctionId,
                amount,
                bidTime,
                BidTransaction.BidResult.valueOf(resultStr)
        );
    }
}