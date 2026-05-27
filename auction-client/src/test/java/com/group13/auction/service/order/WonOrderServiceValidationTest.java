package com.group13.auction.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link WonOrderService}. */
class WonOrderServiceValidationTest {

  @BeforeEach
  void clearSession() {
    AppContext.getInstance().getSessionManager().clearSession();
  }

  @Test
  void getMyWonOrdersShouldFailWhenUserIsNotLoggedIn() {
    WonOrderService service = createService();

    assertFutureFailsWithMessage(service.getMyWonOrders(), "Vui lòng đăng nhập để xem đơn hàng.");
  }

  @Test
  void payForOrderShouldFailWhenAuctionIdIsNull() {
    WonOrderService service = createService();

    assertFutureFailsWithMessage(
        service.payForOrder(null), "Thiếu mã phiên đấu giá cần thanh toán.");
  }

  @Test
  void payForOrderShouldFailWhenAuctionIdIsBlank() {
    WonOrderService service = createService();

    assertFutureFailsWithMessage(
        service.payForOrder("   "), "Thiếu mã phiên đấu giá cần thanh toán.");
  }

  @Test
  void confirmItemReceivedShouldFailWhenAuctionIdIsNull() {
    WonOrderService service = createService();

    assertFutureFailsWithMessage(
        service.confirmItemReceived(null), "Thiếu mã phiên đấu giá cần xác nhận.");
  }

  @Test
  void confirmItemReceivedShouldFailWhenAuctionIdIsBlank() {
    WonOrderService service = createService();

    assertFutureFailsWithMessage(
        service.confirmItemReceived("   "), "Thiếu mã phiên đấu giá cần xác nhận.");
  }

  private static WonOrderService createService() {
    return new WonOrderService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
