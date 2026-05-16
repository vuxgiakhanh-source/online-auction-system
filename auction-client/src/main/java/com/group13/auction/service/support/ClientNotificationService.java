package com.group13.auction.service.support;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Gom server push (notify/update) — UI bind {@link #messages()} để hiển thị hộp thông báo.
 */
public final class ClientNotificationService implements ClientEventListener {

    private final ObservableList<String> messages = FXCollections.observableArrayList();

    public ObservableList<String> messages() {
        return messages;
    }

    private void push(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        FxThreadUtil.runOnFxThread(() -> {
            messages.add(0, text);
            if (messages.size() > 100) {
                messages.remove(messages.size() - 1);
            }
        });
    }

    @Override
    public void onBidUpdate(BidDTOs.BidUpdateDTO update) {
        if (update != null) {
            push("Giá mới: " + update.getNewCurrentPrice());
        }
    }

    @Override
    public void onAuctionUpcomingEnd(AuctionDTOs.AuctionUpcomingEndDTO dto) {
        if (dto != null) {
            push("Phiên sắp kết thúc — còn " + dto.getRemainingSeconds() + " giây");
        }
    }

    @Override
    public void onAuctionExtended(AuctionDTOs.AuctionExtendedDTO dto) {
        push("Phiên được gia hạn (anti-sniping)");
    }

    @Override
    public void onPaymentExpiredNotify(PaymentDTOs.PaymentExpiredDTO dto) {
        push("Hết hạn thanh toán — cọc có thể bị tịch thu");
    }

    @Override
    public void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {
        push("Bạn nhận được đề nghị Second Chance");
    }

    @Override
    public void onAccountBanned(RatingDTOs.AccountBannedDTO dto) {
        String reason = dto != null ? dto.getReason() : "";
        FxThreadUtil.runOnFxThread(() -> AlertUtil.showError("Tài khoản bị khóa: " + reason));
    }

    @Override
    public void onAccountSuspended(RatingDTOs.AccountSuspendedDTO dto) {
        push("Tài khoản bị tạm khóa");
    }

    @Override
    public void onFraudDetectedNotify(AdminDTOs.FraudDetectedDTO dto) {
        push("Cảnh báo gian lận: " + (dto != null ? dto.getDescription() : ""));
    }

    @Override
    public void onSystemAnnouncement(AdminDTOs.SystemAnnouncementDTO dto) {
        if (dto != null) {
            push(dto.getMessage());
        }
    }

    @Override
    public void onServerShutdown(AdminDTOs.ServerShutdownDTO dto) {
        push("Server sắp bảo trì");
    }

    @Override
    public void onQualityReportReceivedNotify(ReportDTOs.QualityReportDTO report) {
        push("Bạn nhận báo cáo chất lượng từ người mua");
    }
}
