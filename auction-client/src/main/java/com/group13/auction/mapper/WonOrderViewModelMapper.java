package com.group13.auction.mapper;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
import java.util.List;

/** Mapper chuyển auction DTO đã thắng sang view model đơn hàng phía client. */
public final class WonOrderViewModelMapper {

  private static final String AUCTION_STATUS_FINISHED = "FINISHED";
  private static final String AUCTION_STATUS_PAID = "PAID";

  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final String PAYMENT_STATUS_FUNDS_HELD = "FUNDS_HELD";
  private static final String PAYMENT_STATUS_ITEM_RECEIVED = "ITEM_RECEIVED";
  private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
  private static final String PAYMENT_STATUS_EXPIRED = "EXPIRED";

  private WonOrderViewModelMapper() {
    // Utility class.
  }

  /**
   * Chuyển auction DTO sang đơn hàng đã thắng.
   *
   * @param auction auction DTO server trả về
   * @return view model dùng để hiển thị trong màn Đơn hàng của tôi
   */
  public static WonOrderViewModel toViewModel(AuctionDTOs.AuctionDTO auction) {
    AuctionDTOs.ItemDTO item = auction == null ? null : auction.getItem();

    String auctionStatus = auction == null ? "" : safe(auction.getStatus());
    String paymentStatus = auction == null ? "" : safe(auction.getPaymentStatus());

    String normalizedAuctionStatus = normalize(auctionStatus);
    String normalizedPaymentStatus = normalize(paymentStatus);

    boolean finished = AUCTION_STATUS_FINISHED.equals(normalizedAuctionStatus);
    boolean paid = AUCTION_STATUS_PAID.equals(normalizedAuctionStatus);

    boolean pendingPayment =
        finished
            || PAYMENT_STATUS_PENDING.equals(normalizedPaymentStatus)
            || (paid && normalizedPaymentStatus.isBlank());

    boolean canPay = pendingPayment;
    boolean canConfirmReceipt =
        paid && PAYMENT_STATUS_FUNDS_HELD.equals(normalizedPaymentStatus);
    boolean canSubmitReport =
        paid && PAYMENT_STATUS_ITEM_RECEIVED.equals(normalizedPaymentStatus);
    boolean completed = paid && PAYMENT_STATUS_COMPLETED.equals(normalizedPaymentStatus);

    return new WonOrderViewModel(
        auction == null ? "" : safe(auction.getId()),
        itemName(item),
        sellerUsername(item),
        sellerText(item),
        auction == null ? "--" : CurrencyUtil.formatVnd(auction.getCurrentPrice()),
        auctionStatus,
        paymentStatus,
        statusText(normalizedAuctionStatus, normalizedPaymentStatus),
        actionHintText(normalizedAuctionStatus, normalizedPaymentStatus),
        primaryImageUrl(item),
        auction == null ? "--" : DateTimeUtil.formatDateTime(auction.getConfirmReceiptDeadline()),
        auction == null ? "--" : DateTimeUtil.formatDateTime(auction.getReportDeadline()),
        canPay,
        canConfirmReceipt,
        canSubmitReport,
        completed,
        finished);
  }

  /** Chuyển danh sách auction DTO sang danh sách view model đơn hàng. */
  public static List<WonOrderViewModel> toViewModels(List<AuctionDTOs.AuctionDTO> auctions) {
    if (auctions == null) {
      return List.of();
    }

    return auctions.stream()
        .map(WonOrderViewModelMapper::toViewModel)
        .toList();
  }

  private static String itemName(AuctionDTOs.ItemDTO item) {
    if (item == null || isBlank(item.getName())) {
      return "Sản phẩm đấu giá";
    }
    return item.getName().trim();
  }

  private static String sellerUsername(AuctionDTOs.ItemDTO item) {
    if (item == null || isBlank(item.getSellerUsername())) {
      return "--";
    }
    return item.getSellerUsername().trim();
  }

  private static String sellerText(AuctionDTOs.ItemDTO item) {
    return "Người bán: " + sellerUsername(item);
  }

  private static String primaryImageUrl(AuctionDTOs.ItemDTO item) {
    if (item == null || item.getImageUrls() == null || item.getImageUrls().isEmpty()) {
      return "";
    }

    return item.getImageUrls().stream()
        .filter(url -> url != null && !url.isBlank())
        .map(String::trim)
        .findFirst()
        .orElse("");
  }

  private static String statusText(String auctionStatus, String paymentStatus) {
    if (AUCTION_STATUS_FINISHED.equals(auctionStatus)
        || PAYMENT_STATUS_PENDING.equals(paymentStatus)) {
      return "Chờ thanh toán";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)
        && PAYMENT_STATUS_FUNDS_HELD.equals(paymentStatus)) {
      return "Chờ xác nhận nhận hàng";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)
        && PAYMENT_STATUS_ITEM_RECEIVED.equals(paymentStatus)) {
      return "Đã nhận hàng";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)
        && PAYMENT_STATUS_COMPLETED.equals(paymentStatus)) {
      return "Đã hoàn tất";
    }

    if (PAYMENT_STATUS_EXPIRED.equals(paymentStatus)) {
      return "Đã hết hạn";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)) {
      return "Đã thanh toán";
    }

    return auctionStatus.isBlank() ? "Không rõ" : auctionStatus;
  }

  private static String actionHintText(String auctionStatus, String paymentStatus) {
    if (AUCTION_STATUS_FINISHED.equals(auctionStatus)
        || PAYMENT_STATUS_PENDING.equals(paymentStatus)) {
      return "Hoàn tất thanh toán để tiếp tục xử lý đơn hàng.";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)
        && PAYMENT_STATUS_FUNDS_HELD.equals(paymentStatus)) {
      return "Xác nhận khi bạn đã nhận được sản phẩm.";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)
        && PAYMENT_STATUS_ITEM_RECEIVED.equals(paymentStatus)) {
      return "Bạn có thể gửi báo cáo nếu sản phẩm có vấn đề.";
    }

    if (AUCTION_STATUS_PAID.equals(auctionStatus)
        && PAYMENT_STATUS_COMPLETED.equals(paymentStatus)) {
      return "Đơn hàng đã hoàn tất.";
    }

    if (PAYMENT_STATUS_EXPIRED.equals(paymentStatus)) {
      return "Đơn hàng đã hết hạn xử lý.";
    }

    return "";
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}