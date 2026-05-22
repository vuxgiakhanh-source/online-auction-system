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

/**
 * DAO cho bảng {@code notifications}.
 *
 * <p>Schema: id, user_id, auction_id, title, body, is_read, created_at, updated_at.
 * (Đã sửa: trước đây SQL dùng cột {@code message} không tồn tại trong DB
 * → đổi sang {@code title} + {@code body} để khớp schema.)
 */
public class NotificationDAO {

    private static final Logger log = LoggerFactory.getLogger(NotificationDAO.class);

    public NotificationDAO() {}

    /** Lưu notification mới vào DB. */
    public boolean save(Notification notification) {
        String sql = "INSERT INTO notifications "
            + "(id, user_id, auction_id, title, body, is_read, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, notification.getId());
            pstmt.setString(2, notification.getUserId());
            pstmt.setString(3, notification.getAuctionId());   // nullable
            pstmt.setString(4, notification.getTitle());
            pstmt.setString(5, notification.getBody());
            pstmt.setBoolean(6, notification.isRead());
            pstmt.setTimestamp(7, Timestamp.valueOf(notification.getCreatedAt()));
            pstmt.setTimestamp(8, Timestamp.valueOf(notification.getUpdatedAt()));

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi lưu notification: userId={}, title={}",
                notification != null ? notification.getUserId() : null,
                notification != null ? notification.getTitle()  : null,
                e);
            return false;
        }
    }

    /** Load tất cả notification của user, mới nhất trước. */
    public List<Notification> findByUserId(String userId) {
        String sql = "SELECT id, user_id, auction_id, title, body, is_read, created_at, updated_at "
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

    /** Đánh dấu notification đã đọc. Chỉ cập nhật nếu thuộc về đúng user. */
    public boolean markRead(String notificationId, String userId) {
        String sql = "UPDATE notifications SET is_read = TRUE, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, notificationId);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi markRead: notificationId={}, userId={}", notificationId, userId, e);
            return false;
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Notification mapRow(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        return Notification.reconstitute(
            rs.getString("id"),
            createdTs != null ? createdTs.toLocalDateTime() : null,
            updatedTs != null ? updatedTs.toLocalDateTime() : null,
            rs.getString("user_id"),
            rs.getString("auction_id"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getBoolean("is_read")
        );
    }
}