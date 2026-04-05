package com.group13.auction.model.item;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.Seller;
import java.time.LocalDateTime;

/**
 * Lớp abstract đại diện sản phẩm đưa ra đấu giá.
 */
public abstract class Item extends Entity {

  /** Loại sản phẩm. */
  public enum ItemCategory {
    ELECTRONICS,
    ART,
    VEHICLE,
    OTHER
  }

  private final String name;
  private final String description;
  private final double startingPrice;
  private final ItemCategory category;
  private final Seller seller;

  // ── Constructor khai sinh ──────────────────────────────────────────────────

  /**
   * Tạo Item mới.
   *
   * @param name          tên sản phẩm
   * @param description   mô tả
   * @param startingPrice giá khởi điểm (> 0)
   * @param category      loại sản phẩm
   * @param seller        người bán
   */
  protected Item(String name, String description, double startingPrice,
      ItemCategory category, Seller seller) {
    super();
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
  }

  // ── Constructor hồi sinh ──────────────────────────────────────────────────

  /**
   * Khôi phục Item từ DB.
   *
   * @param id            id gốc
   * @param createdAt     thời gian tạo gốc
   * @param updatedAt     thời gian cập nhật gốc
   * @param name          tên sản phẩm
   * @param description   mô tả
   * @param startingPrice giá khởi điểm
   * @param category      loại sản phẩm
   * @param seller        người bán
   */
  public Item(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String name, String description, double startingPrice,
      ItemCategory category, Seller seller) {
    super(id, createdAt, updatedAt);
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
  }

  /**
   * Kiểm tra dữ liệu hợp lệ trước khi đưa lên đấu giá.
   *
   * @return true nếu hợp lệ
   */
  public boolean validate() {
    return name != null && !name.isBlank()
        && startingPrice > 0
        && seller != null;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public double getStartingPrice() {
    return startingPrice;
  }

  public ItemCategory getCategory() {
    return category;
  }

  public Seller getSeller() {
    return seller;
  }

  @Override
  public abstract void printInfo();
}