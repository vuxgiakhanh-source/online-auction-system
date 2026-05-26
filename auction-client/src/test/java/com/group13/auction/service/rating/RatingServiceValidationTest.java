package com.group13.auction.service.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link RatingService}. */
class RatingServiceValidationTest {

  @Test
  void rateSellerShouldFailWhenSellerIdIsBlank() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateSeller("   ", "A-1", 5.0, "Good seller."),
        "Thiếu mã người được đánh giá.");
  }

  @Test
  void rateSellerShouldFailWhenAuctionIdIsBlank() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateSeller("SELLER-1", "   ", 5.0, "Good seller."),
        "Thiếu mã phiên đấu giá liên quan.");
  }

  @Test
  void rateSellerShouldFailWhenRatingIsLowerThanMinimum() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateSeller("SELLER-1", "A-1", 0.5, "Good seller."),
        "Điểm đánh giá phải nằm trong khoảng 1 đến 5.");
  }

  @Test
  void rateSellerShouldFailWhenRatingIsGreaterThanMaximum() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateSeller("SELLER-1", "A-1", 5.5, "Good seller."),
        "Điểm đánh giá phải nằm trong khoảng 1 đến 5.");
  }

  @Test
  void rateSellerShouldFailWhenCommentIsTooLong() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateSeller("SELLER-1", "A-1", 5.0, "x".repeat(501)),
        "Nội dung đánh giá không được vượt quá 500 ký tự.");
  }

  @Test
  void rateBidderShouldFailWhenBidderIdIsBlank() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateBidder("   ", "A-1", 5.0, "Fast payment."),
        "Thiếu mã người được đánh giá.");
  }

  @Test
  void rateBidderShouldFailWhenAuctionIdIsBlank() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateBidder("BIDDER-1", "   ", 5.0, "Fast payment."),
        "Thiếu mã phiên đấu giá liên quan.");
  }

  @Test
  void rateBidderShouldFailWhenRatingIsOutOfRange() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.rateBidder("BIDDER-1", "A-1", 0.0, "Fast payment."),
        "Điểm đánh giá phải nằm trong khoảng 1 đến 5.");
  }

  @Test
  void getRatingsShouldFailWhenUserIdIsBlank() {
    RatingService service = createService();

    assertFutureFailsWithMessage(
        service.getRatings("   "),
        "Thiếu mã người dùng cần xem rating.");
  }

  private static RatingService createService() {
    return new RatingService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}