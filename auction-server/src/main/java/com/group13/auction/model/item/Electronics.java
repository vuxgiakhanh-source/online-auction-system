package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Sản phẩm điện tử. */
public class Electronics extends Item {

  private final String brand;
  private final int warrantyMonths;
  private final String condition;

  // Static factory method

  protected static Electronics create(String name, String description, double startingPrice,
                                      NormalUser seller, String brand, int warrantyMonths, String condition) {
    return new Electronics(name, description, startingPrice, seller, brand, warrantyMonths, condition);
  }

  public static Electronics reconstitute(String id, LocalDateTime createdAt,
                                         LocalDateTime updatedAt, String name, String description, double startingPrice,
                                         NormalUser seller, String brand, int warrantyMonths, String condition) {
    return new Electronics(id, createdAt, updatedAt, name, description, startingPrice,
            seller, brand, warrantyMonths, condition);
  }

  // Private Constructors: Ngăn chặn new cứng

  /** Khai sinh */
  private Electronics(String name, String description, double startingPrice,
                      NormalUser seller, String brand, int warrantyMonths, String condition) {
    super(name, description, startingPrice, ItemCategory.ELECTRONICS, seller);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  /** Hồi sinh từ DB */
  private Electronics(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                      String name, String description, double startingPrice, NormalUser seller,
                      String brand, int warrantyMonths, String condition) {
    super(id, createdAt, updatedAt, name, description, startingPrice, ItemCategory.ELECTRONICS, seller);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  // Getters
  public String getBrand() { return brand; }
  public int getWarrantyMonths() { return warrantyMonths; }
  public String getCondition() { return condition; }

  @Override
  public void printInfo() {
    System.out.println("THÔNG TIN SẢN PHẨM - ELECTRONICS");
    System.out.printf("Tên : %s%n", getName());
    System.out.printf("Hãng : %s%n", brand);
    System.out.printf("Bảo hành : %d tháng%n", warrantyMonths);
    System.out.printf("Tình trạng : %s%n", condition);
    System.out.printf("Giá khởi điểm: %.0f%n", getStartingPrice());
    System.out.println("======================================");
  }
}