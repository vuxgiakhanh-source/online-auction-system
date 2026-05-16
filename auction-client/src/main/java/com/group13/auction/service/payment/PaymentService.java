package com.group13.auction.service.payment;



import com.group13.auction.common.dto.core.ErrorDTO;

import com.group13.auction.common.dto.payment.PaymentDTOs;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.network.client.session.ClientEventListener;

import com.group13.auction.service.base.NetworkService;

import com.group13.auction.ui.util.AlertUtil;

import com.group13.auction.ui.util.ErrorMessages;

import com.group13.auction.ui.util.FormatUtil;

import com.group13.auction.ui.util.FxThreadUtil;

import javafx.beans.property.ObjectProperty;

import javafx.beans.property.SimpleObjectProperty;

import javafx.beans.property.SimpleStringProperty;

import javafx.beans.property.StringProperty;



/**

 * Luồng PAYMENT & SECOND_CHANCE sau đấu giá.

 */

public final class PaymentService extends NetworkService implements ClientEventListener {



    private final ObjectProperty<PaymentDTOs.SecondChanceOfferDTO> pendingOffer =

            new SimpleObjectProperty<>();

    private final ObjectProperty<PaymentDTOs.PaymentResultDTO> lastResult =

            new SimpleObjectProperty<>();

    private final StringProperty lastStatusMessage = new SimpleStringProperty();



    public ObjectProperty<PaymentDTOs.SecondChanceOfferDTO> pendingOfferProperty() {

        return pendingOffer;

    }



    public ObjectProperty<PaymentDTOs.PaymentResultDTO> lastResultProperty() {

        return lastResult;

    }



    public StringProperty lastStatusMessageProperty() {

        return lastStatusMessage;

    }



    public void payForAuction(String auctionId) {

        lastStatusMessage.set("Đang xử lý thanh toán...");

        network().requestPayment(auctionId);

    }



    public void acceptSecondChance(String auctionId) {

        lastStatusMessage.set("Đang chấp nhận Second Chance...");

        network().acceptSecondChance(auctionId);

    }



    public void declineSecondChance(String auctionId) {

        lastStatusMessage.set("Đang từ chối Second Chance...");

        network().declineSecondChance(auctionId);

    }



    @Override

    public void onPaymentSuccess(PaymentDTOs.PaymentResultDTO result) {

        lastResult.set(result);

        if (result != null) {

            String msg = "Thanh toán thành công — giá cuối: "

                    + FormatUtil.currency(result.getFinalPrice())

                    + ", số dư mới: " + FormatUtil.currency(result.getNewBalance());

            lastStatusMessage.set(msg);

            FxThreadUtil.runOnFxThread(() -> {

                AlertUtil.showInfo(msg);

                AppContext.getInstance().services().walletService().refreshBalance();

            });

        }

    }



    @Override

    public void onPaymentFailed(ErrorDTO error) {

        String msg = ErrorMessages.from(error);

        lastStatusMessage.set(msg);

        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError(msg));

    }



    @Override

    public void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {

        pendingOffer.set(offer);

    }



    @Override

    public void onSecondChanceAcceptSuccess(PaymentDTOs.PaymentResultDTO result) {

        pendingOffer.set(null);

        onPaymentSuccess(result);

    }



    @Override

    public void onSecondChanceDeclineSuccess() {

        pendingOffer.set(null);

        lastStatusMessage.set("Đã từ chối Second Chance.");

        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo("Đã từ chối đề nghị Second Chance."));

    }



    @Override

    public void onSecondChanceExpiredNotify(String auctionId) {

        pendingOffer.set(null);

        lastStatusMessage.set("Second Chance đã hết hạn.");

    }

}

