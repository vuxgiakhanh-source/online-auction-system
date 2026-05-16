package com.group13.auction.core.workflow;

/**
 * Các giai đoạn vòng đời phiên đấu giá — khớp trạng thái server broadcast qua WebSocket.
 */
public enum AuctionLifecyclePhase {

    /** Phiên ở trạng thái OPEN — Seller có thể sửa, chưa bắt đầu đấu. */
    OPEN,

    /** Server broadcast {@code AUCTION_STARTED_UPDATE}. */
    RUNNING,

    /** Cảnh báo sắp kết thúc — {@code AUCTION_UPCOMING_END_NOTIFY}. */
    UPCOMING_END,

    /** Gia hạn anti-sniping — {@code AUCTION_EXTENDED_NOTIFY}. */
    EXTENDED,

    /** Kết thúc có người thắng — {@code AUCTION_ENDED_UPDATE}. */
    FINISHED_WITH_WINNER,

    /** Kết thúc không đạt reserve — {@code AUCTION_RESERVE_NOT_MET_UPDATE}. */
    RESERVE_NOT_MET,

    /** Kết thúc không có bid — {@code AUCTION_NO_WINNER_UPDATE}. */
    NO_WINNER,

    /** Bị hủy — {@code AUCTION_CANCELED_UPDATE}. */
    CANCELED
}
