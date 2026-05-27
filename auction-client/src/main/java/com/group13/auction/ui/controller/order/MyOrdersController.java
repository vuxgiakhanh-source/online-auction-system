package com.group13.auction.ui.controller.order;

import com.group13.auction.common.dto.payment.ConfirmItemReceivedResultDTO;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.order.WonOrderService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.ui.util.ImageLoader;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller cho màn Đơn hàng của tôi. */
public final class MyOrdersController {

  private final WonOrderService wonOrderService = new WonOrderService();

  @FXML private VBox orderContainer;
  @FXML private Label statusLabel;
  @FXML private ProgressIndicator loadingIndicator;
  @FXML private Button refreshButton;
  @FXML private Button backButton;

  /** Khởi tạo màn và tải danh sách đơn hàng đã thắng. */
  @FXML
  private void initialize() {
    loadOrders();
  }

  @FXML
  private void handleRefresh() {
    loadOrders();
  }

  @FXML
  private void handleOpenMyQualityReports() {
    Navigator.getInstance().goToMyQualityReports();
  }

  @FXML
  private void handleBackToMain() {
    Navigator.getInstance().goToMainLayout();
  }

  private void loadOrders() {
    setLoading(true, "Đang tải đơn hàng...");
    orderContainer.getChildren().clear();

    wonOrderService
        .getMyWonOrders()
        .thenAccept(orders -> FxThreadUtil.runOnFxThread(() -> renderOrders(orders)))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không tải được đơn hàng.");
                    orderContainer.getChildren().setAll(createErrorState());
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void renderOrders(List<WonOrderViewModel> orders) {
    List<WonOrderViewModel> safeOrders = orders == null ? List.of() : orders;
    setLoading(false, "Tìm thấy " + safeOrders.size() + " đơn hàng.");
    orderContainer.getChildren().clear();

    if (safeOrders.isEmpty()) {
      orderContainer.getChildren().add(createEmptyState());
      return;
    }

    for (WonOrderViewModel order : safeOrders) {
      orderContainer.getChildren().add(createOrderCard(order));
    }
  }

  private VBox createOrderCard(WonOrderViewModel order) {
    VBox card = new VBox(12.0);
    card.getStyleClass().add("order-card");
    card.setPadding(new Insets(18.0, 20.0, 18.0, 20.0));

    HBox topRow = new HBox(14.0);
    topRow.setAlignment(Pos.CENTER_LEFT);

    VBox thumbnailBox = createThumbnailBox(order.primaryImageUrl());

    VBox infoBox = new VBox(6.0);
    HBox.setHgrow(infoBox, Priority.ALWAYS);

    Label titleLabel = new Label(order.itemName());
    titleLabel.getStyleClass().add("order-title");
    titleLabel.setWrapText(true);

    Label sellerLabel = new Label(order.sellerText());
    sellerLabel.getStyleClass().add("order-muted-text");

    Label idLabel = new Label("Mã phiên: " + order.auctionId());
    idLabel.getStyleClass().add("order-muted-text");

    infoBox.getChildren().addAll(titleLabel, sellerLabel, idLabel);

    Label statusBadge = new Label(order.statusText());
    statusBadge.getStyleClass().add("order-status-badge");

    topRow.getChildren().addAll(thumbnailBox, infoBox, statusBadge);

    HBox bottomRow = new HBox(12.0);
    bottomRow.setAlignment(Pos.CENTER_LEFT);

    VBox priceBox = createMetric("Giá thắng", order.winningPriceText());
    VBox hintBox = createHintBox(order);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button detailButton = new Button("Xem chi tiết");
    detailButton.getStyleClass().add("secondary-button");
    detailButton.setOnAction(event -> openAuctionDetail(order));

    Button primaryActionButton = createPrimaryActionButton(order);

    bottomRow.getChildren().addAll(priceBox, hintBox, spacer, detailButton);
    if (primaryActionButton != null) {
      bottomRow.getChildren().add(primaryActionButton);
    }

    card.getChildren().addAll(topRow, bottomRow);
    return card;
  }

  private VBox createThumbnailBox(String imageUrl) {
    VBox thumbnailBox = new VBox();
    thumbnailBox.getStyleClass().add("order-thumbnail-box");
    thumbnailBox.setAlignment(Pos.CENTER);
    thumbnailBox.setMinWidth(128.0);
    thumbnailBox.setPrefWidth(128.0);
    thumbnailBox.setMaxWidth(128.0);

    if (imageUrl == null || imageUrl.isBlank()) {
      Label placeholder = new Label("Chưa có ảnh");
      placeholder.getStyleClass().add("order-thumbnail-placeholder");
      thumbnailBox.getChildren().add(placeholder);
      return thumbnailBox;
    }

    ImageView imageView = new ImageView();
    imageView.getStyleClass().add("order-thumbnail-image");
    imageView.setFitWidth(116.0);
    imageView.setFitHeight(86.0);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);

    ImageLoader.load(imageView, imageUrl);
    thumbnailBox.getChildren().add(imageView);
    return thumbnailBox;
  }

  private VBox createMetric(String title, String value) {
    VBox metric = new VBox(4.0);
    metric.getStyleClass().add("order-metric-box");
    metric.setMinWidth(0.0);
    metric.setPrefWidth(190.0);
    metric.setMaxWidth(Double.MAX_VALUE);

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("order-muted-text");

    String safeValue = value == null || value.isBlank() ? "--" : value;
    Label valueLabel = new Label(safeValue);
    valueLabel.getStyleClass().add("order-metric-value");
    valueLabel.setWrapText(true);
    valueLabel.setMaxWidth(Double.MAX_VALUE);
    valueLabel.setTooltip(new Tooltip(safeValue));

    metric.getChildren().addAll(titleLabel, valueLabel);
    return metric;
  }

  private VBox createHintBox(WonOrderViewModel order) {
    VBox hintBox = new VBox(4.0);
    hintBox.getStyleClass().add("order-hint-box");

    Label hintLabel = new Label(order.actionHintText());
    hintLabel.getStyleClass().add("order-muted-text");
    hintLabel.setWrapText(true);

    hintBox.getChildren().add(hintLabel);

    if (order.canConfirmReceipt() && !isPlaceholder(order.confirmReceiptDeadlineText())) {
      Label deadlineLabel = new Label("Hạn xác nhận: " + order.confirmReceiptDeadlineText());
      deadlineLabel.getStyleClass().add("order-muted-text");
      hintBox.getChildren().add(deadlineLabel);
    }

    if (order.canSubmitReport() && !isPlaceholder(order.reportDeadlineText())) {
      Label deadlineLabel = new Label("Hạn gửi báo cáo: " + order.reportDeadlineText());
      deadlineLabel.getStyleClass().add("order-muted-text");
      hintBox.getChildren().add(deadlineLabel);
    }

    return hintBox;
  }

  private Button createPrimaryActionButton(WonOrderViewModel order) {
    if (order.canPay()) {
      Button payButton = new Button("Thanh toán");
      payButton.getStyleClass().add("primary-button");
      payButton.setOnAction(event -> payForOrder(order));
      return payButton;
    }

    if (order.canConfirmReceipt()) {
      Button confirmButton = new Button("Đã nhận hàng");
      confirmButton.getStyleClass().add("primary-button");
      confirmButton.setOnAction(event -> confirmItemReceived(order));
      return confirmButton;
    }

    if (order.canSubmitReport()) {
      Button reportButton = new Button("Gửi báo cáo");
      reportButton.getStyleClass().add("primary-button");
      reportButton.setOnAction(event -> openQualityReport(order));
      return reportButton;
    }

    return null;
  }

  private Label createEmptyState() {
    Label emptyLabel = new Label("Bạn chưa có đơn hàng nào từ phiên đấu giá đã thắng.");
    emptyLabel.getStyleClass().add("order-empty-state");
    return emptyLabel;
  }

  private Label createErrorState() {
    Label errorLabel = new Label("Không tải được đơn hàng. Vui lòng thử lại sau.");
    errorLabel.getStyleClass().add("order-empty-state");
    return errorLabel;
  }

  private void openAuctionDetail(WonOrderViewModel order) {
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.SELECTED_AUCTION_ID, order.auctionId());
    Navigator.getInstance().goToAuctionDetail();
  }

  private void payForOrder(WonOrderViewModel order) {
    setLoading(true, "Đang xử lý thanh toán...");

    wonOrderService
        .payForOrder(order.auctionId())
        .whenComplete(
            (result, throwable) ->
                FxThreadUtil.runOnFxThread(() -> handlePaymentResult(result, throwable)));
  }

  private void handlePaymentResult(PaymentResultViewModel result, Throwable throwable) {
    if (throwable != null) {
      setLoading(false, "Không thanh toán được đơn hàng.");
      AlertUtil.showError(extractMessage(throwable));
      return;
    }

    String message =
        result == null
            ? "Thanh toán thành công."
            : "Thanh toán thành công. Số dư hiện tại: " + result.newBalanceText();

    showStatus(message);
    loadOrders();
  }

  private void confirmItemReceived(WonOrderViewModel order) {
    setLoading(true, "Đang xác nhận nhận hàng...");

    wonOrderService
        .confirmItemReceived(order.auctionId())
        .whenComplete(
            (result, throwable) ->
                FxThreadUtil.runOnFxThread(() -> handleConfirmResult(result, throwable)));
  }

  private void handleConfirmResult(ConfirmItemReceivedResultDTO result, Throwable throwable) {
    if (throwable != null) {
      setLoading(false, "Không xác nhận được trạng thái nhận hàng.");
      AlertUtil.showError(extractMessage(throwable));
      return;
    }

    String message =
        result != null && result.isCanSubmitReport()
            ? "Đã xác nhận nhận hàng. Bạn có thể gửi báo cáo nếu sản phẩm có vấn đề."
            : "Đã xác nhận nhận hàng.";

    showStatus(message);
    loadOrders();
  }

  private void openQualityReport(WonOrderViewModel order) {
    if (!order.canSubmitReport()) {
      showStatus("Vui lòng xác nhận đã nhận hàng trước khi gửi báo cáo.");
      return;
    }

    AppContext.getInstance().getScreenStateStore().put(ScreenStateKeys.SELECTED_WON_ORDER, order);
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.SELECTED_AUCTION_ID, order.auctionId());
    Navigator.getInstance().goToQualityReport();
  }

  private void setLoading(boolean loading, String message) {
    if (loadingIndicator != null) {
      loadingIndicator.setVisible(loading);
      loadingIndicator.setManaged(loading);
    }
    if (refreshButton != null) {
      refreshButton.setDisable(loading);
    }
    if (backButton != null) {
      backButton.setDisable(loading);
    }
    showStatus(message);
  }

  private void showStatus(String message) {
    if (statusLabel != null) {
      statusLabel.setText(message == null ? "" : message);
    }
  }

  private String extractMessage(Throwable throwable) {
    Throwable current = throwable;
    if (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    String message = current == null ? null : current.getMessage();
    return message == null || message.isBlank() ? "Không tải được dữ liệu." : message;
  }

  private boolean isPlaceholder(String value) {
    return value == null || value.isBlank() || "--".equals(value.trim());
  }
}
