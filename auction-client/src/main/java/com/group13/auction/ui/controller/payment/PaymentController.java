package com.group13.auction.ui.controller.payment;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FormatUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class PaymentController extends BaseController implements PageLifecycle {

    @FXML private Label auctionIdLabel;
    @FXML private Label statusLabel;
    @FXML private Label resultLabel;

    @FXML
    private void initialize() {
        services().paymentService().lastStatusMessageProperty().addListener((obs, o, msg) -> {
            if (msg != null) {
                statusLabel.setText(msg);
            }
        });
        services().paymentService().lastResultProperty().addListener((obs, o, r) -> {
            if (r != null) {
                resultLabel.setText(formatResult(r));
            }
        });
    }

    @Override
    public void onShow() {
        String auctionId = screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse("—");
        auctionIdLabel.setText(auctionId);
        statusLabel.setText("Nhấn Thanh toán để trừ cọc và hoàn tất giao dịch.");
        PaymentDTOs.PaymentResultDTO last = services().paymentService().lastResultProperty().get();
        resultLabel.setText(last != null ? formatResult(last) : "");
    }

    @FXML
    private void onPay() {
        String auctionId = screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse(null);
        if (auctionId == null) {
            AlertUtil.showWarning("Chưa chọn phiên.");
            return;
        }
        services().paymentService().payForAuction(auctionId);
    }

    private static String formatResult(PaymentDTOs.PaymentResultDTO r) {
        return "Kết quả: " + r.getPaymentStatus()
                + " — giá: " + FormatUtil.currency(r.getFinalPrice())
                + ", số dư: " + FormatUtil.currency(r.getNewBalance());
    }
}
