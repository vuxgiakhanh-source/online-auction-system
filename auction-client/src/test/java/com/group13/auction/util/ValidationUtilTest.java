package com.group13.auction.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ValidationUtil}. */
class ValidationUtilTest {

  @Test
  void hasTextShouldReturnFalseWhenValueIsNull() {
    assertFalse(ValidationUtil.hasText(null));
  }

  @Test
  void hasTextShouldReturnFalseWhenValueIsBlank() {
    assertFalse(ValidationUtil.hasText(""));
    assertFalse(ValidationUtil.hasText("   "));
  }

  @Test
  void hasTextShouldReturnTrueWhenValueContainsVisibleText() {
    assertTrue(ValidationUtil.hasText("omni"));
    assertTrue(ValidationUtil.hasText("  omni  "));
  }

  @Test
  void isBasicEmailShouldReturnTrueForBasicValidEmail() {
    assertTrue(ValidationUtil.isBasicEmail("beo@example.com"));
  }

  @Test
  void isBasicEmailShouldReturnFalseForInvalidEmail() {
    assertFalse(ValidationUtil.isBasicEmail(null));
    assertFalse(ValidationUtil.isBasicEmail(""));
    assertFalse(ValidationUtil.isBasicEmail("beo.example.com"));
    assertFalse(ValidationUtil.isBasicEmail("beo@example"));
  }

  @Test
  void isPositiveNumberShouldReturnTrueForPositiveIntegerText() {
    assertTrue(ValidationUtil.isPositiveNumber("1000"));
  }

  @Test
  void isPositiveNumberShouldReturnTrueForPositiveDecimalTextWithSpaces() {
    assertTrue(ValidationUtil.isPositiveNumber("  12.5  "));
  }

  @Test
  void isPositiveNumberShouldReturnFalseForNullBlankZeroNegativeOrInvalidText() {
    assertFalse(ValidationUtil.isPositiveNumber(null));
    assertFalse(ValidationUtil.isPositiveNumber(""));
    assertFalse(ValidationUtil.isPositiveNumber("   "));
    assertFalse(ValidationUtil.isPositiveNumber("0"));
    assertFalse(ValidationUtil.isPositiveNumber("-1"));
    assertFalse(ValidationUtil.isPositiveNumber("abc"));
  }
}
