package com.group13.auction.core.state;

/** Lưu trạng thái màn hình hiện tại để các controller truyền dữ liệu ngắn hạn cho nhau. */
public final class ScreenStateStore {

    private static String selectedAuctionId;

    private ScreenStateStore() {}

    /**
     * Trả về giá trị selectedAuctionId dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị selectedAuctionId
     */
    public static String getSelectedAuctionId() {
        return selectedAuctionId;
    }

    /**
     * Cập nhật giá trị selectedAuctionId.
     */
    public static void setSelectedAuctionId(String selectedAuctionId) {
        ScreenStateStore.selectedAuctionId = selectedAuctionId;
    }
}
