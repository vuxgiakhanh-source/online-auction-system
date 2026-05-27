package com.group13.auction.model.auction;

/**
 * Trạng thái OPEN: phiên đã được tạo, chờ bắt đầu.
 *
 * <p>Các chuyển trạng thái hợp lệ:
 *
 * <ul>
 *   <li>OPEN -> RUNNING (khi {@link #start()} được gọi)
 *   <li>OPEN -> CANCELED (khi {@link #cancel()} được gọi)
 * </ul>
 */
public class OpenState implements AuctionState {

  /** Instance dùng chung */
  public static final OpenState INSTANCE = new OpenState();

  private OpenState() {}

  @Override
  public AuctionState start() {
    return RunningState.INSTANCE;
  }

  @Override
  public AuctionState close(boolean hasWinner) {
    throw new IllegalStateException("Phiên không ở trạng thái RUNNING - không thể đóng: OPEN");
  }

  @Override
  public AuctionState cancel() {
    return CanceledState.INSTANCE;
  }

  @Override
  public AuctionState markPaid() {
    throw new IllegalStateException(
        "Phiên không ở trạng thái FINISHED - không thể đánh dấu PAID: OPEN");
  }

  @Override
  public Auction.AuctionStatus getStatus() {
    return Auction.AuctionStatus.OPEN;
  }
}
