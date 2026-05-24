package com.group13.auction.common.dto.auction;

import java.time.LocalDateTime;
import java.util.List;

/** Namespace class chứa toàn bộ DTO liên quan đến Auction. */
public final class AuctionDTOs {

    private AuctionDTOs() {}

    // ══════════════════════════════════════════════════════════════════════════
    // Item DTO
    // ══════════════════════════════════════════════════════════════════════════

    /** Thông tin sản phẩm đấu giá (nhúng trong AuctionDTO). */
    public static class ItemDTO {
        private String id;
        private String name;
        private String description;
        private String category;
        private double startingPrice;
        private String sellerId;
        private String sellerUsername;
        private java.util.Map<String, Object> extraFields;

        /**
         * Danh sách URL ảnh sản phẩm dạng "/uploads/items/{uuid}.jpg".
         * Null-safe: null khi client cũ nhận từ server cũ chưa có field này.
         * Dùng hasImages() để kiểm tra an toàn.
         */
        private List<String> imageUrls;

        public ItemDTO() {}

        public String getId()                                          { return id; }
        public void setId(String id)                                   { this.id = id; }
        public String getName()                                        { return name; }
        public void setName(String name)                               { this.name = name; }
        public String getDescription()                                 { return description; }
        public void setDescription(String description)                 { this.description = description; }
        public String getCategory()                                    { return category; }
        public void setCategory(String category)                       { this.category = category; }
        public double getStartingPrice()                               { return startingPrice; }
        public void setStartingPrice(double startingPrice)             { this.startingPrice = startingPrice; }
        public String getSellerId()                                    { return sellerId; }
        public void setSellerId(String sellerId)                       { this.sellerId = sellerId; }
        public String getSellerUsername()                              { return sellerUsername; }
        public void setSellerUsername(String sellerUsername)           { this.sellerUsername = sellerUsername; }
        public java.util.Map<String, Object> getExtraFields()         { return extraFields; }
        public void setExtraFields(java.util.Map<String, Object> extraFields) { this.extraFields = extraFields; }

        public List<String> getImageUrls()                             { return imageUrls; }
        public void setImageUrls(List<String> imageUrls)               { this.imageUrls = imageUrls; }

        /** Null-safe — trả về false nếu imageUrls null hoặc rỗng. */
        public boolean hasImages() {
            return imageUrls != null && !imageUrls.isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AuctionDTO {
        private String id;
        private ItemDTO item;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime extendedEndTime;
        private double currentPrice;
        private double reservePrice;
        private String status;
        private String currentLeaderId;
        private String currentLeaderUsername;
        private int viewerCount;
        private boolean reserveMet;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        /**
         * Trạng thái thanh toán của AuctionWinner (PENDING, FUNDS_HELD, ITEM_RECEIVED, ...).
         * Null nếu auction chưa FINISHED hoặc chưa có winner.
         * Client dùng để hiển thị đúng trạng thái đơn hàng sau refresh.
         */
        private String paymentStatus;
        /** Hạn xác nhận nhận hàng (7 ngày sau thanh toán). Null nếu chưa thanh toán. */
        private LocalDateTime confirmReceiptDeadline;
        /** Hạn gửi report (3 ngày sau bấm nhận hàng). Null nếu chưa xác nhận nhận hàng. */
        private LocalDateTime reportDeadline;

        /**
         * User hiện tại đã JOIN phiên này chưa (đặt cọc thành công, được quyền bid).
         * Server điền dựa trên {@code user.hasJoined(auctionId)}.
         * Null nếu request không gắn với user cụ thể (ví dụ: anonymous watch, broadcast list).
         * Client dùng để khôi phục trạng thái join sau khi tắt/mở lại app.
         */
        private Boolean joinedByCurrentUser;

        /**
         * User hiện tại đã rời phiên này chưa (LEAVE_AUCTION, không thể join lại).
         * Server điền dựa trên {@code user.hasLeft(auctionId)}.
         * Null nếu request không gắn với user cụ thể.
         * Client dùng để ẩn nút "Tham gia đặt giá" và không cần gửi JOIN rồi bị reject.
         */
        private Boolean leftByCurrentUser;

        public AuctionDTO() {}

        public String getId()                                              { return id; }
        public void setId(String id)                                       { this.id = id; }
        public ItemDTO getItem()                                           { return item; }
        public void setItem(ItemDTO item)                                  { this.item = item; }
        public LocalDateTime getStartTime()                                { return startTime; }
        public void setStartTime(LocalDateTime startTime)                  { this.startTime = startTime; }
        public LocalDateTime getEndTime()                                  { return endTime; }
        public void setEndTime(LocalDateTime endTime)                      { this.endTime = endTime; }
        public LocalDateTime getExtendedEndTime()                          { return extendedEndTime; }
        public void setExtendedEndTime(LocalDateTime extendedEndTime)      { this.extendedEndTime = extendedEndTime; }
        public double getCurrentPrice()                                    { return currentPrice; }
        public void setCurrentPrice(double currentPrice)                   { this.currentPrice = currentPrice; }
        public double getReservePrice()                                    { return reservePrice; }
        public void setReservePrice(double reservePrice)                   { this.reservePrice = reservePrice; }
        public String getStatus()                                          { return status; }
        public void setStatus(String status)                               { this.status = status; }
        public String getCurrentLeaderId()                                 { return currentLeaderId; }
        public void setCurrentLeaderId(String currentLeaderId)             { this.currentLeaderId = currentLeaderId; }
        public String getCurrentLeaderUsername()                           { return currentLeaderUsername; }
        public void setCurrentLeaderUsername(String currentLeaderUsername) { this.currentLeaderUsername = currentLeaderUsername; }
        public int getViewerCount()                                        { return viewerCount; }
        public void setViewerCount(int viewerCount)                        { this.viewerCount = viewerCount; }
        public boolean isReserveMet()                                      { return reserveMet; }
        public void setReserveMet(boolean reserveMet)                      { this.reserveMet = reserveMet; }
        public LocalDateTime getCreatedAt()                                { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt)                  { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt()                                { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt)                  { this.updatedAt = updatedAt; }
        public String getPaymentStatus()                                   { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus)                 { this.paymentStatus = paymentStatus; }
        public LocalDateTime getConfirmReceiptDeadline()                   { return confirmReceiptDeadline; }
        public void setConfirmReceiptDeadline(LocalDateTime d)             { this.confirmReceiptDeadline = d; }
        public LocalDateTime getReportDeadline()                           { return reportDeadline; }
        public void setReportDeadline(LocalDateTime reportDeadline)        { this.reportDeadline = reportDeadline; }
        public Boolean getJoinedByCurrentUser()                            { return joinedByCurrentUser; }
        public void setJoinedByCurrentUser(Boolean joinedByCurrentUser)    { this.joinedByCurrentUser = joinedByCurrentUser; }
        public Boolean getLeftByCurrentUser()                              { return leftByCurrentUser; }
        public void setLeftByCurrentUser(Boolean leftByCurrentUser)        { this.leftByCurrentUser = leftByCurrentUser; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionListDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AuctionListDTO {
        private List<AuctionDTO> auctions;
        private int totalCount;
        private int page;
        private int pageSize;

        public AuctionListDTO() {}

        public AuctionListDTO(List<AuctionDTO> auctions, int totalCount) {
            this.auctions   = auctions;
            this.totalCount = totalCount;
        }

        public List<AuctionDTO> getAuctions()              { return auctions; }
        public void setAuctions(List<AuctionDTO> auctions) { this.auctions = auctions; }
        public int getTotalCount()                         { return totalCount; }
        public void setTotalCount(int totalCount)          { this.totalCount = totalCount; }
        public int getPage()                               { return page; }
        public void setPage(int page)                      { this.page = page; }
        public int getPageSize()                           { return pageSize; }
        public void setPageSize(int pageSize)              { this.pageSize = pageSize; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CreateAuctionRequestDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class CreateAuctionRequestDTO {
        private String itemName;
        private String itemDescription;
        private String itemCategory;
        private double startingPrice;
        private double reservePrice;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private java.util.Map<String, Object> itemExtraFields;

        /**
         * Danh sách URL ảnh sản phẩm (đã upload lên ImageUploadServer).
         * Mỗi phần tử là URL dạng "/uploads/items/{uuid}.jpg".
         * Null hoặc rỗng nếu seller không upload ảnh.
         */
        private List<String> imageUrls;

        public CreateAuctionRequestDTO() {}

        public String getItemName()                                        { return itemName; }
        public void setItemName(String itemName)                           { this.itemName = itemName; }
        public String getItemDescription()                                 { return itemDescription; }
        public void setItemDescription(String itemDescription)             { this.itemDescription = itemDescription; }
        public String getItemCategory()                                    { return itemCategory; }
        public void setItemCategory(String itemCategory)                   { this.itemCategory = itemCategory; }
        public double getStartingPrice()                                   { return startingPrice; }
        public void setStartingPrice(double startingPrice)                 { this.startingPrice = startingPrice; }
        public double getReservePrice()                                    { return reservePrice; }
        public void setReservePrice(double reservePrice)                   { this.reservePrice = reservePrice; }
        public LocalDateTime getStartTime()                                { return startTime; }
        public void setStartTime(LocalDateTime startTime)                  { this.startTime = startTime; }
        public LocalDateTime getEndTime()                                  { return endTime; }
        public void setEndTime(LocalDateTime endTime)                      { this.endTime = endTime; }
        public java.util.Map<String, Object> getItemExtraFields()         { return itemExtraFields; }
        public void setItemExtraFields(java.util.Map<String, Object> f)   { this.itemExtraFields = f; }

        public List<String> getImageUrls()                                 { return imageUrls; }
        public void setImageUrls(List<String> imageUrls)                   { this.imageUrls = imageUrls; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionListRequestDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AuctionListRequestDTO {
        private String statusFilter;
        private String sortBy;
        private int page;
        private int pageSize;
        /**
         * Phạm vi lọc theo hoạt động của user hiện tại:
         * <ul>
         *   <li>{@code ALL}      — tất cả phiên (mặc định, không lọc thêm).</li>
         *   <li>{@code OWNED}    — phiên do user hiện tại tạo (seller).</li>
         *   <li>{@code JOINED}   — phiên user đã tham gia đặt cọc.</li>
         *   <li>{@code WATCHING} — phiên user đang theo dõi.</li>
         * </ul>
         * Null hoặc trống tương đương {@code ALL}.
         */
        private String scopeFilter;

        public AuctionListRequestDTO() {
            this.page     = 0;
            this.pageSize = 20;
        }

        public String getStatusFilter()                    { return statusFilter; }
        public void setStatusFilter(String statusFilter)   { this.statusFilter = statusFilter; }
        public String getSortBy()                          { return sortBy; }
        public void setSortBy(String sortBy)               { this.sortBy = sortBy; }
        public int getPage()                               { return page; }
        public void setPage(int page)                      { this.page = page; }
        public int getPageSize()                           { return pageSize; }
        public void setPageSize(int pageSize)              { this.pageSize = pageSize; }
        public String getScopeFilter()                     { return scopeFilter; }
        public void setScopeFilter(String scopeFilter)     { this.scopeFilter = scopeFilter; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UpdateAuctionDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class UpdateAuctionDTO {
        private String auctionId;
        private LocalDateTime newEndTime;
        private Double newReservePrice;

        public UpdateAuctionDTO() {}

        public String getAuctionId()                       { return auctionId; }
        public void setAuctionId(String auctionId)         { this.auctionId = auctionId; }
        public LocalDateTime getNewEndTime()               { return newEndTime; }
        public void setNewEndTime(LocalDateTime newEndTime){ this.newEndTime = newEndTime; }
        public Double getNewReservePrice()                 { return newReservePrice; }
        public void setNewReservePrice(Double newReservePrice) { this.newReservePrice = newReservePrice; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CancelAuctionRequestDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class CancelAuctionRequestDTO {
        private String auctionId;
        private String reason;

        public CancelAuctionRequestDTO() {}

        public String getAuctionId()                       { return auctionId; }
        public void setAuctionId(String auctionId)         { this.auctionId = auctionId; }
        public String getReason()                          { return reason; }
        public void setReason(String reason)               { this.reason = reason; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AdminCancelAuctionDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AdminCancelAuctionDTO {
        private String auctionId;
        private String reason;

        public AdminCancelAuctionDTO() {}

        public String getAuctionId()                       { return auctionId; }
        public void setAuctionId(String auctionId)         { this.auctionId = auctionId; }
        public String getReason()                          { return reason; }
        public void setReason(String reason)               { this.reason = reason; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionUpdateDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AuctionUpdateDTO {
        private String auctionId;
        private String newStatus;
        private double finalPrice;
        private String winnerId;
        private String winnerUsername;
        private String cancelReason;
        private LocalDateTime extendedEndTime;
        private String message;

        public AuctionUpdateDTO() {}

        public String getAuctionId()                                   { return auctionId; }
        public void setAuctionId(String auctionId)                     { this.auctionId = auctionId; }
        public String getNewStatus()                                   { return newStatus; }
        public void setNewStatus(String newStatus)                     { this.newStatus = newStatus; }
        public double getFinalPrice()                                  { return finalPrice; }
        public void setFinalPrice(double finalPrice)                   { this.finalPrice = finalPrice; }
        public String getWinnerId()                                    { return winnerId; }
        public void setWinnerId(String winnerId)                       { this.winnerId = winnerId; }
        public String getWinnerUsername()                              { return winnerUsername; }
        public void setWinnerUsername(String winnerUsername)           { this.winnerUsername = winnerUsername; }
        public String getCancelReason()                                { return cancelReason; }
        public void setCancelReason(String cancelReason)               { this.cancelReason = cancelReason; }
        public LocalDateTime getExtendedEndTime()                      { return extendedEndTime; }
        public void setExtendedEndTime(LocalDateTime extendedEndTime)  { this.extendedEndTime = extendedEndTime; }
        public String getMessage()                                     { return message; }
        public void setMessage(String message)                         { this.message = message; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionExtendedDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AuctionExtendedDTO {
        private String auctionId;
        private LocalDateTime newEndTime;
        private int extendedBySeconds;

        public AuctionExtendedDTO() {}

        public String getAuctionId()                                { return auctionId; }
        public void setAuctionId(String auctionId)                  { this.auctionId = auctionId; }
        public LocalDateTime getNewEndTime()                        { return newEndTime; }
        public void setNewEndTime(LocalDateTime newEndTime)         { this.newEndTime = newEndTime; }
        public int getExtendedBySeconds()                           { return extendedBySeconds; }
        public void setExtendedBySeconds(int extendedBySeconds)     { this.extendedBySeconds = extendedBySeconds; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SellerCancelRequestNotifyDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class SellerCancelRequestNotifyDTO {
        private String auctionId;
        private String auctionName;
        private String sellerUsername;
        private String reason;
        private LocalDateTime requestTime;

        public SellerCancelRequestNotifyDTO() {}

        public String getAuctionId()                               { return auctionId; }
        public void setAuctionId(String auctionId)                 { this.auctionId = auctionId; }
        public String getAuctionName()                             { return auctionName; }
        public void setAuctionName(String auctionName)             { this.auctionName = auctionName; }
        public String getSellerUsername()                          { return sellerUsername; }
        public void setSellerUsername(String sellerUsername)       { this.sellerUsername = sellerUsername; }
        public String getReason()                                  { return reason; }
        public void setReason(String reason)                       { this.reason = reason; }
        public LocalDateTime getRequestTime()                      { return requestTime; }
        public void setRequestTime(LocalDateTime requestTime)      { this.requestTime = requestTime; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JoinAuctionResponseDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class JoinAuctionResponseDTO {
        private AuctionDTO auction;
        private double depositAmount;
        private double newAvailableBalance;

        public JoinAuctionResponseDTO() {}

        public AuctionDTO getAuction()                                 { return auction; }
        public void setAuction(AuctionDTO auction)                     { this.auction = auction; }
        public double getDepositAmount()                               { return depositAmount; }
        public void setDepositAmount(double depositAmount)             { this.depositAmount = depositAmount; }
        public double getNewAvailableBalance()                         { return newAvailableBalance; }
        public void setNewAvailableBalance(double newAvailableBalance) { this.newAvailableBalance = newAvailableBalance; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LeaveAuctionResponseDTO
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Payload của LEAVE_AUCTION_SUCCESS.
     * Mang thông tin về cọc bị tịch thu (nếu có) để client hiển thị cảnh báo.
     */
    public static class LeaveAuctionResponseDTO {
        private String  auctionId;
        /** true nếu cọc bị tịch thu toàn bộ (rời khi đang leader hoặc sau 2/3 thời gian). */
        private boolean depositForfeited;
        /** Số tiền cọc bị tịch thu (0 nếu không bị phạt). */
        private long    forfeitedAmount;
        /** true nếu rating bị trừ điểm. */
        private boolean ratingPenalized;
        /** Số dư khả dụng sau khi rời phiên. */
        private long    newAvailableBalance;

        public LeaveAuctionResponseDTO() {}

        public String  getAuctionId()             { return auctionId; }
        public void    setAuctionId(String v)     { this.auctionId = v; }
        public boolean isDepositForfeited()        { return depositForfeited; }
        public void    setDepositForfeited(boolean v) { this.depositForfeited = v; }
        public long    getForfeitedAmount()        { return forfeitedAmount; }
        public void    setForfeitedAmount(long v)  { this.forfeitedAmount = v; }
        public boolean isRatingPenalized()         { return ratingPenalized; }
        public void    setRatingPenalized(boolean v) { this.ratingPenalized = v; }
        public long    getNewAvailableBalance()    { return newAvailableBalance; }
        public void    setNewAvailableBalance(long v) { this.newAvailableBalance = v; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AuctionUpcomingEndDTO
    // ══════════════════════════════════════════════════════════════════════════

    public static class AuctionUpcomingEndDTO {
        private String auctionId;
        private long remainingSeconds;

        public AuctionUpcomingEndDTO() {}

        public String getAuctionId()                               { return auctionId; }
        public void setAuctionId(String auctionId)                 { this.auctionId = auctionId; }
        public long getRemainingSeconds()                          { return remainingSeconds; }
        public void setRemainingSeconds(long remainingSeconds)     { this.remainingSeconds = remainingSeconds; }
    }
}