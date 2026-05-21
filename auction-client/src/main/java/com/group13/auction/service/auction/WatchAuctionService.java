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

    /**
     * Join phiên đấu giá để có thể đặt giá. Server sẽ xử lý tiền cọc và rule nghiệp vụ.
     *
     * <p>Guard chống join trùng ở client: nếu {@link JoinedAuctionState} đã ghi nhận user
     * đang tham gia phiên này thì trả về lỗi ngay, không gửi request lên server.
     * Điều này tránh việc bấm nút JOIN liên tiếp gây khóa cọc nhiều lần.
     *
     * <p>Sau khi server xác nhận thành công, trạng thái được đánh dấu vào
     * {@link JoinedAuctionState} để các lần kiểm tra sau biết user đã join.
     */
    public CompletableFuture<AuctionDTOs.JoinAuctionResponseDTO> joinAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
        }

        // Guard: không gửi join khi client đã ghi nhận user đang tham gia phiên này.
        // Trả về null thành công im lặng — UI coi như "đã join rồi", không hiện lỗi,
        // không gửi request thừa lên server (tránh khóa cọc nhiều lần).
        if (JoinedAuctionState.getInstance().hasJoined(auctionId)) {
            return CompletableFuture.completedFuture(null);
        }

        return AuctionServiceSupport.sendRequest(
                networkFacade,
                ClientRequestFactory.joinAuction(auctionId),
                PacketType.JOIN_AUCTION_SUCCESS,
                AuctionDTOs.JoinAuctionResponseDTO.class,
                "Không tham gia được phiên đấu giá.")
            .whenComplete((response, throwable) -> {
                if (throwable == null) {
                    // Server xác nhận join thành công — đánh dấu để chặn join lại
                    JoinedAuctionState.getInstance().markJoined(auctionId);
                }
            });
    }

    /**
     * Rời phiên đấu giá realtime hiện tại.
     *
     * <p>Sau khi server xác nhận thành công, trạng thái join cục bộ được xóa khỏi
     * {@link JoinedAuctionState} để user có thể join lại phiên này nếu muốn.
     */
    public CompletableFuture<Void> leaveAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.leaveAuction(auctionId),
                PacketType.LEAVE_AUCTION_SUCCESS,
                "Không rời được phiên đấu giá.")
            .whenComplete((ignored, throwable) -> {
                if (throwable == null) {
                    // Server xác nhận leave thành công — xóa trạng thái để cho phép join lại
                    JoinedAuctionState.getInstance().forgetJoined(auctionId);
                }
            });
    }
}