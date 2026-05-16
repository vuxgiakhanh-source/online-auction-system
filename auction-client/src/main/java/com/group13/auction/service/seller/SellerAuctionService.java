package com.group13.auction.service.seller;



import com.group13.auction.common.dto.auction.AuctionDTOs;

import com.group13.auction.common.dto.core.ErrorDTO;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;

import com.group13.auction.core.state.ScreenKeys;

import com.group13.auction.network.client.session.ClientEventListener;

import com.group13.auction.service.base.NetworkService;

import com.group13.auction.ui.util.AlertUtil;

import com.group13.auction.ui.util.ErrorMessages;

import com.group13.auction.ui.util.FxThreadUtil;

import javafx.beans.property.SimpleStringProperty;

import javafx.beans.property.StringProperty;



/**

 * Luồng Seller: tạo / sửa / yêu cầu hủy phiên.

 */

public final class SellerAuctionService extends NetworkService implements ClientEventListener {



    private final StringProperty lastStatusMessage = new SimpleStringProperty();



    public StringProperty lastStatusMessageProperty() {

        return lastStatusMessage;

    }



    public void createAuction(AuctionDTOs.CreateAuctionRequestDTO request) {

        lastStatusMessage.set("Đang tạo phiên đấu giá...");

        network().createAuction(request);

    }



    public void updateAuction(AuctionDTOs.UpdateAuctionDTO request) {

        lastStatusMessage.set("Đang cập nhật phiên...");

        network().updateAuction(request);

    }



    public void requestCancel(AuctionDTOs.CancelAuctionRequestDTO request) {

        lastStatusMessage.set("Đang gửi yêu cầu hủy...");

        network().requestCancelAuction(request);

    }



    @Override

    public void onAuctionCreated(AuctionDTOs.AuctionDTO auction) {

        if (auction == null) {

            return;

        }

        lastStatusMessage.set("Tạo phiên thành công.");

        FxThreadUtil.runOnFxThread(() -> {

            AlertUtil.showInfo("Tạo phiên đấu giá thành công: " + auction.getId());

            AppContext.getInstance().getScreenStateStore().put(
                    ScreenKeys.SELECTED_AUCTION_ID, auction.getId());

            Navigator.getInstance().goToSellerAuctionList();

        });

    }



    @Override

    public void onAuctionCreateFailed(ErrorDTO error) {

        String msg = ErrorMessages.from(error);

        lastStatusMessage.set(msg);

        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(msg));

    }



    @Override

    public void onAuctionUpdated(AuctionDTOs.AuctionDTO auction) {

        lastStatusMessage.set("Cập nhật phiên thành công.");

        FxThreadUtil.runOnFxThread(() -> {

            AlertUtil.showInfo("Cập nhật phiên thành công.");

            if (auction != null) {

                AppContext.getInstance().getScreenStateStore().put(
                    ScreenKeys.SELECTED_AUCTION_ID, auction.getId());

            }

            Navigator.getInstance().goToSellerAuctionList();

        });

    }



    @Override

    public void onAuctionUpdateFailed(ErrorDTO error) {

        String msg = ErrorMessages.from(error);

        lastStatusMessage.set(msg);

        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(msg));

    }



    @Override

    public void onCancelAuctionRequestSuccess(String auctionId) {

        lastStatusMessage.set("Đã gửi yêu cầu hủy phiên.");

        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Yêu cầu hủy phiên đã được gửi."));

    }



    @Override

    public void onCancelAuctionRequestFailed(ErrorDTO error) {

        String msg = ErrorMessages.from(error);

        lastStatusMessage.set(msg);

        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(msg));

    }

}

