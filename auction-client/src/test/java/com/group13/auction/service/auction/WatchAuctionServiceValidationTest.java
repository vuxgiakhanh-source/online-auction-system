package com.group13.auction.service.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link WatchAuctionService}. */
class WatchAuctionServiceValidationTest {

  @Test
  void watchAuctionShouldFailWhenAuctionIdIsNull() {
    WatchAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.watchAuction(null),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void watchAuctionShouldFailWhenAuctionIdIsBlank() {
    WatchAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.watchAuction("   "),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void joinAuctionShouldFailWhenAuctionIdIsBlank() {
    WatchAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.joinAuction("   "),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void leaveAuctionShouldFailWhenAuctionIdIsBlank() {
    WatchAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.leaveAuction("   "),
        "Thiếu mã phiên đấu giá.");
  }

  private static WatchAuctionService createService() {
    return new WatchAuctionService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}