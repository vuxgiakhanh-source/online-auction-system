package com.group13.auction.ui.controller.auction;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;

/** Controller cho màn danh sách phiên đấu giá. */
public final class AuctionListController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 30;

    private final AuctionQueryService auctionQueryService = new AuctionQueryService();

    @FXML
    private ComboBox<String> statusFilterComboBox;

    @FXML
    private ComboBox<String> sortByComboBox;

    @FXML
    private VBox auctionCardContainer;

    @FXML
    private Label statusLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    /** Khởi tạo combobox và tải danh sách phiên đấu giá. */
    @FXML
    public void initialize() {
        setupFilters();
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

    /** Áp dụng filter/sort đang chọn. */
    @FXML
    public void handleApplyFilter() {
        loadAuctions();
    }

    private void setupFilters() {
        statusFilterComboBox
                .getItems()
                .setAll("Tất cả", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
        statusFilterComboBox.getSelectionModel().selectFirst();

        sortByComboBox.getItems().setAll("START_TIME", "VIEWER_COUNT", "CURRENT_PRICE");
        sortByComboBox.getSelectionModel().selectFirst();
    }

    private void loadAuctions() {
        setLoading(true, "Đang tải danh sách phiên đấu giá...");
        auctionCardContainer.getChildren().clear();

        auctionQueryService
                .getAuctionCards(selectedStatusFilter(), selectedSortBy(), DEFAULT_PAGE, DEFAULT_PAGE_SIZE)
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
        setLoading(false, "Tìm thấy " + cards.size() + " phiên đấu giá.");
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
        topRow.setFillHeight(false);

        VBox thumbnailBox = createThumbnailBox(card.primaryImageUrl());

        VBox titleBox = new VBox(5.0);
        Label titleLabel = new Label(card.itemName());
        titleLabel.getStyleClass().add("auction-card-title");

        Label metaLabel = new Label(card.categoryText() + " • " + card.sellerText());
        metaLabel.getStyleClass().add("auction-muted-text");

        titleBox.getChildren().addAll(titleLabel, metaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Label statusBadge = new Label(card.statusText());
        statusBadge.getStyleClass().add("auction-status-badge");

        topRow.getChildren().addAll(thumbnailBox, titleBox, spacer, statusBadge);

        HBox priceRow = new HBox(18.0);
        priceRow
                .getChildren()
                .addAll(
                        createMetric("Giá hiện tại", card.currentPriceText()),
                        createMetric("Giá khởi điểm", card.startingPriceText()),
                        createMetric("Còn lại", card.remainingTimeText()),
                        createMetric("Lượt xem", card.viewerCountText()));

        HBox actionRow = new HBox(10.0);

        Button detailButton = new Button("Xem chi tiết");
        detailButton.getStyleClass().add("auction-secondary-button");
        detailButton.setOnAction(event -> openAuctionDetail(card.auctionId()));

        Button liveButton = new Button("Vào phòng live");
        liveButton.getStyleClass().add("auction-primary-button");
        liveButton.setDisable(!card.joinable());
        liveButton.setOnAction(event -> openLiveBidding(card.auctionId()));

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

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("auction-muted-text");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("auction-metric-value");

        metric.getChildren().addAll(titleLabel, valueLabel);
        return metric;
    }

    private Label createEmptyState() {
        Label emptyLabel = new Label("Chưa có phiên đấu giá phù hợp với bộ lọc hiện tại.");
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

    private String selectedStatusFilter() {
        String value = statusFilterComboBox.getValue();
        return value == null || "Tất cả".equals(value) ? null : value;
    }

    private String selectedSortBy() {
        return sortByComboBox.getValue();
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