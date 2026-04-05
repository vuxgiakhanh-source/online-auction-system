package com.group13.auction.model.item;

import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Seller;
import com.group13.auction.service.IRatingService;

/**
 * Factory tạo Item — tập trung validate và khởi tạo.
 * ID được sinh bởi Entity (UUID).
 */
public abstract class ItemFactory {

  private static ItemFactory instance;
  private final IRatingService ratingService;

  protected ItemFactory(IRatingService ratingService) {
    this.ratingService = ratingService;
  }

  /**
   * Logic chung cho mọi loại Item.
   * @param name            tên sản phẩm
   * @param description     mô tả
   * @param startingPrice   giá khởi điểm
   * @param seller          người bán
   * @param args       các tham số khác tùy thuộc vào ItemCategory
   * @return gọi tới hàm createProduct()
   */
  public Item createItem(String name, String description, double startingPrice, Seller seller, Object... args) {
    validateCommon(name, startingPrice, seller);
    return createProduct(name, description, startingPrice, seller, args);
  }

  /** Cho lớp con tự định nghĩa cách 1 đối tượng của chúng
   * được tạo ra như thế nào.
   */
  protected abstract Item createProduct(String name, String description,
                                        double startingPrice, Seller seller, Object... args);

  // Validation logic

  private void validateCommon(String name, double startingPrice,
      Seller seller) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Tên sản phẩm không được trống.");
    }
    if (startingPrice <= 0) {
      throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0.");
    }
    if (seller == null) {
      throw new IllegalArgumentException("Thông tin người bán không hợp lệ.");
    }

    /** Hệ thống tự check quyền của Seller */
    if (!ratingService.canSellerCreateAuction(seller)) {
        throw new IllegalStateException("Tài khoản người bán đang bị khóa hoặc uy tín thấp.");
    }
  }
}