package com.group13.auction.common.dto.bid;

import java.time.LocalDateTime;
import java.util.List;

/** Namespace class chứa toàn bộ DTO liên quan đến Bid. */
public final class BidDTOs {

    private BidDTOs() {}

    // ══════════════════════════════════════════════════════════════════════════
    // Manual Bid
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của PLACE_BID. */
    public static class BidRequestDTO {
        private String auctionId;
        private long amount;

        public BidRequestDTO() {}

        public BidRequestDTO(String auctionId, long amount) {
            this.auctionId = auctionId;
            this.amount = amount;
        }

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
    }

    /** Payload của PLACE_BID_SUCCESS. */
    public static class BidResultDTO {
        private String bidId;
        private String auctionId;
        private long amount;
        private boolean reserveMet;
        private long currentPrice;
        private LocalDateTime timestamp;

        public BidResultDTO() {}

        public String getBidId() { return bidId; }
        public void setBidId(String bidId) { this.bidId = bidId; }
        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
        public boolean isReserveMet() { return reserveMet; }
        public void setReserveMet(boolean reserveMet) { this.reserveMet = reserveMet; }
        public long getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(long currentPrice) { this.currentPrice = currentPrice; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Payload của BID_UPDATE và BID_RESERVE_NOT_MET_UPDATE (broadcast).
     * Gửi cho tất cả client đang xem phiên.
     */
    public static class BidUpdateDTO {
        private String auctionId;
        private long newCurrentPrice;
        /**
         * FIX: Giá trước khi bid này được chấp nhận.
         * Client dùng để hiển thị hướng tăng/giảm (trong đấu giá luôn tăng)
         * và delta: "+50.000đ".
         */
        private long previousPrice;
        /**
         * FIX: Delta = newCurrentPrice - previousPrice.
         * Luôn dương trong đấu giá hợp lệ.
         * Client hiển thị trực tiếp: "+200.000đ" mà không cần tính thêm.
         */
        private long priceChange;
        private String leaderId;
        private String leaderUsername;
        private boolean reserveMet;
        private LocalDateTime timestamp;
        /** Null nếu anti-sniping không kích hoạt, ngược lại chứa endTime mới. */
        private LocalDateTime newEndTime;

        public BidUpdateDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getNewCurrentPrice() { return newCurrentPrice; }
        public void setNewCurrentPrice(long newCurrentPrice) { this.newCurrentPrice = newCurrentPrice; }
        public long getPreviousPrice() { return previousPrice; }
        public void setPreviousPrice(long previousPrice) { this.previousPrice = previousPrice; }
        public long getPriceChange() { return priceChange; }
        public void setPriceChange(long priceChange) { this.priceChange = priceChange; }
        public String getLeaderId() { return leaderId; }
        public void setLeaderId(String leaderId) { this.leaderId = leaderId; }
        public String getLeaderUsername() { return leaderUsername; }
        public void setLeaderUsername(String leaderUsername) { this.leaderUsername = leaderUsername; }
        public boolean isReserveMet() { return reserveMet; }
        public void setReserveMet(boolean reserveMet) { this.reserveMet = reserveMet; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public LocalDateTime getNewEndTime() { return newEndTime; }
        public void setNewEndTime(LocalDateTime newEndTime) { this.newEndTime = newEndTime; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Auto-Bid
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của REGISTER_AUTO_BID và UPDATE_AUTO_BID. */
    public static class AutoBidRequestDTO {
        private String auctionId;
        private long maxBid;

        public AutoBidRequestDTO() {}

        public AutoBidRequestDTO(String auctionId, long maxBid) {
            this.auctionId = auctionId;
            this.maxBid = maxBid;
        }

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getMaxBid() { return maxBid; }
        public void setMaxBid(long maxBid) { this.maxBid = maxBid; }
    }

    /** Payload của REGISTER_AUTO_BID_SUCCESS và GET_AUTO_BID_STATUS_SUCCESS. */
    public static class AutoBidRegistrationDTO {
        private String auctionId;
        private long maxBid;
        private long currentSystemBid;
        private boolean active;
        private LocalDateTime registeredAt;

        public AutoBidRegistrationDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getMaxBid() { return maxBid; }
        public void setMaxBid(long maxBid) { this.maxBid = maxBid; }
        public long getCurrentSystemBid() { return currentSystemBid; }
        public void setCurrentSystemBid(long currentSystemBid) { this.currentSystemBid = currentSystemBid; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public LocalDateTime getRegisteredAt() { return registeredAt; }
        public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    }

    /**
     * Payload của AUTO_BID_TRIGGERED_NOTIFY.
     * Push riêng tới client có auto-bid đang hoạt động khi hệ thống tự bid thay.
     */
    public static class AutoBidTriggeredDTO {
        private String auctionId;
        private long bidAmount;
        private long newCurrentPrice;
        private long remainingMaxBid;
        private boolean isNowLeading;
        private LocalDateTime timestamp;

        public AutoBidTriggeredDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getBidAmount() { return bidAmount; }
        public void setBidAmount(long bidAmount) { this.bidAmount = bidAmount; }
        public long getNewCurrentPrice() { return newCurrentPrice; }
        public void setNewCurrentPrice(long newCurrentPrice) { this.newCurrentPrice = newCurrentPrice; }
        public long getRemainingMaxBid() { return remainingMaxBid; }
        public void setRemainingMaxBid(long remainingMaxBid) { this.remainingMaxBid = remainingMaxBid; }
        public boolean isNowLeading() { return isNowLeading; }
        public void setNowLeading(boolean nowLeading) { isNowLeading = nowLeading; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Payload của AUTO_BID_EXHAUSTED_NOTIFY.
     * Push khi auto-bid bị đối thủ vượt và maxBid đã cạn kiệt.
     */
    public static class AutoBidExhaustedDTO {
        private String auctionId;
        private long maxBid;
        private long currentPrice;
        private String leadingBidderUsername;

        public AutoBidExhaustedDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getMaxBid() { return maxBid; }
        public void setMaxBid(long maxBid) { this.maxBid = maxBid; }
        public long getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(long currentPrice) { this.currentPrice = currentPrice; }
        public String getLeadingBidderUsername() { return leadingBidderUsername; }
        public void setLeadingBidderUsername(String leadingBidderUsername) { this.leadingBidderUsername = leadingBidderUsername; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bid History (cho line chart)
    // ══════════════════════════════════════════════════════════════════════════

    /** Một điểm dữ liệu trên line chart. */
    public static class BidChartPointDTO {
        private String auctionId;
        private long price;
        private String bidderUsername;
        private LocalDateTime timestamp;
        /** true nếu bid này là auto-bid do hệ thống thực hiện. */
        private boolean isAutoBid;

        public BidChartPointDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getPrice() { return price; }
        public void setPrice(long price) { this.price = price; }
        public String getBidderUsername() { return bidderUsername; }
        public void setBidderUsername(String bidderUsername) { this.bidderUsername = bidderUsername; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public boolean isAutoBid() { return isAutoBid; }
        public void setAutoBid(boolean autoBid) { isAutoBid = autoBid; }
    }

    /** Payload của GET_BID_HISTORY_SUCCESS — toàn bộ lịch sử bid để khởi tạo chart. */
    public static class BidHistoryResponseDTO {
        private String auctionId;
        private List<BidChartPointDTO> points;
        private long startingPrice;
        private long reservePrice;

        public BidHistoryResponseDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public List<BidChartPointDTO> getPoints() { return points; }
        public void setPoints(List<BidChartPointDTO> points) { this.points = points; }
        public long getStartingPrice() { return startingPrice; }
        public void setStartingPrice(long startingPrice) { this.startingPrice = startingPrice; }
        public long getReservePrice() { return reservePrice; }
        public void setReservePrice(long reservePrice) { this.reservePrice = reservePrice; }
    }
}