package com.group13.auction.model.notification;

import com.group13.auction.model.entity.Entity;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thông báo gửi tới user — khớp với bảng {@code notifications} trong DB.
 *
 * <p>Schema: id, user_id, auction_id, notification_type, title, body, is_read, created_at,
 * updated_at.
 */
public class Notification extends Entity {

  private static final Logger log = LoggerFactory.getLogger(Notification.class);

  private final String userId;
  private final String auctionId;
  private final String notificationType;
  private final String title;
  private final String body;
  private boolean isRead;

  // ── Static factory methods ─────────────────────────────────────────────

  /** Tạo mới notification (chưa có ID — Entity tự sinh UUID). */
  public static Notification create(String userId, String auctionId, String title, String body) {
    return create(userId, auctionId, NotificationTypes.SYSTEM, title, body);
  }

  public static Notification create(
      String userId, String auctionId, String notificationType, String title, String body) {
    return new Notification(userId, auctionId, notificationType, title, body);
  }

  /** Khôi phục từ DB (có sẵn ID, timestamps). */
  public static Notification reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String userId,
      String auctionId,
      String notificationType,
      String title,
      String body,
      boolean isRead) {
    return new Notification(
        id, createdAt, updatedAt, userId, auctionId, notificationType, title, body, isRead);
  }

  // ── Constructors ───────────────────────────────────────────────────────

  private Notification(
      String userId, String auctionId, String notificationType, String title, String body) {
    super();
    this.userId = userId;
    this.auctionId = auctionId;
    this.notificationType = notificationType != null ? notificationType : NotificationTypes.SYSTEM;
    this.title = title;
    this.body = body;
    this.isRead = false;
  }

  private Notification(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String userId,
      String auctionId,
      String notificationType,
      String title,
      String body,
      boolean isRead) {
    super(id, createdAt, updatedAt);
    this.userId = userId;
    this.auctionId = auctionId;
    this.notificationType = notificationType != null ? notificationType : NotificationTypes.SYSTEM;
    this.title = title;
    this.body = body;
    this.isRead = isRead;
  }

  // ── Mutation ───────────────────────────────────────────────────────────

  public void markRead() {
    this.isRead = true;
    markUpdated();
  }

  // ── Getters ────────────────────────────────────────────────────────────

  public String getUserId() {
    return userId;
  }

  public String getAuctionId() {
    return auctionId;
  }

  public String getNotificationType() {
    return notificationType;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public boolean isRead() {
    return isRead;
  }

  @Override
  public void printInfo() {
    log.info(
        "[NOTIFICATION] userId={} | read={} | title={} | body={}", userId, isRead, title, body);
  }
}
