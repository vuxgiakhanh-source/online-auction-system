package com.group13.auction.service.support;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.payment.SecondChanceRealtimeService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service lắng nghe các realtime notification quan trọng từ server.
 *
 * <p>Service này chỉ hiển thị thông báo phía client. Dữ liệu và nghiệp vụ vẫn được xử lý bởi server.
 */
public final class ClientNotificationService implements ClientEventListener {

    private static final ClientNotificationService INSTANCE =
            new ClientNotificationService(ClientNetworkFacade.getDefault());

    private final ClientNetworkFacade networkFacade;
    private final SecondChanceRealtimeService secondChanceRealtimeService =
            SecondChanceRealtimeService.getInstance();
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ClientNotificationService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /**
     * Lấy singleton notification service.
     *
     * @return singleton service
     */
    public static ClientNotificationService getInstance() {
        return INSTANCE;
    }

    /** Bắt đầu lắng nghe realtime notification từ server. */
    public void start() {
        if (started.compareAndSet(false, true)) {
            secondChanceRealtimeService.start();
            networkFacade.addListener(this);
        }
    }

    /** Dừng lắng nghe realtime notification từ server. */
    public void stop() {
        if (started.compareAndSet(true, false)) {
            networkFacade.removeListener(this);
        }
    }

    @Override
    public void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {
        if (offer == null) {
            return;
        }

        FxThreadUtil.runOnFxThread(
                () ->
                        AlertUtil.showInfo(
                                "Bạn vừa nhận được một Second Chance Offer.\n\n"
                                        + "Sản phẩm: "
                                        + fallback(offer.getAuctionItemName())
                                        + "\n"
                                        + "Giá đề nghị: "
                                        + CurrencyUtil.formatVnd(offer.getOfferPrice())
                                        + "\n"
                                        + "Tiền cọc yêu cầu: "
                                        + CurrencyUtil.formatVnd(offer.getDepositRequired())
                                        + "\n"
                                        + "Hạn phản hồi: "
                                        + DateTimeUtil.formatDateTime(offer.getDeadline())
                                        + "\n\n"
                                        + "Hãy mở Trung tâm thông báo để chấp nhận hoặc từ chối."));
    }

    @Override
    public void onSecondChanceExpiredNotify(String auctionId) {
        FxThreadUtil.runOnFxThread(
                () ->
                        AlertUtil.showWarning(
                                "Một Second Chance Offer đã hết hạn.\n"
                                        + "Mã phiên đấu giá: "
                                        + fallback(auctionId)));
    }

    @Override
    public void onPaymentCompletedNotify(PaymentDTOs.PaymentResultDTO result) {
        if (result == null) {
            return;
        }

        FxThreadUtil.runOnFxThread(
                () ->
                        AlertUtil.showInfo(
                                "Một phiên đấu giá đã được thanh toán thành công.\n\n"
                                        + "Mã phiên đấu giá: "
                                        + fallback(result.getAuctionId())
                                        + "\n"
                                        + "Giá chốt: "
                                        + CurrencyUtil.formatVnd(result.getFinalPrice())
                                        + "\n"
                                        + "Thời điểm thanh toán: "
                                        + DateTimeUtil.formatDateTime(result.getPaidAt())));
    }

    @Override
    public void onPaymentExpiredNotify(PaymentDTOs.PaymentExpiredDTO dto) {
        if (dto == null) {
            return;
        }

        FxThreadUtil.runOnFxThread(
                () ->
                        AlertUtil.showWarning(
                                "Thời hạn thanh toán của một phiên đấu giá đã hết.\n\n"
                                        + "Mã phiên đấu giá: "
                                        + fallback(dto.getAuctionId())
                                        + "\n"
                                        + "Tiền cọc bị xử lý: "
                                        + CurrencyUtil.formatVnd(dto.getDepositForfeited())
                                        + "\n"
                                        + "Điểm phạt đánh giá: "
                                        + dto.getRatingPenalty()));
    }

    @Override
    public void onDepositRefundNotify(PaymentDTOs.DepositRefundDTO dto) {
        if (dto == null) {
            return;
        }

        FxThreadUtil.runOnFxThread(
                () ->
                        AlertUtil.showInfo(
                                "Tiền cọc của bạn đã được hoàn lại.\n\n"
                                        + "Mã phiên đấu giá: "
                                        + fallback(dto.getAuctionId())
                                        + "\n"
                                        + "Số tiền hoàn: "
                                        + CurrencyUtil.formatVnd(dto.getRefundAmount())
                                        + "\n"
                                        + "Số dư mới: "
                                        + CurrencyUtil.formatVnd(dto.getNewBalance())));
    }

    @Override
    public void onDepositForfeitedNotify(PaymentDTOs.DepositForfeitedDTO dto) {
        if (dto == null) {
            return;
        }

        FxThreadUtil.runOnFxThread(
                () ->
                        AlertUtil.showWarning(
                                "Tiền cọc của bạn đã bị xử lý do quá hạn thanh toán.\n\n"
                                        + "Mã phiên đấu giá: "
                                        + fallback(dto.getAuctionId())
                                        + "\n"
                                        + "Số tiền bị xử lý: "
                                        + CurrencyUtil.formatVnd(dto.getForfeitedAmount())
                                        + "\n"
                                        + "Số dư mới: "
                                        + CurrencyUtil.formatVnd(dto.getNewBalance())));
    }

    private String fallback(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }
}