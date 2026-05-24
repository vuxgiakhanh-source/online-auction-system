package com.group13.auction.ui.controller.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.mapper.BidViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.auction.AutoBidService;
import com.group13.auction.service.auction.BidHistoryService;
import com.group13.auction.service.auction.BidService;
import com.group13.auction.service.auction.JoinedAuctionState;
import com.group13.auction.service.auction.WatchAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import com.group13.auction.viewmodel.auction.BidHistoryPointViewModel;
import com.group13.auction.viewmodel.auction.LiveBidViewModel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

/** Controller cho màn đấu giá trực tiếp và realtime bid chart. */
public final class LiveBiddingController implements ClientEventListener {

  private static final int MAX_CHART_POINTS = 40;
  private static final long TIER_LOW = 1_000_000L;
  private static final long TIER_MID = 10_000_000L;
  private static final long INCREMENT_LOW = 50_000L;
  private static final long INCREMENT_MID = 200_000L;
  private static final long INCREMENT_HIGH = 500_000L;

  private final ClientNetworkFacade networkFacade = ClientNetworkFacade.getDefault();
  private final WatchAuctionService watchAuctionService = new WatchAuctionService();
  private final JoinedAuctionState joinedAuctionState = JoinedAuctionState.getInstance();
  private final BidService bidService = new BidService();
  private final BidHistoryService bidHistoryService = new BidHistoryService();
  private final AutoBidService autoBidService = new AutoBidService();
  private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();

  private String auctionId;
  private boolean bidAllowed;
  private long currentPriceRaw;
  private volatile boolean pendingBidRequest;
  private volatile boolean pendingAutoBidRequest;

  /** Thời điểm kết thúc phiên — cập nhật khi nhận AUCTION_EXTENDED. Dùng cho countdown. */
  private LocalDateTime auctionEndTime;

  /** Timeline đếm ngược hiển thị thời gian còn lại. Hủy khi rời màn hoặc phiên kết thúc. */
  private Timeline countdownTimer;

  @FXML private Label titleLabel;
  @FXML private Label statusLabel;
  @FXML private Label currentPriceLabel;
  @FXML private Label leaderLabel;
  @FXML private Label reserveLabel;
  @FXML private Label endTimeLabel;
  @FXML private Label countdownLabel;
  @FXML private Label messageLabel;
  @FXML private TextField bidAmountField;
  @FXML private Label minimumBidHintLabel;
  @FXML private Button placeBidButton;
  @FXML private Label autoBidStatusLabel;
  @FXML private TextField autoBidMaxField;
  @FXML private Button registerAutoBidButton;
  @FXML private Button updateAutoBidButton;
  @FXML private Button cancelAutoBidButton;
  @FXML private ProgressIndicator loadingIndicator;
  @FXML private LineChart<String, Number> bidLineChart;
  @FXML private ListView<String> bidHistoryListView;

  /** Đăng ký realtime listener, watch auction và tải lịch sử bid ban đầu. */
  @FXML
  public void initialize() {
    priceSeries.setName("Giá cao nhất");
    bidLineChart.getData().clear();
    bidLineChart.getData().add(priceSeries);
    bidLineChart.setAnimated(false);
    bidLineChart.setLegendVisible(false);

    auctionId =
        AppContext.getInstance()
            .getScreenStateStore()
            .get(ScreenStateKeys.SELECTED_AUCTION_ID, String.class)
            .orElse("");

    if (auctionId.isBlank()) {
      setMessage("Thiếu mã phiên đấu giá. Hãy quay lại danh sách và chọn lại phiên.");
      placeBidButton.setDisable(true);
      syncAutoBidButtons(true);
      return;
    }

    syncAutoBidButtons(true);

    networkFacade.addListener(this);
    watchCurrentAuction();
    loadBidHistory();
  }

  /** Quay lại màn chi tiết và hủy listener realtime của controller hiện tại. */
  @FXML
  public void handleBackToDetail() {
    stopCountdownTimer();
    cleanupRealtimeListener();

    // Quay lại màn chi tiết không đồng nghĩa với hủy tham gia phiên.
    // Không gửi LEAVE_AUCTION ở đây vì server hiện xử lý packet đó như xóa JOINED.
    Navigator.getInstance().goToAuctionDetail();
  }

  /** Gửi yêu cầu đặt giá. */
  @FXML
  public void handlePlaceBid() {
    if (!bidAllowed) {
      AlertUtil.showWarning("Hãy tham gia phiên đấu giá trước khi đặt giá.");
      return;
    }

    pendingBidRequest = true;
    setLoading(true, "Đang gửi giá đặt...");

    bidService
        .placeBid(auctionId, bidAmountField.getText())
        .thenAccept(
            liveBid ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      pendingBidRequest = false;
                      renderLiveBid(liveBid);
                      bidAmountField.clear();
                      setLoading(false, "Đặt giá thành công. Đang chờ realtime update...");
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    pendingBidRequest = false;
                    setLoading(false, "Đặt giá thất bại.");
                    AlertUtil.showError(extractMessage(throwable));
                    bidAmountField.requestFocus();
                  });
              return null;
            });
  }

  /** Đăng ký Auto-Bid cho phiên hiện tại. */
  @FXML
  public void handleRegisterAutoBid() {
    if (!bidAllowed) {
      AlertUtil.showWarning("Hãy tham gia phiên đấu giá trước khi bật auto-bid.");
      return;
    }

    pendingAutoBidRequest = true;
    setAutoBidLoading(true, "Đang đăng ký auto-bid...");

    autoBidService
        .registerAutoBid(auctionId, autoBidMaxField.getText())
        .thenAccept(
            registration ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      pendingAutoBidRequest = false;
                      renderAutoBidRegistration(registration, "Đã bật auto-bid.");
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    pendingAutoBidRequest = false;
                    setAutoBidLoading(false, "Đăng ký auto-bid thất bại.");
                    AlertUtil.showError(extractMessage(throwable));
                    autoBidMaxField.requestFocus();
                  });
              return null;
            });
  }

  /** Cập nhật maxBid cho Auto-Bid đang hoạt động. */
  @FXML
  public void handleUpdateAutoBid() {
    if (!bidAllowed) {
      AlertUtil.showWarning("Hãy tham gia phiên đấu giá trước khi cập nhật auto-bid.");
      return;
    }

    pendingAutoBidRequest = true;
    setAutoBidLoading(true, "Đang cập nhật auto-bid...");

    autoBidService
        .updateAutoBid(auctionId, autoBidMaxField.getText())
        .thenAccept(
            registration ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      pendingAutoBidRequest = false;
                      renderAutoBidRegistration(registration, "Đã cập nhật auto-bid.");
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    pendingAutoBidRequest = false;
                    setAutoBidLoading(false, "Cập nhật auto-bid thất bại.");
                    AlertUtil.showError(extractMessage(throwable));
                    autoBidMaxField.requestFocus();
                  });
              return null;
            });
  }

  /** Hủy Auto-Bid của user trong phiên hiện tại. */
  @FXML
  public void handleCancelAutoBid() {
    if (!bidAllowed) {
      AlertUtil.showWarning("Hãy tham gia phiên đấu giá trước khi quản lý auto-bid.");
      return;
    }

    pendingAutoBidRequest = true;
    setAutoBidLoading(true, "Đang hủy auto-bid...");

    autoBidService
        .cancelAutoBid(auctionId)
        .thenRun(
            () ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      pendingAutoBidRequest = false;
                      renderAutoBidInactive("Đã hủy auto-bid.");
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    pendingAutoBidRequest = false;
                    setAutoBidLoading(false, "Hủy auto-bid thất bại.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  /** Tải lại lịch sử bid và biểu đồ. */
  @FXML
  public void handleRefreshHistory() {
    loadBidHistory();
  }

  @Override
  public void onAutoBidRegistered(BidDTOs.AutoBidRegistrationDTO registration) {
    if (pendingAutoBidRequest) {
      return;
    }

    if (!isCurrentAuction(registration == null ? null : registration.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> renderAutoBidRegistration(registration, "Đã tải trạng thái auto-bid."));
  }

  @Override
  public void onUpdateAutoBidSuccess(BidDTOs.AutoBidRegistrationDTO registration) {
    if (pendingAutoBidRequest) {
      return;
    }

    if (!isCurrentAuction(registration == null ? null : registration.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> renderAutoBidRegistration(registration, "Đã cập nhật auto-bid."));
  }

  @Override
  public void onAutoBidFailed(ErrorDTO error) {
    if (pendingAutoBidRequest) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          setAutoBidLoading(false, "Auto-bid thất bại.");
          AlertUtil.showError(error == null ? "Auto-bid thất bại." : error.getMessage());
        });
  }

  @Override
  public void onUpdateAutoBidFailed(ErrorDTO error) {
    if (pendingAutoBidRequest) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          setAutoBidLoading(false, "Cập nhật auto-bid thất bại.");
          AlertUtil.showError(error == null ? "Cập nhật auto-bid thất bại." : error.getMessage());
        });
  }

  @Override
  public void onCancelAutoBidSuccess(String incomingAuctionId) {
    if (pendingAutoBidRequest) {
      return;
    }

    if (!isCurrentAuction(incomingAuctionId)) {
      return;
    }

    FxThreadUtil.runOnFxThread(() -> renderAutoBidInactive("Đã hủy auto-bid."));
  }

  @Override
  public void onAutoBidTriggered(BidDTOs.AutoBidTriggeredDTO notify) {
    if (!isCurrentAuction(notify == null ? null : notify.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          currentPriceRaw = notify.getNewCurrentPrice();
          currentPriceLabel.setText(CurrencyUtil.formatVnd(notify.getNewCurrentPrice()));
          updateMinimumBidHint();

          setAutoBidStatusText(
              "Auto-bid vừa đặt "
                  + CurrencyUtil.formatVnd(notify.getBidAmount())
                  + ". Còn lại trong maxBid: "
                  + CurrencyUtil.formatVnd(notify.getRemainingMaxBid())
                  + ".");

          if (notify.isNowLeading()) {
            leaderLabel.setText("Người dẫn đầu: Bạn");
          }

          setMessage("Auto-bid đã tự đặt giá thay bạn.");
        });
  }

  @Override
  public void onAutoBidExhausted(BidDTOs.AutoBidExhaustedDTO notify) {
    if (!isCurrentAuction(notify == null ? null : notify.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          setAutoBidStatusText(
              "Auto-bid đã đạt giới hạn "
                  + CurrencyUtil.formatVnd(notify.getMaxBid())
                  + ". Giá hiện tại: "
                  + CurrencyUtil.formatVnd(notify.getCurrentPrice())
                  + ". Người dẫn đầu: "
                  + notify.getLeadingBidderUsername()
                  + ".");
          setMessage("Auto-bid đã hết hiệu lực vì maxBid không đủ để vượt giá hiện tại.");
        });
  }

  @Override
  public void onBidUpdate(BidDTOs.BidUpdateDTO update) {
    if (!isCurrentAuction(update == null ? null : update.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(() -> renderBidUpdate(update));
  }

  @Override
  public void onBidReserveNotMet(BidDTOs.BidUpdateDTO update) {
    if (!isCurrentAuction(update == null ? null : update.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(() -> renderBidUpdate(update));
  }

  @Override
  public void onBidChartPointUpdate(BidDTOs.BidChartPointDTO point) {
    if (!isCurrentAuction(point == null ? null : point.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> appendHistoryPoint(BidViewModelMapper.toHistoryPointViewModel(point)));
  }

  @Override
  public void onAuctionExtended(AuctionDTOs.AuctionExtendedDTO dto) {
    if (!isCurrentAuction(dto == null ? null : dto.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          endTimeLabel.setText(DateTimeUtil.formatDateTime(dto.getNewEndTime()));
          auctionEndTime = dto.getNewEndTime();
          startCountdownTimer();
          setMessage("Phiên được gia hạn thêm " + dto.getExtendedBySeconds() + " giây.");
        });
  }

  @Override
  public void onAuctionEnded(AuctionDTOs.AuctionUpdateDTO update) {
    if (!isCurrentAuction(update == null ? null : update.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> renderAuctionClosed(update, "Đã kết thúc", "Phiên đấu giá đã kết thúc."));
  }

  @Override
  public void onAuctionNoWinner(AuctionDTOs.AuctionUpdateDTO update) {
    if (!isCurrentAuction(update == null ? null : update.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () ->
            renderAuctionClosed(
                update,
                "Không có người thắng",
                "Phiên đấu giá đã kết thúc mà chưa có người thắng."));
  }

  @Override
  public void onAuctionReserveNotMet(AuctionDTOs.AuctionUpdateDTO update) {
    if (!isCurrentAuction(update == null ? null : update.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () ->
            renderAuctionClosed(
                update,
                "Chưa đạt giá sàn",
                "Phiên đấu giá đã kết thúc nhưng chưa đạt giá sàn."));
  }

  @Override
  public void onAuctionUpcomingEnd(AuctionDTOs.AuctionUpcomingEndDTO dto) {
    if (!isCurrentAuction(dto == null ? null : dto.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () ->
            setMessage(
                "Phiên sắp kết thúc trong "
                    + Math.max(0L, dto.getRemainingSeconds())
                    + " giây. Bid cuối có thể kích hoạt gia hạn."));
  }

  @Override
  public void onAuctionCanceled(AuctionDTOs.AuctionUpdateDTO update) {
    if (!isCurrentAuction(update == null ? null : update.getAuctionId())) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          stopCountdownTimer();
          if (countdownLabel != null) {
            countdownLabel.setText("--:--");
          }
          statusLabel.setText("Đã hủy");
          bidAllowed = false;
          placeBidButton.setDisable(true);
          syncAutoBidButtons(false);
          setMessage(
              update.getMessage() == null
                  ? "Phiên đấu giá đã bị hủy."
                  : update.getMessage());
        });
  }

  @Override
  public void onPlaceBidFailed(ErrorDTO error) {
    if (pendingBidRequest) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          setLoading(false, "Đặt giá thất bại.");

          if (error != null && ErrorDTO.NOT_JOINED_AUCTION.equals(error.getCode())) {
            joinedAuctionState.forgetJoined(auctionId);
            bidAllowed = false;
            placeBidButton.setDisable(true);
            syncAutoBidButtons(false);
          }

          AlertUtil.showError(error == null ? "Đặt giá thất bại." : error.getMessage());
        });
  }

  private void watchCurrentAuction() {
    setLoading(true, "Đang kết nối phòng đấu giá realtime...");

    watchAuctionService
        .watchAuction(auctionId)
        .thenAccept(
            detail ->
                FxThreadUtil.runOnFxThread(
                    () -> {
                      renderAuctionDetail(detail);
                      loadAutoBidStatus();
                    }))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không vào được phòng đấu giá.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void loadBidHistory() {
    bidHistoryService
        .getBidHistory(auctionId)
        .thenAccept(points -> FxThreadUtil.runOnFxThread(() -> renderBidHistory(points)))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () ->
                      setMessage(
                          "Chưa tải được lịch sử bid: "
                              + extractMessage(throwable)));
              return null;
            });
  }

  private void loadAutoBidStatus() {
    autoBidService
        .getAutoBidStatus(auctionId)
        .thenAccept(
            registration ->
                FxThreadUtil.runOnFxThread(
                    () ->
                        renderAutoBidRegistration(
                            registration,
                            "Đã tải trạng thái auto-bid.")))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () ->
                      setAutoBidStatusText(
                          "Chưa tải được trạng thái auto-bid."));
              return null;
            });
  }

  private void renderAuctionDetail(AuctionDetailViewModel detail) {
    titleLabel.setText(detail.itemName());
    statusLabel.setText(detail.statusText());
    currentPriceLabel.setText(detail.currentPriceText());
    currentPriceRaw = Math.round(detail.currentPrice());
    updateMinimumBidHint();
    leaderLabel.setText(detail.leaderText());
    reserveLabel.setText(detail.reservePriceText());
    endTimeLabel.setText(detail.endTimeText());

    auctionEndTime = detail.rawEndTime();
    startCountdownTimer();

    bidAllowed = detail.liveBiddingAllowed() && joinedAuctionState.hasJoined(auctionId);
    placeBidButton.setDisable(!bidAllowed);
    syncAutoBidButtons(false);

    if (bidAllowed) {
      setLoading(false, "Đã kết nối realtime. Bạn có thể đặt giá cho phiên này.");
    } else {
      setLoading(false, "Đã kết nối realtime ở chế độ theo dõi.");
    }
  }

  private void renderBidUpdate(BidDTOs.BidUpdateDTO update) {
    if (update == null) {
      return;
    }

    renderLiveBid(BidViewModelMapper.toLiveBidViewModel(update));

    if (update.getNewEndTime() != null) {
      auctionEndTime = update.getNewEndTime();
      endTimeLabel.setText(DateTimeUtil.formatDateTime(update.getNewEndTime()));
      startCountdownTimer();
    }
  }

  private void renderAuctionClosed(
      AuctionDTOs.AuctionUpdateDTO update, String statusText, String fallbackMessage) {
    stopCountdownTimer();

    if (countdownLabel != null) {
      countdownLabel.setText("00:00");
    }

    if (statusLabel != null) {
      statusLabel.setText(statusText);
    }

    if (update != null && update.getFinalPrice() > 0) {
      long finalPrice = Math.round(update.getFinalPrice());
      currentPriceRaw = finalPrice;
      currentPriceLabel.setText(CurrencyUtil.formatVnd(finalPrice));
      updateMinimumBidHint();
    }

    if (update != null
        && update.getWinnerUsername() != null
        && !update.getWinnerUsername().isBlank()) {
      leaderLabel.setText("Người thắng: " + update.getWinnerUsername());
    }

    bidAllowed = false;
    placeBidButton.setDisable(true);
    syncAutoBidButtons(false);

    String message =
        update == null || update.getMessage() == null || update.getMessage().isBlank()
            ? fallbackMessage
            : update.getMessage();
    setMessage(message);
  }

  private void renderLiveBid(LiveBidViewModel liveBid) {
    currentPriceLabel.setText(liveBid.currentPriceText());
    currentPriceRaw = liveBid.currentPrice();
    updateMinimumBidHint();
    leaderLabel.setText(liveBid.leaderText());
    reserveLabel.setText(liveBid.reserveText());

    if (!"--".equals(liveBid.endTimeText())) {
      endTimeLabel.setText(liveBid.endTimeText());
    }

    setMessage("Cập nhật lúc " + liveBid.timestampText());
  }

  private void renderBidHistory(List<BidHistoryPointViewModel> points) {
    priceSeries.getData().clear();
    bidHistoryListView.getItems().clear();

    if (points.isEmpty()) {
      setMessage("Phiên này chưa có lịch sử đặt giá.");
      return;
    }

    points.forEach(this::appendHistoryPoint);
    setMessage("Đã tải " + points.size() + " điểm lịch sử bid.");
  }

  private void appendHistoryPoint(BidHistoryPointViewModel point) {
    priceSeries.getData().add(new XYChart.Data<>(point.timestampText(), point.price()));

    if (priceSeries.getData().size() > MAX_CHART_POINTS) {
      priceSeries.getData().remove(0);
    }

    bidHistoryListView
        .getItems()
        .add(
            0,
            point.timestampText()
                + " • "
                + point.bidderUsername()
                + " • "
                + point.priceText());

    currentPriceRaw = point.price();
    currentPriceLabel.setText(CurrencyUtil.formatVnd(point.price()));
    updateMinimumBidHint();
  }

  private void renderAutoBidRegistration(
      BidDTOs.AutoBidRegistrationDTO registration, String message) {
    if (registration == null || !registration.isActive()) {
      renderAutoBidInactive("Bạn chưa bật auto-bid cho phiên này.");
      return;
    }

    if (autoBidMaxField != null) {
      autoBidMaxField.setText(String.valueOf(registration.getMaxBid()));
    }

    setAutoBidStatusText(
        "Đang bật • MaxBid: "
            + CurrencyUtil.formatVnd(registration.getMaxBid())
            + " • Giá hệ thống hiện tại: "
            + CurrencyUtil.formatVnd(registration.getCurrentSystemBid()));

    setAutoBidLoading(false, message);
  }

  private void renderAutoBidInactive(String message) {
    setAutoBidStatusText("Chưa bật auto-bid cho phiên này.");
    setAutoBidLoading(false, message);
  }

  private boolean isCurrentAuction(String incomingAuctionId) {
    return incomingAuctionId != null && incomingAuctionId.equals(auctionId);
  }

  /**
   * Khởi động countdown timer tick mỗi giây trên JavaFX thread. Nếu timer cũ đang chạy, dừng trước
   * khi tạo mới.
   */
  private void startCountdownTimer() {
    stopCountdownTimer();

    if (auctionEndTime == null || countdownLabel == null) {
      return;
    }

    countdownTimer =
        new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> tickCountdown()));
    countdownTimer.setCycleCount(Timeline.INDEFINITE);
    countdownTimer.play();

    tickCountdown();
  }

  /** Tính thời gian còn lại và cập nhật countdownLabel. */
  private void tickCountdown() {
    if (auctionEndTime == null || countdownLabel == null) {
      return;
    }

    Duration remaining = Duration.between(LocalDateTime.now(), auctionEndTime);

    if (remaining.isNegative() || remaining.isZero()) {
      countdownLabel.setText("00:00");
      stopCountdownTimer();
      return;
    }

    long totalSeconds = remaining.getSeconds();
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;
    long seconds = totalSeconds % 60;

    String text =
        hours > 0
            ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
            : String.format("%02d:%02d", minutes, seconds);

    countdownLabel.setText(text);

    if (totalSeconds <= 30) {
      countdownLabel.setStyle(
          "-fx-text-fill: #fb7185;"
              + "-fx-font-weight: 800;"
              + "-fx-effect: dropshadow(gaussian, rgba(251, 113, 133, 0.45), 12, 0.35, 0, 0);");
    } else if (totalSeconds <= 60) {
      countdownLabel.setStyle(
          "-fx-text-fill: #fbbf24;"
              + "-fx-font-weight: 800;"
              + "-fx-effect: dropshadow(gaussian, rgba(251, 191, 36, 0.35), 10, 0.3, 0, 0);");
    } else if (totalSeconds <= 300) {
      countdownLabel.setStyle(
          "-fx-text-fill: #7dd3fc;"
              + "-fx-font-weight: 700;"
              + "-fx-effect: dropshadow(gaussian, rgba(125, 211, 252, 0.25), 8, 0.25, 0, 0);");
    } else {
      countdownLabel.setStyle("");
    }
  }

  /** Dừng và hủy countdown timer. An toàn khi gọi nhiều lần. */
  private void stopCountdownTimer() {
    if (countdownTimer != null) {
      countdownTimer.stop();
      countdownTimer = null;
    }
  }

  private void cleanupRealtimeListener() {
    networkFacade.removeListener(this);
  }

  private void updateMinimumBidHint() {
    if (minimumBidHintLabel == null) {
      return;
    }

    long increment = calculateMinimumIncrement(currentPriceRaw);
    long minimumBidAmount = currentPriceRaw + increment;

    minimumBidHintLabel.setText(
        "Bước giá tối thiểu: "
            + CurrencyUtil.formatVnd(increment)
            + " • Giá đặt thấp nhất: "
            + CurrencyUtil.formatVnd(minimumBidAmount));
  }

  private long calculateMinimumIncrement(long currentPrice) {
    if (currentPrice < TIER_LOW) {
      return INCREMENT_LOW;
    }
    if (currentPrice <= TIER_MID) {
      return INCREMENT_MID;
    }
    return INCREMENT_HIGH;
  }

  private void setLoading(boolean loading, String message) {
    loadingIndicator.setVisible(loading);
    loadingIndicator.setManaged(loading);
    placeBidButton.setDisable(loading || !bidAllowed);
    syncAutoBidButtons(loading);
    setMessage(message);
  }

  private void setAutoBidLoading(boolean loading, String message) {
    syncAutoBidButtons(loading);
    setMessage(message);
  }

  private void syncAutoBidButtons(boolean loading) {
    boolean disableBidAction = loading || !bidAllowed || auctionId == null || auctionId.isBlank();

    if (registerAutoBidButton != null) {
      registerAutoBidButton.setDisable(disableBidAction);
    }

    if (updateAutoBidButton != null) {
      updateAutoBidButton.setDisable(disableBidAction);
    }

    if (cancelAutoBidButton != null) {
      cancelAutoBidButton.setDisable(disableBidAction);
    }
  }

  private void setAutoBidStatusText(String message) {
    if (autoBidStatusLabel != null) {
      autoBidStatusLabel.setText(message == null ? "" : message);
    }
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