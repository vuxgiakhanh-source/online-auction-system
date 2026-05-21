package com.group13.auction.ui.controller.seller;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.DialogSoundUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.ui.util.ImageLoader;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;

/** Controller cho màn chi tiết phiên đấu giá trong khu vực người bán. */
public final class SellerAuctionDetailController {

    private final SellerAuctionService sellerAuctionService = new SellerAuctionService();

    private SellerAuctionRowViewModel selectedAuction;

    @FXML private Label titleLabel;

    @FXML private Label auctionIdLabel;

    @FXML private Label categoryLabel;

    @FXML private Label statusLabel;

    @FXML private Label currentPriceLabel;

    @FXML private Label startingPriceLabel;

    @FXML private Label reservePriceLabel;

    @FXML private Label startTimeLabel;

    @FXML private Label endTimeLabel;

    @FXML private Label viewerCountLabel;

    @FXML private FlowPane imageGalleryPane;

    @FXML private Label messageLabel;

    @FXML private Button editButton;

    @FXML private Button cancelButton;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo màn chi tiết từ phiên đã chọn ở danh sách người bán. */
    @FXML
    public void initialize() {
        selectedAuction =
                AppContext.getInstance()
                        .getScreenStateStore()
                        .get(ScreenStateKeys.SELECTED_SELLER_AUCTION_ROW, SellerAuctionRowViewModel.class)
                        .orElse(null);

        if (selectedAuction == null) {
            renderMissingState();
            return;
        }

        renderAuctionDetail();
        setLoading(false, "Sẵn sàng.");
    }

    /** Quay lại danh sách phiên của người bán. */
    @FXML
    public void handleBackToSellerList() {
        Navigator.getInstance().goToSellerAuctionList();
    }

    /** Mở màn gia hạn thời gian kết thúc phiên. */
    @FXML
    public void handleEditAuction() {
        if (selectedAuction == null) {
            AlertUtil.showWarning("Chưa chọn phiên đấu giá để chỉnh sửa.");
            return;
        }

        AppContext.getInstance()
                .getScreenStateStore()
                .put(ScreenStateKeys.SELECTED_SELLER_AUCTION_ROW, selectedAuction);
        Navigator.getInstance().goToEditAuction();
    }

    /** Gửi yêu cầu hủy phiên đấu giá. */
    @FXML
    public void handleRequestCancel() {
        if (selectedAuction == null) {
            AlertUtil.showWarning("Chưa chọn phiên đấu giá để hủy.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Yêu cầu hủy phiên đấu giá");
        dialog.setHeaderText(null);
        dialog.setContentText("Nhập lý do hủy phiên:");
        DialogSoundUtil.installButtonClickSound(dialog);

        dialog
                .showAndWait()
                .ifPresent(
                        reason -> {
                            if (reason == null || reason.isBlank()) {
                                AlertUtil.showWarning("Lý do hủy phiên không được để trống.");
                                return;
                            }

                            sendCancelRequest(reason);
                        });
    }

    private void renderAuctionDetail() {
        titleLabel.setText(selectedAuction.itemName());
        ImageLoader.fillPreviewableGallery(imageGalleryPane, selectedAuction.imageUrls());
        auctionIdLabel.setText(selectedAuction.auctionId());
        categoryLabel.setText(selectedAuction.categoryText());
        statusLabel.setText(selectedAuction.statusText());
        currentPriceLabel.setText(selectedAuction.currentPriceText());
        startingPriceLabel.setText(selectedAuction.startingPriceText());
        reservePriceLabel.setText(selectedAuction.reservePriceText());
        startTimeLabel.setText(selectedAuction.startTimeText());
        endTimeLabel.setText(selectedAuction.endTimeText());
        viewerCountLabel.setText(selectedAuction.viewerCountText());

        editButton.setDisable(!selectedAuction.editable());
        cancelButton.setDisable(!selectedAuction.cancelRequestAllowed());

        if (!selectedAuction.editable()) {
            messageLabel.setText("Chỉ phiên sắp mở mới có thể gia hạn hoặc gửi yêu cầu hủy.");
        }
    }

    private void renderMissingState() {
        titleLabel.setText("Chưa chọn phiên đấu giá");
        auctionIdLabel.setText("-");
        categoryLabel.setText("-");
        statusLabel.setText("-");
        currentPriceLabel.setText("-");
        startingPriceLabel.setText("-");
        reservePriceLabel.setText("-");
        startTimeLabel.setText("-");
        endTimeLabel.setText("-");
        viewerCountLabel.setText("-");
        clearImageGallery();

        editButton.setDisable(true);
        cancelButton.setDisable(true);

        setLoading(false, "Không tìm thấy dữ liệu phiên đấu giá.");
    }

    private void clearImageGallery() {
        if (imageGalleryPane != null) {
            imageGalleryPane.getChildren().clear();
        }
    }

    private void sendCancelRequest(String reason) {
        boolean confirmed =
                AlertUtil.confirm("Xác nhận gửi yêu cầu hủy phiên đấu giá này?");

        if (!confirmed) {
            return;
        }

        setLoading(true, "Đang gửi yêu cầu hủy phiên...");

        sellerAuctionService
                .requestCancelAuction(selectedAuction.auctionId(), reason)
                .thenAccept(
                        ignored ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            AlertUtil.showInfo("Yêu cầu hủy phiên đã được gửi thành công.");
                                            Navigator.getInstance().goToSellerAuctionList();
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không gửi được yêu cầu hủy phiên.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        editButton.setDisable(loading || selectedAuction == null || !selectedAuction.editable());
        cancelButton.setDisable(
                loading || selectedAuction == null || !selectedAuction.cancelRequestAllowed());

        messageLabel.setText(message);
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? "Không xử lý được yêu cầu." : message;
    }
}
