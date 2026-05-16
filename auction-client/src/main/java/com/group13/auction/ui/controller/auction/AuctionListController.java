package com.group13.auction.ui.controller.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.FormatUtil;
import com.group13.auction.ui.util.ImageLoader;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;

public final class AuctionListController extends BaseController implements PageLifecycle {

    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<AuctionDTOs.AuctionDTO> auctionTable;

    @FXML
    private void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "OPEN", "RUNNING", "FINISHED", "CANCELED", "PAID"));
        statusFilter.getSelectionModel().select(0);
        statusFilter.setOnAction(e -> refresh());

        TableColumn<AuctionDTOs.AuctionDTO, AuctionDTOs.AuctionDTO> thumbCol = new TableColumn<>("");
        thumbCol.setPrefWidth(56);
        thumbCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        thumbCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            {
                imageView.setFitHeight(40);
                imageView.setFitWidth(40);
                imageView.setPreserveRatio(true);
            }
            @Override
            protected void updateItem(AuctionDTOs.AuctionDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.getItem() == null || !item.getItem().hasImages()) {
                    setGraphic(null);
                } else {
                    ImageLoader.load(imageView, item.getItem().getImageUrls().get(0));
                    setGraphic(imageView);
                }
            }
        });

        TableColumn<AuctionDTOs.AuctionDTO, String> nameCol = new TableColumn<>("Sản phẩm");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getItem() != null ? c.getValue().getItem().getName() : "—"));
        nameCol.setPrefWidth(200);

        TableColumn<AuctionDTOs.AuctionDTO, String> priceCol = new TableColumn<>("Giá hiện tại");
        priceCol.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.currency(c.getValue().getCurrentPrice())));

        TableColumn<AuctionDTOs.AuctionDTO, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.auctionStatus(c.getValue().getStatus())));

        TableColumn<AuctionDTOs.AuctionDTO, String> endCol = new TableColumn<>("Kết thúc");
        endCol.setCellValueFactory(c -> new SimpleStringProperty(
                FormatUtil.dateTime(c.getValue().getEndTime())));

        auctionTable.getColumns().setAll(thumbCol, nameCol, priceCol, statusCol, endCol);
        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        services().auctionCommandService().auctionListProperty().addListener((obs, o, list) -> {
            if (list != null && list.getAuctions() != null) {
                auctionTable.setItems(FXCollections.observableArrayList(list.getAuctions()));
            }
        });

        auctionTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                AuctionDTOs.AuctionDTO row = auctionTable.getSelectionModel().getSelectedItem();
                if (row != null) {
                    navigator().openAuctionDetail(row.getId());
                }
            }
        });
    }

    @Override
    public void onShow() {
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        AuctionDTOs.AuctionListRequestDTO req = new AuctionDTOs.AuctionListRequestDTO();
        String filter = statusFilter.getSelectionModel().getSelectedItem();
        if (filter != null && !filter.equals("Tất cả")) {
            req.setStatusFilter(filter);
        }
        services().auctionCommandService().loadAuctionList(req);
    }
}
