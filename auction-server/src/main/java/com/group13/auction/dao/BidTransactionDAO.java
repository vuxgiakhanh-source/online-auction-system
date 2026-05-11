package com.group13.auction.dao;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.auction.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BidTransactionDAO {
    private static final Logger log = LoggerFactory.getLogger(BidTransactionDAO.class);

    private final UserDAO userDAO = new UserDAO();
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
            pstmt.setLong(4, tx.getAmount());
            pstmt.setString(5, tx.getResult().name());

            boolean saved = pstmt.executeUpdate() > 0;
            log.debug("Bid transaction saved: txId={}, auctionId={}, bidderId={}, amount={}, result={}",
                    tx.getId(), tx.getAuctionId(), tx.getBidder().getId(), tx.getAmount(), tx.getResult());
            return saved;

        } catch (SQLException e) {
            log.error("Failed to save bid transaction: txId={}, auctionId={}, bidderId={}",
                    tx != null ? tx.getId() : null,
                    tx != null ? tx.getAuctionId() : null,
                    tx != null && tx.getBidder() != null ? tx.getBidder().getId() : null,
                    e);
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
            log.error("Failed to find bidders by auction: auctionId={}", auctionId, e);
        }
        log.debug("Bidders loaded by auction: auctionId={}, count={}", auctionId, bidders.size());
        return bidders;
    }

    /**
     * 3. Lấy toàn bộ lịch sử đặt giá HỢP LỆ theo auctionId, sắp xếp theo thời gian tăng dần.
     * Dùng cho GET_BID_HISTORY (vẽ line chart).
     *
     * FIX BUG #1: Thêm WHERE result != 'REJECTED' để không đưa bid bị từ chối lên chart.
     * Bid REJECTED là bid không hợp lệ (giá thấp hơn giá hiện tại, phiên đã đóng...)
     * — hiển thị chúng sẽ làm đường giá bị tụt xuống một cách sai.
     */
    public List<BidTransaction> findByAuctionId(String auctionId) {
        List<BidTransaction> result = new ArrayList<>();

        // FIX BUG #1: Thêm "AND result != 'REJECTED'" — chỉ lấy bid hợp lệ để vẽ chart
        String sql = "SELECT id, auction_id, bidder_id, bid_amount, result, bid_time " +
                "FROM bid_transactions " +
                "WHERE auction_id = ? AND result != 'REJECTED' " +
                "ORDER BY bid_time ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id             = rs.getString("id");
                    String fetchedAuction = rs.getString("auction_id");
                    String bidderId       = rs.getString("bidder_id");
                    long   amount         = rs.getLong("bid_amount");
                    String resultStr      = rs.getString("result");

                    java.sql.Timestamp ts = rs.getTimestamp("bid_time");
                    java.time.LocalDateTime bidTime = (ts != null)
                            ? ts.toLocalDateTime()
                            : java.time.LocalDateTime.now();

                    NormalUser bidder = userDAO.findNormalUserById(bidderId);

                    BidTransaction tx = BidTransaction.reconstitute(
                            id,
                            bidTime,
                            bidTime,
                            bidder,
                            fetchedAuction,
                            amount,
                            bidTime,
                            BidTransaction.BidResult.valueOf(resultStr)
                    );
                    result.add(tx);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find bid history by auction: auctionId={}", auctionId, e);
        }
        log.debug("Bid history loaded: auctionId={}, count={}", auctionId, result.size());
        return result;
    }

    /**
     * 4. Tìm lượt đặt giá HỢP LỆ cao nhất, ngoại trừ người thắng cuộc (winner).
     * Dùng để tìm Runner-up (người về nhì) cho Second Chance Offer.
     */
    public BidTransaction findHighestValidBidExcept(String auctionId, String excludedBidderId) {
        String sql = "SELECT * FROM bid_transactions WHERE auction_id = ? AND bidder_id != ? AND result = 'ACCEPTED' ORDER BY bid_amount DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);
            pstmt.setString(2, excludedBidderId != null ? excludedBidderId : "");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String fetchedAuctionId = rs.getString("auction_id");
                    String bidderId = rs.getString("bidder_id");
                    long amount = rs.getLong("bid_amount");
                    String resultStr = rs.getString("result");

                    java.sql.Timestamp bidTimeTs = rs.getTimestamp("bid_time");
                    java.time.LocalDateTime bidTime = (bidTimeTs != null) ?
                            bidTimeTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    UserDAO userDAO = new UserDAO();
                    NormalUser bidder = userDAO.findNormalUserById(bidderId);

                    return BidTransaction.reconstitute(
                            id,
                            bidTime,
                            bidTime,
                            bidder,
                            null,
                            amount,
                            bidTime,
                            BidTransaction.BidResult.valueOf(resultStr)
                    );
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find highest valid bid except bidder: auctionId={}, excludedBidderId={}",
                    auctionId, excludedBidderId, e);
        }
        log.debug("No runner-up bid found: auctionId={}, excludedBidderId={}", auctionId, excludedBidderId);
        return null;
    }
}
