package com.group13.auction.model.item;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Lớp abstract đại diện sản phẩm đưa ra đấu giá. */
public abstract class Item extends Entity {

  /** Loại sản phẩm. */
  public enum ItemCategory { ELECTRONICS, ART, VEHICLE, OTHER }

  private final String       name;
  private final String       description;
  private final double       startingPrice;
  private final ItemCategory category;
  private final NormalUser   seller;

  // ── Constructor khai sinh ──────────────────────────────────────────────

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
                 ItemCategory category, NormalUser seller) {
    super();
    this.name          = name;
    this.description   = description;
    this.startingPrice = startingPrice;
    this.category      = category;
    this.seller        = seller;
  }

  // ── Constructor hồi sinh ──────────────────────────────────────────────

  /**
   * Khôi phục Item từ DB.
   */
  public Item(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
              String name, String description, double startingPrice,
              ItemCategory category, NormalUser seller) {
    super(id, createdAt, updatedAt);
    this.name          = name;
    this.description   = description;
    this.startingPrice = startingPrice;
    this.category      = category;
    this.seller        = seller;
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

  public String       getName()          { return name; }
  public String       getDescription()   { return description; }
  public double       getStartingPrice() { return startingPrice; }
  public ItemCategory getCategory()      { return category; }
  public NormalUser   getSeller()        { return seller; }

  @Override
  public abstract void printInfo();
}