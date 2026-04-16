package com.group13.auction.model.auction;

/**
 * Trạng thái CANCELED: phiên đã bị hủy - trạng thái cuối cùng.
 *
 * <p>Không cho phép chuyển sang bất kỳ trạng thái nào khác.
 */
public class CanceledState implements AuctionState {

    /** Instance dùng chung */
    public static final CanceledState INSTANCE = new CanceledState();

    private CanceledState() {}

    @Override
    public AuctionState start() {
        throw new IllegalStateException("Phiên đã CANCELED - không thể thay đổi trạng thái.");
    }

    @Override
    public AuctionState close(boolean hasWinner) {
        throw new IllegalStateException("Phiên đã CANCELED - không thể đóng.");
    }

    @Override
    public AuctionState cancel() {
        // không làm gì
        return this;
    }

    @Override
    public AuctionState markPaid() {
        throw new IllegalStateException("Phiên đã CANCELED - không thể đánh dấu PAID.");
    }

    @Override
    public Auction.AuctionStatus getStatus() {
        return Auction.AuctionStatus.CANCELED;
    }
}