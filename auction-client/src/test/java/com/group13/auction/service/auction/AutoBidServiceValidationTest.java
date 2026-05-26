package com.group13.auction.service.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link AutoBidService}. */
class AutoBidServiceValidationTest {

  @Test
  void getAutoBidStatusShouldFailWhenAuctionIdIsBlank() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.getAutoBidStatus("   "),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void registerAutoBidShouldFailWhenAuctionIdIsBlank() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.registerAutoBid("   ", "2500000"),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void registerAutoBidShouldFailWhenMaxBidIsBlank() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.registerAutoBid("A-1", "   "),
        "Bạn chưa nhập giá tối đa cho auto-bid.");
  }

  @Test
  void registerAutoBidShouldFailWhenMaxBidIsNotNumber() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.registerAutoBid("A-1", "abc"),
        "Giá tối đa phải là số nguyên hợp lệ.");
  }

  @Test
  void registerAutoBidShouldFailWhenMaxBidIsLowerThanMinimum() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.registerAutoBid("A-1", "999"),
        "Giá tối đa tối thiểu là 1.000 ₫.");
  }

  @Test
  void registerAutoBidShouldFailWhenMaxBidExceedsLimit() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.registerAutoBid("A-1", "100000000001"),
        "Giá tối đa vượt quá giới hạn cho phép.");
  }

  @Test
  void updateAutoBidShouldFailWhenAuctionIdIsBlank() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.updateAutoBid("   ", "2500000"),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void updateAutoBidShouldFailWhenMaxBidIsInvalid() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.updateAutoBid("A-1", "abc"),
        "Giá tối đa phải là số nguyên hợp lệ.");
  }

  @Test
  void cancelAutoBidShouldFailWhenAuctionIdIsBlank() {
    AutoBidService service = createService();

    assertFutureFailsWithMessage(
        service.cancelAutoBid("   "),
        "Thiếu mã phiên đấu giá.");
  }

  private static AutoBidService createService() {
    return new AutoBidService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}