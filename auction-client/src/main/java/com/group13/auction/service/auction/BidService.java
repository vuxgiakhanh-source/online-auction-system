package com.group13.auction.service.auction;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.BidViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.viewmodel.auction.LiveBidViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service gửi yêu cầu đặt giá từ client xuống server. */
public final class BidService {

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của app. */
    public BidService() {
        this(ClientNetworkFacade.getDefault());
    }

    /** Tạo service với dependency truyền vào để dễ test. */
    public BidService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /** Đặt giá thủ công. Client chỉ validate format; nghiệp vụ hợp lệ vẫn do server quyết định. */
    public CompletableFuture<LiveBidViewModel> placeBid(String auctionId, String amountText) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
        }

        Long amount = parsePositiveLong(amountText);
        if (amount == null) {
            return AuctionServiceSupport.failedFuture("Giá đặt phải là số nguyên lớn hơn 0.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.placeBid(auctionId, amount),
                        PacketType.PLACE_BID_SUCCESS,
                        BidDTOs.BidResultDTO.class,
                        "Không đặt giá được.")
                .thenApply(BidViewModelMapper::toLiveBidViewModel);
    }

    private Long parsePositiveLong(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            return null;
        }

        try {
            long amount = Long.parseLong(amountText.trim().replace(".", "").replace(",", ""));
            return amount > 0 ? amount : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}