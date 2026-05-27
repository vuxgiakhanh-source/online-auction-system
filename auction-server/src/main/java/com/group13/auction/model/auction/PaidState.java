package com.group13.auction.model.auction;

/**
 * Trạng thái PAID: phiên đã thanh toán xong - trạng thái cuối cùng.
 *
 * <p>Không cho phép chuyển sang bất kỳ trạng thái nào khác.
 */
public class PaidState implements AuctionState {

  /** Instance dùng chung */
  public static final PaidState INSTANCE = new PaidState();

  private PaidState() {}

  @Override
  public AuctionState start() {
    throw new IllegalStateException("Phiên đã PAID - không thể thay đổi trạng thái.");
  }

  @Override
  public AuctionState close(boolean hasWinner) {
    throw new IllegalStateException("Phiên đã PAID - không thể thay đổi trạng thái.");
  }

  @Override
  public AuctionState cancel() {
    throw new IllegalStateException("Phiên đã PAID - không thể hủy.");
  }

  @Override
  public AuctionState markPaid() {
    throw new IllegalStateException("Phiên đã PAID - không thể đánh dấu lại.");
  }

  @Override
  public Auction.AuctionStatus getStatus() {
    return Auction.AuctionStatus.PAID;
  }
}
