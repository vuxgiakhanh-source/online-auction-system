package com.group13.auction.service.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link WalletService}. */
class WalletServiceValidationTest {

  @Test
  void depositShouldFailWhenAmountIsZero() {
    WalletService service = createService();

    assertFutureFailsWithMessage(
        service.deposit(0L),
        "Số tiền nạp phải lớn hơn 0.");
  }

  @Test
  void depositShouldFailWhenAmountIsNegative() {
    WalletService service = createService();

    assertFutureFailsWithMessage(
        service.deposit(-1L),
        "Số tiền nạp phải lớn hơn 0.");
  }

  @Test
  void withdrawShouldFailWhenAmountIsZero() {
    WalletService service = createService();

    assertFutureFailsWithMessage(
        service.withdraw(0L),
        "Số tiền rút phải lớn hơn 0.");
  }

  @Test
  void withdrawShouldFailWhenAmountIsNegative() {
    WalletService service = createService();

    assertFutureFailsWithMessage(
        service.withdraw(-1L),
        "Số tiền rút phải lớn hơn 0.");
  }

  private static WalletService createService() {
    return new WalletService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}