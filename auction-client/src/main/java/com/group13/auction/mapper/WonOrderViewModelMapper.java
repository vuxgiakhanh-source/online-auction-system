package com.group13.auction.mapper;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.viewmodel.order.WonOrderViewModel;
import java.util.List;

/** Mapper chuyển auction DTO đã thắng sang view model đơn hàng phía client. */
public final class WonOrderViewModelMapper {

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
    String status = auction == null ? "" : safe(auction.getStatus());
    String normalizedStatus = normalize(status);

    return new WonOrderViewModel(
        auction == null ? "" : safe(auction.getId()),
        itemName(item),
        sellerUsername(item),
        sellerText(item),
        auction == null ? "--" : CurrencyUtil.formatVnd(auction.getCurrentPrice()),
        status,
        statusText(status),
        primaryImageUrl(item),
        "PAID".equals(normalizedStatus),
        "FINISHED".equals(normalizedStatus));
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

  private static String statusText(String status) {
    return switch (normalize(status)) {
      case "FINISHED" -> "Chờ thanh toán";
      case "PAID" -> "Đã thanh toán";
      default -> isBlank(status) ? "Không rõ" : status.trim();
    };
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