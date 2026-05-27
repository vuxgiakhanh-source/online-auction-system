package com.group13.auction.dao;

import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.notification.NotificationTypes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO cho bảng {@code notifications}.
 *
 * <p>Schema: id, user_id, auction_id, notification_type, title, body, is_read, created_at,
 * updated_at. (Đã sửa: trước đây SQL dùng cột {@code message} không tồn tại trong DB → đổi sang
 * {@code title} + {@code body} để khớp schema.)
 */
public class NotificationDAO {

  private static final Logger log = LoggerFactory.getLogger(NotificationDAO.class);

  public NotificationDAO() {}

  /** Lưu notification mới vào DB. */
  public boolean save(Notification notification) {
    if (notification == null) {
      return false;
    }
    if (saveWithTypeColumn(notification)) {
      return true;
    }
    return saveLegacyWithoutTypeColumn(notification);
  }

  private boolean saveWithTypeColumn(Notification notification) {
    String sql =
        "INSERT INTO notifications (id, user_id, auction_id, notification_type, title, body,"
            + " is_read, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, notification.getId());
      pstmt.setString(2, notification.getUserId());
      pstmt.setString(3, notification.getAuctionId());
      pstmt.setString(4, notification.getNotificationType());
      pstmt.setString(5, notification.getTitle());
      pstmt.setString(6, notification.getBody());
      pstmt.setBoolean(7, notification.isRead());
      pstmt.setTimestamp(8, Timestamp.valueOf(notification.getCreatedAt()));
      pstmt.setTimestamp(9, Timestamp.valueOf(notification.getUpdatedAt()));

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      if (isUnknownColumn(e, "notification_type")) {
        return false;
      }
      log.error(
          "Lỗi lưu notification: userId={}, title={}",
          notification.getUserId(),
          notification.getTitle(),
          e);
      return false;
    }
  }

  private boolean saveLegacyWithoutTypeColumn(Notification notification) {
    String sql =
        "INSERT INTO notifications "
            + "(id, user_id, auction_id, title, body, is_read, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, notification.getId());
      pstmt.setString(2, notification.getUserId());
      pstmt.setString(3, notification.getAuctionId());
      pstmt.setString(4, notification.getTitle());
      pstmt.setString(5, notification.getBody());
      pstmt.setBoolean(6, notification.isRead());
      pstmt.setTimestamp(7, Timestamp.valueOf(notification.getCreatedAt()));
      pstmt.setTimestamp(8, Timestamp.valueOf(notification.getUpdatedAt()));

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi lưu notification (legacy schema): userId={}, title={}",
          notification.getUserId(),
          notification.getTitle(),
          e);
      return false;
    }
  }

  private static boolean isUnknownColumn(SQLException e, String columnName) {
    String msg = e.getMessage();
    return msg != null && msg.toLowerCase().contains(columnName.toLowerCase());
  }

  /** Load tất cả notification của user, mới nhất trước. */
  public List<Notification> findByUserId(String userId) {
    String sql =
        "SELECT id, user_id, auction_id, notification_type, title, body, is_read, created_at,"
            + " updated_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC";
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
    String sql =
        "UPDATE notifications SET is_read = TRUE, updated_at = CURRENT_TIMESTAMP "
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
    String type =
        hasColumn(rs, "notification_type")
            ? rs.getString("notification_type")
            : NotificationTypes.SYSTEM;
    return Notification.reconstitute(
        rs.getString("id"),
        createdTs != null ? createdTs.toLocalDateTime() : null,
        updatedTs != null ? updatedTs.toLocalDateTime() : null,
        rs.getString("user_id"),
        rs.getString("auction_id"),
        type,
        rs.getString("title"),
        rs.getString("body"),
        rs.getBoolean("is_read"));
  }

  private static boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
    var meta = rs.getMetaData();
    int count = meta.getColumnCount();
    for (int i = 1; i <= count; i++) {
      if (columnName.equalsIgnoreCase(meta.getColumnLabel(i))) {
        return true;
      }
    }
    return false;
  }
}
