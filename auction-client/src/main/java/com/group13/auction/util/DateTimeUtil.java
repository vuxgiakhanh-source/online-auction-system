package com.group13.auction.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Utility format thời gian dùng chung cho view model và controller. */
public final class DateTimeUtil {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DateTimeUtil() {
        // Utility class.
    }

    /**
     * Format thời gian ngày/giờ để hiển thị trong UI.
     *
     * @param dateTime thời gian cần format
     * @return chuỗi thời gian hoặc {@code --} nếu null
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "--" : DATE_TIME_FORMATTER.format(dateTime);
    }

    /**
     * Format khoảng thời gian còn lại theo dạng ngắn gọn.
     *
     * @param duration khoảng thời gian
     * @return chuỗi thời gian còn lại
     */
    public static String formatRemaining(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "Đã kết thúc";
        }

        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();

        if (days > 0) {
            return days + " ngày " + hours + " giờ";
        }
        if (hours > 0) {
            return hours + " giờ " + minutes + " phút";
        }
        return Math.max(1, minutes) + " phút";
    }
}