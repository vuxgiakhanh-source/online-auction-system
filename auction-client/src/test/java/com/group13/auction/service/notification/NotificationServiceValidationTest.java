package com.group13.auction.service.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link NotificationService}. */
class NotificationServiceValidationTest {

  @Test
  void markNotificationReadShouldFailWhenNotificationIdIsNull() {
    NotificationService service = createService();

    assertFutureFailsWithMessage(
        service.markNotificationRead(null),
        "Thiếu mã thông báo.");
  }

  @Test
  void markNotificationReadShouldFailWhenNotificationIdIsBlank() {
    NotificationService service = createService();

    assertFutureFailsWithMessage(
        service.markNotificationRead("   "),
        "Thiếu mã thông báo.");
  }

  @Test
  void markNotificationsReadShouldCompleteImmediatelyWhenIdsAreNull() {
    NotificationService service = createService();

    assertDoesNotThrow(() -> service.markNotificationsRead(null).join());
  }

  @Test
  void markNotificationsReadShouldCompleteImmediatelyWhenIdsAreEmpty() {
    NotificationService service = createService();

    assertDoesNotThrow(() -> service.markNotificationsRead(List.of()).join());
  }

  @Test
  void markNotificationsReadShouldCompleteImmediatelyWhenIdsAreOnlyBlank() {
    NotificationService service = createService();

    assertDoesNotThrow(() -> service.markNotificationsRead(List.of("   ", "\t")).join());
  }

  private static NotificationService createService() {
    return new NotificationService(ClientNetworkFacade.getDefault());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}