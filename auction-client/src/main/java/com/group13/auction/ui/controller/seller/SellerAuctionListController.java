package com.group13.auction.ui.controller.seller;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public final class SellerAuctionListController extends BaseController implements PageLifecycle {

    @FXML private TableView<AuctionDTOs.AuctionDTO> table;

    @FXML
    private void initialize() {
        TableColumn<AuctionDTOs.AuctionDTO, String> n = new TableColumn<>("Tên");
        n.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getItem() != null ? c.getValue().getItem().getName() : ""));
        TableColumn<AuctionDTOs.AuctionDTO, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        table.getColumns().setAll(n, statusCol);
        services().auctionCommandService().auctionListProperty().addListener((obs, o, list) -> {
            String myId = session().getCurrentSession().map(sess -> sess.getUserId()).orElse("");
            if (list != null && list.getAuctions() != null) {
                var mine = list.getAuctions().stream()
                        .filter(a -> a.getItem() != null && myId.equals(a.getItem().getSellerId()))
                        .toList();
                table.setItems(FXCollections.observableArrayList(mine));
            }
        });
    }

    @Override
    public void onShow() {
        services().auctionCommandService().loadAuctionList();
    }
}
