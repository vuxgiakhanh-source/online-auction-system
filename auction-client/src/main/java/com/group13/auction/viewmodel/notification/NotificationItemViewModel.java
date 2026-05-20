package com.group13.auction.viewmodel.notification;

/** Dữ liệu thông báo đã được format để hiển thị trên giao diện JavaFX. */
public final class NotificationItemViewModel {

    private final String id;
    private final String type;
    private final String title;
    private final String body;
    private final String createdAtText;
    private final String relatedAuctionId;
    private final boolean read;

    /**
     * Tạo view model cho một thông báo.
     *
     * @param id mã thông báo
     * @param type loại thông báo
     * @param title tiêu đề
     * @param body nội dung
     * @param createdAtText thời điểm tạo đã format
     * @param relatedAuctionId mã phiên đấu giá liên quan, nếu có
     * @param read thông báo đã đọc hay chưa
     */
    public NotificationItemViewModel(
            String id,
            String type,
            String title,
            String body,
            String createdAtText,
            String relatedAuctionId,
            boolean read) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.body = body;
        this.createdAtText = createdAtText;
        this.relatedAuctionId = relatedAuctionId;
        this.read = read;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public String createdAtText() {
        return createdAtText;
    }

    public String relatedAuctionId() {
        return relatedAuctionId;
    }

    public boolean read() {
        return read;
    }

    public boolean hasRelatedAuction() {
        return relatedAuctionId != null && !relatedAuctionId.isBlank();
    }

    public String readStateText() {
        return read ? "Đã đọc" : "Chưa đọc";
    }

    public NotificationItemViewModel markRead() {
        return new NotificationItemViewModel(
                id, type, title, body, createdAtText, relatedAuctionId, true);
    }
}