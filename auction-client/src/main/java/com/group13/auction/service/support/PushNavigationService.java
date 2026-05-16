package com.group13.auction.service.support;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.state.ScreenKeys;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FormatUtil;
import com.group13.auction.ui.util.FxThreadUtil;

/**
 * Điều hướng tự động khi server push sự kiện quan trọng (Second Chance, thắng phiên, v.v.).
 */
public final class PushNavigationService implements ClientEventListener {

    private static boolean isLoggedIn() {
        return AppContext.getInstance().getSessionManager().isLoggedIn();
    }

    @Override
    public void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {
        if (offer == null || !isLoggedIn()) {
            return;
        }
        FxThreadUtil.runOnFxThread(() -> {
            AppContext.getInstance().getScreenStateStore().put(
                    ScreenKeys.SELECTED_AUCTION_ID, offer.getAuctionId());
            String name = offer.getAuctionItemName() != null ? offer.getAuctionItemName() : offer.getAuctionId();
            AlertUtil.showInfo("Bạn nhận đề nghị Second Chance: " + name
                    + " — " + FormatUtil.currency(offer.getOfferPrice()));
            Navigator.getInstance().goToSecondChance();
        });
    }

    @Override
    public void onAuctionEnded(AuctionDTOs.AuctionUpdateDTO update) {
        if (update == null || update.getWinnerId() == null) {
            return;
        }
        AppContext.getInstance().getSessionManager().getCurrentSession().ifPresent(session -> {
            if (!session.getUserId().equals(update.getWinnerId())) {
                return;
            }
            FxThreadUtil.runOnFxThread(() -> {
                AppContext.getInstance().getScreenStateStore().put(
                        ScreenKeys.SELECTED_AUCTION_ID, update.getAuctionId());
                String msg = "Chúc mừng! Bạn thắng phiên đấu giá với giá "
                        + FormatUtil.currency(update.getFinalPrice()) + ". Mở màn thanh toán?";
                if (AlertUtil.confirm(msg)) {
                    Navigator.getInstance().goToPayment();
                }
            });
        });
    }

    @Override
    public void onPaymentExpiredNotify(PaymentDTOs.PaymentExpiredDTO dto) {
        if (dto == null) {
            return;
        }
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showWarning(
                "Hết hạn thanh toán phiên " + dto.getAuctionId()
                        + ". Cọc có thể bị tịch thu: "
                        + FormatUtil.currency(dto.getDepositForfeited())));
    }

    @Override
    public void onDepositRefundNotify(PaymentDTOs.DepositRefundDTO dto) {
        if (dto == null) {
            return;
        }
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo(
                "Hoàn cọc " + FormatUtil.currency(dto.getRefundAmount())
                        + " — số dư mới: " + FormatUtil.currency(dto.getNewBalance())));
    }

    @Override
    public void onAccountBanned(RatingDTOs.AccountBannedDTO dto) {
        FxThreadUtil.runOnFxThread(() -> {
            AlertUtil.showError("Tài khoản bị khóa: " + (dto != null ? dto.getReason() : ""));
            Navigator.getInstance().logout();
        });
    }

    @Override
    public void onQualityReportApprovedNotify(ReportDTOs.QualityReportResultDTO result) {
        if (result == null) {
            return;
        }
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showInfo(
                "Báo cáo được duyệt. Hoàn tiền: " + FormatUtil.currency(result.getRefundedAmount())));
    }
}
