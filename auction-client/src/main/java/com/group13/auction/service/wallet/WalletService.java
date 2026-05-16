package com.group13.auction.service.wallet;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Luồng DEPOSIT: nạp / rút / số dư ví.
 */
public final class WalletService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<PaymentDTOs.WalletBalanceResponseDTO> balance =
            new SimpleObjectProperty<>();

    public ObjectProperty<PaymentDTOs.WalletBalanceResponseDTO> balanceProperty() {
        return balance;
    }

    public void refreshBalance() {
        network().getWalletBalance();
    }

    public void deposit(long amount) {
        network().deposit(amount);
    }

    public void withdraw(long amount) {
        network().withdraw(amount);
    }

    @Override
    public void onWalletBalanceReceived(PaymentDTOs.WalletBalanceResponseDTO response) {
        balance.set(response);
    }

    @Override
    public void onDepositSuccess(PaymentDTOs.WalletBalanceResponseDTO response) {
        balance.set(response);
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Nạp tiền thành công."));
    }

    @Override
    public void onWithdrawSuccess(PaymentDTOs.WalletBalanceResponseDTO response) {
        balance.set(response);
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Rút tiền thành công."));
    }

    @Override
    public void onDepositFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onWithdrawFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onDepositRefundNotify(PaymentDTOs.DepositRefundDTO dto) {
        refreshBalance();
    }

    @Override
    public void onDepositForfeitedNotify(PaymentDTOs.DepositForfeitedDTO dto) {
        refreshBalance();
    }
}
