package com.group13.auction.model.notification;

import com.group13.auction.model.entity.Entity;
import java.time.LocalDateTime;


public class Notification extends Entity {

    private final String userId;
    private final String auctionId;
    private final String message;
    private boolean isRead;

    public static Notification create(String userId, String auctionId, String message) {
        return new Notification(userId, auctionId, message);
    }

    public static Notification reconstitute(String id, LocalDateTime createdAt,
                                            LocalDateTime updatedAt, String userId,
                                            String auctionId, String message, boolean isRead) {
        return new Notification(id, createdAt, updatedAt, userId, auctionId, message, isRead);
    }

    private Notification(String userId, String auctionId, String message) {
        super();
        this.userId    = userId;
        this.auctionId = auctionId;
        this.message   = message;
        this.isRead    = false;
    }

    private Notification(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                         String userId, String auctionId, String message, boolean isRead) {
        super(id, createdAt, updatedAt);
        this.userId    = userId;
        this.auctionId = auctionId;
        this.message   = message;
        this.isRead    = isRead;
    }

    public void markRead() {
        this.isRead = true;
        markUpdated();
    }

    @Override
    public void printInfo() {
        System.out.printf("[NOTIFICATION] %s | Read: %s | %s%n",
                userId, isRead, message);
    }

    // Getters

    public String getUserId() { return userId; }
    public String getAuctionId() { return auctionId; }
    public String getMessage() { return message; }
    public boolean isRead() { return isRead; }
}