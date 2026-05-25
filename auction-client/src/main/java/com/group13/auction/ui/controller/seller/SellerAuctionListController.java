package com.group13.auction.ui.controller.seller;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.DialogSoundUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.ui.util.ImageLoader;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.state.ScreenStateKeys;
import java.util.List;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;

/** Controller cho màn danh sách phiên đấu giá của người bán. */
public final class SellerAuctionListController {

    private final SellerAuctionService sellerAuctionService = new SellerAuctionService();

    @FXML private ComboBox<String> statusFilterComboBox;

    @FXML private VBox sellerAuctionContainer;

    @FXML private Label statusLabel;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo bộ lọc và tải danh sách phiên của người bán. */
    @FXML
    public void initialize() {
        setupStatusFilter();
        loadSellerAuctions();
    }

    /** Quay lại dashboard người bán. */
    @FXML
    public void handleBackToSellerDashboard() {
        Navigator.getInstance().goToSellerDashboard();
    }

    /** Mở màn tạo phiên đấu giá. */
    @FXML
    public void handleCreateAuction() {
        Navigator.getInstance().goToCreateAuction();
    }

    /** Tải lại danh sách phiên theo bộ lọc hiện tại. */
    @FXML
    public void handleRefresh() {
        loadSellerAuctions();
    }

    /** Áp dụng bộ lọc trạng thái. */
    @FXML
    public void handleApplyFilter() {
        loadSellerAuctions();
    }

    private void setupStatusFilter() {
        statusFilterComboBox
                .getItems()
                .setAll("Tất cả", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED", "RESERVE_NOT_MET");
        statusFilterComboBox.getSelectionModel().selectFirst();
    }

    private void loadSellerAuctions() {
        setLoading(true, "Đang tải danh sách phiên đấu giá...");
        sellerAuctionContainer.getChildren().clear();

        sellerAuctionService
                .getMyAuctionRows(selectedStatusFilter())
                .thenAccept(rows -> FxThreadUtil.runOnFxThread(() -> renderRows(rows)))
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

    private void renderRows(List<SellerAuctionRowViewModel> rows) {
        sellerAuctionContainer.getChildren().clear();

        if (rows == null || rows.isEmpty()) {
            sellerAuctionContainer.getChildren().add(createEmptyState());
            setLoading(false, "Không có phiên đấu giá phù hợp với bộ lọc hiện tại.");
            return;
        }

        rows.stream().map(this::createAuctionCard).forEach(sellerAuctionContainer.getChildren()::add);
        setLoading(false, "Tìm thấy " + rows.size() + " phiên đấu giá.");
    }

    private void openEditAuction(SellerAuctionRowViewModel row) {
        AppContext.getInstance()
                .getScreenStateStore()
                .put(ScreenStateKeys.SELECTED_SELLER_AUCTION_ROW, row);
        Navigator.getInstance().goToEditAuction();
    }

    private void openSellerAuctionDetail(SellerAuctionRowViewModel row) {
        AppContext.getInstance()
                .getScreenStateStore()
                .put(ScreenStateKeys.SELECTED_SELLER_AUCTION_ROW, row);
        Navigator.getInstance().goToSellerAuctionDetail();
    }

    private VBox createAuctionCard(SellerAuctionRowViewModel row) {
        VBox root = new VBox(16.0);
        root.getStyleClass().add("seller-auction-card");
        root.setPadding(new Insets(20.0));

        HBox topRow = new HBox(14.0);
        topRow.setFillHeight(true);

        VBox thumbnailBox = createThumbnailBox(row.primaryImageUrl());

        VBox titleBox = new VBox(5.0);

        Label titleLabel = new Label(row.itemName());
        titleLabel.getStyleClass().add("seller-card-title");

        Label metaLabel = new Label(row.categoryText() + " • Mã phiên: " + row.auctionId());
        metaLabel.getStyleClass().add("seller-muted-text");

        titleBox.getChildren().addAll(titleLabel, metaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusBadge = new Label(row.statusText());
        statusBadge.getStyleClass().add("seller-status-badge");

        topRow.getChildren().addAll(thumbnailBox, titleBox, spacer, statusBadge);

        HBox metricRow = new HBox(14.0);
        metricRow
                .getChildren()
                .addAll(
                        createMetric("Giá hiện tại", row.currentPriceText()),
                        createMetric("Giá khởi điểm", row.startingPriceText()),
                        createMetric("Giá sàn", row.reservePriceText()),
                        createMetric("Lượt truy cập", row.viewerCountText()));

        HBox timeRow = new HBox(14.0);
        timeRow
                .getChildren()
                .addAll(
                        createMetric("Bắt đầu", row.startTimeText()),
                        createMetric("Kết thúc", row.endTimeText()));

        HBox actionRow = new HBox(10.0);

        Button detailButton = new Button("Xem chi tiết");
        detailButton.getStyleClass().add("seller-secondary-button");
        detailButton.setOnAction(event -> openSellerAuctionDetail(row));

        Button editButton = new Button("Gia hạn thời gian");
        editButton.getStyleClass().add("seller-secondary-button");
        editButton.setDisable(!row.editable());
        editButton.setOnAction(event -> openEditAuction(row));

        Button cancelButton = new Button("Yêu cầu hủy");
        cancelButton.getStyleClass().add("seller-danger-button");
        cancelButton.setDisable(!row.cancelRequestAllowed());
        cancelButton.setOnAction(event -> requestCancelAuction(row));

        actionRow.getChildren().addAll(detailButton, editButton, cancelButton);

        root.getChildren().addAll(topRow, metricRow, timeRow, actionRow);
        return root;
    }

    private VBox createThumbnailBox(String imageUrl) {
        VBox thumbnailBox = new VBox();
        thumbnailBox.getStyleClass().add("seller-thumbnail-box");
        thumbnailBox.setAlignment(Pos.CENTER);
        thumbnailBox.setMinWidth(122.0);
        thumbnailBox.setPrefWidth(122.0);
        thumbnailBox.setMaxWidth(122.0);

        if (imageUrl == null || imageUrl.isBlank()) {
            Label placeholder = new Label("Chưa có ảnh");
            placeholder.getStyleClass().add("seller-thumbnail-placeholder");
            thumbnailBox.getChildren().add(placeholder);
            return thumbnailBox;
        }

        ImageView thumbnail = new ImageView();
        thumbnail.getStyleClass().add("seller-thumbnail-image");
        thumbnail.setFitWidth(112.0);
        thumbnail.setFitHeight(84.0);
        thumbnail.setPreserveRatio(true);
        thumbnail.setSmooth(true);

        ImageLoader.load(thumbnail, imageUrl);
        thumbnailBox.getChildren().add(thumbnail);
        return thumbnailBox;
    }

    private VBox createMetric(String title, String value) {
        VBox metric = new VBox(4.0);
        metric.getStyleClass().add("seller-metric-box");
        metric.setMinWidth(0.0);
        metric.setPrefWidth(170.0);
        metric.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(metric, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("seller-muted-text");

        String safeValue = value == null || value.isBlank() ? "--" : value;
        Label valueLabel = new Label(safeValue);
        valueLabel.getStyleClass().add("seller-metric-value");
        valueLabel.setWrapText(true);
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        valueLabel.setTooltip(new Tooltip(safeValue));

        metric.getChildren().addAll(titleLabel, valueLabel);
        return metric;
    }

    private Label createEmptyState() {
        Label emptyLabel = new Label("Chưa có phiên đấu giá phù hợp với bộ lọc hiện tại.");
        emptyLabel.getStyleClass().add("seller-empty-state");
        return emptyLabel;
    }

    private void requestCancelAuction(SellerAuctionRowViewModel row) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Yêu cầu hủy phiên đấu giá");
        dialog.setHeaderText(null);
        dialog.setContentText("Nhập lý do hủy phiên:");
        DialogSoundUtil.installButtonClickSound(dialog);

        dialog
                .showAndWait()
                .ifPresent(
                        reason -> {
                            if (reason.isBlank()) {
                                AlertUtil.showWarning("Lý do hủy phiên không được để trống.");
                                return;
                            }

                            sendCancelRequest(row.auctionId(), reason);
                        });
    }

    private void sendCancelRequest(String auctionId, String reason) {
        setLoading(true, "Đang gửi yêu cầu hủy phiên...");

        sellerAuctionService
                .requestCancelAuction(auctionId, reason)
                .thenAccept(
                        ignored ->
                                FxThreadUtil.runOnFxThread(
                                        () -> {
                                            AlertUtil.showInfo("Yêu cầu hủy phiên đã được gửi thành công.");
                                            loadSellerAuctions();
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

    private String selectedStatusFilter() {
        String value = statusFilterComboBox.getValue();
        return value == null || "Tất cả".equals(value) ? null : value;
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
        return message == null || message.isBlank() ? "Không xử lý được yêu cầu." : message;
    }
}
