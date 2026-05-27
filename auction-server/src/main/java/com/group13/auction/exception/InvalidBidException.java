package com.group13.auction.exception;

/** Ném khi bid không hợp lệ theo nghiệp vụ. */
public class InvalidBidException extends RuntimeException {

  private final long attemptedAmount;
  private final long currentPrice;

  public InvalidBidException(String message, long attemptedAmount, long currentPrice) {
    super(message);
    this.attemptedAmount = attemptedAmount;
    this.currentPrice = currentPrice;
  }

  public InvalidBidException(String message) {
    super(message);
    this.attemptedAmount = 0;
    this.currentPrice = 0;
  }

  public long getAttemptedAmount() {
    return attemptedAmount;
  }

  public long getCurrentPrice() {
    return currentPrice;
  }
}
