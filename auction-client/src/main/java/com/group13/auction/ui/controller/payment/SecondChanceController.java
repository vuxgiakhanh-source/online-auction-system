package com.group13.auction.ui.controller.payment;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FormatUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class SecondChanceController extends BaseController implements PageLifecycle {

    @FXML private Label offerLabel;

    @FXML
    private void initialize() {
        services().paymentService().pendingOfferProperty().addListener((obs, o, offer) -> render(offer));
    }

    @Override
    public void onShow() {
        PaymentDTOs.SecondChanceOfferDTO offer = services().paymentService().pendingOfferProperty().get();
        render(offer);
    }

    @FXML
    private void onAccept() {
        String id = currentAuctionId();
        if (id != null) {
            services().paymentService().acceptSecondChance(id);
        }
    }

    @FXML
    private void onDecline() {
        String id = currentAuctionId();
        if (id != null) {
            services().paymentService().declineSecondChance(id);
        }
    }

    private String currentAuctionId() {
        PaymentDTOs.SecondChanceOfferDTO offer = services().paymentService().pendingOfferProperty().get();
        if (offer != null && offer.getAuctionId() != null) {
            return offer.getAuctionId();
        }
        return screenState().get(ScreenKeys.SELECTED_AUCTION_ID, String.class).orElse(null);
    }

    private void render(PaymentDTOs.SecondChanceOfferDTO offer) {
        if (offer == null) {
            offerLabel.setText("Chưa có đề nghị Second Chance.");
            return;
        }
        offerLabel.setText("Phiên: " + offer.getAuctionId()
                + " | Giá: " + FormatUtil.currency(offer.getOfferPrice())
                + " | Hạn: " + FormatUtil.dateTime(offer.getDeadline()));
    }
}
