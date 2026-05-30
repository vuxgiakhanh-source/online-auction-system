package com.group13.auction.util;

/** Định dạng số hiển thị trên UI (không phải tiền tệ). */
public final class NumberDisplayUtil {

  private NumberDisplayUtil() {
    // utility
  }

  /**
   * Số nguyên không phân tách hàng nghìn — dùng cho năm (2022), tránh locale vi_VN thành {@code
   * 2.022}.
   */
  public static String formatPlainInteger(Object value) {
    if (value == null) {
      return "";
    }

    if (value instanceof Number number) {
      long longValue = number.longValue();
      if (Math.abs(number.doubleValue() - longValue) < 0.000_001D) {
        return Long.toString(longValue);
      }
      return "";
    }

    String text = String.valueOf(value).trim();
    if (text.isBlank() || "null".equalsIgnoreCase(text)) {
      return "";
    }

    try {
      double parsed = Double.parseDouble(text);
      long longValue = (long) parsed;
      if (Math.abs(parsed - longValue) < 0.000_001D) {
        return Long.toString(longValue);
      }
    } catch (NumberFormatException ignored) {
      // fall through
    }

    return text;
  }

  /** Số có phân tách hàng nghìn theo locale JVM (vd. km đã đi). */
  public static String formatGroupedNumber(Object value) {
    if (value == null) {
      return "";
    }

    if (value instanceof Number number) {
      long longValue = number.longValue();
      if (Math.abs(number.doubleValue() - longValue) < 0.000_001D) {
        return String.format("%,d", longValue);
      }
      return String.format("%,.2f", number.doubleValue());
    }

    String text = String.valueOf(value).trim();
    if (text.isBlank() || "null".equalsIgnoreCase(text)) {
      return "";
    }

    try {
      double parsed = Double.parseDouble(text);
      long longValue = (long) parsed;
      if (Math.abs(parsed - longValue) < 0.000_001D) {
        return String.format("%,d", longValue);
      }
      return String.format("%,.2f", parsed);
    } catch (NumberFormatException exception) {
      return text;
    }
  }
}
