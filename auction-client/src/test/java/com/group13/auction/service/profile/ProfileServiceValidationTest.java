package com.group13.auction.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.core.session.SessionManager;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link ProfileService}. */
class ProfileServiceValidationTest {

  @Test
  void getUserProfileShouldFailWhenUserIdIsNull() {
    ProfileService service = createService();

    assertFutureFailsWithMessage(service.getUserProfile(null), "Thiếu mã người dùng.");
  }

  @Test
  void getUserProfileShouldFailWhenUserIdIsBlank() {
    ProfileService service = createService();

    assertFutureFailsWithMessage(service.getUserProfile("   "), "Thiếu mã người dùng.");
  }

  private static ProfileService createService() {
    return new ProfileService(ClientNetworkFacade.getDefault(), new SessionManager());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
