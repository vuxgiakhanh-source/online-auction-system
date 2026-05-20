package com.group13.auction.viewmodel.payment;

/** Dữ liệu Second Chance Offer đã format để hiển thị trên giao diện client. */
public final class SecondChanceOfferViewModel {

    private final String offerId;
    private final String auctionId;
    private final String auctionItemName;
    private final String offerPriceText;
    private final String depositRequiredText;
    private final String deadlineText;
    private final boolean expired;

    /**
     * Tạo view model cho Second Chance Offer.
     *
     * @param offerId mã lời mời
     * @param auctionId mã phiên đấu giá
     * @param auctionItemName tên sản phẩm
     * @param offerPriceText giá đề nghị đã format
     * @param depositRequiredText tiền cọc yêu cầu đã format
     * @param deadlineText hạn phản hồi đã format
     * @param expired true nếu lời mời đã hết hạn theo thời gian client quan sát được
     */
    public SecondChanceOfferViewModel(
            String offerId,
            String auctionId,
            String auctionItemName,
            String offerPriceText,
            String depositRequiredText,
            String deadlineText,
            boolean expired) {
        this.offerId = offerId;
        this.auctionId = auctionId;
        this.auctionItemName = auctionItemName;
        this.offerPriceText = offerPriceText;
        this.depositRequiredText = depositRequiredText;
        this.deadlineText = deadlineText;
        this.expired = expired;
    }

    public String offerId() {
        return offerId;
    }

    public String auctionId() {
        return auctionId;
    }

    public String auctionItemName() {
        return auctionItemName;
    }

    public String offerPriceText() {
        return offerPriceText;
    }

    public String depositRequiredText() {
        return depositRequiredText;
    }

    public String deadlineText() {
        return deadlineText;
    }

    public boolean expired() {
        return expired;
    }
}