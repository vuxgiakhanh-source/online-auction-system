package com.group13.auction.service.seller;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.mapper.SellerAuctionViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.seller.AuctionFormViewModel;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service phía client cho các flow Seller liên quan đến phiên đấu giá.
 *
 * <p>Service này chỉ validate input cơ bản, gọi network facade và map DTO sang view model. Các rule
 * nghiệp vụ như quyền Seller, rating, trạng thái phiên, quyền sửa/hủy thật sự vẫn do server quyết
 * định.
 */
public final class SellerAuctionService {

    private static final int SELLER_LIST_PAGE_SIZE = 200;

    private final ClientNetworkFacade networkFacade;
    private final SessionManager sessionManager;

    /** Tạo service dùng context/network mặc định của ứng dụng. */
    public SellerAuctionService() {
        this(ClientNetworkFacade.getDefault(), AppContext.getInstance().getSessionManager());
    }

    /**
     * Tạo service với dependency truyền vào để dễ test.
     *
     * @param networkFacade network facade
     * @param sessionManager session manager
     */
    public SellerAuctionService(ClientNetworkFacade networkFacade, SessionManager sessionManager) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    /**
     * Lấy danh sách phiên thuộc Seller hiện tại.
     *
     * <p>Backend hiện chưa có API riêng cho "my seller auctions". Vì vậy client gọi
     * {@code GET_AUCTION_LIST} rồi lọc theo {@code sellerId/sellerUsername} từ DTO trả về. Khi backend
     * có packet riêng, method này nên đổi sang gọi API đó.
     *
     * @param statusFilter status cần lọc, ví dụ {@code OPEN}, {@code RUNNING}; null là tất cả
     * @return future chứa danh sách row view model
     */
    public CompletableFuture<List<SellerAuctionRowViewModel>> getMyAuctionRows(String statusFilter) {
        UserSession session;
        try {
            session = requireSellerSession();
        } catch (RuntimeException exception) {
            return AuctionServiceSupport.failedFuture(exception.getMessage());
        }

        AuctionDTOs.AuctionListRequestDTO request = new AuctionDTOs.AuctionListRequestDTO();
        request.setStatusFilter(normalizeStatusFilter(statusFilter));
        request.setSortBy("START_TIME");
        request.setPage(0);
        request.setPageSize(SELLER_LIST_PAGE_SIZE);

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getAuctionList(request),
                        PacketType.GET_AUCTION_LIST_SUCCESS,
                        AuctionDTOs.AuctionListDTO.class,
                        "Không tải được danh sách phiên của Seller.")
                .thenApply(AuctionDTOs.AuctionListDTO::getAuctions)
                .thenApply(auctions -> SellerAuctionViewModelMapper.toSellerRows(auctions, session));
    }

    /**
     * Tạo phiên đấu giá mới.
     *
     * @param form dữ liệu form đã nhập
     * @return future chứa phiên vừa tạo dưới dạng row view model
     */
    public CompletableFuture<SellerAuctionRowViewModel> createAuction(AuctionFormViewModel form) {
        try {
            requireSellerSession();
            Objects.requireNonNull(form, "form must not be null").validateForCreate();
        } catch (RuntimeException exception) {
            return AuctionServiceSupport.failedFuture(exception.getMessage());
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.createAuction(form.toCreateRequest()),
                        PacketType.CREATE_AUCTION_SUCCESS,
                        AuctionDTOs.AuctionDTO.class,
                        "Không tạo được phiên đấu giá.")
                .thenApply(SellerAuctionViewModelMapper::toRow);
    }

    /**
     * Cập nhật thời gian kết thúc của phiên đang ở trạng thái OPEN.
     *
     * <p>Common DTO có field {@code newReservePrice}, nhưng server handler hiện chưa xử lý rõ field này.
     * Vì vậy client chỉ triển khai update end time để tránh UI hứa sai chức năng.
     *
     * @param auctionId mã phiên
     * @param newEndTime thời gian kết thúc mới
     * @return future chứa phiên sau khi update
     */
    public CompletableFuture<SellerAuctionRowViewModel> updateOpenAuctionEndTime(
            String auctionId, LocalDateTime newEndTime) {
        try {
            requireSellerSession();
            requireText(auctionId, "Thiếu mã phiên đấu giá.");
            if (newEndTime == null) {
                throw new IllegalArgumentException("Thời gian kết thúc mới không được để trống.");
            }
        } catch (RuntimeException exception) {
            return AuctionServiceSupport.failedFuture(exception.getMessage());
        }

        AuctionDTOs.UpdateAuctionDTO request = new AuctionDTOs.UpdateAuctionDTO();
        request.setAuctionId(auctionId.trim());
        request.setNewEndTime(newEndTime);

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.updateAuction(request),
                        PacketType.UPDATE_AUCTION_SUCCESS,
                        AuctionDTOs.AuctionDTO.class,
                        "Không cập nhật được phiên đấu giá.")
                .thenApply(SellerAuctionViewModelMapper::toRow);
    }

    /**
     * Gửi yêu cầu hủy phiên đấu giá cho server.
     *
     * <p>Server hiện chỉ cho Seller request cancel khi phiên ở trạng thái {@code OPEN}.
     *
     * @param auctionId mã phiên
     * @param reason lý do hủy
     * @return future chứa auctionId được server xác nhận
     */
    public CompletableFuture<String> requestCancelAuction(String auctionId, String reason) {
        try {
            requireSellerSession();
            requireText(auctionId, "Thiếu mã phiên đấu giá.");
            requireText(reason, "Lý do hủy phiên không được để trống.");
        } catch (RuntimeException exception) {
            return AuctionServiceSupport.failedFuture(exception.getMessage());
        }

        AuctionDTOs.CancelAuctionRequestDTO request = new AuctionDTOs.CancelAuctionRequestDTO();
        request.setAuctionId(auctionId.trim());
        request.setReason(reason.trim());

        return AuctionServiceSupport.sendRequest(
                networkFacade,
                ClientRequestFactory.requestCancelAuction(request),
                PacketType.CANCEL_AUCTION_REQUEST_SUCCESS,
                String.class,
                "Không gửi được yêu cầu hủy phiên.");
    }

    private UserSession requireSellerSession() {
        UserSession session = sessionManager.requireSession();
        if (!session.isSeller()) {
            throw new IllegalStateException("Tài khoản hiện tại chưa có quyền Seller.");
        }
        return session;
    }

    private static String normalizeStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "Tất cả".equalsIgnoreCase(statusFilter)) {
            return null;
        }
        return statusFilter.trim().toUpperCase();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}