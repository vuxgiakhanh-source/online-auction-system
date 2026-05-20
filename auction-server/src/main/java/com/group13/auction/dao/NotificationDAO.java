package com.group13.auction.dao;

import com.group13.auction.model.notification.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO cho bảng notifications. */
public class NotificationDAO {

    private static final Logger log = LoggerFactory.getLogger(NotificationDAO.class);

    public NotificationDAO() {}

    public boolean save(Notification notification) {
        String sql = "INSERT INTO notifications (id, user_id, auction_id, message, is_read, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, notification.getId());
            pstmt.setString(2, notification.getUserId());
            pstmt.setString(3, notification.getAuctionId());
            pstmt.setString(4, notification.getMessage());
            pstmt.setBoolean(5, notification.isRead());
            pstmt.setTimestamp(6, Timestamp.valueOf(notification.getCreatedAt()));
            pstmt.setTimestamp(7, Timestamp.valueOf(notification.getUpdatedAt()));
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi lưu notification: userId={}, auctionId={}",
                    notification != null ? notification.getUserId() : null,
                    notification != null ? notification.getAuctionId() : null,
                    e);
            return false;
        }
    }

    public List<Notification> findByUserId(String userId) {
        String sql = "SELECT id, user_id, auction_id, message, is_read, created_at, updated_at "
                + "FROM notifications WHERE user_id = ? ORDER BY created_at DESC";
        List<Notification> notifications = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Lỗi load notifications: userId={}", userId, e);
        }
        return notifications;
    }

    public boolean markRead(String notificationId, String userId) {
        String sql = "UPDATE notifications SET is_read = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, notificationId);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi markRead notificationId={}, userId={}", notificationId, userId, e);
            return false;
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        return Notification.reconstitute(
                rs.getString("id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getString("user_id"),
                rs.getString("auction_id"),
                rs.getString("message"),
                rs.getBoolean("is_read")
        );
    }
}
