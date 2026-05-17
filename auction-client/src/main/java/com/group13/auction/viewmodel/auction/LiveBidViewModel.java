package com.group13.auction.viewmodel.auction;

/** Dữ liệu trạng thái hiện tại của màn đấu giá trực tiếp. */
public final class LiveBidViewModel {

    private final String auctionId;
    private final String currentPriceText;
    private final String leaderText;
    private final String reserveText;
    private final String timestampText;
    private final String endTimeText;
    private final long currentPrice;
    private final boolean reserveMet;

    /** Tạo view model cho realtime bid state. */
    public LiveBidViewModel(
            String auctionId,
            String currentPriceText,
            String leaderText,
            String reserveText,
            String timestampText,
            String endTimeText,
            long currentPrice,
            boolean reserveMet) {
        this.auctionId = auctionId;
        this.currentPriceText = currentPriceText;
        this.leaderText = leaderText;
        this.reserveText = reserveText;
        this.timestampText = timestampText;
        this.endTimeText = endTimeText;
        this.currentPrice = currentPrice;
        this.reserveMet = reserveMet;
    }

    public String auctionId() {
        return auctionId;
    }

    public String currentPriceText() {
        return currentPriceText;
    }

    public String leaderText() {
        return leaderText;
    }

    public String reserveText() {
        return reserveText;
    }

    public String timestampText() {
        return timestampText;
    }

    public String endTimeText() {
        return endTimeText;
    }

    public long currentPrice() {
        return currentPrice;
    }

    public boolean reserveMet() {
        return reserveMet;
    }
}