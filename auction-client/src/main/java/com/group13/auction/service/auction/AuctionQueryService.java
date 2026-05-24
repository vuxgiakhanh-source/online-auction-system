package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.search.SearchDTOs;
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

    private static final String SORT_BY_CURRENT_PRICE = "CURRENT_PRICE";

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
        return getAuctionCards(null, statusFilter, false, null, sortBy, page, pageSize);
    }

    /**
     * Lấy danh sách phiên đấu giá theo keyword, trạng thái và phạm vi hiển thị.
     *
     * <p>Khi {@code keyword} rỗng, service dùng API danh sách auction hiện có. Khi có keyword,
     * service dùng API {@code SEARCH_ITEMS}; vì search DTO chưa hỗ trợ status/mine filter, client
     * lọc thêm trạng thái và seller hiện tại sau khi server trả kết quả.
     *
     * @param keyword từ khóa tìm kiếm theo tên sản phẩm
     * @param statusFilter trạng thái phiên cần lọc, null nghĩa là tất cả
     * @param onlyMine true nếu chỉ lấy phiên của seller hiện tại
     * @param currentUserId id user hiện tại, dùng khi {@code onlyMine} là true
     * @param sortBy khóa sắp xếp của màn danh sách auction
     * @param page trang bắt đầu từ 0
     * @param pageSize số item mỗi trang
     * @return danh sách card view model đã format cho UI
     */
    public CompletableFuture<List<AuctionCardViewModel>> getAuctionCards(
        String keyword,
        String statusFilter,
        boolean onlyMine,
        String currentUserId,
        String sortBy,
        int page,
        int pageSize) {
        String normalizedKeyword = blankToNull(keyword);

        CompletableFuture<List<AuctionDTOs.AuctionDTO>> auctionFuture =
            normalizedKeyword == null
                ? fetchAuctionDtos(statusFilter, sortBy, page, pageSize)
                : searchAuctionDtos(normalizedKeyword, sortBy, page, pageSize);

        return auctionFuture
            .thenApply(auctions -> filterByStatus(auctions, normalizedKeyword, statusFilter))
            .thenApply(auctions -> filterBySeller(auctions, onlyMine, currentUserId))
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

    private CompletableFuture<List<AuctionDTOs.AuctionDTO>> fetchAuctionDtos(
        String statusFilter, String sortBy, int page, int pageSize) {
        AuctionDTOs.AuctionListRequestDTO request = new AuctionDTOs.AuctionListRequestDTO();
        request.setStatusFilter(blankToNull(statusFilter));
        request.setSortBy(blankToNull(sortBy));
        request.setPage(Math.max(0, page));
        request.setPageSize(normalizePageSize(pageSize));

        return AuctionServiceSupport
            .sendRequest(
                networkFacade,
                ClientRequestFactory.getAuctionList(request),
                PacketType.GET_AUCTION_LIST_SUCCESS,
                AuctionDTOs.AuctionListDTO.class,
                "Không tải được danh sách phiên đấu giá.")
            .thenApply(AuctionDTOs.AuctionListDTO::getAuctions)
            .thenApply(this::safeAuctions);
    }

    private CompletableFuture<List<AuctionDTOs.AuctionDTO>> searchAuctionDtos(
        String keyword, String sortBy, int page, int pageSize) {
        SearchDTOs.ItemSearchRequestDTO request = new SearchDTOs.ItemSearchRequestDTO();
        request.setKeyword(keyword);
        request.setPage(Math.max(0, page));
        request.setSize(normalizePageSize(pageSize));
        request.setSortBy(toSearchSortBy(sortBy));
        request.setSortDir("DESC");

        return AuctionServiceSupport
            .sendRequest(
                networkFacade,
                ClientRequestFactory.searchItems(request),
                PacketType.SEARCH_ITEMS_SUCCESS,
                SearchDTOs.ItemSearchResponseDTO.class,
                "Không tìm kiếm được phiên đấu giá.")
            .thenApply(SearchDTOs.ItemSearchResponseDTO::getAuctions)
            .thenApply(this::safeAuctions);
    }

    private List<AuctionDTOs.AuctionDTO> filterByStatus(
        List<AuctionDTOs.AuctionDTO> auctions, String keyword, String statusFilter) {
        String normalizedStatus = blankToNull(statusFilter);
        if (keyword == null || normalizedStatus == null) {
            return auctions;
        }

        return auctions.stream()
            .filter(auction -> normalizedStatus.equalsIgnoreCase(auction.getStatus()))
            .toList();
    }

    private List<AuctionDTOs.AuctionDTO> filterBySeller(
        List<AuctionDTOs.AuctionDTO> auctions, boolean onlyMine, String currentUserId) {
        String normalizedUserId = blankToNull(currentUserId);
        if (!onlyMine || normalizedUserId == null) {
            return auctions;
        }

        return auctions.stream()
            .filter(auction -> isOwnedBySeller(auction, normalizedUserId))
            .toList();
    }

    private boolean isOwnedBySeller(AuctionDTOs.AuctionDTO auction, String sellerId) {
        if (auction == null || auction.getItem() == null) {
            return false;
        }

        String auctionSellerId = blankToNull(auction.getItem().getSellerId());
        return sellerId.equals(auctionSellerId);
    }

    private List<AuctionDTOs.AuctionDTO> safeAuctions(List<AuctionDTOs.AuctionDTO> auctions) {
        return auctions == null ? List.of() : auctions;
    }

    private int normalizePageSize(int pageSize) {
        return pageSize <= 0 ? 20 : Math.min(pageSize, 100);
    }

    private String toSearchSortBy(String sortBy) {
        return SORT_BY_CURRENT_PRICE.equals(sortBy) ? "currentPrice" : "createdAt";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}