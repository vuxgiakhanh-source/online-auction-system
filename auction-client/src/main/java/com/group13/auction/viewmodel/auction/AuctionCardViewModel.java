package com.group13.auction.viewmodel.auction;

/** Dữ liệu đã format để hiển thị một phiên đấu giá trong danh sách. */
public final class AuctionCardViewModel {

    private final String auctionId;
    private final String itemName;
    private final String categoryText;
    private final String statusText;
    private final String currentPriceText;
    private final String startingPriceText;
    private final String remainingTimeText;
    private final String endTimeText;
    private final String sellerText;
    private final String viewerCountText;
    private final String primaryImageUrl;
    private final boolean joinable;

    /** Tạo view model cho card đấu giá. */
    public AuctionCardViewModel(
        String auctionId,
        String itemName,
        String categoryText,
        String statusText,
        String currentPriceText,
        String startingPriceText,
        String remainingTimeText,
        String endTimeText,
        String sellerText,
        String viewerCountText,
        String primaryImageUrl,
        boolean joinable) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.categoryText = categoryText;
        this.statusText = statusText;
        this.currentPriceText = currentPriceText;
        this.startingPriceText = startingPriceText;
        this.remainingTimeText = remainingTimeText;
        this.endTimeText = endTimeText;
        this.sellerText = sellerText;
        this.viewerCountText = viewerCountText;
        this.primaryImageUrl = primaryImageUrl == null ? "" : primaryImageUrl;
        this.joinable = joinable;
    }

    public String auctionId() {
        return auctionId;
    }

    public String itemName() {
        return itemName;
    }

    public String categoryText() {
        return categoryText;
    }

    public String statusText() {
        return statusText;
    }

    public String currentPriceText() {
        return currentPriceText;
    }

    public String startingPriceText() {
        return startingPriceText;
    }

    public String remainingTimeText() {
        return remainingTimeText;
    }

    public String endTimeText() {
        return endTimeText;
    }

    public String sellerText() {
        return sellerText;
    }

    public String viewerCountText() {
        return viewerCountText;
    }

    public String primaryImageUrl() {
        return primaryImageUrl;
    }

    public boolean hasImage() {
        return !primaryImageUrl.isBlank();
    }

    public boolean joinable() {
        return joinable;
    }
}