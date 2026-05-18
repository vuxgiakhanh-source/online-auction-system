package com.group13.auction.ui.controller.wallet;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.wallet.WalletService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.wallet.WalletViewModel;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

/** Controller cho màn ví người dùng. */
public final class WalletController {

    private final WalletService walletService = new WalletService();

    @FXML private Label balanceLabel;

    @FXML private Label availableBalanceLabel;

    @FXML private Label lockedDepositLabel;

    @FXML private Label statusLabel;

    @FXML private TextField depositAmountField;

    @FXML private TextField withdrawAmountField;

    @FXML private Button refreshButton;

    @FXML private Button depositButton;

    @FXML private Button withdrawButton;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo màn ví và tải số dư hiện tại. */
    @FXML
    public void initialize() {
        loadWallet();
    }

    /** Quay lại dashboard chính. */
    @FXML
    public void handleBackToHome() {
        Navigator.getInstance().goToMainLayout();
    }

    /** Tải lại số dư ví. */
    @FXML
    public void handleRefresh() {
        loadWallet();
    }

    /** Gửi yêu cầu nạp tiền. */
    @FXML
    public void handleDeposit() {
        long amount = parseAmount(depositAmountField.getText());
        if (amount <= 0) {
            AlertUtil.showError("Số tiền nạp phải lớn hơn 0.");
            return;
        }

        setLoading(true, "Đang gửi yêu cầu nạp tiền...");
        walletService
                .deposit(amount)
                .thenAccept(
                        wallet ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            depositAmountField.clear();
                                            renderWallet(wallet);
                                            setLoading(false, "Nạp tiền thành công.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không nạp được tiền vào ví.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    /** Gửi yêu cầu rút tiền. */
    @FXML
    public void handleWithdraw() {
        long amount = parseAmount(withdrawAmountField.getText());
        if (amount <= 0) {
            AlertUtil.showError("Số tiền rút phải lớn hơn 0.");
            return;
        }

        setLoading(true, "Đang gửi yêu cầu rút tiền...");
        walletService
                .withdraw(amount)
                .thenAccept(
                        wallet ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            withdrawAmountField.clear();
                                            renderWallet(wallet);
                                            setLoading(false, "Rút tiền thành công.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không rút được tiền khỏi ví.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void loadWallet() {
        setLoading(true, "Đang tải số dư ví...");

        walletService
                .getWalletBalance()
                .thenAccept(
                        wallet ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            renderWallet(wallet);
                                            setLoading(false, "Đã tải số dư ví mới nhất.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không tải được số dư ví.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void renderWallet(WalletViewModel wallet) {
        balanceLabel.setText(wallet.balanceText());
        availableBalanceLabel.setText(wallet.availableBalanceText());
        lockedDepositLabel.setText(wallet.lockedDepositText());
    }

    private long parseAmount(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return -1L;
        }

        String digitsOnly = rawText.replaceAll("[^0-9]", "");
        if (digitsOnly.isBlank()) {
            return -1L;
        }

        try {
            return Long.parseLong(digitsOnly);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        refreshButton.setDisable(loading);
        depositButton.setDisable(loading);
        withdrawButton.setDisable(loading);
        depositAmountField.setDisable(loading);
        withdrawAmountField.setDisable(loading);

        statusLabel.setText(message);
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Có lỗi xảy ra khi xử lý ví." : current.getMessage();
    }
}