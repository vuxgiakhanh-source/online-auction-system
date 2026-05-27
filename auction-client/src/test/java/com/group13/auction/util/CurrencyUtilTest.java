package com.group13.auction.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CurrencyUtil}. */
class CurrencyUtilTest {

  @Test
  void formatVndShouldReturnPlaceholderWhenAmountIsNull() {
    assertEquals("--", CurrencyUtil.formatVnd((BigDecimal) null));
  }

  @Test
  void formatVndShouldFormatBigDecimalAsVietnameseCurrency() {
    String actual = normalizeCurrencyText(CurrencyUtil.formatVnd(BigDecimal.valueOf(1_234_000)));

    assertTrue(actual.contains("1.234.000"));
    assertTrue(actual.contains("₫"));
  }

  @Test
  void formatVndShouldFormatDoubleAsVietnameseCurrency() {
    String actual = normalizeCurrencyText(CurrencyUtil.formatVnd(2_500_000D));

    assertTrue(actual.contains("2.500.000"));
    assertTrue(actual.contains("₫"));
  }

  @Test
  void formatVndShouldFormatZeroAmount() {
    String actual = normalizeCurrencyText(CurrencyUtil.formatVnd(BigDecimal.ZERO));

    assertTrue(actual.contains("0"));
    assertTrue(actual.contains("₫"));
  }

  private static String normalizeCurrencyText(String value) {
    return value.replace('\u00A0', ' ').replace('\u202F', ' ');
  }
}
