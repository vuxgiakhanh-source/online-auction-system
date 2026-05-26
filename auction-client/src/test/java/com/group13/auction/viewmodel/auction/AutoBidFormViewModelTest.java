package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AutoBidFormViewModel}. */
class AutoBidFormViewModelTest {

  @Test
  void constructorShouldConvertNullTextToEmptyString() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel(null);

    assertEquals("", formState.maxBidText());
  }

  @Test
  void validateShouldReturnErrorWhenMaxBidTextIsBlank() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("   ");

    assertEquals(Optional.of("Bạn chưa nhập giá tối đa cho auto-bid."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenMaxBidTextIsNotNumber() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("abc");

    assertEquals(Optional.of("Giá tối đa phải là số nguyên hợp lệ."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenMaxBidIsLowerThanMinimum() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("999");

    assertEquals(Optional.of("Giá tối đa tối thiểu là 1.000 ₫."), formState.validate());
  }

  @Test
  void validateShouldReturnErrorWhenMaxBidExceedsLimit() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("100000000001");

    assertEquals(Optional.of("Giá tối đa vượt quá giới hạn cho phép."), formState.validate());
  }

  @Test
  void validateShouldReturnEmptyWhenMaxBidIsValid() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("1.000.000");

    assertFalse(formState.validate().isPresent());
  }

  @Test
  void maxBidAmountShouldParsePlainNumberText() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("2500000");

    assertEquals(2_500_000L, formState.maxBidAmount());
  }

  @Test
  void maxBidAmountShouldRemoveSpacesCommasAndDots() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("  2,500.000  ");

    assertEquals(2_500_000L, formState.maxBidAmount());
  }

  @Test
  void maxBidAmountShouldThrowWhenTextIsInvalid() {
    AutoBidFormViewModel formState = new AutoBidFormViewModel("invalid");

    assertThrows(NumberFormatException.class, formState::maxBidAmount);
  }
}