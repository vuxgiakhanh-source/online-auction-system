package com.group13.auction.model.auction;

/**
 * Trạng thái FINISHED: phiên kết thúc có winner, chờ thanh toán.
 *
 * <p>Các chuyển trạng thái hợp lệ:
 *
 * <ul>
 *   <li>FINISHED -> PAID (khi {@link #markPaid()} được gọi)
 * </ul>
 */
public class FinishedState implements AuctionState {

  /** Instance dùng chung vì FinishedState không có state riêng. */
  public static final FinishedState INSTANCE = new FinishedState();

  private FinishedState() {}

  @Override
  public AuctionState start() {
    throw new IllegalStateException("Phiên đã FINISHED - không thể start lại.");
  }

  @Override
  public AuctionState close(boolean hasWinner) {
    throw new IllegalStateException("Phiên đã FINISHED - không thể đóng lại.");
  }

  @Override
  public AuctionState cancel() {
    throw new IllegalStateException("Phiên đã FINISHED - không thể hủy.");
  }

  @Override
  public AuctionState markPaid() {
    return PaidState.INSTANCE;
  }

  @Override
  public Auction.AuctionStatus getStatus() {
    return Auction.AuctionStatus.FINISHED;
  }
}
