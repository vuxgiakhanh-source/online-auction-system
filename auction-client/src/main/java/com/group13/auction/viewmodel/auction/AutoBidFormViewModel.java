package com.group13.auction.viewmodel.auction;

import com.group13.auction.util.CurrencyUtil;
import java.util.Optional;

/** Dữ liệu form đăng ký Auto-Bid ở phía client. */
public final class AutoBidFormViewModel {

  private static final long MIN_MAX_BID_AMOUNT = 1_000L;
  private static final long MAX_MAX_BID_AMOUNT = 100_000_000_000L;

  private final String maxBidText;

  public AutoBidFormViewModel(String maxBidText) {
    this.maxBidText = maxBidText == null ? "" : maxBidText;
  }

  public String maxBidText() {
    return maxBidText;
  }

  /** Validate giá tối đa trước khi gửi request Auto-Bid. */
  public Optional<String> validate() {
    if (maxBidText.trim().isEmpty()) {
      return Optional.of("Bạn chưa nhập giá tối đa cho auto-bid.");
    }

    try {
      long maxBid = maxBidAmount();
      if (maxBid < MIN_MAX_BID_AMOUNT) {
        return Optional.of("Giá tối đa tối thiểu là 1.000 ₫.");
      }
      if (maxBid > MAX_MAX_BID_AMOUNT) {
        return Optional.of("Giá tối đa vượt quá giới hạn cho phép.");
      }
    } catch (NumberFormatException exception) {
      return Optional.of("Giá tối đa phải là số nguyên hợp lệ.");
    }

    return Optional.empty();
  }

  /**
   * Validate maxBid theo giá hiện tại + bước giá, và (khi cập nhật) phải lớn hơn maxBid trước đó.
   *
   * @param minimumMaxBid giá tối đa tối thiểu = giá hiện tại + bước giá
   * @param previousMaxBid maxBid auto-bid hiện tại; {@code null} khi đăng ký mới
   */
  public Optional<String> validateAgainstMinimum(long minimumMaxBid, Long previousMaxBid) {
    Optional<String> basicValidation = validate();
    if (basicValidation.isPresent()) {
      return basicValidation;
    }

    long maxBid = maxBidAmount();
    if (maxBid < minimumMaxBid) {
      return Optional.of(
          "Giá tối đa phải >= "
              + CurrencyUtil.formatVnd(minimumMaxBid)
              + " (giá hiện tại + bước giá).");
    }

    if (previousMaxBid != null && previousMaxBid > 0 && maxBid <= previousMaxBid) {
      return Optional.of(
          "Giá tối đa mới phải lớn hơn maxBid hiện tại ("
              + CurrencyUtil.formatVnd(previousMaxBid)
              + ").");
    }

    return Optional.empty();
  }

  public long maxBidAmount() {
    return Long.parseLong(normalizedAmountText());
  }

  private String normalizedAmountText() {
    return maxBidText.trim().replaceAll("[\\s,.]", "");
  }
}
