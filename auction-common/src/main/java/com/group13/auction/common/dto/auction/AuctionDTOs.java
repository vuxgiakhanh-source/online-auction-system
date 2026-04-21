package com.group13.auction.common.dto.auction;

import java.time.LocalDateTime;
import java.util.List;

/** Namespace class chứa toàn bộ DTO liên quan đến Auction. */
public final class AuctionDTOs {

    private AuctionDTOs() {}

    // ══════════════════════════════════════════════════════════════════════════
    // Item DTO (nhúng trong AuctionDTO)
    // ══════════════════════════════════════════════════════════════════════════

    /** Thông tin sản phẩm đấu giá (nhúng trong AuctionDTO). */
    public static class ItemDTO {
        private String id;
        private String name;
        private String description;
        private String category;        // "ELECTRONICS" | "ART" | "VEHICLE"
        private long startingPrice;
        private String sellerId;
        private String sellerUsername;
        /** Thông tin mở rộng theo loại sản phẩm (brand, medium, mileage, v.v.) */
        private java.util.Map<String, Object> extraFields;

        public ItemDTO() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public long getStartingPrice() { return startingPrice; }
        public void setStartingPrice(long startingPrice) { this.startingPrice = startingPrice; }
        public String getSellerId() { return sellerId; }
        public void setSellerId(String sellerId) { this.sellerId = sellerId; }
        public String getSellerUsername() { return sellerUsername; }
        public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }
        public java.util.Map<String, Object> getExtraFields() { return extraFields; }
        public void setExtraFields(java.util.Map<String, Object> extraFields) { this.extraFields = extraFields; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionDTO — thông tin đầy đủ của một phiên
    // ══════════════════════════════════════════════════════════════════════════

    /** DTO đầy đủ của một phiên đấu giá. Dùng cho GET_AUCTION_DETAIL_SUCCESS và CREATE_AUCTION_SUCCESS. */
    public static class AuctionDTO {
        private String id;
        private ItemDTO item;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        /** Null nếu anti-sniping chưa kích hoạt. */
        private LocalDateTime extendedEndTime;
        private long currentPrice;
        private long reservePrice;
        /** "OPEN" | "RUNNING" | "FINISHED" | "PAID" | "CANCELED" | "RESERVE_NOT_MET" */
        private String status;
        private String currentLeaderId;
        private String currentLeaderUsername;
        private int viewerCount;
        private boolean reserveMet;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public AuctionDTO() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public ItemDTO getItem() { return item; }
        public void setItem(ItemDTO item) { this.item = item; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public LocalDateTime getExtendedEndTime() { return extendedEndTime; }
        public void setExtendedEndTime(LocalDateTime extendedEndTime) { this.extendedEndTime = extendedEndTime; }
        public long getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(long currentPrice) { this.currentPrice = currentPrice; }
        public long getReservePrice() { return reservePrice; }
        public void setReservePrice(long reservePrice) { this.reservePrice = reservePrice; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCurrentLeaderId() { return currentLeaderId; }
        public void setCurrentLeaderId(String currentLeaderId) { this.currentLeaderId = currentLeaderId; }
        public String getCurrentLeaderUsername() { return currentLeaderUsername; }
        public void setCurrentLeaderUsername(String currentLeaderUsername) { this.currentLeaderUsername = currentLeaderUsername; }
        public int getViewerCount() { return viewerCount; }
        public void setViewerCount(int viewerCount) { this.viewerCount = viewerCount; }
        public boolean isReserveMet() { return reserveMet; }
        public void setReserveMet(boolean reserveMet) { this.reserveMet = reserveMet; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionListDTO — danh sách phiên (có phân trang)
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của GET_AUCTION_LIST_SUCCESS. */
    public static class AuctionListDTO {
        private List<AuctionDTO> auctions;
        private int totalCount;
        private int page;
        private int pageSize;

        public AuctionListDTO() {}

        public AuctionListDTO(List<AuctionDTO> auctions, int totalCount) {
            this.auctions = auctions;
            this.totalCount = totalCount;
        }

        public List<AuctionDTO> getAuctions() { return auctions; }
        public void setAuctions(List<AuctionDTO> auctions) { this.auctions = auctions; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Request DTOs (Client → Server)
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của CREATE_AUCTION. */
    public static class CreateAuctionRequestDTO {
        private String itemName;
        private String itemDescription;
        /** "ELECTRONICS" | "ART" | "VEHICLE" */
        private String itemCategory;
        private long startingPrice;
        private long reservePrice;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        /** Thông tin extra theo loại item (brand, mileage, medium, v.v.) */
        private java.util.Map<String, Object> itemExtraFields;

        public CreateAuctionRequestDTO() {}

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getItemDescription() { return itemDescription; }
        public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
        public String getItemCategory() { return itemCategory; }
        public void setItemCategory(String itemCategory) { this.itemCategory = itemCategory; }
        public long getStartingPrice() { return startingPrice; }
        public void setStartingPrice(long startingPrice) { this.startingPrice = startingPrice; }
        public long getReservePrice() { return reservePrice; }
        public void setReservePrice(long reservePrice) { this.reservePrice = reservePrice; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public java.util.Map<String, Object> getItemExtraFields() { return itemExtraFields; }
        public void setItemExtraFields(java.util.Map<String, Object> itemExtraFields) { this.itemExtraFields = itemExtraFields; }
    }

    /** Payload của GET_AUCTION_LIST. */
    public static class AuctionListRequestDTO {
        /** null = lấy tất cả. Ví dụ: "RUNNING", "OPEN". */
        private String statusFilter;
        /** "START_TIME" | "VIEWER_COUNT" | "CURRENT_PRICE" */
        private String sortBy;
        private int page;
        private int pageSize;

        public AuctionListRequestDTO() {
            this.page = 0;
            this.pageSize = 20;
        }

        public String getStatusFilter() { return statusFilter; }
        public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }

    /** Payload của UPDATE_AUCTION. */
    public static class UpdateAuctionDTO {
        private String auctionId;
        private LocalDateTime newEndTime;
        private Long newReservePrice;

        public UpdateAuctionDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public LocalDateTime getNewEndTime() { return newEndTime; }
        public void setNewEndTime(LocalDateTime newEndTime) { this.newEndTime = newEndTime; }
        public Long getNewReservePrice() { return newReservePrice; }
        public void setNewReservePrice(Long newReservePrice) { this.newReservePrice = newReservePrice; }
    }

    /** Payload của CANCEL_AUCTION_REQUEST. */
    public static class CancelAuctionRequestDTO {
        private String auctionId;
        private String reason;

        public CancelAuctionRequestDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /** Payload của ADMIN_CANCEL_AUCTION. */
    public static class AdminCancelAuctionDTO {
        private String auctionId;
        /** "SELLER_REQUEST" | "FRAUD" | "SYSTEM_ERROR" | "NO_WINNER" | "RESERVE_NOT_MET" */
        private String reason;

        public AdminCancelAuctionDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Update / Notify DTOs (Server → Client)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Payload của các packet broadcast về vòng đời phiên:
     * AUCTION_STARTED_UPDATE, AUCTION_ENDED_UPDATE, AUCTION_NO_WINNER_UPDATE,
     * AUCTION_RESERVE_NOT_MET_UPDATE, AUCTION_CANCELED_UPDATE.
     */
    public static class AuctionUpdateDTO {
        private String auctionId;
        private String newStatus;
        private long finalPrice;
        private String winnerId;
        private String winnerUsername;
        /** Lý do hủy nếu bị cancel. */
        private String cancelReason;
        private LocalDateTime extendedEndTime;
        private String message;

        public AuctionUpdateDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
        public long getFinalPrice() { return finalPrice; }
        public void setFinalPrice(long finalPrice) { this.finalPrice = finalPrice; }
        public String getWinnerId() { return winnerId; }
        public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
        public String getWinnerUsername() { return winnerUsername; }
        public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }
        public String getCancelReason() { return cancelReason; }
        public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
        public LocalDateTime getExtendedEndTime() { return extendedEndTime; }
        public void setExtendedEndTime(LocalDateTime extendedEndTime) { this.extendedEndTime = extendedEndTime; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /** Payload của AUCTION_EXTENDED_NOTIFY. */
    public static class AuctionExtendedDTO {
        private String auctionId;
        private LocalDateTime newEndTime;
        private int extendedBySeconds;

        public AuctionExtendedDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public LocalDateTime getNewEndTime() { return newEndTime; }
        public void setNewEndTime(LocalDateTime newEndTime) { this.newEndTime = newEndTime; }
        public int getExtendedBySeconds() { return extendedBySeconds; }
        public void setExtendedBySeconds(int extendedBySeconds) { this.extendedBySeconds = extendedBySeconds; }
    }

    /** Payload của SELLER_CANCEL_REQUEST_NOTIFY (gửi cho Staff Admin). */
    public static class SellerCancelRequestNotifyDTO {
        private String auctionId;
        private String auctionName;
        private String sellerUsername;
        private String reason;
        private LocalDateTime requestTime;

        public SellerCancelRequestNotifyDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getAuctionName() { return auctionName; }
        public void setAuctionName(String auctionName) { this.auctionName = auctionName; }
        public String getSellerUsername() { return sellerUsername; }
        public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public LocalDateTime getRequestTime() { return requestTime; }
        public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }
    }

    /** Payload của JOIN_AUCTION_SUCCESS. */
    public static class JoinAuctionResponseDTO {
        private AuctionDTO auction;
        private long depositAmount;
        private long newAvailableBalance;

        public JoinAuctionResponseDTO() {}

        public AuctionDTO getAuction() { return auction; }
        public void setAuction(AuctionDTO auction) { this.auction = auction; }
        public long getDepositAmount() { return depositAmount; }
        public void setDepositAmount(long depositAmount) { this.depositAmount = depositAmount; }
        public long getNewAvailableBalance() { return newAvailableBalance; }
        public void setNewAvailableBalance(long newAvailableBalance) { this.newAvailableBalance = newAvailableBalance; }
    }

    /** Payload của AUCTION_UPCOMING_END_NOTIFY. */
    public static class AuctionUpcomingEndDTO {
        private String auctionId;
        private long remainingSeconds;

        public AuctionUpcomingEndDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getRemainingSeconds() { return remainingSeconds; }
        public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }
    }
}