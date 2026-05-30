package com.group13.auction.ui.controller.auction;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.core.state.ScreenStateKeys;
import com.group13.auction.service.auction.AuctionQueryService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.ui.util.ImageLoader;
import com.group13.auction.viewmodel.auction.AuctionCardViewModel;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller cho màn danh sách phiên đấu giá. */
public final class AuctionListController {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_PAGE_SIZE = 30;

  private static final String STATUS_ALL_LABEL = "Tất cả";

  private static final String SCOPE_ALL_LABEL = "Tất cả phiên";
  private static final String SCOPE_MY_AUCTIONS_LABEL = "Phiên của tôi";
  private static final String SCOPE_JOINED_LABEL = "Phiên đã tham gia";

  private static final String SCOPE_ALL_VALUE = "ALL";
  private static final String SCOPE_MY_AUCTIONS_VALUE = "OWNED";
  private static final String SCOPE_JOINED_VALUE = "JOINED";

  private static final String SORT_BY_START_TIME_LABEL = "Thời gian bắt đầu";
  private static final String SORT_BY_ACCESS_COUNT_LABEL = "Lượt truy cập";
  private static final String SORT_BY_CURRENT_PRICE_LABEL = "Giá hiện tại";

  private final AuctionQueryService auctionQueryService = new AuctionQueryService();

  @FXML private TextField productSearchField;

  @FXML private HBox scopeFilterGroup;

  @FXML private ComboBox<String> scopeFilterComboBox;

  @FXML private ComboBox<String> statusFilterComboBox;

  @FXML private ComboBox<String> sortByComboBox;

  @FXML private VBox auctionCardContainer;

  @FXML private Label statusLabel;

  @FXML private ProgressIndicator loadingIndicator;

  /** Khởi tạo bộ lọc, ô tìm kiếm và tải danh sách phiên đấu giá. */
  @FXML
  public void initialize() {
    setupFilters();
    setupSearchField();
    loadAuctions();
  }

  /** Quay lại dashboard chính. */
  @FXML
  public void handleBackToHome() {
    Navigator.getInstance().goToMainLayout();
  }

  /** Tải lại danh sách phiên đấu giá theo filter hiện tại. */
  @FXML
  public void handleRefresh() {
    loadAuctions();
  }

  /** Áp dụng filter/sort/tìm kiếm đang chọn. */
  @FXML
  public void handleApplyFilter() {
    loadAuctions();
  }

  private void setupFilters() {
    setupScopeFilter();

    statusFilterComboBox
        .getItems()
        .setAll(STATUS_ALL_LABEL, "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
    statusFilterComboBox.getSelectionModel().select("RUNNING");

    sortByComboBox
        .getItems()
        .setAll(SORT_BY_START_TIME_LABEL, SORT_BY_ACCESS_COUNT_LABEL, SORT_BY_CURRENT_PRICE_LABEL);
    sortByComboBox.getSelectionModel().select(SORT_BY_ACCESS_COUNT_LABEL);
  }

  private void setupScopeFilter() {
    scopeFilterGroup.setVisible(true);
    scopeFilterGroup.setManaged(true);

    if (isCurrentUserSeller()) {
      scopeFilterComboBox
          .getItems()
          .setAll(SCOPE_ALL_LABEL, SCOPE_MY_AUCTIONS_LABEL, SCOPE_JOINED_LABEL);
    } else {
      scopeFilterComboBox.getItems().setAll(SCOPE_ALL_LABEL, SCOPE_JOINED_LABEL);
    }

    scopeFilterComboBox.getSelectionModel().selectFirst();
  }

  private void setupSearchField() {
    productSearchField.setOnAction(event -> handleApplyFilter());
  }

  private void loadAuctions() {
    setLoading(true, "Đang tải danh sách phiên đấu giá...");
    auctionCardContainer.getChildren().clear();

    auctionQueryService
        .getAuctionCards(
            selectedKeyword(),
            selectedStatusFilter(),
            selectedScopeFilter(),
            selectedSortBy(),
            DEFAULT_PAGE,
            DEFAULT_PAGE_SIZE)
        .thenAccept(cards -> FxThreadUtil.runOnFxThread(() -> renderAuctionCards(cards)))
        .exceptionally(
            throwable -> {
              FxThreadUtil.runOnFxThread(
                  () -> {
                    setLoading(false, "Không tải được danh sách phiên đấu giá.");
                    AlertUtil.showError(extractMessage(throwable));
                  });
              return null;
            });
  }

  private void renderAuctionCards(List<AuctionCardViewModel> cards) {
    setLoading(false, buildResultMessage(cards.size()));
    auctionCardContainer.getChildren().clear();

    if (cards.isEmpty()) {
      auctionCardContainer.getChildren().add(createEmptyState());
      return;
    }

    for (AuctionCardViewModel card : cards) {
      auctionCardContainer.getChildren().add(createAuctionCard(card));
    }
  }

  private VBox createAuctionCard(AuctionCardViewModel card) {
    VBox root = new VBox(10.0);
    root.getStyleClass().add("auction-card");
    root.setPadding(new Insets(18.0, 20.0, 18.0, 20.0));

    HBox topRow = new HBox(12.0);
    topRow.setAlignment(Pos.TOP_LEFT);
    topRow.setFillHeight(false);

    VBox thumbnailBox = createThumbnailBox(card.primaryImageUrl());

    VBox titleBox = new VBox(5.0);
    HBox.setHgrow(titleBox, Priority.ALWAYS);
    titleBox.setMinWidth(0);
    titleBox.setMaxWidth(Double.MAX_VALUE);

    Label titleLabel = new Label(card.itemName());
    titleLabel.getStyleClass().add("auction-card-title");
    titleLabel.setWrapText(true);
    titleLabel.setMaxWidth(Double.MAX_VALUE);

    Label metaLabel = new Label(card.categoryText() + " • " + card.sellerText());
    metaLabel.getStyleClass().add("auction-muted-text");
    metaLabel.setWrapText(true);
    metaLabel.setMaxWidth(Double.MAX_VALUE);

    titleBox.getChildren().addAll(titleLabel, metaLabel);

    Label statusBadge = new Label(card.statusText());
    statusBadge.getStyleClass().add("auction-status-badge");
    statusBadge.setMinWidth(Region.USE_PREF_SIZE);
    HBox.setHgrow(statusBadge, Priority.NEVER);

    topRow.getChildren().addAll(thumbnailBox, titleBox, statusBadge);

    HBox priceRow = new HBox(18.0);
    priceRow.setFillHeight(false);
    priceRow
        .getChildren()
        .addAll(
            createMetric("Giá hiện tại", card.currentPriceText()),
            createMetric("Giá khởi điểm", card.startingPriceText()),
            createMetric("Còn lại", card.remainingTimeText()),
            createMetric("Lượt truy cập", card.viewerCountText()));

    HBox actionRow = new HBox(10.0);

    Button detailButton = new Button("Xem chi tiết");
    detailButton.getStyleClass().add("auction-secondary-button");
    detailButton.setOnAction(event -> openAuctionDetail(card.auctionId()));

    Button liveButton = new Button(card.joinable() ? "Vào phòng live" : "Xem lịch sử");
    liveButton.getStyleClass().add("auction-primary-button");
    liveButton.setDisable(false);
    if (card.joinable()) {
      liveButton.setOnAction(event -> openLiveBidding(card.auctionId()));
    } else {
      liveButton.setOnAction(event -> openLiveBiddingReadOnly(card.auctionId()));
    }

    actionRow.getChildren().addAll(detailButton, liveButton);
    root.getChildren().addAll(topRow, priceRow, actionRow);

    return root;
  }

  private VBox createThumbnailBox(String imageUrl) {
    VBox thumbnailBox = new VBox();
    thumbnailBox.getStyleClass().add("auction-thumbnail-box");
    thumbnailBox.setAlignment(Pos.CENTER);
    thumbnailBox.setMinWidth(122.0);
    thumbnailBox.setPrefWidth(122.0);
    thumbnailBox.setMaxWidth(122.0);

    if (imageUrl == null || imageUrl.isBlank()) {
      Label placeholder = new Label("Chưa có ảnh");
      placeholder.getStyleClass().add("auction-thumbnail-placeholder");
      thumbnailBox.getChildren().add(placeholder);
      return thumbnailBox;
    }

    ImageView thumbnail = new ImageView();
    thumbnail.getStyleClass().add("auction-thumbnail-image");
    thumbnail.setFitWidth(112.0);
    thumbnail.setFitHeight(84.0);
    thumbnail.setPreserveRatio(true);
    thumbnail.setSmooth(true);

    ImageLoader.load(thumbnail, imageUrl);
    thumbnailBox.getChildren().add(thumbnail);
    return thumbnailBox;
  }

  private VBox createMetric(String title, String value) {
    VBox metric = new VBox(3.0);
    metric.getStyleClass().add("auction-metric-box");
    metric.setMinWidth(0.0);
    metric.setPrefWidth(170.0);
    metric.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(metric, Priority.ALWAYS);

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("auction-muted-text");

    String safeValue = value == null || value.isBlank() ? "--" : value;
    Label valueLabel = new Label(safeValue);
    valueLabel.getStyleClass().add("auction-metric-value");
    valueLabel.setWrapText(true);
    valueLabel.setMaxWidth(Double.MAX_VALUE);
    valueLabel.setTooltip(new Tooltip(safeValue));

    metric.getChildren().addAll(titleLabel, valueLabel);
    return metric;
  }

  private Label createEmptyState() {
    Label emptyLabel = new Label("Không có phiên đấu giá phù hợp với bộ lọc hiện tại.");
    emptyLabel.getStyleClass().add("auction-empty-state");
    return emptyLabel;
  }

  private void openAuctionDetail(String auctionId) {
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.SELECTED_AUCTION_ID, auctionId);
    Navigator.getInstance().goToAuctionDetail();
  }

  private void openLiveBidding(String auctionId) {
    AppContext.getInstance()
        .getScreenStateStore()
        .put(ScreenStateKeys.SELECTED_AUCTION_ID, auctionId);
    Navigator.getInstance().goToLiveBidding();
  }

  private void openLiveBiddingReadOnly(String auctionId) {
    Navigator.getInstance().goToLiveBiddingReadOnly(auctionId);
  }

  private String selectedKeyword() {
    String keyword = productSearchField.getText();
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  private String selectedScopeFilter() {
    String selectedLabel = scopeFilterComboBox.getValue();
    if (SCOPE_MY_AUCTIONS_LABEL.equals(selectedLabel)) {
      return SCOPE_MY_AUCTIONS_VALUE;
    }
    if (SCOPE_JOINED_LABEL.equals(selectedLabel)) {
      return SCOPE_JOINED_VALUE;
    }
    return SCOPE_ALL_VALUE;
  }

  private boolean isCurrentUserSeller() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(UserSession::isSeller)
        .orElse(false);
  }

  private String selectedStatusFilter() {
    String value = statusFilterComboBox.getValue();
    return value == null || STATUS_ALL_LABEL.equals(value) ? null : value;
  }

  private String selectedSortBy() {
    String selectedLabel = sortByComboBox.getValue();
    if (SORT_BY_ACCESS_COUNT_LABEL.equals(selectedLabel)) {
      return "VIEWER_COUNT";
    }
    if (SORT_BY_CURRENT_PRICE_LABEL.equals(selectedLabel)) {
      return "CURRENT_PRICE";
    }
    return "START_TIME";
  }

  private String buildResultMessage(int count) {
    String keyword = selectedKeyword();
    String scopeSuffix = selectedScopeMessageSuffix();

    if (keyword == null) {
      return "Tìm thấy " + count + " phiên đấu giá" + scopeSuffix + ".";
    }

    return "Tìm thấy " + count + " phiên đấu giá" + scopeSuffix + " cho \"" + keyword + "\".";
  }

  private String selectedScopeMessageSuffix() {
    String selectedLabel = scopeFilterComboBox.getValue();
    if (SCOPE_MY_AUCTIONS_LABEL.equals(selectedLabel)) {
      return " trong phiên của tôi";
    }
    if (SCOPE_JOINED_LABEL.equals(selectedLabel)) {
      return " trong phiên đã tham gia";
    }
    return "";
  }

  private void setLoading(boolean loading, String message) {
    loadingIndicator.setVisible(loading);
    loadingIndicator.setManaged(loading);
    statusLabel.setText(message);
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
