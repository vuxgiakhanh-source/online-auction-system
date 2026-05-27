package com.group13.auction.service.payment;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.mapper.PaymentViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.viewmodel.payment.SecondChanceOfferViewModel;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime store cho Second Chance Offer phía client.
 *
 * <p>Server hiện có realtime event {@code SECOND_CHANCE_OFFER_NOTIFY}, nhưng chưa có API lấy danh
 * sách pending Second Chance Offer. Vì vậy service này chỉ lưu các offer mà client nhận được trong
 * phiên chạy hiện tại.
 */
public final class SecondChanceRealtimeService implements ClientEventListener {

  private static final SecondChanceRealtimeService INSTANCE =
      new SecondChanceRealtimeService(ClientNetworkFacade.getDefault());

  private final ClientNetworkFacade networkFacade;
  private final ConcurrentHashMap<String, PaymentDTOs.SecondChanceOfferDTO> pendingOffers =
      new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean(false);

  private SecondChanceRealtimeService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Lấy singleton runtime service.
   *
   * @return singleton second chance realtime service
   */
  public static SecondChanceRealtimeService getInstance() {
    return INSTANCE;
  }

  /** Đăng ký listener nhận realtime event từ server. */
  public void start() {
    if (started.compareAndSet(false, true)) {
      networkFacade.addListener(this);
    }
  }

  /**
   * Lấy danh sách Second Chance Offer đang pending trong phiên chạy hiện tại.
   *
   * @return danh sách offer đã format cho UI
   */
  public List<SecondChanceOfferViewModel> getPendingOffers() {
    removeExpiredOffers();

    return pendingOffers.values().stream()
        .sorted(Comparator.comparing(this::deadlineOrMax))
        .map(PaymentViewModelMapper::toSecondChanceOfferViewModel)
        .toList();
  }

  /**
   * Xóa offer theo mã phiên đấu giá.
   *
   * @param auctionId mã phiên đấu giá
   */
  public void removeOfferByAuctionId(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return;
    }

    pendingOffers.remove(auctionId);
  }

  /** Xóa toàn bộ offer runtime, thường dùng khi logout. */
  public void clear() {
    pendingOffers.clear();
  }

  @Override
  public void onSecondChanceOffer(PaymentDTOs.SecondChanceOfferDTO offer) {
    if (offer == null || offer.getAuctionId() == null || offer.getAuctionId().isBlank()) {
      return;
    }

    pendingOffers.put(offer.getAuctionId(), offer);
  }

  @Override
  public void onSecondChanceAcceptSuccess(PaymentDTOs.PaymentResultDTO result) {
    if (result != null) {
      removeOfferByAuctionId(result.getAuctionId());
    }
  }

  @Override
  public void onSecondChanceAcceptFailed(ErrorDTO error) {
    // UI sẽ hiển thị lỗi từ request future. Không cần xử lý thêm ở runtime store.
  }

  @Override
  public void onSecondChanceDeclineSuccess() {
    // Packet success hiện không trả auctionId, controller sẽ xóa offer đang chọn sau khi request
    // thành công.
  }

  @Override
  public void onSecondChanceExpiredNotify(String auctionId) {
    removeOfferByAuctionId(auctionId);
  }

  private void removeExpiredOffers() {
    LocalDateTime now = LocalDateTime.now();
    pendingOffers
        .entrySet()
        .removeIf(
            entry -> {
              LocalDateTime deadline = entry.getValue().getDeadline();
              return deadline != null && !deadline.isAfter(now);
            });
  }

  private LocalDateTime deadlineOrMax(PaymentDTOs.SecondChanceOfferDTO offer) {
    return offer.getDeadline() == null ? LocalDateTime.MAX : offer.getDeadline();
  }
}
