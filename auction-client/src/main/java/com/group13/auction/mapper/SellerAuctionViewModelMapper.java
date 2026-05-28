package com.group13.auction.mapper;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.util.DateTimeUtil;
import com.group13.auction.viewmodel.auction.ProductSpecificationViewModel;
import com.group13.auction.viewmodel.seller.SellerAuctionRowViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mapper chuyển auction DTO sang view model phục vụ màn quản lý phiên của người bán. */
public final class SellerAuctionViewModelMapper {

  private SellerAuctionViewModelMapper() {
    // Utility class.
  }

  /**
   * Lọc và map danh sách auction chung thành danh sách phiên thuộc người bán hiện tại.
   *
   * <p>Hiện backend chưa có API riêng cho danh sách phiên của từng người bán, nên client lọc theo
   * {@code sellerId} hoặc {@code sellerUsername} từ DTO trả về. Đây chỉ là bước lọc hiển thị; kiểm
   * tra quyền cuối cùng vẫn do server xử lý.
   *
   * @param auctions danh sách auction từ server
   * @param session session hiện tại
   * @return danh sách row view model của người bán
   */
  public static List<SellerAuctionRowViewModel> toSellerRows(
      List<AuctionDTOs.AuctionDTO> auctions, UserSession session) {
    if (auctions == null || session == null) {
      return List.of();
    }

    return auctions.stream()
        .filter(auction -> belongsToSeller(auction, session))
        .map(SellerAuctionViewModelMapper::toRow)
        .toList();
  }

  /**
   * Chuyển một auction DTO sang row view model.
   *
   * @param auction auction DTO từ server
   * @return row view model
   */
  public static SellerAuctionRowViewModel toRow(AuctionDTOs.AuctionDTO auction) {
    AuctionDTOs.ItemDTO item = auction == null ? null : auction.getItem();
    String status = normalize(auction == null ? null : auction.getStatus());
    boolean isOpen = "OPEN".equals(status);

    LocalDateTime startTime = auction == null ? null : auction.getStartTime();
    LocalDateTime endTime = effectiveEndTime(auction);

    return new SellerAuctionRowViewModel(
        auction == null || auction.getId() == null ? "" : auction.getId(),
        itemName(item),
        categoryText(item),
        statusText(status),
        CurrencyUtil.formatVnd(auction == null ? 0 : auction.getCurrentPrice()),
        CurrencyUtil.formatVnd(item == null ? 0 : item.getStartingPrice()),
        CurrencyUtil.formatVnd(auction == null ? 0 : auction.getReservePrice()),
        DateTimeUtil.formatDateTime(startTime),
        DateTimeUtil.formatDateTime(endTime),
        startTime,
        endTime,
        Math.max(0, auction == null ? 0 : auction.getViewerCount()) + " lượt truy cập",
        imageUrls(item),
        productSpecifications(item),
        isOpen,
        isOpen);
  }

  private static boolean belongsToSeller(AuctionDTOs.AuctionDTO auction, UserSession session) {
    if (auction == null || auction.getItem() == null || session == null) {
      return false;
    }

    AuctionDTOs.ItemDTO item = auction.getItem();
    if (hasText(item.getSellerId()) && item.getSellerId().equals(session.getUserId())) {
      return true;
    }

    return hasText(item.getSellerUsername())
        && item.getSellerUsername().equalsIgnoreCase(session.getUsername());
  }

  private static LocalDateTime effectiveEndTime(AuctionDTOs.AuctionDTO auction) {
    if (auction == null) {
      return null;
    }
    return auction.getExtendedEndTime() != null
        ? auction.getExtendedEndTime()
        : auction.getEndTime();
  }

  private static String itemName(AuctionDTOs.ItemDTO item) {
    if (item == null || !hasText(item.getName())) {
      return "Phiên đấu giá chưa có tên";
    }
    return item.getName();
  }

  private static List<String> imageUrls(AuctionDTOs.ItemDTO item) {
    if (item == null || item.getImageUrls() == null || item.getImageUrls().isEmpty()) {
      return List.of();
    }

    return item.getImageUrls().stream()
        .filter(url -> url != null && !url.isBlank())
        .map(String::trim)
        .toList();
  }

  private static List<ProductSpecificationViewModel> productSpecifications(
      AuctionDTOs.ItemDTO item) {
    if (item == null || item.getExtraFields() == null || item.getExtraFields().isEmpty()) {
      return List.of();
    }

    Map<String, Object> fields = item.getExtraFields();
    List<ProductSpecificationViewModel> specifications = new ArrayList<>();

    switch (normalize(item.getCategory())) {
      case "ELECTRONICS" -> {
        addTextSpecification(specifications, "Thương hiệu", fields.get("brand"));
        addNumberSpecification(specifications, "Bảo hành", fields.get("warrantyMonths"), " tháng");
        addTextSpecification(specifications, "Tình trạng", fields.get("condition"));
      }
      case "ART" -> {
        addTextSpecification(specifications, "Nghệ sĩ", fields.get("artist"));
        addNumberSpecification(specifications, "Năm sáng tác", fields.get("yearCreated"), "");
        addTextSpecification(specifications, "Chất liệu", fields.get("medium"));
      }
      case "VEHICLE" -> {
        addTextSpecification(specifications, "Nhà sản xuất", fields.get("manufacturer"));
        addNumberSpecification(specifications, "Năm sản xuất", fields.get("year"), "");
        addNumberSpecification(specifications, "Số km đã đi", fields.get("mileage"), " km");
      }
      default -> { }
    }

    addRemainingSpecifications(specifications, fields, knownSpecificationKeys(item.getCategory()));

    return List.copyOf(specifications);
  }

  private static Set<String> knownSpecificationKeys(String category) {
    return switch (normalize(category)) {
      case "ELECTRONICS" -> Set.of("brand", "warrantyMonths", "condition");
      case "ART" -> Set.of("artist", "yearCreated", "medium");
      case "VEHICLE" -> Set.of("manufacturer", "year", "mileage");
      default -> Set.of();
    };
  }

  private static void addRemainingSpecifications(
      List<ProductSpecificationViewModel> specifications,
      Map<String, Object> fields,
      Set<String> handledKeys) {
    fields.forEach(
        (key, value) -> {
          if (key == null || !handledKeys.contains(key)) {
            addTextSpecification(specifications, humanReadableKey(key), value);
          }
        });
  }

  private static void addTextSpecification(
      List<ProductSpecificationViewModel> specifications, String label, Object value) {
    String text = objectToText(value);
    if (hasText(label) && hasText(text)) {
      specifications.add(new ProductSpecificationViewModel(label, text));
    }
  }

  private static void addNumberSpecification(
      List<ProductSpecificationViewModel> specifications,
      String label,
      Object value,
      String suffix) {
    String numberText = numberToText(value);
    if (hasText(numberText)) {
      specifications.add(
          new ProductSpecificationViewModel(label, numberText + (suffix == null ? "" : suffix)));
    }
  }

  private static String objectToText(Object value) {
    if (value == null) {
      return "";
    }

    String text = String.valueOf(value).trim();
    return "null".equalsIgnoreCase(text) ? "" : text;
  }

  private static String numberToText(Object value) {
    if (value == null) {
      return "";
    }

    if (value instanceof Number number) {
      long longValue = number.longValue();
      if (Math.abs(number.doubleValue() - longValue) < 0.000_001D) {
        return String.format("%,d", longValue);
      }
      return String.format("%,.2f", number.doubleValue());
    }

    String text = objectToText(value);
    if (!hasText(text)) {
      return "";
    }

    try {
      double number = Double.parseDouble(text);
      long longValue = (long) number;
      if (Math.abs(number - longValue) < 0.000_001D) {
        return String.format("%,d", longValue);
      }
      return String.format("%,.2f", number);
    } catch (NumberFormatException exception) {
      return text;
    }
  }

  private static String humanReadableKey(String key) {
    if (!hasText(key)) {
      return "Thông tin";
    }

    String withSpaces =
        key.trim().replace('_', ' ').replace('-', ' ').replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
  }

  private static String categoryText(AuctionDTOs.ItemDTO item) {
    if (item == null || !hasText(item.getCategory())) {
      return "Khác";
    }

    return switch (normalize(item.getCategory())) {
      case "ELECTRONICS" -> "Điện tử";
      case "ART" -> "Nghệ thuật";
      case "VEHICLE" -> "Phương tiện";
      default -> item.getCategory();
    };
  }

  private static String statusText(String status) {
    return switch (normalize(status)) {
      case "OPEN" -> "Sắp mở";
      case "RUNNING" -> "Đang đấu giá";
      case "FINISHED" -> "Đã kết thúc";
      case "PAID" -> "Đã thanh toán";
      case "CANCELED" -> "Đã hủy";
      case "RESERVE_NOT_MET" -> "Chưa đạt giá sàn";
      default -> "Không rõ";
    };
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
