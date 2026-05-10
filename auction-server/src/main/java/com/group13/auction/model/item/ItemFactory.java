package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.iservice.IRatingService;
import java.util.Map;

/**
 * Abstract Factory tạo Item - tập trung validate và khởi tạo.
 * ID được sinh bởi Entity (UUID).
 */
public abstract class ItemFactory {

  private final IRatingService ratingService;

  protected ItemFactory(IRatingService ratingService) {
    this.ratingService = ratingService;
  }

  /**
   * Facade tạo item theo category — phục vụ tầng network DTO.
   *
   * <p>Để tránh handler phải biết từng factory cụ thể, method này tự dispatch sang
   * đúng factory dựa theo {@code itemCategory} và {@code extraFields}.
   */
  public Item create(String itemCategory,
                     String name,
                     String description,
                     long startingPrice,
                     NormalUser seller,
                     Map<String, Object> extraFields) {
    String cat = itemCategory != null ? itemCategory.trim().toUpperCase() : "OTHER";
    Map<String, Object> fields = extraFields != null ? extraFields : Map.of();

    return switch (cat) {
      case "ELECTRONICS" -> new ElectronicsFactory(ratingService).createItem(
              name, description, startingPrice, seller,
              fields.get("brand"),
              ((Number) fields.getOrDefault("warrantyMonths", 0)).intValue(),
              fields.get("condition")
      );
      case "ART" -> new ArtFactory(ratingService).createItem(
              name, description, startingPrice, seller,
              fields.get("artist"),
              ((Number) fields.getOrDefault("yearCreated", 0)).intValue(),
              fields.get("medium")
      );
      case "VEHICLE" -> new VehicleFactory(ratingService).createItem(
              name, description, startingPrice, seller,
              fields.get("manufacturer"),
              ((Number) fields.getOrDefault("year", 0)).intValue(),
              ((Number) fields.getOrDefault("mileage", 0)).doubleValue()
      );
      default -> throw new IllegalArgumentException("Loại item không được hỗ trợ: " + itemCategory);
    };
  }

  /**
   * Logic chung cho mọi loại Item.
   *
   * @param name tên sản phẩm
   * @param description mô tả
   * @param startingPrice giá khởi điểm
   * @param seller người bán
   * @param args các tham số khác tùy thuộc vào ItemCategory
   * @return gọi tới hàm createProduct()
   */
  public Item createItem(String name, String description,
                         long startingPrice, NormalUser seller, Object... args) {
    validateCommon(name, startingPrice, seller);
    return createProduct(name, description, startingPrice, seller, args);
  }

  /** Cho lớp con tự định nghĩa cách một đối tượng của chúng được tạo ra như thế nào. */
  protected abstract Item createProduct(String name, String description,
                                        long startingPrice, NormalUser seller, Object... args);

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
    /** Hệ thống check quyền của Seller */
    if (!ratingService.canSellerCreateAuction(seller)) {
      throw new IllegalStateException("Tài khoản người bán đang bị khóa hoặc uy tín thấp.");
    }
  }
}