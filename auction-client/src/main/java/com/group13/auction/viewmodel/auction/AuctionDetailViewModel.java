package com.group13.auction.viewmodel.auction;

import java.util.List;

/** Dữ liệu chi tiết phiên đấu giá đã format cho màn auction detail. */
public final class AuctionDetailViewModel {

    private final String auctionId;
    private final String itemName;
    private final String description;
    private final String categoryText;
    private final String sellerText;
    private final String rawStatus;
    private final String statusText;
    private final String currentLeaderId;
    private final String currentLeaderUsername;
    private final String currentPriceText;
    private final String startingPriceText;
    private final String reservePriceText;
    private final String leaderText;
    private final String viewerCountText;
    private final String startTimeText;
    private final String endTimeText;
    private final String remainingTimeText;
    private final List<String> imageUrls;
    private final boolean joinable;
    private final boolean liveBiddingAllowed;
    private final double currentPrice;

    /** Tạo view model chi tiết phiên đấu giá. */
    public AuctionDetailViewModel(
        String auctionId,
        String itemName,
        String description,
        String categoryText,
        String sellerText,
        String rawStatus,
        String statusText,
        String currentLeaderId,
        String currentLeaderUsername,
        String currentPriceText,
        String startingPriceText,
        String reservePriceText,
        String leaderText,
        String viewerCountText,
        String startTimeText,
        String endTimeText,
        String remainingTimeText,
        List<String> imageUrls,
        boolean joinable,
        boolean liveBiddingAllowed,
        double currentPrice) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.description = description;
        this.categoryText = categoryText;
        this.sellerText = sellerText;
        this.rawStatus = rawStatus == null ? "" : rawStatus;
        this.statusText = statusText;
        this.currentLeaderId = currentLeaderId == null ? "" : currentLeaderId;
        this.currentLeaderUsername = currentLeaderUsername == null ? "" : currentLeaderUsername;
        this.currentPriceText = currentPriceText;
        this.startingPriceText = startingPriceText;
        this.reservePriceText = reservePriceText;
        this.leaderText = leaderText;
        this.viewerCountText = viewerCountText;
        this.startTimeText = startTimeText;
        this.endTimeText = endTimeText;
        this.remainingTimeText = remainingTimeText;
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        this.joinable = joinable;
        this.liveBiddingAllowed = liveBiddingAllowed;
        this.currentPrice = currentPrice;
    }

    public String auctionId() {
        return auctionId;
    }

    public String itemName() {
        return itemName;
    }

    public String description() {
        return description;
    }

    public String categoryText() {
        return categoryText;
    }

    public String sellerText() {
        return sellerText;
    }

    public String rawStatus() {
        return rawStatus;
    }

    public String statusText() {
        return statusText;
    }

    public String currentLeaderId() {
        return currentLeaderId;
    }

    public String currentLeaderUsername() {
        return currentLeaderUsername;
    }

    public String currentPriceText() {
        return currentPriceText;
    }

    public String startingPriceText() {
        return startingPriceText;
    }

    public String reservePriceText() {
        return reservePriceText;
    }

    public String leaderText() {
        return leaderText;
    }

    public String viewerCountText() {
        return viewerCountText;
    }

    public String startTimeText() {
        return startTimeText;
    }

    public String endTimeText() {
        return endTimeText;
    }

    public String remainingTimeText() {
        return remainingTimeText;
    }

    public List<String> imageUrls() {
        return imageUrls;
    }

    public String primaryImageUrl() {
        return imageUrls.isEmpty() ? "" : imageUrls.get(0);
    }

    public boolean hasImages() {
        return !imageUrls.isEmpty();
    }

    public boolean joinable() {
        return joinable;
    }

    public boolean liveBiddingAllowed() {
        return liveBiddingAllowed;
    }

    public double currentPrice() {
        return currentPrice;
    }

    public boolean finished() {
        return "FINISHED".equalsIgnoreCase(rawStatus);
    }

    public boolean paid() {
        return "PAID".equalsIgnoreCase(rawStatus);
    }

    /**
     * Kiểm tra điều kiện hiển thị nút thanh toán ở client.
     *
     * <p>Đây chỉ là kiểm tra UI. Server vẫn là nơi xác thực winner và xử lý nghiệp vụ thanh toán.
     *
     * @param currentUserId id người dùng hiện tại
     * @return true nếu client có thể cho hiển thị nút thanh toán
     */
    public boolean canRequestPayment(String currentUserId) {
        return finished()
            && !paid()
            && currentUserId != null
            && !currentUserId.isBlank()
            && !currentLeaderId.isBlank()
            && currentLeaderId.equals(currentUserId);
    }
}