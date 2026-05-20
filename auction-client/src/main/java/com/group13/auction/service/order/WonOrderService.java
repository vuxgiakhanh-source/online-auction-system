package com.group13.auction.service.order;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.mapper.WonOrderViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service tải danh sách các phiên đấu giá mà người dùng hiện tại đã thắng. */
public final class WonOrderService {

  private static final String STATUS_FINISHED = "FINISHED";
  private static final String STATUS_PAID = "PAID";
  private static final int PAGE_SIZE = 100;

  private final ClientNetworkFacade networkFacade;

  /** Tạo service dùng network facade mặc định của ứng dụng. */
  public WonOrderService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo service với network facade truyền vào.
   *
   * @param networkFacade facade giao tiếp server
   */
  public WonOrderService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Tải danh sách đơn hàng đã thắng của người dùng hiện tại.
   *
   * <p>Source hiện tại chưa có API riêng cho đơn hàng, nên client dùng danh sách auction đã kết
   * thúc/đã thanh toán rồi lọc theo {@code currentLeaderId}.
   *
   * @return future chứa danh sách đơn hàng đã thắng đã format để hiển thị
   */
  public CompletableFuture<List<WonOrderViewModel>> getMyWonOrders() {
    String currentUserId = currentUserId();
    if (currentUserId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Vui lòng đăng nhập để xem đơn hàng.");
    }

    CompletableFuture<List<AuctionDTOs.AuctionDTO>> finishedAuctions =
        getAuctionsByStatus(STATUS_FINISHED);
    CompletableFuture<List<AuctionDTOs.AuctionDTO>> paidAuctions =
        getAuctionsByStatus(STATUS_PAID);

    return finishedAuctions.thenCombine(
        paidAuctions,
        (finished, paid) -> {
          List<AuctionDTOs.AuctionDTO> wonAuctions =
              mergeAndFilterWonAuctions(finished, paid, currentUserId);

          return WonOrderViewModelMapper.toViewModels(wonAuctions).stream()
              .sorted(orderComparator())
              .toList();
        });
  }

  private CompletableFuture<List<AuctionDTOs.AuctionDTO>> getAuctionsByStatus(String status) {
    AuctionDTOs.AuctionListRequestDTO request = new AuctionDTOs.AuctionListRequestDTO();
    request.setStatusFilter(status);
    request.setSortBy(null);
    request.setPage(0);
    request.setPageSize(PAGE_SIZE);

    return AuctionServiceSupport
        .sendRequest(
            networkFacade,
            ClientRequestFactory.getAuctionList(request),
            PacketType.GET_AUCTION_LIST_SUCCESS,
            AuctionDTOs.AuctionListDTO.class,
            "Không tải được danh sách đơn hàng.")
        .thenApply(AuctionDTOs.AuctionListDTO::getAuctions)
        .thenApply(auctions -> auctions == null ? List.of() : auctions);
  }

  private List<AuctionDTOs.AuctionDTO> mergeAndFilterWonAuctions(
      List<AuctionDTOs.AuctionDTO> finished,
      List<AuctionDTOs.AuctionDTO> paid,
      String currentUserId) {
    Map<String, AuctionDTOs.AuctionDTO> uniqueAuctions = new LinkedHashMap<>();

    List<AuctionDTOs.AuctionDTO> combined = new ArrayList<>();
    if (finished != null) {
      combined.addAll(finished);
    }
    if (paid != null) {
      combined.addAll(paid);
    }

    for (AuctionDTOs.AuctionDTO auction : combined) {
      if (!isWonByCurrentUser(auction, currentUserId)) {
        continue;
      }

      String auctionId = safe(auction.getId());
      if (!auctionId.isBlank()) {
        uniqueAuctions.putIfAbsent(auctionId, auction);
      }
    }

    return List.copyOf(uniqueAuctions.values());
  }

  private boolean isWonByCurrentUser(AuctionDTOs.AuctionDTO auction, String currentUserId) {
    if (auction == null || currentUserId == null || currentUserId.isBlank()) {
      return false;
    }

    String leaderId = safe(auction.getCurrentLeaderId());
    return !leaderId.isBlank() && leaderId.equals(currentUserId);
  }

  private Comparator<WonOrderViewModel> orderComparator() {
    return (left, right) -> {
      int reportableCompare = Boolean.compare(right.reportable(), left.reportable());
      if (reportableCompare != 0) {
        return reportableCompare;
      }

      return left.itemName().compareToIgnoreCase(right.itemName());
    };
  }

  private String currentUserId() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(session -> safe(session.getUserId()))
        .orElse("");
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }
}