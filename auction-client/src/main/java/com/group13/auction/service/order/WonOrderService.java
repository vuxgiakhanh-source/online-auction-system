package com.group13.auction.service.order;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.payment.ConfirmItemReceivedResultDTO;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.mapper.WonOrderViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.service.payment.PaymentService;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
import com.group13.auction.viewmodel.payment.PaymentResultViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Service tải và xử lý các phiên đấu giá mà người dùng hiện tại đã thắng. */
public final class WonOrderService {

  private static final String STATUS_FINISHED = "FINISHED";
  private static final String STATUS_PAID = "PAID";
  private static final int PAGE_SIZE = 100;

  private final ClientNetworkFacade networkFacade;
  private final PaymentService paymentService;

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
    this.paymentService = new PaymentService(networkFacade);
  }

  /**
   * Tải danh sách đơn hàng đã thắng của người dùng hiện tại.
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
    CompletableFuture<List<AuctionDTOs.AuctionDTO>> paidAuctions = getAuctionsByStatus(STATUS_PAID);

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

  /**
   * Thanh toán đơn hàng đã thắng.
   *
   * @param auctionId mã phiên đấu giá cần thanh toán
   * @return future chứa kết quả thanh toán đã format
   */
  public CompletableFuture<PaymentResultViewModel> payForOrder(String auctionId) {
    if (isBlank(auctionId)) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá cần thanh toán.");
    }

    return paymentService.requestPayment(auctionId.trim());
  }

  /**
   * Xác nhận người dùng đã nhận hàng.
   *
   * @param auctionId mã phiên đấu giá cần xác nhận
   * @return future chứa kết quả xác nhận từ server
   */
  public CompletableFuture<ConfirmItemReceivedResultDTO> confirmItemReceived(String auctionId) {
    if (isBlank(auctionId)) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá cần xác nhận.");
    }

    return paymentService.confirmItemReceived(auctionId.trim());
  }

  private CompletableFuture<List<AuctionDTOs.AuctionDTO>> getAuctionsByStatus(String status) {
    AuctionDTOs.AuctionListRequestDTO request = new AuctionDTOs.AuctionListRequestDTO();
    request.setStatusFilter(status);
    request.setSortBy(null);
    request.setPage(0);
    request.setPageSize(PAGE_SIZE);

    return AuctionServiceSupport.sendRequest(
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

    String winnerId = safe(auction.getWinnerId());
    if (!winnerId.isBlank()) {
      return winnerId.equals(currentUserId);
    }

    // Fallback cho server/client cũ chưa có winnerId — giữ hành vi cũ.
    String leaderId = safe(auction.getCurrentLeaderId());
    return !leaderId.isBlank() && leaderId.equals(currentUserId);
  }

  private Comparator<WonOrderViewModel> orderComparator() {
    return (left, right) -> {
      int priorityCompare = Integer.compare(orderPriority(left), orderPriority(right));
      if (priorityCompare != 0) {
        return priorityCompare;
      }

      return left.itemName().compareToIgnoreCase(right.itemName());
    };
  }

  private int orderPriority(WonOrderViewModel order) {
    if (order == null) {
      return 99;
    }
    if (order.canPay()) {
      return 1;
    }
    if (order.canConfirmReceipt()) {
      return 2;
    }
    if (order.canSubmitReport()) {
      return 3;
    }
    if (order.completed()) {
      return 4;
    }
    return 10;
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

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
