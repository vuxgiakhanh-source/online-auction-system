package com.group13.auction.ui.controller.seller;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public final class EditAuctionController extends BaseController implements PageLifecycle {

    @FXML private Label auctionIdLabel;
    @FXML private TextField reserveField;
    @FXML private DatePicker endDate;
    @FXML private Label statusLabel;

    private String auctionId;

    @FXML
    private void initialize() {
        services().sellerAuctionService().lastStatusMessageProperty().addListener((obs, o, msg) -> {
            if (msg != null) {
                statusLabel.setText(msg);
            }
        });
        services().auctionCommandService().selectedAuctionProperty().addListener((obs, o, a) -> {
            if (a != null) {
                populate(a);
            }
        });
    }

    @Override
    public void onShow() {
        auctionId = screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse(null);
        if (auctionId == null) {
            AlertUtil.showWarning("Chưa chọn phiên cần sửa.");
            return;
        }
        auctionIdLabel.setText(auctionId);
        services().auctionCommandService().loadAuctionDetail(auctionId);
    }

    @FXML
    private void onSave() {
        if (auctionId == null) {
            return;
        }
        AuctionDTOs.UpdateAuctionDTO dto = new AuctionDTOs.UpdateAuctionDTO();
        dto.setAuctionId(auctionId);
        String reserveText = reserveField.getText().trim();
        if (!reserveText.isEmpty()) {
            try {
                dto.setNewReservePrice(Double.parseDouble(reserveText));
            } catch (NumberFormatException e) {
                AlertUtil.showWarning("Giá dự trữ không hợp lệ.");
                return;
            }
        }
        if (endDate.getValue() != null) {
            dto.setNewEndTime(endDate.getValue().atTime(23, 59));
        }
        if (dto.getNewEndTime() == null && dto.getNewReservePrice() == null) {
            AlertUtil.showWarning("Nhập ít nhất một trường cần thay đổi.");
            return;
        }
        services().sellerAuctionService().updateAuction(dto);
    }

    @FXML
    private void onCancel() {
        navigator().goToSellerAuctionList();
    }

    private void populate(AuctionDTOs.AuctionDTO a) {
        reserveField.setText(String.valueOf((long) a.getReservePrice()));
        if (a.getEndTime() != null) {
            endDate.setValue(a.getEndTime().toLocalDate());
        }
    }
}
