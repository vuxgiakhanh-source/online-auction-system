package com.group13.auction.viewmodel.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RegisterFormState}. */
class RegisterFormStateTest {

  @Test
  void constructorShouldConvertNullValuesToEmptyStrings() {
    RegisterFormState formState = new RegisterFormState(null, null, null, null);

    assertEquals("", formState.email());
    assertEquals("", formState.username());
    assertEquals("", formState.password());
    assertEquals("", formState.confirmPassword());
  }

  @Test
  void threeArgumentConstructorShouldUsePasswordAsConfirmPassword() {
    RegisterFormState formState =
        new RegisterFormState("beo@example.com", "bidder01", "secret1");

    assertEquals("secret1", formState.confirmPassword());
  }

  @Test
  void normalizedEmailAndUsernameShouldTrimSpaces() {
    RegisterFormState formState =
        new RegisterFormState("  beo@example.com  ", "  bidder01  ", "secret1", "secret1");

    assertEquals("beo@example.com", formState.normalizedEmail());
    assertEquals("bidder01", formState.normalizedUsername());
  }

  @Test
  void validateShouldReturnErrorWhenEmailIsBlank() {
    RegisterFormState formState = new RegisterFormState("   ", "bidder01", "secret1", "secret1");

    assertEquals(Optional.of("Bạn chưa nhập email."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenEmailFormatIsInvalid() {
    RegisterFormState formState =
        new RegisterFormState("beo.example.com", "bidder01", "secret1", "secret1");

    assertEquals(Optional.of("Email chưa đúng định dạng."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenUsernameIsTooShort() {
    RegisterFormState formState = new RegisterFormState("beo@example.com", "bo", "secret1", "secret1");

    assertEquals(Optional.of("Tên đăng nhập cần có ít nhất 3 ký tự."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenPasswordIsTooShort() {
    RegisterFormState formState =
        new RegisterFormState("beo@example.com", "bidder01", "12345", "12345");

    assertEquals(Optional.of("Mật khẩu cần có ít nhất 6 ký tự."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenConfirmPasswordIsBlank() {
    RegisterFormState formState =
        new RegisterFormState("beo@example.com", "bidder01", "secret1", "   ");

    assertEquals(Optional.of("Bạn chưa nhập lại mật khẩu."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenConfirmPasswordDoesNotMatch() {
    RegisterFormState formState =
        new RegisterFormState("beo@example.com", "bidder01", "secret1", "secret2");

    assertEquals(Optional.of("Mật khẩu nhập lại chưa khớp."), formState.validate());
  }

  @Test
  void validateShouldReturnEmptyWhenFormIsValid() {
    RegisterFormState formState =
        new RegisterFormState(" beo@example.com ", " bidder01 ", "secret1", "secret1");

    assertFalse(formState.validate().isPresent());
  }
}