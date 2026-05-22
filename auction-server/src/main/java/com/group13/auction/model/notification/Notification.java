package com.group13.auction.model.notification;

import com.group13.auction.model.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Thông báo gửi tới user — khớp với bảng {@code notifications} trong DB.
 *
 * <p>Schema: id, user_id, auction_id, title, body, is_read, created_at, updated_at.
 * (Trước đây dùng field {@code message} — không khớp DB → đã đổi sang {@code title} + {@code body}.)
 */
public class Notification extends Entity {

    private static final Logger log = LoggerFactory.getLogger(Notification.class);

    private final String userId;
    private final String auctionId;
    private final String title;
    private final String body;
    private boolean isRead;

    // ── Static factory methods ─────────────────────────────────────────────

    /** Tạo mới notification (chưa có ID — Entity tự sinh UUID). */
    public static Notification create(String userId, String auctionId,
                                      String title, String body) {
        return new Notification(userId, auctionId, title, body);
    }

    /** Khôi phục từ DB (có sẵn ID, timestamps). */
    public static Notification reconstitute(String id,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt,
                                            String userId,
                                            String auctionId,
                                            String title,
                                            String body,
                                            boolean isRead) {
        return new Notification(id, createdAt, updatedAt, userId, auctionId, title, body, isRead);
    }

    // ── Constructors ───────────────────────────────────────────────────────

    private Notification(String userId, String auctionId, String title, String body) {
        super();
        this.userId    = userId;
        this.auctionId = auctionId;
        this.title     = title;
        this.body      = body;
        this.isRead    = false;
    }

    private Notification(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                         String userId, String auctionId,
                         String title, String body, boolean isRead) {
        super(id, createdAt, updatedAt);
        this.userId    = userId;
        this.auctionId = auctionId;
        this.title     = title;
        this.body      = body;
        this.isRead    = isRead;
    }

    // ── Mutation ───────────────────────────────────────────────────────────

    public void markRead() {
        this.isRead = true;
        markUpdated();
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String getUserId()    { return userId;    }
    public String getAuctionId() { return auctionId; }
    public String getTitle()     { return title;     }
    public String getBody()      { return body;      }
    public boolean isRead()      { return isRead;    }

    @Override
    public void printInfo() {
        log.info("[NOTIFICATION] userId={} | read={} | title={} | body={}",
            userId, isRead, title, body);
    }
}