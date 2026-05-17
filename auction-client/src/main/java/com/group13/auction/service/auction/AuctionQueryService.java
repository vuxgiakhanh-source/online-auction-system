package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.AuctionViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.viewmodel.auction.AuctionCardViewModel;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service đọc danh sách và chi tiết phiên đấu giá từ server. */
public final class AuctionQueryService {

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của app. */
    public AuctionQueryService() {
        this(ClientNetworkFacade.getDefault());
    }

    /** Tạo service với dependency truyền vào để dễ test. */
    public AuctionQueryService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /**
     * Lấy danh sách phiên đấu giá.
     *
     * <p>Client chỉ gửi filter/sort/page xuống server. Việc lọc dữ liệu thật vẫn do server xử lý.
     */
    public CompletableFuture<List<AuctionCardViewModel>> getAuctionCards(
            String statusFilter, String sortBy, int page, int pageSize) {
        AuctionDTOs.AuctionListRequestDTO request = new AuctionDTOs.AuctionListRequestDTO();
        request.setStatusFilter(blankToNull(statusFilter));
        request.setSortBy(blankToNull(sortBy));
        request.setPage(Math.max(0, page));
        request.setPageSize(pageSize <= 0 ? 20 : pageSize);

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getAuctionList(request),
                        PacketType.GET_AUCTION_LIST_SUCCESS,
                        AuctionDTOs.AuctionListDTO.class,
                        "Không tải được danh sách phiên đấu giá.")
                .thenApply(AuctionDTOs.AuctionListDTO::getAuctions)
                .thenApply(AuctionViewModelMapper::toCardViewModels);
    }

    /** Lấy chi tiết một phiên đấu giá theo id. */
    public CompletableFuture<AuctionDetailViewModel> getAuctionDetail(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getAuctionDetail(auctionId),
                        PacketType.GET_AUCTION_DETAIL_SUCCESS,
                        AuctionDTOs.AuctionDTO.class,
                        "Không tải được chi tiết phiên đấu giá.")
                .thenApply(AuctionViewModelMapper::toDetailViewModel);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}