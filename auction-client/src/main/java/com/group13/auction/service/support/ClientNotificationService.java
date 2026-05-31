package com.group13.auction.service.support;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
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
 * <p>Service này chỉ hiển thị thông báo phía client. Dữ liệu và nghiệp vụ vẫn được xử lý bởi
 * server.
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
  public void onOutbidNotify(BidDTOs.OutbidNotifyDTO notify) {
    if (notify == null) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () ->
            AlertUtil.showWarning(
                "Bạn vừa bị vượt giá!\n\n"
                    + "Sản phẩm: "
                    + fallback(notify.getAuctionItemName())
                    + "\n"
                    + "Người vượt: "
                    + fallback(notify.getNewBidderUsername())
                    + "\n"
                    + "Giá mới: "
                    + CurrencyUtil.formatVnd(notify.getNewCurrentPrice())
                    + "\n"
                    + "Giá của bạn: "
                    + CurrencyUtil.formatVnd(notify.getPreviousPrice())));
  }

  @Override
  public void onAutoBidExhausted(BidDTOs.AutoBidExhaustedDTO notify) {
    if (notify == null) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () ->
            AlertUtil.showWarning(
                "Auto-bid đã hết hiệu lực (vượt quá max bid).\n\n"
                    + "Mã phiên: "
                    + fallback(notify.getAuctionId())
                    + "\n"
                    + "Max bid của bạn: "
                    + CurrencyUtil.formatVnd(notify.getMaxBid())
                    + "\n"
                    + "Giá hiện tại: "
                    + CurrencyUtil.formatVnd(notify.getCurrentPrice())
                    + "\n"
                    + "Người dẫn đầu: "
                    + fallback(notify.getLeadingBidderUsername())));
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
                "Second Chance Offer của bạn đã hết hạn.\n"
                    + "Mã phiên đấu giá: "
                    + fallback(auctionId)));
  }

  @Override
  public void onAuctionEnded(AuctionDTOs.AuctionUpdateDTO update) {
    if (update == null) {
      return;
    }

    FxThreadUtil.runOnFxThread(() -> showAuctionEndedPopup(update));
  }

  @Override
  public void onAuctionNoWinner(AuctionDTOs.AuctionUpdateDTO update) {
    if (update == null) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () ->
            AlertUtil.showInfo(
                prefixAuctionId(update)
                    + "Phiên đấu giá đã kết thúc mà không có ai đặt giá.\n"
                    + "Tiền cọc (nếu có) sẽ được hoàn trả theo quy định hệ thống."));
  }

  @Override
  public void onAuctionReserveNotMet(AuctionDTOs.AuctionUpdateDTO update) {
    if (update == null) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          StringBuilder message =
              new StringBuilder(prefixAuctionId(update))
                  .append("Phiên đấu giá đã kết thúc nhưng chưa đạt giá sàn (reserve price).");
          long highestPrice = Math.round(update.getFinalPrice());
          if (highestPrice > 0) {
            message.append("\nGiá cao nhất: ").append(CurrencyUtil.formatVnd(highestPrice));
          }
          message.append("\nTiền cọc (nếu có) sẽ được hoàn trả theo quy định hệ thống.");
          AlertUtil.showInfo(message.toString());
        });
  }

  @Override
  public void onAuctionCanceled(AuctionDTOs.AuctionUpdateDTO update) {
    if (update == null) {
      return;
    }

    FxThreadUtil.runOnFxThread(
        () -> {
          String reason =
              update.getCancelReason() != null && !update.getCancelReason().isBlank()
                  ? update.getCancelReason()
                  : update.getMessage();
          StringBuilder message =
              new StringBuilder(prefixAuctionId(update)).append("Phiên đấu giá đã bị hủy.");
          if (reason != null && !reason.isBlank()) {
            message.append("\nLý do: ").append(reason);
          }
          message.append("\nTiền cọc (nếu có) sẽ được hoàn trả theo quy định hệ thống.");
          AlertUtil.showWarning(message.toString());
        });
  }

  @Override
  public void onLeaveAuctionCompleted(
      String auctionId,
      boolean depositForfeited,
      long forfeitedAmount,
      boolean ratingPenalized,
      long newAvailableBalance) {
    StringBuilder message =
        new StringBuilder("Bạn đã thoát phiên đấu giá.\n\nMã phiên: ")
            .append(fallback(auctionId))
            .append("\n");

    if (depositForfeited) {
      message
          .append("Tiền cọc không được hoàn lại: ")
          .append(CurrencyUtil.formatVnd(forfeitedAmount))
          .append(".\n");
      if (ratingPenalized) {
        message.append("Điểm uy tín của bạn có thể đã bị trừ theo quy định.\n");
      }
      FxThreadUtil.runOnFxThread(
          () ->
              AlertUtil.showWarning(
                  message
                      .append("Số dư khả dụng mới: ")
                      .append(CurrencyUtil.formatVnd(newAvailableBalance))
                      .toString()));
    } else {
      message.append("Tiền cọc đã được hoàn lại.\n");
      FxThreadUtil.runOnFxThread(
          () ->
              AlertUtil.showInfo(
                  message
                      .append("Số dư khả dụng mới: ")
                      .append(CurrencyUtil.formatVnd(newAvailableBalance))
                      .toString()));
    }
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

  private void showAuctionEndedPopup(AuctionDTOs.AuctionUpdateDTO update) {
    String currentUserId = resolveCurrentUserId();
    String winnerId = update.getWinnerId();
    long finalPrice = Math.round(update.getFinalPrice());
    boolean currentUserWon = Objects.equals(currentUserId, winnerId);

    StringBuilder message = new StringBuilder(prefixAuctionId(update));

    if (currentUserWon) {
      message.append("Chúc mừng! Bạn đã thắng phiên đấu giá này.\n");
      if (finalPrice > 0) {
        message.append("Giá chốt: ").append(CurrencyUtil.formatVnd(finalPrice)).append("\n");
      }
      message.append("Vui lòng thanh toán phần còn lại trong vòng 24 giờ để hoàn tất giao dịch.");
      AlertUtil.showInfo(message.toString());
      return;
    }

    message.append("Phiên đấu giá đã kết thúc.");
    if (winnerId != null && !winnerId.isBlank()) {
      message.append("\nNgười thắng: ").append(fallback(update.getWinnerUsername()));
      if (finalPrice > 0) {
        message.append("\nGiá chốt: ").append(CurrencyUtil.formatVnd(finalPrice));
      }
      if (currentUserId != null) {
        message.append(
            "\n\nTiền cọc của bạn (nếu có) sẽ được hoàn theo quy định hệ thống.");
      }
    }

    AlertUtil.showInfo(message.toString());
  }

  private String prefixAuctionId(AuctionDTOs.AuctionUpdateDTO update) {
    return "Mã phiên: " + fallback(update.getAuctionId()) + "\n\n";
  }

  private String resolveCurrentUserId() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(UserSession::getUserId)
        .orElse(null);
  }

  private String fallback(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }
}
