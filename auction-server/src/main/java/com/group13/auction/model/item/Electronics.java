package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sản phẩm điện tử. */
public class Electronics extends Item {

  private static final Logger log = LoggerFactory.getLogger(Electronics.class);

  private final String brand;
  private final int warrantyMonths;
  private final String condition;

  // Static factory methods

  /** Khai sinh — không ảnh (backward-compatible). */
  protected static Electronics create(
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String brand,
      int warrantyMonths,
      String condition) {
    return new Electronics(
        name, description, startingPrice, seller, brand, warrantyMonths, condition, List.of());
  }

  /** Khai sinh — có ảnh. */
  protected static Electronics create(
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String brand,
      int warrantyMonths,
      String condition,
      List<String> imageUrls) {
    return new Electronics(
        name, description, startingPrice, seller, brand, warrantyMonths, condition, imageUrls);
  }

  /** Hồi sinh từ DB — không ảnh (backward-compatible, TestFixture dùng). */
  public static Electronics reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String brand,
      int warrantyMonths,
      String condition) {
    return new Electronics(
        id,
        createdAt,
        updatedAt,
        name,
        description,
        startingPrice,
        seller,
        brand,
        warrantyMonths,
        condition,
        List.of());
  }

  /** Hồi sinh từ DB — có ảnh (ItemDAO dùng). */
  public static Electronics reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String brand,
      int warrantyMonths,
      String condition,
      List<String> imageUrls) {
    return new Electronics(
        id,
        createdAt,
        updatedAt,
        name,
        description,
        startingPrice,
        seller,
        brand,
        warrantyMonths,
        condition,
        imageUrls);
  }

  // Private constructors

  private Electronics(
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String brand,
      int warrantyMonths,
      String condition,
      List<String> imageUrls) {
    super(name, description, startingPrice, ItemCategory.ELECTRONICS, seller, imageUrls);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  private Electronics(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String brand,
      int warrantyMonths,
      String condition,
      List<String> imageUrls) {
    super(
        id,
        createdAt,
        updatedAt,
        name,
        description,
        startingPrice,
        ItemCategory.ELECTRONICS,
        seller,
        imageUrls);
    this.brand = brand;
    this.warrantyMonths = warrantyMonths;
    this.condition = condition;
  }

  // Getters

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
    log.info("THÔNG TIN SẢN PHẨM - ELECTRONICS");
    log.info("Tên          : {}", getName());
    log.info("Hãng         : {}", brand);
    log.info("Bảo hành     : {} tháng", warrantyMonths);
    log.info("Tình trạng   : {}", condition);
    log.info("Giá khởi điểm: {}", getStartingPrice());
    log.info("Số ảnh       : {}", getImageUrls().size());
    log.info("======================================");
  }
}
