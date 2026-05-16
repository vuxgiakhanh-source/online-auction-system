package com.group13.auction.ui.controller.admin;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public final class AdminAuctionsController extends BaseController implements PageLifecycle {

    @FXML private TableView<AuctionDTOs.AuctionDTO> table;
    @FXML private TextField cancelReasonField;

    @FXML
    private void initialize() {
        TableColumn<AuctionDTOs.AuctionDTO, String> n = new TableColumn<>("Sản phẩm");
        n.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getItem() != null ? c.getValue().getItem().getName() : c.getValue().getId()));
        TableColumn<AuctionDTOs.AuctionDTO, String> s = new TableColumn<>("Status");
        s.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        table.getColumns().setAll(n, s);
        services().adminAuctionService().allAuctionsProperty().addListener((obs, o, list) -> {
            if (list != null && list.getAuctions() != null) {
                table.setItems(FXCollections.observableArrayList(list.getAuctions()));
            }
        });
    }

    @Override
    public void onShow() {
        services().adminAuctionService().loadAllAuctions();
    }

    @FXML
    private void onCancel() {
        AuctionDTOs.AuctionDTO a = table.getSelectionModel().getSelectedItem();
        if (a == null) {
            return;
        }
        AuctionDTOs.AdminCancelAuctionDTO dto = new AuctionDTOs.AdminCancelAuctionDTO();
        dto.setAuctionId(a.getId());
        dto.setReason(cancelReasonField.getText().isBlank() ? "Admin cancel" : cancelReasonField.getText());
        services().adminAuctionService().cancelAuction(dto);
    }
}
