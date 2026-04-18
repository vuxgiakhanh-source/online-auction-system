package com.group13.auction.common.dto.rating;

import java.time.LocalDateTime;
import java.util.List;

/** Namespace class chứa toàn bộ DTO liên quan đến Rating. */
public final class RatingDTOs {

    private RatingDTOs() {}

    /** Payload của RATE_SELLER. */
    public static class RateSellerRequestDTO {
        private String sellerId;
        private double rating;
        private String comment;
        private String auctionId;

        public RateSellerRequestDTO() {}

        public String getSellerId() { return sellerId; }
        public void setSellerId(String sellerId) { this.sellerId = sellerId; }
        public double getRating() { return rating; }
        public void setRating(double rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
    }

    /** Payload của RATE_BIDDER. */
    public static class RateBidderRequestDTO {
        private String bidderId;
        private double rating;
        private String comment;
        private String auctionId;

        public RateBidderRequestDTO() {}

        public String getBidderId() { return bidderId; }
        public void setBidderId(String bidderId) { this.bidderId = bidderId; }
        public double getRating() { return rating; }
        public void setRating(double rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
    }

    /** Một entry đánh giá. */
    public static class RatingEntryDTO {
        private String fromUserId;
        private String fromUsername;
        private double rating;
        private String comment;
        private LocalDateTime createdAt;

        public RatingEntryDTO() {}

        public String getFromUserId() { return fromUserId; }
        public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
        public String getFromUsername() { return fromUsername; }
        public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }
        public double getRating() { return rating; }
        public void setRating(double rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    /** Payload của GET_USER_RATINGS_SUCCESS. */
    public static class RatingHistoryDTO {
        private String userId;
        private double averageRating;
        private int totalRatings;
        private List<RatingEntryDTO> entries;

        public RatingHistoryDTO() {}

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public double getAverageRating() { return averageRating; }
        public void setAverageRating(double averageRating) { this.averageRating = averageRating; }
        public int getTotalRatings() { return totalRatings; }
        public void setTotalRatings(int totalRatings) { this.totalRatings = totalRatings; }
        public List<RatingEntryDTO> getEntries() { return entries; }
        public void setEntries(List<RatingEntryDTO> entries) { this.entries = entries; }
    }

    /** Payload của ACCOUNT_SUSPENDED_NOTIFY. */
    public static class AccountSuspendedDTO {
        private double currentRating;
        private double threshold;
        private String reason;

        public AccountSuspendedDTO() {}

        public double getCurrentRating() { return currentRating; }
        public void setCurrentRating(double currentRating) { this.currentRating = currentRating; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** Payload của ACCOUNT_RESTORED_NOTIFY. */
    public static class AccountRestoredDTO {
        private double newRating;
        private String newStatus;

        public AccountRestoredDTO() {}

        public double getNewRating() { return newRating; }
        public void setNewRating(double newRating) { this.newRating = newRating; }
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    }

    /** Payload của ACCOUNT_BANNED_NOTIFY. */
    public static class AccountBannedDTO {
        private String reason;
        private String bannedBy;

        public AccountBannedDTO() {}

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getBannedBy() { return bannedBy; }
        public void setBannedBy(String bannedBy) { this.bannedBy = bannedBy; }
    }
}
