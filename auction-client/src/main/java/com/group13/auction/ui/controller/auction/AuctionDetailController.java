package com.group13.auction.ui.controller.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.auction.AuctionQueryService;
import com.group13.auction.service.auction.JoinedAuctionState;
import com.group13.auction.service.auction.WatchAuctionService;
import com.group13.auction.service.payment.PaymentService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.ui.util.ImageLoader;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;

import java.util.concurrent.CompletionException;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.FlowPane;

/**
 * Controller cho màn chi tiết phiên đấu giá.
 */
public final class AuctionDetailController {

  private final AuctionQueryService auctionQueryService = new AuctionQueryService();
  private final WatchAuctionService watchAuctionService = new WatchAuctionService();
  private final JoinedAuctionState joinedAuctionState = JoinedAuctionState.getInstance();
  private final PaymentService paymentService = new PaymentService();

  private String auctionId;
  private boolean currentAuctionJoinable;
  private boolean currentUserLeftAuction;
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
  private Button cancelJoinButton;

  @FXML
  private FlowPane imageGalleryPane;

  @FXML
  private ProgressIndicator loadingIndicator;

  /**
   * Đọc auction id từ screen state và tải chi tiết phiên.
   */
  @FXML
  public void initialize() {
    auctionId = AppContext.getInstance()
        .getScreenStateStore()
        .get(ScreenStateKeys.SELECTED_AUCTION_ID, String.class)
        .orElse("");

    if (auctionId.isBlank()) {
      setMessage("Thiếu mã phiên đấu giá. Hãy quay lại danh sách và chọn lại phiên.");
      clearImageGallery();
      joinLiveButton.setDisable(true);
      watchLiveButton.setDisable(true);
      paymentButton.setDisable(true);
      paymentButton.setVisible(false);
      paymentButton.setManaged(false);
      setCancelJoinButtonVisible(false);
      paymentStatusLabel.setText("Không thể xác định phiên cần thanh toán.");
      return;
    }

    loadAuctionDetail();
  }

  /**
   * Quay lại danh sách phiên đấu giá.
   */
  @FXML
  public void handleBackToList() {
    Navigator.getInstance().goToAuctionList();
  }

  /**
   * Tải lại chi tiết phiên hiện tại.
   */
  @FXML
  public void handleRefresh() {
    loadAuctionDetail();
  }

  /**
   * Tham gia phiên rồi mở màn live bidding.
   */
  @FXML
  public void handleJoinLive() {
    if (joinedAuctionState.hasLeft(auctionId)) {
      AlertUtil.showWarning("Bạn đã hủy tham gia phiên đấu giá này và không thể tham gia lại.");
      return;
    }

    if (joinedAuctionState.hasJoined(auctionId)) {
      openLiveBidding();
      return;
    }

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
                    String message = extractMessage(throwable);

                    if (message.contains("ALREADY_LEFT_AUCTION") || message.contains("đã rời")) {
                      joinedAuctionState.markLeft(auctionId);
                      setLoading(false, "Bạn đã hủy tham gia phiên đấu giá này.");
                      AlertUtil.showWarning(
                          "Bạn đã từng hủy tham gia phiên này và không thể tham gia lại.");
                      loadAuctionDetail();
                      return;
                    }

                    setLoading(false, "Không tham gia được phiên đấu giá.");
                    AlertUtil.showError(message);
                  });
              return null;
            });
  }

  /**
   * Watch phiên rồi mở màn live bidding.
   */
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

  /**
   * Hủy tham gia phiên đấu giá và xử lý tiền cọc theo rule của server.
   */
  @FXML
  public void handleCancelJoin() {
    if (!joinedAuctionState.hasJoined(auctionId)) {
      AlertUtil.showWarning("Bạn chưa tham gia phiên đấu giá này.");
      setCancelJoinButtonVisible(false);
      loadAuctionDetail();
      return;
    }

    setLoading(true, "Đang kiểm tra trạng thái phiên trước khi hủy tham gia...");

    auctionQueryService
        .getAuctionDetail(auctionId)
        .thenAccept(
            detail ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      renderDetail(detail);
                      setLoading(false, "Vui lòng xác nhận nếu bạn muốn hủy tham gia.");

                      if (!joinedAuctionState.hasJoined(auctionId)) {
                        AlertUtil.showWarning("Bạn chưa tham gia phiên đấu giá này.");
                        setCancelJoinButtonVisible(false);
                        return;
                      }

                      boolean confirmed =
                          AlertUtil.confirm(buildCancelJoinConfirmationMessage(detail));
                      if (!confirmed) {
                        setMessage("Đã giữ trạng thái tham gia phiên.");
                        return;
                      }

                      executeCancelJoin();
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không kiểm tra được trạng thái phiên.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  /**
   * Gửi yêu cầu thanh toán phiên đấu giá đã thắng.
   */
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
    ImageLoader.fillPreviewableGallery(imageGalleryPane, detail.imageUrls());
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

    boolean joinedByCurrentUser = joinedAuctionState.hasJoined(detail.auctionId());
    currentUserLeftAuction = joinedAuctionState.hasLeft(detail.auctionId());
    currentAuctionJoinable = detail.joinable();

    if (currentUserLeftAuction) {
      joinLiveButton.setText("Đã hủy tham gia");
      joinLiveButton.setDisable(true);
    } else {
      joinLiveButton.setText(joinedByCurrentUser ? "Tiếp tục đặt giá" : "Tham gia đặt giá");
      joinLiveButton.setDisable(!currentAuctionJoinable);
    }

    watchLiveButton.setDisable(!currentAuctionJoinable);
    setCancelJoinButtonVisible(joinedByCurrentUser && currentAuctionJoinable && !currentUserLeftAuction);

    updatePaymentControls(detail);
  }

  private void executeCancelJoin() {
    setLoading(true, "Đang hủy tham gia phiên đấu giá...");

    watchAuctionService
        .leaveAuction(auctionId)
        .thenAccept(
            response ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      joinedAuctionState.markLeft(auctionId);
                      currentUserLeftAuction = true;
                      setCancelJoinButtonVisible(false);
                      setLoading(false, "Đã hủy tham gia phiên đấu giá.");
                      AlertUtil.showInfo(buildCancelJoinSuccessMessage(response));
                      loadAuctionDetail();
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không hủy được tham gia phiên.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private String buildCancelJoinConfirmationMessage(AuctionDetailViewModel detail) {
    if (isCurrentUserLeading(detail)) {
      return "Hủy tham gia phiên đấu giá?\n\n"
          + "Theo trạng thái hiện tại, bạn đang là người dẫn đầu phiên này.\n"
          + "Nếu xác nhận hủy tham gia, hệ thống sẽ phạt 100% tiền cọc và có thể trừ điểm uy tín.\n\n"
          + "Kết quả cuối cùng sẽ được server xử lý tại thời điểm xác nhận hủy.\n\n"
          + "Bạn có chắc muốn tiếp tục không?";
    }

    if (detail != null && detail.pastTwoThirdsElapsed()) {
      return "Hủy tham gia phiên đấu giá?\n\n"
          + "Theo trạng thái hiện tại, phiên đã đi qua hơn 2/3 thời gian đấu giá.\n"
          + "Nếu xác nhận hủy tham gia, hệ thống sẽ phạt 100% tiền cọc và có thể trừ điểm uy tín.\n\n"
          + "Kết quả cuối cùng sẽ được server xử lý tại thời điểm xác nhận hủy.\n\n"
          + "Bạn có chắc muốn tiếp tục không?";
    }

    if (hasCurrentLeader(detail)) {
      return "Hủy tham gia phiên đấu giá?\n\n"
          + "Theo trạng thái hiện tại, bạn không phải người dẫn đầu và phiên chưa đi qua 2/3 thời gian.\n"
          + "Nếu xác nhận hủy tham gia, tiền cọc sẽ được hoàn lại theo xử lý của hệ thống.\n\n"
          + "Kết quả cuối cùng sẽ được server xử lý tại thời điểm xác nhận hủy.\n\n"
          + "Bạn có chắc muốn tiếp tục không?";
    }

    return "Hủy tham gia phiên đấu giá?\n\n"
        + "Hiện phiên chưa có người dẫn đầu rõ ràng.\n"
        + "Tiền cọc sẽ được xử lý theo trạng thái phiên tại thời điểm server xác nhận hủy.\n\n"
        + "Bạn có chắc muốn tiếp tục không?";
  }

  private String buildCancelJoinSuccessMessage(AuctionDTOs.LeaveAuctionResponseDTO response) {
    if (response == null) {
      return "Bạn đã hủy tham gia phiên đấu giá.";
    }

    if (response.isDepositForfeited()) {
      String message =
          "Bạn đã hủy tham gia phiên đấu giá.\n"
              + "Hệ thống đã phạt tiền cọc: "
              + CurrencyUtil.formatVnd(response.getForfeitedAmount())
              + ".\n";

      if (response.isRatingPenalized()) {
        message += "Điểm uy tín của bạn có thể đã bị trừ theo quy định.\n";
      }

      return message
          + "Số dư khả dụng mới: "
          + CurrencyUtil.formatVnd(response.getNewAvailableBalance())
          + ".";
    }

    return "Bạn đã hủy tham gia phiên đấu giá.\n"
        + "Tiền cọc đã được hoàn lại.\n"
        + "Số dư khả dụng mới: "
        + CurrencyUtil.formatVnd(response.getNewAvailableBalance())
        + ".";
  }

  private boolean isCurrentUserLeading(AuctionDetailViewModel detail) {
    String currentUserId = currentUserId();
    return detail != null
        && currentUserId != null
        && !currentUserId.isBlank()
        && detail.currentLeaderId() != null
        && !detail.currentLeaderId().isBlank()
        && detail.currentLeaderId().equals(currentUserId);
  }

  private boolean hasCurrentLeader(AuctionDetailViewModel detail) {
    return detail != null
        && detail.currentLeaderId() != null
        && !detail.currentLeaderId().isBlank();
  }

  private void setCancelJoinButtonVisible(boolean visible) {
    if (cancelJoinButton == null) {
      return;
    }

    cancelJoinButton.setVisible(visible);
    cancelJoinButton.setManaged(visible);
    cancelJoinButton.setDisable(!visible);
  }

  private void clearImageGallery() {
    if (imageGalleryPane != null) {
      imageGalleryPane.getChildren().clear();
    }
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

    joinLiveButton.setDisable(loading || !currentAuctionJoinable || currentUserLeftAuction);
    watchLiveButton.setDisable(loading || !currentAuctionJoinable);
    paymentButton.setDisable(loading || !paymentAllowed);

    if (cancelJoinButton != null && cancelJoinButton.isManaged()) {
      cancelJoinButton.setDisable(loading);
    }

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