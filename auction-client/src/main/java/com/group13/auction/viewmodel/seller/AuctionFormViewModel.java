package com.group13.auction.viewmodel.seller;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * View model đại diện dữ liệu form tạo phiên đấu giá của Seller.
 *
 * <p>Lớp này chỉ validate dữ liệu nhập cơ bản ở phía client và chuyển sang DTO dùng chung trong
 * {@code auction-common}. Nghiệp vụ chính như quyền Seller, rating, trạng thái phiên và điều kiện
 * hợp lệ cuối cùng vẫn do server xử lý.
 */
public final class AuctionFormViewModel {

  public static final int MAX_IMAGE_COUNT = 3;

  private final String itemName;
  private final String itemDescription;
  private final String itemCategory;
  private final double startingPrice;
  private final double reservePrice;
  private final LocalDateTime startTime;
  private final LocalDateTime endTime;
  private final Map<String, Object> itemExtraFields;
  private final List<Path> imagePaths;

  /**
   * Tạo view model cho form tạo phiên đấu giá.
   *
   * @param itemName tên sản phẩm
   * @param itemDescription mô tả sản phẩm
   * @param itemCategory loại sản phẩm: {@code ELECTRONICS}, {@code ART}, {@code VEHICLE}
   * @param startingPrice giá khởi điểm
   * @param reservePrice giá sàn bí mật
   * @param startTime thời gian bắt đầu
   * @param endTime thời gian kết thúc
   * @param itemExtraFields thông tin mở rộng theo loại sản phẩm
   * @param imagePaths danh sách ảnh local được chọn ở client
   */
  public AuctionFormViewModel(
      String itemName,
      String itemDescription,
      String itemCategory,
      double startingPrice,
      double reservePrice,
      LocalDateTime startTime,
      LocalDateTime endTime,
      Map<String, Object> itemExtraFields,
      List<Path> imagePaths) {
    this.itemName = trimToEmpty(itemName);
    this.itemDescription = trimToEmpty(itemDescription);
    this.itemCategory = trimToEmpty(itemCategory).toUpperCase();
    this.startingPrice = startingPrice;
    this.reservePrice = reservePrice;
    this.startTime = startTime;
    this.endTime = endTime;
    this.itemExtraFields = normalizeExtraFields(itemExtraFields);
    this.imagePaths = normalizeImagePaths(imagePaths);
  }

  /**
   * Validate form tạo phiên ở mức cơ bản trước khi gửi request.
   *
   * <p>Đây không thay thế validation nghiệp vụ của server. Client chỉ chặn lỗi nhập liệu rõ ràng để
   * tránh gửi request vô nghĩa.
   */
  public void validateForCreate() {
    if (itemName.isBlank()) {
      throw new IllegalArgumentException("Tên sản phẩm không được để trống.");
    }
    if (itemDescription.isBlank()) {
      throw new IllegalArgumentException("Mô tả sản phẩm không được để trống.");
    }
    if (!isSupportedCategory(itemCategory)) {
      throw new IllegalArgumentException("Loại sản phẩm chỉ hỗ trợ ELECTRONICS, ART hoặc VEHICLE.");
    }
    if (startingPrice <= 0) {
      throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0.");
    }
    if (reservePrice <= 0) {
      throw new IllegalArgumentException("Giá sàn phải lớn hơn 0.");
    }
    if (startTime == null) {
      throw new IllegalArgumentException("Thời gian bắt đầu không được để trống.");
    }
    if (endTime == null) {
      throw new IllegalArgumentException("Thời gian kết thúc không được để trống.");
    }
    if (!endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
    }
    if (imagePaths.size() > MAX_IMAGE_COUNT) {
      throw new IllegalArgumentException("Chỉ được chọn tối đa " + MAX_IMAGE_COUNT + " ảnh.");
    }
  }

  /**
   * Chuyển form sang DTO request của {@code auction-common} khi không có ảnh upload.
   *
   * @return request tạo phiên đấu giá
   */
  public AuctionDTOs.CreateAuctionRequestDTO toCreateRequest() {
    return toCreateRequest(List.of());
  }

  /**
   * Chuyển form sang DTO request của {@code auction-common}.
   *
   * <p>{@code imageUrls} phải là URL server trả về sau khi upload ảnh, ví dụ {@code
   * /uploads/items/{uuid}.jpg}.
   *
   * @param imageUrls danh sách URL ảnh đã upload
   * @return request tạo phiên đấu giá
   */
  public AuctionDTOs.CreateAuctionRequestDTO toCreateRequest(List<String> imageUrls) {
    validateForCreate();

    AuctionDTOs.CreateAuctionRequestDTO request = new AuctionDTOs.CreateAuctionRequestDTO();
    request.setItemName(itemName);
    request.setItemDescription(itemDescription);
    request.setItemCategory(itemCategory);
    request.setStartingPrice(startingPrice);
    request.setReservePrice(reservePrice);
    request.setStartTime(startTime);
    request.setEndTime(endTime);
    request.setItemExtraFields(itemExtraFields);
    request.setImageUrls(normalizeImageUrls(imageUrls));
    return request;
  }

  public String itemName() {
    return itemName;
  }

  public String itemDescription() {
    return itemDescription;
  }

  public String itemCategory() {
    return itemCategory;
  }

  public double startingPrice() {
    return startingPrice;
  }

  public double reservePrice() {
    return reservePrice;
  }

  public LocalDateTime startTime() {
    return startTime;
  }

  public LocalDateTime endTime() {
    return endTime;
  }

  public Map<String, Object> itemExtraFields() {
    return itemExtraFields;
  }

  public List<Path> imagePaths() {
    return imagePaths;
  }

  private static boolean isSupportedCategory(String category) {
    return "ELECTRONICS".equals(category) || "ART".equals(category) || "VEHICLE".equals(category);
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static Map<String, Object> normalizeExtraFields(Map<String, Object> fields) {
    if (fields == null || fields.isEmpty()) {
      return Map.of();
    }

    Map<String, Object> normalized = new LinkedHashMap<>();
    fields.forEach(
        (key, value) -> {
          if (key == null || key.isBlank() || value == null) {
            return;
          }
          if (value instanceof String text && text.isBlank()) {
            return;
          }
          normalized.put(key.trim(), value);
        });
    return Map.copyOf(normalized);
  }

  private static List<Path> normalizeImagePaths(List<Path> paths) {
    if (paths == null || paths.isEmpty()) {
      return List.of();
    }

    List<Path> normalized = new ArrayList<>();
    for (Path path : paths) {
      if (path != null && !normalized.contains(path)) {
        normalized.add(path);
      }
    }
    return List.copyOf(normalized);
  }

  private static List<String> normalizeImageUrls(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
      return List.of();
    }

    List<String> normalized = new ArrayList<>();
    for (String url : urls) {
      if (url != null && !url.isBlank()) {
        normalized.add(url.trim());
      }
    }
    return List.copyOf(normalized);
  }
}
