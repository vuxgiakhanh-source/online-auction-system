package com.group13.auction.model.item;

import com.group13.auction.model.user.Seller;
import java.time.LocalDateTime;

/** Sản phẩm điện tử. */
public class Electronics extends Item {

  private final String brand;
  private final int warrantyMonths;
  private final String condition;

  // ── Constructor khai sinh ──────────────────────────────────────────────────

  public Electronics(String name, String description, double startingPrice,
      Seller seller, String brand, int warrantyMonths, String condition) {
    super(name, description, startingPrice, ItemCategory.ELECTRONICS, seller);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  // ── Constructor hồi sinh ──────────────────────────────────────────────────

  public Electronics(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String name, String description, double startingPrice, Seller seller,
      String brand, int warrantyMonths, String condition) {
    super(id, createdAt, updatedAt, name, description, startingPrice,
        ItemCategory.ELECTRONICS, seller);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  public String getBrand() {
    return brand;
  }

  public int getWarrantyMonths() {
    return warrantyMonths;
  }

  public String getCondition() {
    return condition;
  }

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