package com.group13.auction.service.admin;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Admin: danh sách phiên & hủy phiên.
 */
public final class AdminAuctionService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<AuctionDTOs.AuctionListDTO> allAuctions =
            new SimpleObjectProperty<>();

    public ObjectProperty<AuctionDTOs.AuctionListDTO> allAuctionsProperty() {
        return allAuctions;
    }

    public void loadAllAuctions() {
        network().adminGetAllAuctions();
    }

    public void cancelAuction(AuctionDTOs.AdminCancelAuctionDTO request) {
        network().adminCancelAuction(request);
    }

    @Override
    public void onAdminAllAuctionsReceived(AuctionDTOs.AuctionListDTO list) {
        allAuctions.set(list);
    }

    @Override
    public void onAdminCancelAuctionSuccess(AuctionDTOs.AuctionDTO auction) {
        FxThreadUtil.runOnFxThread(() -> {
            AlertUtil.showInfo("Đã hủy phiên: " + (auction != null ? auction.getId() : ""));
            loadAllAuctions();
        });
    }

    @Override
    public void onAdminCancelAuctionFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }
}
