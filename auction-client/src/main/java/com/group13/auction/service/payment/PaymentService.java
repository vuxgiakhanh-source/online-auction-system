package com.group13.auction.service.payment;

import com.group13.auction.common.dto.payment.ConfirmItemReceivedResultDTO;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.PaymentViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import com.group13.auction.viewmodel.payment.SecondChanceOfferViewModel;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý payment và Second Chance ở phía client.
 *
 * <p>Lớp này chỉ validate input cơ bản, gửi request tới server và map response sang view model.
 * Toàn bộ nghiệp vụ thanh toán, kiểm tra winner, kiểm tra runner-up, trừ tiền và cập nhật trạng
 * thái phiên đấu giá vẫn thuộc trách nhiệm server.
 */
public final class PaymentService {

  private final ClientNetworkFacade networkFacade;

  /** Tạo payment service dùng network facade mặc định của ứng dụng. */
  public PaymentService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo payment service với dependency truyền vào, hữu ích cho test.
   *
   * @param networkFacade facade tầng network
   */
  public PaymentService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Gửi yêu cầu thanh toán cho phiên đấu giá đã kết thúc.
   *
   * <p>Client không tự quyết định người dùng có phải winner hay không. Server sẽ kiểm tra lại và
   * trả {@code PAYMENT_SUCCESS} hoặc {@code PAYMENT_FAILED}.
   *
   * @param auctionId mã phiên đấu giá
   * @return future chứa kết quả thanh toán đã format
   */
  public CompletableFuture<PaymentResultViewModel> requestPayment(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá cần thanh toán.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.requestPayment(auctionId.trim()),
            PacketType.PAYMENT_SUCCESS,
            PaymentDTOs.PaymentResultDTO.class,
            "Không thực hiện được thanh toán.")
        .thenApply(PaymentViewModelMapper::toPaymentResultViewModel);
  }

  /**
   * Gửi yêu cầu xác nhận đã nhận hàng cho đơn hàng đã thanh toán.
   *
   * <p>Server sẽ kiểm tra người dùng hiện tại có phải winner của phiên đấu giá hay không và trạng
   * thái thanh toán hiện tại có cho phép xác nhận nhận hàng hay không.
   *
   * @param auctionId mã phiên đấu giá
   * @return future chứa kết quả xác nhận nhận hàng từ server
   */
  public CompletableFuture<ConfirmItemReceivedResultDTO> confirmItemReceived(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá cần xác nhận.");
    }

    return AuctionServiceSupport.sendRequest(
        networkFacade,
        ClientRequestFactory.confirmItemReceived(auctionId.trim()),
        PacketType.CONFIRM_ITEM_RECEIVED_SUCCESS,
        ConfirmItemReceivedResultDTO.class,
        "Không xác nhận được trạng thái nhận hàng.");
  }

  /**
   * Chấp nhận Second Chance Offer của phiên đấu giá.
   *
   * <p>Server sẽ kiểm tra offer còn pending hay không và caller có đúng là runner-up không.
   *
   * @param auctionId mã phiên đấu giá có Second Chance Offer
   * @return future chứa kết quả accept đã format
   */
  public CompletableFuture<PaymentResultViewModel> acceptSecondChance(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá của Second Chance Offer.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.acceptSecondChance(auctionId.trim()),
            PacketType.SECOND_CHANCE_ACCEPT_SUCCESS,
            PaymentDTOs.PaymentResultDTO.class,
            "Không chấp nhận được Second Chance Offer.")
        .thenApply(PaymentViewModelMapper::toPaymentResultViewModel);
  }

  /**
   * Từ chối Second Chance Offer của phiên đấu giá.
   *
   * @param auctionId mã phiên đấu giá có Second Chance Offer
   * @return future hoàn tất khi server xác nhận đã ghi nhận từ chối
   */
  public CompletableFuture<Void> declineSecondChance(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá của Second Chance Offer.");
    }

    return AuctionServiceSupport.sendVoidRequest(
        networkFacade,
        ClientRequestFactory.declineSecondChance(auctionId.trim()),
        PacketType.SECOND_CHANCE_DECLINE_SUCCESS,
        "Không từ chối được Second Chance Offer.");
  }

  /**
   * Lấy danh sách Second Chance Offer đang PENDING của runner-up hiện tại từ server.
   *
   * @return future chứa danh sách offer DTO
   */
  public CompletableFuture<List<PaymentDTOs.SecondChanceOfferDTO>> fetchMyPendingSecondChanceOfferDtos() {
    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.getMySecondChanceOffers(),
            PacketType.GET_MY_SECOND_CHANCE_OFFERS_SUCCESS,
            PaymentDTOs.SecondChanceOfferDTO[].class,
            "Không tải được Second Chance Offer.")
        .thenApply(Arrays::asList);
  }

  /**
   * Lấy danh sách Second Chance Offer đang PENDING của runner-up hiện tại từ server.
   *
   * @return future chứa danh sách offer đã format cho UI
   */
  public CompletableFuture<List<SecondChanceOfferViewModel>> getMyPendingSecondChanceOffers() {
    return fetchMyPendingSecondChanceOfferDtos()
        .thenApply(
            offers ->
                offers.stream().map(PaymentViewModelMapper::toSecondChanceOfferViewModel).toList());
  }
}
