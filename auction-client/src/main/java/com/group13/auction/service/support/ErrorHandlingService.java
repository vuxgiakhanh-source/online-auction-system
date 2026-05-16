package com.group13.auction.service.support;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;

/**
 * Xử lý lỗi hệ thống và hiển thị toast/dialog thống nhất.
 */
public final class ErrorHandlingService implements ClientEventListener {

    @Override
    public void onSystemError(ErrorDTO error) {
        String message = error != null && error.getMessage() != null
                ? error.getMessage()
                : "Đã xảy ra lỗi hệ thống.";
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(message));
    }
}
