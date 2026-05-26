package com.group13.auction.service.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link BidService}. */
class BidServiceValidationTest {

  @Test
  void placeBidShouldFailWhenAuctionIdIsBlank() {
    BidService service = createService();

    assertFutureFailsWithMessage(
        service.placeBid("   ", "2500000"),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void placeBidShouldFailWhenAmountIsBlank() {
    BidService service = createService();

    assertFutureFailsWithMessage(
        service.placeBid("A-1", "   "),
        "Giá đặt phải là số nguyên hợp lệ.");
  }

  @Test
  void placeBidShouldFailWhenAmountIsNotNumber() {
    BidService service = createService();

    assertFutureFailsWithMessage(
        service.placeBid("A-1", "abc"),
        "Giá đặt phải là số nguyên hợp lệ.");
  }

  @Test
  void placeBidShouldFailWhenAmountIsLowerThanMinimum() {
    BidService service = createService();

    assertFutureFailsWithMessage(
        service.placeBid("A-1", "999"),
        "Giá đặt tối thiểu là 1.000 ₫.");
  }

  @Test
  void placeBidShouldFailWhenAmountExceedsLimit() {
    BidService service = createService();

    assertFutureFailsWithMessage(
        service.placeBid("A-1", "100000000001"),
        "Giá đặt vượt quá giới hạn cho phép.");
  }

  private static BidService createService() {
    return new BidService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}