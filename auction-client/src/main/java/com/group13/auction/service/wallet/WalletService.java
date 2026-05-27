package com.group13.auction.service.wallet;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.WalletViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.wallet.WalletViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý các thao tác ví ở phía client.
 *
 * <p>Lớp này chỉ validate input cơ bản, gửi request tới server và map response sang view model.
 * Nghiệp vụ cộng/trừ tiền, khóa đặt cọc và kiểm tra số dư vẫn thuộc trách nhiệm server.
 */
public final class WalletService {

  private final ClientNetworkFacade networkFacade;

  /** Tạo wallet service dùng network facade mặc định của app. */
  public WalletService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo wallet service với dependency truyền vào, hữu ích cho test.
   *
   * @param networkFacade facade tầng network
   */
  public WalletService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Lấy số dư ví hiện tại của người dùng đã đăng nhập.
   *
   * @return future chứa view model số dư ví
   */
  public CompletableFuture<WalletViewModel> getWalletBalance() {
    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.getWalletBalance(),
            PacketType.GET_WALLET_BALANCE_SUCCESS,
            PaymentDTOs.WalletBalanceResponseDTO.class,
            "Không tải được số dư ví.")
        .thenApply(WalletViewModelMapper::toViewModel);
  }

  /**
   * Nạp tiền vào ví.
   *
   * @param amount số tiền cần nạp
   * @return future chứa số dư mới
   */
  public CompletableFuture<WalletViewModel> deposit(long amount) {
    if (amount <= 0) {
      return AuctionServiceSupport.failedFuture("Số tiền nạp phải lớn hơn 0.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.deposit(amount),
            PacketType.DEPOSIT_SUCCESS,
            PaymentDTOs.WalletBalanceResponseDTO.class,
            "Không nạp được tiền vào ví.")
        .thenApply(WalletViewModelMapper::toViewModel);
  }

  /**
   * Rút tiền khỏi ví.
   *
   * @param amount số tiền cần rút
   * @return future chứa số dư mới
   */
  public CompletableFuture<WalletViewModel> withdraw(long amount) {
    if (amount <= 0) {
      return AuctionServiceSupport.failedFuture("Số tiền rút phải lớn hơn 0.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.withdraw(amount),
            PacketType.WITHDRAW_SUCCESS,
            PaymentDTOs.WalletBalanceResponseDTO.class,
            "Không rút được tiền khỏi ví.")
        .thenApply(WalletViewModelMapper::toViewModel);
  }
}
