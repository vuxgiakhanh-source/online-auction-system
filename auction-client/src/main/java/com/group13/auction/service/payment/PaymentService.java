package com.group13.auction.service.payment;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.PaymentViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý payment và Second Chance ở phía client.
 *
 * <p>Lớp này chỉ validate input cơ bản, gửi request tới server và map response sang view model.
 * Toàn bộ nghiệp vụ thanh toán, kiểm tra winner, kiểm tra runner-up, trừ tiền và cập nhật trạng thái
 * phiên đấu giá vẫn thuộc trách nhiệm server.
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
     * <p>Client không tự quyết định người dùng có phải winner hay không. Server sẽ kiểm tra lại và trả
     * {@code PAYMENT_SUCCESS} hoặc {@code PAYMENT_FAILED}.
     *
     * @param auctionId mã phiên đấu giá
     * @return future chứa kết quả thanh toán đã format
     */
    public CompletableFuture<PaymentResultViewModel> requestPayment(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá cần thanh toán.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.requestPayment(auctionId.trim()),
                        PacketType.PAYMENT_SUCCESS,
                        PaymentDTOs.PaymentResultDTO.class,
                        "Không thực hiện được thanh toán.")
                .thenApply(PaymentViewModelMapper::toPaymentResultViewModel);
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
            return AuctionServiceSupport.failedFuture(
                    "Thiếu mã phiên đấu giá của Second Chance Offer.");
        }

        return AuctionServiceSupport
                .sendRequest(
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
            return AuctionServiceSupport.failedFuture(
                    "Thiếu mã phiên đấu giá của Second Chance Offer.");
        }

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.declineSecondChance(auctionId.trim()),
                PacketType.SECOND_CHANCE_DECLINE_SUCCESS,
                "Không từ chối được Second Chance Offer.");
    }
}