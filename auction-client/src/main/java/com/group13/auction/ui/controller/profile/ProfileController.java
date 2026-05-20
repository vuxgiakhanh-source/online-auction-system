package com.group13.auction.ui.controller.profile;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.profile.ProfileService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.profile.UserProfileViewModel;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/** Controller cho màn hồ sơ người dùng. */
public final class ProfileController {

    private final ProfileService profileService = new ProfileService();

    @FXML private Label usernameLabel;

    @FXML private Label emailLabel;

    @FXML private Label rolesLabel;

    @FXML private Label primaryRoleLabel;

    @FXML private Label accountStatusLabel;

    @FXML private Label ratingLabel;

    @FXML private Label balanceLabel;

    @FXML private Label availableBalanceLabel;

    @FXML private Label lockedDepositLabel;

    @FXML private Label createdAtLabel;

    @FXML private Label updatedAtLabel;

    @FXML private Label sellerRequestHintLabel;

    @FXML private Label statusLabel;

    @FXML private Button requestSellerRoleButton;

    @FXML private Button refreshButton;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo màn hồ sơ và tải thông tin user hiện tại. */
    @FXML
    public void initialize() {
        loadProfile();
    }

    /** Quay lại dashboard chính. */
    @FXML
    public void handleBackToHome() {
        Navigator.getInstance().goToMainLayout();
    }

    /** Tải lại hồ sơ người dùng. */
    @FXML
    public void handleRefresh() {
        loadProfile();
    }

    /** Gửi yêu cầu nâng cấp tài khoản thành Seller. */
    @FXML
    public void handleRequestSellerRole() {
        if (!AlertUtil.confirm("Gửi yêu cầu nâng cấp tài khoản hiện tại thành Seller?")) {
            return;
        }

        setLoading(true, "Đang gửi yêu cầu nâng cấp Seller...");

        profileService
                .requestSellerRole()
                .thenAccept(
                        profile ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            renderProfile(profile);
                                            setLoading(false, "Yêu cầu nâng cấp Seller đã được xử lý.");
                                            AlertUtil.showInfo("Tài khoản đã được cập nhật theo phản hồi từ server.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không gửi được yêu cầu nâng cấp Seller.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void loadProfile() {
        setLoading(true, "Đang tải hồ sơ người dùng...");

        profileService
                .getMyProfile()
                .thenAccept(
                        profile ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            renderProfile(profile);
                                            setLoading(false, "Đã tải hồ sơ người dùng mới nhất.");
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không tải được hồ sơ người dùng.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void renderProfile(UserProfileViewModel profile) {
        usernameLabel.setText(profile.username());
        emailLabel.setText(profile.email());
        rolesLabel.setText(profile.rolesText());
        primaryRoleLabel.setText(profile.primaryRoleText());
        accountStatusLabel.setText(profile.accountStatusText());
        ratingLabel.setText(profile.ratingText());

        balanceLabel.setText(profile.balanceText());
        availableBalanceLabel.setText(profile.availableBalanceText());
        lockedDepositLabel.setText(profile.lockedDepositText());

        createdAtLabel.setText(profile.createdAtText());
        updatedAtLabel.setText(profile.updatedAtText());

        updateSellerRequestState(profile);
    }

    private void updateSellerRequestState(UserProfileViewModel profile) {
        requestSellerRoleButton.setDisable(!profile.canRequestSellerRole());

        if (profile.seller()) {
            sellerRequestHintLabel.setText("Tài khoản hiện tại đã có quyền Seller.");
            requestSellerRoleButton.setText("Đã là Seller");
            return;
        }

        if (profile.admin()) {
            sellerRequestHintLabel.setText("Tài khoản Admin không cần yêu cầu quyền Seller.");
            requestSellerRoleButton.setText("Không áp dụng");
            return;
        }

        if (profile.penalized()) {
            sellerRequestHintLabel.setText("Tài khoản đã từng bị xử phạt nên không thể tự động nâng cấp Seller.");
            requestSellerRoleButton.setText("Không đủ điều kiện");
            return;
        }

        if (profile.canRequestSellerRole()) {
            sellerRequestHintLabel.setText("Tài khoản đủ điều kiện gửi yêu cầu nâng cấp thành Seller.");
            requestSellerRoleButton.setText("Yêu cầu quyền Seller");
            return;
        }

        sellerRequestHintLabel.setText("Tài khoản hiện tại chưa đủ điều kiện yêu cầu quyền Seller.");
        requestSellerRoleButton.setText("Không đủ điều kiện");
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        refreshButton.setDisable(loading);
        requestSellerRoleButton.setDisable(loading);

        statusLabel.setText(message);
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Có lỗi xảy ra khi xử lý hồ sơ." : current.getMessage();
    }
}