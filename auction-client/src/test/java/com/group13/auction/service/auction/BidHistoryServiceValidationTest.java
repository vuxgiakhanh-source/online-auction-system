package com.group13.auction.service.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link BidHistoryService}. */
class BidHistoryServiceValidationTest {

  @Test
  void getBidHistoryShouldFailWhenAuctionIdIsNull() {
    BidHistoryService service = createService();

    assertFutureFailsWithMessage(service.getBidHistory(null), "Thiếu mã phiên đấu giá.");
  }

  @Test
  void getBidHistoryShouldFailWhenAuctionIdIsBlank() {
    BidHistoryService service = createService();

    assertFutureFailsWithMessage(service.getBidHistory("   "), "Thiếu mã phiên đấu giá.");
  }

  private static BidHistoryService createService() {
    return new BidHistoryService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
