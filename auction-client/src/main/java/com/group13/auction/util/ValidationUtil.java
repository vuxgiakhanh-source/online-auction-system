package com.group13.auction.util;

/** Utility validate dữ liệu nhập cơ bản ở phía client. */
public final class ValidationUtil {

    private ValidationUtil() {
        // Utility class.
    }

    /**
     * Kiểm tra chuỗi có giá trị thật hay không.
     *
     * @param value chuỗi cần kiểm tra
     * @return true nếu chuỗi khác null và không blank
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Kiểm tra email ở mức cơ bản cho form client.
     *
     * @param email email cần kiểm tra
     * @return true nếu email có dạng cơ bản hợp lệ
     */
    public static boolean isBasicEmail(String email) {
        return hasText(email) && email.contains("@") && email.indexOf('@') < email.lastIndexOf('.');
    }

    /**
     * Kiểm tra giá trị có parse được sang số dương hay không.
     *
     * @param value chuỗi số cần kiểm tra
     * @return true nếu value là số dương
     */
    public static boolean isPositiveNumber(String value) {
        if (!hasText(value)) {
            return false;
        }

        try {
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}