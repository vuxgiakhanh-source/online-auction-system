package com.group13.auction.viewmodel.auction;

/** Dữ liệu đếm ngược phiên đấu giá để controller bind vào UI. */
public final class AuctionTimerViewModel {

    private final String remainingTimeText;
    private final String endTimeText;
    private final boolean ended;

    /** Tạo timer view model. */
    public AuctionTimerViewModel(String remainingTimeText, String endTimeText, boolean ended) {
        this.remainingTimeText = remainingTimeText;
        this.endTimeText = endTimeText;
        this.ended = ended;
    }

    public String remainingTimeText() {
        return remainingTimeText;
    }

    public String endTimeText() {
        return endTimeText;
    }

    public boolean ended() {
        return ended;
    }
}