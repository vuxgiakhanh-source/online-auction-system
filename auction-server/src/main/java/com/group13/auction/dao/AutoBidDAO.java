package com.group13.auction.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO persist AutoBid entries xuống DB.
 * Giải quyết vấn đề: auto-bid registry chỉ in-memory → mất khi server restart.
 *
 * <p>Schema (cần thêm vào database.sql):
 * <pre>
 * CREATE TABLE auto_bids (
 *   user_id     VARCHAR(36) NOT NULL,
 *   auction_id  VARCHAR(36) NOT NULL,
 *   max_bid     BIGINT      NOT NULL,
 *   registered_at DATETIME  NOT NULL,
 *   PRIMARY KEY (user_id, auction_id),
 *   FOREIGN KEY (auction_id) REFERENCES auctions(id)
 * );
 * </pre>
 */
public class AutoBidDAO {

    private static final Logger log = LoggerFactory.getLogger(AutoBidDAO.class);

    /** Lưu hoặc cập nhật auto-bid (upsert). */
    public boolean upsert(String userId, String auctionId, long maxBid, LocalDateTime registeredAt) {
        String sql = "INSERT INTO auto_bids (user_id, auction_id, max_bid, registered_at) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE max_bid = VALUES(max_bid), registered_at = VALUES(registered_at)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, auctionId);
            ps.setLong(3, maxBid);
            ps.setObject(4, registeredAt);
            boolean ok = ps.executeUpdate() > 0;
            log.debug("upsert auto_bid: userId={} auctionId={} maxBid={} ok={}", userId, auctionId, maxBid, ok);
            return ok;
        } catch (SQLException e) {
            // Dùng warn thay vì error vì caller (AutoBidRegistry.register) đã bắt và xử lý gracefully.
            // Lỗi phổ biến nhất là FK violation khi user/auction chưa có trong DB (test env).
            log.warn("Lỗi upsert auto_bid: userId={} auctionId={} — {}", userId, auctionId, e.getMessage());
            return false;
        }
    }

    /** Xóa 1 auto-bid entry. */
    public boolean delete(String userId, String auctionId) {
        String sql = "DELETE FROM auto_bids WHERE user_id = ? AND auction_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, auctionId);
            boolean ok = ps.executeUpdate() > 0;
            log.debug("delete auto_bid: userId={} auctionId={} removed={}", userId, auctionId, ok);
            return ok;
        } catch (SQLException e) {
            log.error("Lỗi delete auto_bid: userId={} auctionId={}", userId, auctionId, e);
            return false;
        }
    }

    /** Xóa tất cả auto-bid của một phiên khi phiên kết thúc. */
    public void deleteByAuction(String auctionId) {
        String sql = "DELETE FROM auto_bids WHERE auction_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            int rows = ps.executeUpdate();
            log.debug("deleteByAuction: auctionId={} rows={}", auctionId, rows);
        } catch (SQLException e) {
            log.error("Lỗi deleteByAuction: auctionId={}", auctionId, e);
        }
    }

    /** Load tất cả auto-bid còn hoạt động khi server restart. */
    public List<AutoBidRow> findAll() {
        List<AutoBidRow> list = new ArrayList<>();
        String sql = "SELECT user_id, auction_id, max_bid, registered_at FROM auto_bids";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AutoBidRow row = new AutoBidRow(
                        rs.getString("user_id"),
                        rs.getString("auction_id"),
                        rs.getLong("max_bid"),
                        rs.getTimestamp("registered_at").toLocalDateTime()
                );
                list.add(row);
            }
        } catch (SQLException e) {
            log.error("Lỗi findAll auto_bids", e);
        }
        return list;
    }

    /** Simple DTO trả về từ findAll(). */
    public static final class AutoBidRow {
        public final String userId;
        public final String auctionId;
        public final long maxBid;
        public final LocalDateTime registeredAt;

        public AutoBidRow(String userId, String auctionId, long maxBid, LocalDateTime registeredAt) {
            this.userId = userId;
            this.auctionId = auctionId;
            this.maxBid = maxBid;
            this.registeredAt = registeredAt;
        }
    }
}