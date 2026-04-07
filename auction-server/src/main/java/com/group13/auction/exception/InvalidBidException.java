package com.group13.auction.exception;

/** Ném khi bid không hợp lệ theo nghiệp vụ. */
public class InvalidBidException extends RuntimeException {

  private final double attemptedAmount;
  private final double currentPrice;

  public InvalidBidException(String message,
                             double attemptedAmount, double currentPrice) {
    super(message);
    this.attemptedAmount = attemptedAmount;
    this.currentPrice    = currentPrice;
  }

  public InvalidBidException(String message) {
    super(message);
    this.attemptedAmount = 0;
    this.currentPrice    = 0;
  }

  public double getAttemptedAmount() { return attemptedAmount; }
  public double getCurrentPrice()    { return currentPrice; }
}
