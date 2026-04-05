package com.group13.auction.model.item;

import com.group13.auction.model.user.Seller;
import java.time.LocalDateTime;

/** Sản phẩm điện tử. */
public class Electronics extends Item {

  private final String brand;
  private final int warrantyMonths;
  private final String condition;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh sản phẩm điện tử mới.
   *
   * @param name           tên sản phẩm
   * @param description    mô tả
   * @param startingPrice  giá khởi điểm
   * @param seller         người bán
   * @param brand          thương hiệu
   * @param warrantyMonths số tháng bảo hành
   * @param condition      tình trạng sản phẩm
   * @return Electronics mới
   */
  protected static Electronics create(String name, String description, double startingPrice,
                                      Seller seller, String brand, int warrantyMonths, String condition) {
    return new Electronics(name, description, startingPrice, seller, brand, warrantyMonths, condition);
  }

  /**
   * Hồi sinh Electronics từ DB.
   *
   * @param id             id gốc
   * @param createdAt      thời gian tạo gốc
   * @param updatedAt      thời gian cập nhật gốc
   * @param name           tên sản phẩm
   * @param description    mô tả
   * @param startingPrice  giá khởi điểm
   * @param seller         người bán
   * @param brand          thương hiệu
   * @param warrantyMonths số tháng bảo hành
   * @param condition      tình trạng
   * @return Electronics được phục hồi
   */
  public static Electronics reconstitute(String id, LocalDateTime createdAt,
                                            LocalDateTime updatedAt, String name, String description, double startingPrice,
                                            Seller seller, String brand, int warrantyMonths, String condition) {
    return new Electronics(id, createdAt, updatedAt, name, description, startingPrice,
            seller, brand, warrantyMonths, condition);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Electronics(String name, String description, double startingPrice,
                      Seller seller, String brand, int warrantyMonths, String condition) {
    super(name, description, startingPrice, ItemCategory.ELECTRONICS, seller);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  private Electronics(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                      String name, String description, double startingPrice, Seller seller,
                      String brand, int warrantyMonths, String condition) {
    super(id, createdAt, updatedAt, name, description, startingPrice,
            ItemCategory.ELECTRONICS, seller);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getBrand() { return brand; }
  public int getWarrantyMonths() { return warrantyMonths; }
  public String getCondition() { return condition; }

  @Override
  public void printInfo() {
    System.out.println("=== ELECTRONICS ======================");
    System.out.printf("Tên           : %s%n", getName());
    System.out.printf("Thương hiệu   : %s%n", brand);
    System.out.printf("Bảo hành      : %d tháng%n", warrantyMonths);
    System.out.printf("Tình trạng    : %s%n", condition);
    System.out.printf("Giá khởi điểm : %.0f%n", getStartingPrice());
    System.out.println("======================================");
  }
}