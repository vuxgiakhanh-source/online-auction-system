package com.group13.auction.ui.controller.auction;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.auction.AuctionQueryService;
import com.group13.auction.service.auction.WatchAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/** Controller cho màn chi tiết phiên đấu giá. */
public final class AuctionDetailController {

    private final AuctionQueryService auctionQueryService = new AuctionQueryService();
    private final WatchAuctionService watchAuctionService = new WatchAuctionService();

    private String auctionId;
    private boolean currentAuctionJoinable;

    @FXML
    private Label titleLabel;

    @FXML
    private Label categoryLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label sellerLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private Label startingPriceLabel;

    @FXML
    private Label reservePriceLabel;

    @FXML
    private Label leaderLabel;

    @FXML
    private Label viewerCountLabel;

    @FXML
    private Label startTimeLabel;

    @FXML
    private Label endTimeLabel;

    @FXML
    private Label remainingTimeLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Button joinLiveButton;

    @FXML
    private Button watchLiveButton;

    @FXML
    private ProgressIndicator loadingIndicator;

    /** Đọc auction id từ screen state và tải chi tiết phiên. */
    @FXML
    public void initialize() {
        auctionId = AppContext.getInstance()
                .getScreenStateStore()
                .get(ScreenStateKeys.SELECTED_AUCTION_ID, String.class)
                .orElse("");

        if (auctionId.isBlank()) {
            setMessage("Thiếu mã phiên đấu giá. Hãy quay lại danh sách và chọn lại phiên.");
            joinLiveButton.setDisable(true);
            watchLiveButton.setDisable(true);
            return;
        }

        loadAuctionDetail();
    }

    /** Quay lại danh sách phiên đấu giá. */
    @FXML
    public void handleBackToList() {
        Navigator.getInstance().goToAuctionList();
    }

    /** Tải lại chi tiết phiên hiện tại. */
    @FXML
    public void handleRefresh() {
        loadAuctionDetail();
    }

    /** Tham gia phiên rồi mở màn live bidding. */
    @FXML
    public void handleJoinLive() {
        setLoading(true, "Đang tham gia phiên đấu giá...");

        watchAuctionService
                .joinAuction(auctionId)
                .thenAccept(
                        response ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            setLoading(false, "Tham gia thành công.");
                                            openLiveBidding();
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không tham gia được phiên đấu giá.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    /** Watch phiên rồi mở màn live bidding. */
    @FXML
    public void handleWatchLive() {
        setLoading(true, "Đang mở phòng live bidding...");

        watchAuctionService
                .watchAuction(auctionId)
                .thenAccept(
                        detail ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            setLoading(false, "Đã vào chế độ theo dõi.");
                                            openLiveBidding();
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không mở được phòng live bidding.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void loadAuctionDetail() {
        setLoading(true, "Đang tải chi tiết phiên đấu giá...");

        auctionQueryService
                .getAuctionDetail(auctionId)
                .thenAccept(detail -> FxThreadUtil.runOnFxThread(() -> renderDetail(detail)))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Không tải được chi tiết phiên đấu giá.");
                                        AlertUtil.showError(extractMessage(throwable));
                                    });
                            return null;
                        });
    }

    private void renderDetail(AuctionDetailViewModel detail) {
        setLoading(false, "Chi tiết phiên đã được cập nhật.");

        titleLabel.setText(detail.itemName());
        categoryLabel.setText(detail.categoryText());
        statusLabel.setText(detail.statusText());
        descriptionLabel.setText(detail.description());
        sellerLabel.setText(detail.sellerText());
        currentPriceLabel.setText(detail.currentPriceText());
        startingPriceLabel.setText(detail.startingPriceText());
        reservePriceLabel.setText(detail.reservePriceText());
        leaderLabel.setText(detail.leaderText());
        viewerCountLabel.setText(detail.viewerCountText());
        startTimeLabel.setText(detail.startTimeText());
        endTimeLabel.setText(detail.endTimeText());
        remainingTimeLabel.setText(detail.remainingTimeText());

        currentAuctionJoinable = detail.joinable();
        joinLiveButton.setDisable(!currentAuctionJoinable);
        watchLiveButton.setDisable(!currentAuctionJoinable);
    }

    private void openLiveBidding() {
        AppContext.getInstance()
                .getScreenStateStore()
                .put(ScreenStateKeys.SELECTED_AUCTION_ID, auctionId);
        Navigator.getInstance().goToLiveBidding();
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        joinLiveButton.setDisable(loading || !currentAuctionJoinable);
        watchLiveButton.setDisable(loading || !currentAuctionJoinable);

        setMessage(message);
    }

    private void setMessage(String message) {
        messageLabel.setText(message == null ? "" : message);
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? "Không tải được dữ liệu." : message;
    }
}