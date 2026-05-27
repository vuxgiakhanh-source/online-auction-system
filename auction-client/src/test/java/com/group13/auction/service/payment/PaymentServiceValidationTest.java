package com.group13.auction.service.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link PaymentService}. */
class PaymentServiceValidationTest {

  @Test
  void requestPaymentShouldFailWhenAuctionIdIsNull() {
    PaymentService service = createService();

    assertFutureFailsWithMessage(
        service.requestPayment(null), "Thiếu mã phiên đấu giá cần thanh toán.");
  }

  @Test
  void requestPaymentShouldFailWhenAuctionIdIsBlank() {
    PaymentService service = createService();

    assertFutureFailsWithMessage(
        service.requestPayment("   "), "Thiếu mã phiên đấu giá cần thanh toán.");
  }

  @Test
  void confirmItemReceivedShouldFailWhenAuctionIdIsBlank() {
    PaymentService service = createService();

    assertFutureFailsWithMessage(
        service.confirmItemReceived("   "), "Thiếu mã phiên đấu giá cần xác nhận.");
  }

  @Test
  void acceptSecondChanceShouldFailWhenAuctionIdIsBlank() {
    PaymentService service = createService();

    assertFutureFailsWithMessage(
        service.acceptSecondChance("   "), "Thiếu mã phiên đấu giá của Second Chance Offer.");
  }

  @Test
  void declineSecondChanceShouldFailWhenAuctionIdIsBlank() {
    PaymentService service = createService();

    assertFutureFailsWithMessage(
        service.declineSecondChance("   "), "Thiếu mã phiên đấu giá của Second Chance Offer.");
  }

  private static PaymentService createService() {
    return new PaymentService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
