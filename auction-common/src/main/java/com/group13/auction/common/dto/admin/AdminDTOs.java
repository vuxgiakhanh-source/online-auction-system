package com.group13.auction.common.dto.admin;

/** Namespace class chứa DTO cho Admin và System operations. */
public final class AdminDTOs {

    private AdminDTOs() {}

    /** Payload của ADMIN_BAN_USER. */
    public static class AdminBanUserDTO {
        private String userId;
        /** "FRAUD" | "LOW_RATING" | "SELLER_REFUND_DEFAULT" | "OTHER" */
        private String reason;

        public AdminBanUserDTO() {}

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** Payload của ADMIN_CREATE_STAFF. */
    public static class CreateStaffAdminDTO {
        private String username;
        private String password;
        private String email;

        public CreateStaffAdminDTO() {}

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    /** Payload của FRAUD_DETECTED_NOTIFY. */
    public static class FraudDetectedDTO {
        private String auctionId;
        private String suspectedUserId;
        private String suspectedUsername;
        private String description;

        public FraudDetectedDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getSuspectedUserId() { return suspectedUserId; }
        public void setSuspectedUserId(String suspectedUserId) { this.suspectedUserId = suspectedUserId; }
        public String getSuspectedUsername() { return suspectedUsername; }
        public void setSuspectedUsername(String suspectedUsername) { this.suspectedUsername = suspectedUsername; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /** Payload của SYSTEM_ANNOUNCEMENT. */
    public static class SystemAnnouncementDTO {
        private String message;
        /** "INFO" | "WARNING" | "CRITICAL" */
        private String severity;

        public SystemAnnouncementDTO() {}

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    /** Payload của SERVER_SHUTDOWN_NOTIFY. */
    public static class ServerShutdownDTO {
        private String reason;
        private int shutdownInSeconds;

        public ServerShutdownDTO() {}

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public int getShutdownInSeconds() { return shutdownInSeconds; }
        public void setShutdownInSeconds(int shutdownInSeconds) { this.shutdownInSeconds = shutdownInSeconds; }
    }
    /** Payload của ACCOUNT_BANNED_NOTIFY — gửi về client bị ban. */
    public static class AccountBannedDTO {
        private String reason;

        public AccountBannedDTO() {}

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** Payload của GET_NOTIFICATIONS_SUCCESS — một notification entry. */
    public static class NotificationDTO {
        private String id;
        private String type;
        private String title;
        private String body;
        private boolean read;
        private java.time.LocalDateTime createdAt;
        /** ID phiên liên quan (nếu có) để navigate. */
        private String relatedAuctionId;

        public NotificationDTO() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public String getRelatedAuctionId() { return relatedAuctionId; }
        public void setRelatedAuctionId(String relatedAuctionId) { this.relatedAuctionId = relatedAuctionId; }
    }
}
