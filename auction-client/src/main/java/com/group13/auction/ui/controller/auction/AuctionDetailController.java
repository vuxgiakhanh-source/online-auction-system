package com.group13.auction.ui.controller.auction;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.auction.AuctionQueryService;
import com.group13.auction.service.auction.WatchAuctionService;
import com.group13.auction.service.payment.PaymentService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/** Controller cho màn chi tiết phiên đấu giá. */
public final class AuctionDetailController {

    private final AuctionQueryService auctionQueryService = new AuctionQueryService();
    private final WatchAuctionService watchAuctionService = new WatchAuctionService();
    private final PaymentService paymentService = new PaymentService();

    private String auctionId;
    private boolean currentAuctionJoinable;
    private boolean paymentAllowed;
    private AuctionDetailViewModel currentDetail;

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
    private Label paymentStatusLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Button joinLiveButton;

    @FXML
    private Button watchLiveButton;

    @FXML
    private Button paymentButton;

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
            paymentButton.setDisable(true);
            paymentButton.setVisible(false);
            paymentButton.setManaged(false);
            paymentStatusLabel.setText("Không thể xác định phiên cần thanh toán.");
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

    /** Gửi yêu cầu thanh toán phiên đấu giá đã thắng. */
    @FXML
    public void handleRequestPayment() {
        if (currentDetail == null) {
            AlertUtil.showWarning("Chưa có dữ liệu phiên đấu giá để thanh toán.");
            return;
        }

        if (!paymentAllowed) {
            AlertUtil.showWarning("Chỉ người thắng phiên đấu giá đã kết thúc mới có thể thanh toán.");
            return;
        }

        boolean confirmed =
                AlertUtil.confirm(
                        "Xác nhận thanh toán cho phiên \""
                                + currentDetail.itemName()
                                + "\"?\nHệ thống sẽ kiểm tra quyền thanh toán và số dư ví trước khi xử lý.");
        if (!confirmed) {
            return;
        }

        setLoading(true, "Đang gửi yêu cầu thanh toán...");

        paymentService
                .requestPayment(auctionId)
                .thenAccept(
                        result ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            renderPaymentResult(result);
                                            AlertUtil.showInfo(buildPaymentSuccessMessage(result));
                                            loadAuctionDetail();
                                        }))
                .exceptionally(
                        throwable -> {
                            FxThreadUtil.runOnFxThread(
                                    () -> {
                                        setLoading(false, "Thanh toán không thành công.");
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
        currentDetail = detail;
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

        updatePaymentControls(detail);
    }

    private void updatePaymentControls(AuctionDetailViewModel detail) {
        String currentUserId = currentUserId();
        paymentAllowed = detail.canRequestPayment(currentUserId);

        boolean finishedButNotPaid = detail.finished() && !detail.paid();
        paymentButton.setVisible(finishedButNotPaid);
        paymentButton.setManaged(finishedButNotPaid);
        paymentButton.setDisable(!paymentAllowed);

        if (detail.paid()) {
            paymentStatusLabel.setText("Phiên đấu giá này đã được thanh toán.");
            return;
        }

        if (paymentAllowed) {
            paymentStatusLabel.setText(
                    "Bạn là người dẫn đầu khi phiên kết thúc. Có thể thanh toán phiên này.");
            return;
        }

        if (finishedButNotPaid) {
            paymentStatusLabel.setText(
                    "Phiên đã kết thúc. Chỉ người thắng phiên đấu giá mới có thể thanh toán.");
            return;
        }

        paymentStatusLabel.setText("Thanh toán sẽ mở sau khi phiên đấu giá kết thúc.");
    }

    private void renderPaymentResult(PaymentResultViewModel result) {
        paymentAllowed = false;
        paymentButton.setDisable(true);
        paymentStatusLabel.setText(
                "Thanh toán thành công. Số dư mới: "
                        + result.newBalanceText()
                        + ". Thời điểm: "
                        + result.paidAtText()
                        + ".");
        setMessage("Thanh toán thành công.");
    }

    private String buildPaymentSuccessMessage(PaymentResultViewModel result) {
        return "Thanh toán thành công.\n"
                + "Giá chốt: "
                + result.finalPriceText()
                + "\n"
                + "Tiền cọc đã trừ: "
                + result.depositDeductedText()
                + "\n"
                + "Phần thanh toán thêm: "
                + result.remainingToPayText()
                + "\n"
                + "Số dư mới: "
                + result.newBalanceText();
    }

    private String currentUserId() {
        return AppContext.getInstance()
                .getSessionManager()
                .getCurrentSession()
                .map(UserSession::getUserId)
                .orElse("");
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
        paymentButton.setDisable(loading || !paymentAllowed);

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