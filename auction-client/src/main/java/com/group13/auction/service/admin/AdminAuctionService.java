package com.group13.auction.service.admin;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.mapper.AuctionViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.admin.AuctionModerationViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý quản trị phiên đấu giá ở phía client.
 *
 * <p>Client chỉ gọi API admin thật của server. Các rule như phiên nào được hủy, trạng thái sau
 * khi hủy, hoàn tiền hay xử lý người thắng là trách nhiệm của server.
 */
public final class AdminAuctionService {

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của ứng dụng. */
    public AdminAuctionService() {
        this(ClientNetworkFacade.getDefault());
    }

    /**
     * Tạo service với dependency truyền vào, hữu ích cho test.
     *
     * @param networkFacade facade tầng network
     */
    public AdminAuctionService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /**
     * Lấy toàn bộ phiên đấu giá cho màn Admin Auction Moderation.
     *
     * @return future chứa danh sách auction view model
     */
    public CompletableFuture<List<AuctionModerationViewModel>> getAllAuctionsForAdmin() {
        if (!currentUserIsAdmin()) {
            return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.adminGetAllAuctions(),
                        PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS,
                        AuctionDTOs.AuctionListDTO.class,
                        "Không tải được danh sách phiên đấu giá.")
                .thenApply(this::mapAuctionList);
    }

    /**
     * Hủy phiên đấu giá với quyền Admin.
     *
     * @param auctionId mã phiên đấu giá
     * @param reason lý do hủy, phải khớp enum server hỗ trợ
     * @return future chứa phiên đấu giá sau khi server cập nhật
     */
    public CompletableFuture<AuctionModerationViewModel> cancelAuctionAsAdmin(
            String auctionId, String reason) {
        if (!currentUserIsAdmin()) {
            return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
        }
        if (isBlank(auctionId)) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá cần hủy.");
        }
        if (isBlank(reason)) {
            return AuctionServiceSupport.failedFuture("Vui lòng chọn lý do hủy phiên đấu giá.");
        }

        AuctionDTOs.AdminCancelAuctionDTO request = new AuctionDTOs.AdminCancelAuctionDTO();
        request.setAuctionId(auctionId.trim());
        request.setReason(reason.trim());

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.adminCancelAuction(request),
                        PacketType.ADMIN_CANCEL_AUCTION_SUCCESS,
                        AuctionDTOs.AuctionDTO.class,
                        "Không hủy được phiên đấu giá.")
                .thenApply(AuctionViewModelMapper::toModerationViewModel);
    }

    private List<AuctionModerationViewModel> mapAuctionList(AuctionDTOs.AuctionListDTO dto) {
        if (dto == null) {
            return List.of();
        }

        return AuctionViewModelMapper.toModerationViewModels(dto.getAuctions());
    }

    private boolean currentUserIsAdmin() {
        return AppContext.getInstance()
                .getSessionManager()
                .getCurrentSession()
                .map(session -> session.isAdmin())
                .orElse(false);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}