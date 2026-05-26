package com.group13.auction.network.server.util;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.Auction.AuctionStatus;

/**
 * Tra cứu phiên đấu giá (memory + DB) và phân biệt chế độ tham gia live vs chỉ xem lịch sử.
 */
public final class AuctionLookup {

    private AuctionLookup() {}

    /**
     * Tìm phiên để đọc dữ liệu (chi tiết, lịch sử bid). Nếu có trong DB nhưng chưa có trong RAM thì nạp vào
     * {@link AuctionManager}.
     */
    public static Auction resolveForRead(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return null;
        }
        AuctionManager manager = AuctionManager.getInstance();
        Auction cached = manager.findAuctionById(auctionId);
        if (cached != null) {
            return cached;
        }
        Auction fromDb = new AuctionDAO().findAuctionById(auctionId);
        if (fromDb != null) {
            manager.registerAuction(fromDb);
        }
        return fromDb;
    }

    /** Cho phép join / watch / đặt giá — chỉ khi phiên còn {@code OPEN} hoặc {@code RUNNING}. */
    public static boolean allowsLiveParticipation(Auction auction) {
        if (auction == null) {
            return false;
        }
        AuctionStatus status = auction.getStatus();
        return status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING;
    }

    /** Thông báo tiếng Việt khi client gọi thao tác live trên phiên đã đóng. */
    public static String liveParticipationBlockedMessage(Auction auction) {
        if (auction == null) {
            return "Không tìm thấy phiên đấu giá.";
        }
        return switch (auction.getStatus()) {
            case CANCELED -> "Phiên đã bị hủy. Bạn chỉ có thể xem lịch sử đặt giá.";
            case FINISHED, PAID -> "Phiên đã kết thúc. Bạn chỉ có thể xem lịch sử đặt giá.";
            case OPEN -> "Phiên chưa mở đặt giá trực tiếp. Vui lòng quay lại sau.";
            case RUNNING -> "Phiên không còn nhận thao tác này.";
        };
    }
}
