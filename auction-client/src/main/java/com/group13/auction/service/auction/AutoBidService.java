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
 * Auto-bid: đăng ký / cập nhật / hủy / trạng thái.
 */
public final class AutoBidService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<BidDTOs.AutoBidRegistrationDTO> registration =
            new SimpleObjectProperty<>();

    public ObjectProperty<BidDTOs.AutoBidRegistrationDTO> registrationProperty() {
        return registration;
    }

    public void register(String auctionId, long maxBid) {
        network().registerAutoBid(auctionId, maxBid);
    }

    public void update(String auctionId, long maxBid) {
        network().updateAutoBid(auctionId, maxBid);
    }

    public void cancel(String auctionId) {
        network().cancelAutoBid(auctionId);
    }

    public void loadStatus(String auctionId) {
        network().getAutoBidStatus(auctionId);
    }

    @Override
    public void onAutoBidRegistered(BidDTOs.AutoBidRegistrationDTO reg) {
        registration.set(reg);
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Đăng ký auto-bid thành công."));
    }

    @Override
    public void onUpdateAutoBidSuccess(BidDTOs.AutoBidRegistrationDTO reg) {
        registration.set(reg);
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Cập nhật auto-bid thành công."));
    }

    @Override
    public void onCancelAutoBidSuccess(String auctionId) {
        registration.set(null);
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Đã hủy auto-bid."));
    }

    @Override
    public void onAutoBidFailed(ErrorDTO error) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(ErrorMessages.from(error)));
    }

    @Override
    public void onAutoBidTriggered(BidDTOs.AutoBidTriggeredDTO notify) {}

    @Override
    public void onAutoBidExhausted(BidDTOs.AutoBidExhaustedDTO notify) {
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showWarning("Auto-bid đã hết hạn mức."));
    }
}
