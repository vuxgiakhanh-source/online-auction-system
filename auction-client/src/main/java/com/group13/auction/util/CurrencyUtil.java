package com.group13.auction.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Utility format tiền tệ phục vụ hiển thị phía JavaFX client. */
public final class CurrencyUtil {

    private static final Locale VIETNAM_LOCALE = Locale.forLanguageTag("vi-VN");

    private CurrencyUtil() {
        // Utility class.
    }

    /**
     * Format số tiền sang dạng VND dễ đọc.
     *
     * @param amount số tiền cần format
     * @return chuỗi tiền tệ, ví dụ {@code 1.000.000 ₫}
     */
    public static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "--";
        }
        return NumberFormat.getCurrencyInstance(VIETNAM_LOCALE).format(amount);
    }

    /**
     * Format số tiền double sang dạng VND dễ đọc.
     *
     * @param amount số tiền cần format
     * @return chuỗi tiền tệ
     */
    public static String formatVnd(double amount) {
        return formatVnd(BigDecimal.valueOf(amount));
    }
}