package com.group13.auction.ui.util;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Định dạng tiền tệ và thời gian thống nhất trên UI. */
public final class FormatUtil {

    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatUtil() {}

    public static String currency(long amount) {
        return VND.format(amount) + " đ";
    }

    public static String currency(double amount) {
        return VND.format(Math.round(amount)) + " đ";
    }

    public static String dateTime(LocalDateTime time) {
        return time == null ? "—" : time.format(DATE_TIME);
    }

    public static String auctionStatus(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status.toUpperCase()) {
            case "OPEN" -> "Chờ bắt đầu";
            case "RUNNING" -> "Đang đấu giá";
            case "FINISHED" -> "Đã kết thúc";
            case "PAID" -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            default -> status;
        };
    }
}
