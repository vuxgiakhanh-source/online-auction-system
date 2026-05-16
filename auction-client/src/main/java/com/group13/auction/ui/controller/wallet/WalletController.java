package com.group13.auction.ui.controller.wallet;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FormatUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public final class WalletController extends BaseController implements PageLifecycle {

    @FXML private Label balanceLabel;
    @FXML private Label lockedLabel;
    @FXML private Label availableLabel;
    @FXML private TextField depositField;
    @FXML private TextField withdrawField;

    @FXML
    private void initialize() {
        services().walletService().balanceProperty().addListener((obs, o, b) -> render(b));
    }

    @Override
    public void onShow() {
        services().walletService().refreshBalance();
    }

    @FXML
    private void onDeposit() {
        try {
            long amount = Long.parseLong(depositField.getText().trim());
            services().walletService().deposit(amount);
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Số tiền không hợp lệ.");
        }
    }

    @FXML
    private void onWithdraw() {
        try {
            long amount = Long.parseLong(withdrawField.getText().trim());
            services().walletService().withdraw(amount);
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Số tiền không hợp lệ.");
        }
    }

    private void render(PaymentDTOs.WalletBalanceResponseDTO b) {
        if (b == null) {
            return;
        }
        balanceLabel.setText(FormatUtil.currency(b.getBalance()));
        lockedLabel.setText(FormatUtil.currency(b.getLockedDeposit()));
        availableLabel.setText(FormatUtil.currency(b.getAvailableBalance()));
    }
}
