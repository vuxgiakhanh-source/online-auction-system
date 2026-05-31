package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.mapper.AuctionViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.viewmodel.auction.AuctionDetailViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service xử lý tham gia, theo dõi và hủy tham gia phiên đấu giá realtime. */
public final class WatchAuctionService {

  private final ClientNetworkFacade networkFacade;

  /** Tạo service dùng network facade mặc định của app. */
  public WatchAuctionService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo service với dependency truyền vào để dễ test.
   *
   * @param networkFacade facade giao tiếp network phía client
   */
  public WatchAuctionService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Theo dõi phiên đấu giá để nhận realtime update mà chưa cần đặt cọc/join.
   *
   * @param auctionId id phiên đấu giá
   * @return thông tin chi tiết phiên phục vụ màn live bidding
   */
  public CompletableFuture<AuctionDetailViewModel> watchAuction(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.watchAuction(auctionId),
            PacketType.WATCH_AUCTION_SUCCESS,
            AuctionDTOs.AuctionDTO.class,
            "Không theo dõi được phiên đấu giá.")
        .thenApply(
            dto -> {
              seedJoinedStateFromDto(dto);
              return AuctionViewModelMapper.toDetailViewModel(dto);
            });
  }

  /**
   * Đồng bộ trạng thái đã tham gia/đã rời từ response WATCH_AUCTION_SUCCESS.
   *
   * <p>Server đã gửi hai field này theo user hiện tại. Nếu không seed lại ở đây, màn live có thể
   * chỉ vào được chế độ theo dõi dù user đã từng join phiên trong phiên đăng nhập trước.
   */
  private static void seedJoinedStateFromDto(AuctionDTOs.AuctionDTO dto) {
    if (dto == null || dto.getId() == null || dto.getId().isBlank()) {
      return;
    }

    JoinedAuctionState state = JoinedAuctionState.getInstance();
    String id = dto.getId();

    if (Boolean.TRUE.equals(dto.getLeftByCurrentUser())) {
      state.markLeft(id);
    } else if (Boolean.TRUE.equals(dto.getJoinedByCurrentUser())) {
      state.markJoined(id);
    } else if (Boolean.FALSE.equals(dto.getJoinedByCurrentUser())) {
      state.forgetJoined(id);
    }
  }

  /**
   * Tham gia phiên đấu giá để có thể đặt giá.
   *
   * <p>Server sẽ xử lý tiền cọc và các rule nghiệp vụ. Client chỉ giữ cache cục bộ để tránh gửi
   * request join trùng trong cùng phiên chạy app.
   *
   * @param auctionId id phiên đấu giá
   * @return response join từ server, gồm thông tin cọc và số dư khả dụng mới
   */
  public CompletableFuture<AuctionDTOs.JoinAuctionResponseDTO> joinAuction(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }
    if (currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture(
          "Tài khoản Admin chỉ được theo dõi phiên, không thể tham gia đặt giá.");
    }

    JoinedAuctionState joinedAuctionState = JoinedAuctionState.getInstance();

    if (joinedAuctionState.hasJoined(auctionId)) {
      return CompletableFuture.completedFuture(null);
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.joinAuction(auctionId),
            PacketType.JOIN_AUCTION_SUCCESS,
            AuctionDTOs.JoinAuctionResponseDTO.class,
            "Không tham gia được phiên đấu giá.")
        .whenComplete(
            (response, throwable) -> {
              if (throwable == null) {
                joinedAuctionState.markJoined(auctionId);
              }
            });
  }

  /**
   * Hủy tham gia phiên đấu giá.
   *
   * <p>Server hiện xử lý {@code LEAVE_AUCTION} như hành động hủy tham gia thật, không phải chỉ rời
   * màn live. Response có thể chứa thông tin cọc bị phạt, rating bị trừ và số dư khả dụng mới để
   * client hiển thị kết quả chính xác cho user.
   *
   * @param auctionId id phiên đấu giá
   * @return response hủy tham gia từ server
   */
  public CompletableFuture<AuctionDTOs.LeaveAuctionResponseDTO> leaveAuction(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.leaveAuction(auctionId),
            PacketType.LEAVE_AUCTION_SUCCESS,
            AuctionDTOs.LeaveAuctionResponseDTO.class,
            "Không hủy được tham gia phiên đấu giá.")
        .whenComplete(
            (response, throwable) -> {
              if (throwable == null) {
                JoinedAuctionState.getInstance().markLeft(auctionId);
              }
            });
  }

  private static boolean currentUserIsAdmin() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(UserSession::isAdmin)
        .orElse(false);
  }
}
