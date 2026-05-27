package com.group13.auction.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.core.session.SessionManager;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link AuthService}. */
class AuthServiceValidationTest {

  @Test
  void loginShouldFailWhenUsernameIsBlank() {
    AuthService service = createService();

    assertFutureFailsWithMessage(service.login("   ", "secret"), "Bạn chưa nhập tên đăng nhập.");
  }

  @Test
  void loginShouldFailWhenPasswordIsBlank() {
    AuthService service = createService();

    assertFutureFailsWithMessage(service.login("bidder01", "   "), "Bạn chưa nhập mật khẩu.");
  }

  @Test
  void registerShouldFailWhenEmailIsBlank() {
    AuthService service = createService();

    assertFutureFailsWithMessage(
        service.register("bidder01", "secret1", "   "), "Bạn chưa nhập email.");
  }

  @Test
  void registerShouldFailWhenEmailFormatIsInvalid() {
    AuthService service = createService();

    assertFutureFailsWithMessage(
        service.register("bidder01", "secret1", "beo.example.com"), "Email chưa đúng định dạng.");
  }

  @Test
  void registerShouldFailWhenUsernameIsTooShort() {
    AuthService service = createService();

    assertFutureFailsWithMessage(
        service.register("bo", "secret1", "beo@example.com"),
        "Tên đăng nhập cần có ít nhất 3 ký tự.");
  }

  @Test
  void registerShouldFailWhenPasswordIsTooShort() {
    AuthService service = createService();

    assertFutureFailsWithMessage(
        service.register("bidder01", "12345", "beo@example.com"),
        "Mật khẩu cần có ít nhất 6 ký tự.");
  }

  private static AuthService createService() {
    return new AuthService(ClientNetworkFacade.getDefault(), new SessionManager());
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
