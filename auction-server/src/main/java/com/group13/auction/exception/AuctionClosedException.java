package com.group13.auction.exception;

import com.group13.auction.model.auction.Auction.AuctionStatus;

/** Ném khi cố đặt giá vào phiên không ở trạng thái RUNNING. */
public class AuctionClosedException extends RuntimeException {

  private final AuctionStatus currentStatus;

  public AuctionClosedException(AuctionStatus currentStatus) {
    super("Phiên đấu giá không thể nhận bid — trạng thái hiện tại: "
            + currentStatus);
    this.currentStatus = currentStatus;
  }

  public AuctionStatus getCurrentStatus() { return currentStatus; }
}