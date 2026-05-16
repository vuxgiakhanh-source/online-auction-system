package com.group13.auction.ui.util;

import com.group13.auction.common.dto.core.ErrorDTO;

public final class ErrorMessages {

    private ErrorMessages() {}

    public static String from(ErrorDTO error) {
        if (error == null) {
            return "Đã xảy ra lỗi.";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        if (error.getCode() != null) {
            return error.getCode();
        }
        return "Đã xảy ra lỗi.";
    }
}
