package com.group13.auction.viewmodel.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProductSpecificationViewModel}. */
class ProductSpecificationViewModelTest {

  @Test
  void constructorShouldConvertNullValuesToEmptyStrings() {
    ProductSpecificationViewModel viewModel = new ProductSpecificationViewModel(null, null);

    assertEquals("", viewModel.label());
    assertEquals("", viewModel.value());
  }

  @Test
  void constructorShouldKeepProvidedValues() {
    ProductSpecificationViewModel viewModel =
        new ProductSpecificationViewModel("Condition", "Like new");

    assertEquals("Condition", viewModel.label());
    assertEquals("Like new", viewModel.value());
  }
}