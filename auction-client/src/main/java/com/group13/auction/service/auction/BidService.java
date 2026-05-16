package com.group13.auction.service.auction;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Đặt giá thủ công (PLACE_BID).
 */
public final class BidService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<BidDTOs.BidUpdateDTO> lastUpdate = new SimpleObjectProperty<>();

    public ObjectProperty<BidDTOs.BidUpdateDTO> lastUpdateProperty() {
        return lastUpdate;
    }

    public void placeBid(String auctionId, long amount) {
        network().placeBid(auctionId, amount);
    }

    @Override
    public void onPlaceBidSuccess(BidDTOs.BidResultDTO result) {
        // Cập nhật realtime qua onBidUpdate — không cần alert mỗi lần bid thành công.
    }

    @Override
    public void onPlaceBidFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onBidUpdate(BidDTOs.BidUpdateDTO update) {
        lastUpdate.set(update);
    }

    @Override
    public void onBidReserveNotMet(BidDTOs.BidUpdateDTO update) {
        lastUpdate.set(update);
    }
}
