package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.AuctionViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service xử lý tham gia, theo dõi và rời phiên đấu giá realtime. */
public final class WatchAuctionService {

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của app. */
    public WatchAuctionService() {
        this(ClientNetworkFacade.getDefault());
    }

    /** Tạo service với dependency truyền vào để dễ test. */
    public WatchAuctionService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /** Watch phiên đấu giá để nhận realtime update mà chưa cần đặt cọc/join. */
    public CompletableFuture<AuctionDetailViewModel> watchAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.watchAuction(auctionId),
                        PacketType.WATCH_AUCTION_SUCCESS,
                        AuctionDTOs.AuctionDTO.class,
                        "Không theo dõi được phiên đấu giá.")
                .thenApply(AuctionViewModelMapper::toDetailViewModel);
    }

    /** Join phiên đấu giá để có thể đặt giá. Server sẽ xử lý tiền cọc và rule nghiệp vụ. */
    public CompletableFuture<AuctionDTOs.JoinAuctionResponseDTO> joinAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
        }

        return AuctionServiceSupport.sendRequest(
                networkFacade,
                ClientRequestFactory.joinAuction(auctionId),
                PacketType.JOIN_AUCTION_SUCCESS,
                AuctionDTOs.JoinAuctionResponseDTO.class,
                "Không tham gia được phiên đấu giá.");
    }

    /** Rời phiên đấu giá realtime hiện tại. */
    public CompletableFuture<Void> leaveAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.leaveAuction(auctionId),
                PacketType.LEAVE_AUCTION_SUCCESS,
                "Không rời được phiên đấu giá.");
    }
}