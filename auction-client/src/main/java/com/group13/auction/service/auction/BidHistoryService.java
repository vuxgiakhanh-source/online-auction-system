package com.group13.auction.service.auction;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.BidViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.viewmodel.auction.BidHistoryPointViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service tải lịch sử bid để khởi tạo bảng và biểu đồ realtime. */
public final class BidHistoryService {

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của app. */
    public BidHistoryService() {
        this(ClientNetworkFacade.getDefault());
    }

    /** Tạo service với dependency truyền vào để dễ test. */
    public BidHistoryService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /** Lấy lịch sử bid của một phiên đấu giá. */
    public CompletableFuture<List<BidHistoryPointViewModel>> getBidHistory(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getBidHistory(auctionId),
                        PacketType.GET_BID_HISTORY_SUCCESS,
                        BidDTOs.BidHistoryResponseDTO.class,
                        "Không tải được lịch sử đặt giá.")
                .thenApply(BidViewModelMapper::toHistoryPointViewModels);
    }
}