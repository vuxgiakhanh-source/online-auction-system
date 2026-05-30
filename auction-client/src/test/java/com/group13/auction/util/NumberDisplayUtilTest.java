package com.group13.auction.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link NumberDisplayUtil}. */
class NumberDisplayUtilTest {

  @Test
  void formatPlainIntegerShouldNotUseThousandsSeparator() {
    assertEquals("2022", NumberDisplayUtil.formatPlainInteger(2022));
    assertEquals("2022", NumberDisplayUtil.formatPlainInteger(2022L));
    assertEquals("2022", NumberDisplayUtil.formatPlainInteger("2022"));
  }

}
