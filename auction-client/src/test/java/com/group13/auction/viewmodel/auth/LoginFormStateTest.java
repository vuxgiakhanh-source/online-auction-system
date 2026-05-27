package com.group13.auction.viewmodel.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link LoginFormState}. */
class LoginFormStateTest {

  @Test
  void constructorShouldConvertNullValuesToEmptyStrings() {
    LoginFormState formState = new LoginFormState(null, null);

    assertEquals("", formState.username());
    assertEquals("", formState.password());
  }

  @Test
  void normalizedUsernameShouldTrimSpaces() {
    LoginFormState formState = new LoginFormState("  bidder01  ", "secret");

    assertEquals("bidder01", formState.normalizedUsername());
  }

  @Test
  void validateShouldReturnErrorWhenUsernameIsBlank() {
    LoginFormState formState = new LoginFormState("   ", "secret");

    assertEquals(Optional.of("Bạn chưa nhập tên đăng nhập."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenPasswordIsBlank() {
    LoginFormState formState = new LoginFormState("bidder01", "   ");

    assertEquals(Optional.of("Bạn chưa nhập mật khẩu."), formState.validate());
  }

  @Test
  void validateShouldReturnEmptyWhenFormIsValid() {
    LoginFormState formState = new LoginFormState(" bidder01 ", "secret");

    assertFalse(formState.validate().isPresent());
  }
}
