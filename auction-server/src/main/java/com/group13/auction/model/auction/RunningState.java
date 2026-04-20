package com.group13.auction.model.auction;

/**
 * Trạng thái RUNNING: phiên đang diễn ra, chấp nhận bid.
 *
 * <p>Các chuyển trạng thái hợp lệ:
 * <ul>
 *   <li>RUNNING -> FINISHED (khi {@link #close(boolean)} với hasWinner = true)</li>
 *   <li>RUNNING -> CANCELED (khi {@link #close(boolean)} với hasWinner = false hoặc {@link #cancel()})</li>
 * </ul>
 */
public class RunningState implements AuctionState {

    /** Instance dùng chung */
    public static final RunningState INSTANCE = new RunningState();

    private RunningState() {}

    @Override
    public AuctionState start() {
        throw new IllegalStateException(
                "Phiên đã ở trạng thái RUNNING - không thể start lại.");
    }

    @Override
    public AuctionState close(boolean hasWinner) {
        return hasWinner ? FinishedState.INSTANCE : CanceledState.INSTANCE;
    }

    @Override
    public AuctionState cancel() {
        return CanceledState.INSTANCE;
    }

    @Override
    public AuctionState markPaid() {
        throw new IllegalStateException(
                "Phiên không ở trạng thái FINISHED - không thể đánh dấu PAID: RUNNING");
    }

    @Override
    public Auction.AuctionStatus getStatus() {
        return Auction.AuctionStatus.RUNNING;
    }
}