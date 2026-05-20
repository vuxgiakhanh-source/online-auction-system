package com.group13.auction.viewmodel.seller;

import java.time.LocalDateTime;
import java.util.List;

/** Dữ liệu đã format để hiển thị một phiên đấu giá trong màn quản lý của người bán. */
public final class SellerAuctionRowViewModel {

    private final String auctionId;
    private final String itemName;
    private final String categoryText;
    private final String statusText;
    private final String currentPriceText;
    private final String startingPriceText;
    private final String reservePriceText;
    private final String startTimeText;
    private final String endTimeText;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String viewerCountText;
    private final List<String> imageUrls;
    private final boolean editable;
    private final boolean cancelRequestAllowed;

    /**
     * Tạo row view model cho danh sách phiên của người bán.
     *
     * @param auctionId mã phiên đấu giá
     * @param itemName tên sản phẩm
     * @param categoryText tên loại sản phẩm đã format
     * @param statusText trạng thái đã format
     * @param currentPriceText giá hiện tại đã format
     * @param startingPriceText giá khởi điểm đã format
     * @param reservePriceText giá sàn đã format
     * @param startTimeText thời gian bắt đầu đã format
     * @param endTimeText thời gian kết thúc đã format
     * @param startTime thời gian bắt đầu dạng raw
     * @param endTime thời gian kết thúc dạng raw
     * @param viewerCountText số người xem đã format
     * @param imageUrls danh sách URL ảnh sản phẩm đã upload
     * @param editable true nếu phiên còn có thể sửa
     * @param cancelRequestAllowed true nếu phiên còn có thể gửi yêu cầu hủy
     */
    public SellerAuctionRowViewModel(
        String auctionId,
        String itemName,
        String categoryText,
        String statusText,
        String currentPriceText,
        String startingPriceText,
        String reservePriceText,
        String startTimeText,
        String endTimeText,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String viewerCountText,
        List<String> imageUrls,
        boolean editable,
        boolean cancelRequestAllowed) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.categoryText = categoryText;
        this.statusText = statusText;
        this.currentPriceText = currentPriceText;
        this.startingPriceText = startingPriceText;
        this.reservePriceText = reservePriceText;
        this.startTimeText = startTimeText;
        this.endTimeText = endTimeText;
        this.startTime = startTime;
        this.endTime = endTime;
        this.viewerCountText = viewerCountText;
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        this.editable = editable;
        this.cancelRequestAllowed = cancelRequestAllowed;
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

    public String reservePriceText() {
        return reservePriceText;
    }

    public String startTimeText() {
        return startTimeText;
    }

    public String endTimeText() {
        return endTimeText;
    }

    public LocalDateTime startTime() {
        return startTime;
    }

    public LocalDateTime endTime() {
        return endTime;
    }

    public String viewerCountText() {
        return viewerCountText;
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

    public boolean editable() {
        return editable;
    }

    public boolean cancelRequestAllowed() {
        return cancelRequestAllowed;
    }
}