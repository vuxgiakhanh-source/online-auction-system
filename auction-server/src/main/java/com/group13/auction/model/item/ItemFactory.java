package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.iservice.IRatingService;
import java.util.List;
import java.util.Map;

/**
 * Abstract Factory tạo Item — tập trung validate và khởi tạo.
 * ID được sinh bởi Entity (UUID).
 */
public abstract class ItemFactory {

  private final IRatingService ratingService;

  protected ItemFactory(IRatingService ratingService) {
    this.ratingService = ratingService;
  }

  // ── Facade create() — không ảnh (backward-compatible) ─────────────────────

  /**
   * Tạo item không có ảnh. API cũ — tất cả code hiện tại vẫn dùng được.
   */
  public Item create(String itemCategory,
                     String name,
                     String description,
                     long startingPrice,
                     NormalUser seller,
                     Map<String, Object> extraFields) {
    return create(itemCategory, name, description, startingPrice,
            seller, extraFields, List.of());
  }

  // ── Facade create() — có ảnh ──────────────────────────────────────────────

  /**
   * Tạo item với danh sách URL ảnh đã được upload lên ImageUploadServer.
   *
   * @param imageUrls danh sách URL dạng "/uploads/items/{uuid}.jpg" (có thể rỗng)
   */
  public Item create(String itemCategory,
                     String name,
                     String description,
                     long startingPrice,
                     NormalUser seller,
                     Map<String, Object> extraFields,
                     List<String> imageUrls) {
    String cat    = itemCategory != null ? itemCategory.trim().toUpperCase() : "OTHER";
    Map<String, Object> fields = extraFields != null ? extraFields : Map.of();
    List<String> imgs          = imageUrls   != null ? imageUrls   : List.of();

    return switch (cat) {
      case "ELECTRONICS" -> new ElectronicsFactory(ratingService).createItem(
              name, description, startingPrice, seller,
              fields.get("brand"),
              ((Number) fields.getOrDefault("warrantyMonths", 0)).intValue(),
              fields.get("condition"),
              imgs);
      case "ART" -> new ArtFactory(ratingService).createItem(
              name, description, startingPrice, seller,
              fields.get("artist"),
              ((Number) fields.getOrDefault("yearCreated", 0)).intValue(),
              fields.get("medium"),
              imgs);
      case "VEHICLE" -> new VehicleFactory(ratingService).createItem(
              name, description, startingPrice, seller,
              fields.get("manufacturer"),
              ((Number) fields.getOrDefault("year", 0)).intValue(),
              ((Number) fields.getOrDefault("mileage", 0)).doubleValue(),
              imgs);
      default -> throw new IllegalArgumentException("Loại item không được hỗ trợ: " + itemCategory);
    };
  }

  // ── Template method ───────────────────────────────────────────────────────

  /**
   * createItem() — không ảnh (backward-compatible).
   * Tất cả test cũ dùng signature này, không cần sửa gì.
   */
  public Item createItem(String name, String description,
                         long startingPrice, NormalUser seller, Object... args) {
    validateCommon(name, startingPrice, seller);
    return createProduct(name, description, startingPrice, seller, args);
  }

  /** Lớp con tự định nghĩa cách tạo sản phẩm. args cuối có thể là List<String> imageUrls. */
  protected abstract Item createProduct(String name, String description,
                                        long startingPrice, NormalUser seller,
                                        Object... args);

  // ── Validation ────────────────────────────────────────────────────────────

  private void validateCommon(String name, long startingPrice, NormalUser seller) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Tên sản phẩm không được trống.");
    }
    if (startingPrice <= 0) {
      throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0.");
    }
    if (seller == null) {
      throw new IllegalArgumentException("Thông tin người bán không hợp lệ.");
    }
    if (!seller.hasRole(User.UserRole.SELLER)) {
      throw new IllegalArgumentException("Người dùng chưa được cấp role SELLER.");
    }
    if (!ratingService.canSellerCreateAuction(seller)) {
      throw new IllegalStateException("Tài khoản người bán đang bị khóa hoặc uy tín thấp.");
    }
  }
}