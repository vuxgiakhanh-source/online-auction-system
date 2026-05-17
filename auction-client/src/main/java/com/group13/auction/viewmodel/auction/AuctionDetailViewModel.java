package com.group13.auction.viewmodel.auction;

/** Dữ liệu chi tiết phiên đấu giá đã format cho màn auction detail. */
public final class AuctionDetailViewModel {

    private final String auctionId;
    private final String itemName;
    private final String description;
    private final String categoryText;
    private final String sellerText;
    private final String statusText;
    private final String currentPriceText;
    private final String startingPriceText;
    private final String reservePriceText;
    private final String leaderText;
    private final String viewerCountText;
    private final String startTimeText;
    private final String endTimeText;
    private final String remainingTimeText;
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
            String statusText,
            String currentPriceText,
            String startingPriceText,
            String reservePriceText,
            String leaderText,
            String viewerCountText,
            String startTimeText,
            String endTimeText,
            String remainingTimeText,
            boolean joinable,
            boolean liveBiddingAllowed,
            double currentPrice) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.description = description;
        this.categoryText = categoryText;
        this.sellerText = sellerText;
        this.statusText = statusText;
        this.currentPriceText = currentPriceText;
        this.startingPriceText = startingPriceText;
        this.reservePriceText = reservePriceText;
        this.leaderText = leaderText;
        this.viewerCountText = viewerCountText;
        this.startTimeText = startTimeText;
        this.endTimeText = endTimeText;
        this.remainingTimeText = remainingTimeText;
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

    public String statusText() {
        return statusText;
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

    public boolean joinable() {
        return joinable;
    }

    public boolean liveBiddingAllowed() {
        return liveBiddingAllowed;
    }

    public double currentPrice() {
        return currentPrice;
    }
}