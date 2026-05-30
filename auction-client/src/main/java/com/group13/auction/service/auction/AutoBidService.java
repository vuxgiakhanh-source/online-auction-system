package com.group13.auction.service.auction;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.viewmodel.auction.AutoBidFormViewModel;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý request Auto-Bid ở phía client.
 *
 * <p>Client chỉ validate dữ liệu nhập và gửi request xuống server. Toàn bộ logic nghiệp vụ như tính
 * bước giá, so sánh nhiều auto-bid, xử lý đồng thời và quyết định winner vẫn do server đảm nhiệm.
 */
public final class AutoBidService {

  private final ClientNetworkFacade networkFacade;

  /** Tạo service dùng network facade mặc định của ứng dụng. */
  public AutoBidService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo service với dependency truyền vào để dễ test.
   *
   * @param networkFacade facade giao tiếp network
   */
  public AutoBidService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Lấy trạng thái Auto-Bid hiện tại của user trong một phiên.
   *
   * @param auctionId mã phiên đấu giá
   * @return trạng thái Auto-Bid
   */
  public CompletableFuture<BidDTOs.AutoBidRegistrationDTO> getAutoBidStatus(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }

    return AuctionServiceSupport.sendRequest(
        networkFacade,
        ClientRequestFactory.getAutoBidStatus(auctionId),
        PacketType.GET_AUTO_BID_STATUS_SUCCESS,
        BidDTOs.AutoBidRegistrationDTO.class,
        "Không tải được trạng thái auto-bid.");
  }

  /**
   * Đăng ký Auto-Bid mới.
   *
   * @param auctionId mã phiên đấu giá
   * @param maxBidText giá tối đa user nhập
   * @return thông tin Auto-Bid sau khi server xác nhận
   */
  public CompletableFuture<BidDTOs.AutoBidRegistrationDTO> registerAutoBid(
      String auctionId, String maxBidText) {
    return registerAutoBid(auctionId, maxBidText, 0L, null);
  }

  public CompletableFuture<BidDTOs.AutoBidRegistrationDTO> registerAutoBid(
      String auctionId, String maxBidText, long currentPrice, Long previousMaxBid) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }

    AutoBidFormViewModel form = new AutoBidFormViewModel(maxBidText);
    Optional<String> validationError = validateAutoBidForm(form, currentPrice, previousMaxBid);
    if (validationError.isPresent()) {
      return AuctionServiceSupport.failedFuture(validationError.get());
    }

    return AuctionServiceSupport.sendRequest(
        networkFacade,
        ClientRequestFactory.registerAutoBid(auctionId, form.maxBidAmount()),
        PacketType.REGISTER_AUTO_BID_SUCCESS,
        BidDTOs.AutoBidRegistrationDTO.class,
        "Không đăng ký được auto-bid.");
  }

  /**
   * Cập nhật maxBid cho Auto-Bid đang hoạt động.
   *
   * @param auctionId mã phiên đấu giá
   * @param maxBidText giá tối đa mới
   * @return thông tin Auto-Bid sau khi server xác nhận
   */
  public CompletableFuture<BidDTOs.AutoBidRegistrationDTO> updateAutoBid(
      String auctionId, String maxBidText) {
    return updateAutoBid(auctionId, maxBidText, 0L, null);
  }

  public CompletableFuture<BidDTOs.AutoBidRegistrationDTO> updateAutoBid(
      String auctionId, String maxBidText, long currentPrice, Long previousMaxBid) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }

    AutoBidFormViewModel form = new AutoBidFormViewModel(maxBidText);
    Optional<String> validationError = validateAutoBidForm(form, currentPrice, previousMaxBid);
    if (validationError.isPresent()) {
      return AuctionServiceSupport.failedFuture(validationError.get());
    }

    return AuctionServiceSupport.sendRequest(
        networkFacade,
        ClientRequestFactory.updateAutoBid(auctionId, form.maxBidAmount()),
        PacketType.UPDATE_AUTO_BID_SUCCESS,
        BidDTOs.AutoBidRegistrationDTO.class,
        "Không cập nhật được auto-bid.");
  }

  /**
   * Hủy Auto-Bid đang hoạt động trong phiên.
   *
   * @param auctionId mã phiên đấu giá
   * @return future hoàn thành khi server xác nhận hủy
   */
  public CompletableFuture<Void> cancelAutoBid(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Thiếu mã phiên đấu giá.");
    }

    return AuctionServiceSupport.sendVoidRequest(
        networkFacade,
        ClientRequestFactory.cancelAutoBid(auctionId),
        PacketType.CANCEL_AUTO_BID_SUCCESS,
        "Không hủy được auto-bid.");
  }

  private static Optional<String> validateAutoBidForm(
      AutoBidFormViewModel form, long currentPrice, Long previousMaxBid) {
    if (currentPrice <= 0) {
      return form.validate();
    }

    long increment = minimumIncrementFor(currentPrice);
    long minimumMaxBid = currentPrice + increment;
    return form.validateAgainstMinimum(minimumMaxBid, previousMaxBid);
  }

  private static long minimumIncrementFor(long currentPrice) {
    if (currentPrice < 1_000_000L) {
      return 50_000L;
    }
    if (currentPrice <= 10_000_000L) {
      return 200_000L;
    }
    return 500_000L;
  }
}
