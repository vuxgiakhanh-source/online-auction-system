package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phương tiện. */
public class Vehicle extends Item {

  private static final Logger log = LoggerFactory.getLogger(Vehicle.class);

  private final String manufacturer;
  private final int year;
  private final double mileage;

  // ── Static factory methods ────────────────────────────────────────────────

  /** Khai sinh — không ảnh (backward-compatible). */
  protected static Vehicle create(
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String manufacturer,
      int year,
      double mileage) {
    return new Vehicle(
        name, description, startingPrice, seller, manufacturer, year, mileage, List.of());
  }

  /** Khai sinh — có ảnh. */
  protected static Vehicle create(
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String manufacturer,
      int year,
      double mileage,
      List<String> imageUrls) {
    return new Vehicle(
        name, description, startingPrice, seller, manufacturer, year, mileage, imageUrls);
  }

  /** Hồi sinh từ DB — không ảnh (backward-compatible, TestFixture dùng). */
  public static Vehicle reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String manufacturer,
      int year,
      double mileage) {
    return new Vehicle(
        id,
        createdAt,
        updatedAt,
        name,
        description,
        startingPrice,
        seller,
        manufacturer,
        year,
        mileage,
        List.of());
  }

  /** Hồi sinh từ DB — có ảnh (ItemDAO dùng). */
  public static Vehicle reconstitute(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String manufacturer,
      int year,
      double mileage,
      List<String> imageUrls) {
    return new Vehicle(
        id,
        createdAt,
        updatedAt,
        name,
        description,
        startingPrice,
        seller,
        manufacturer,
        year,
        mileage,
        imageUrls);
  }

  // ── Private constructors ──────────────────────────────────────────────────

  private Vehicle(
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String manufacturer,
      int year,
      double mileage,
      List<String> imageUrls) {
    super(name, description, startingPrice, ItemCategory.VEHICLE, seller, imageUrls);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  private Vehicle(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      NormalUser seller,
      String manufacturer,
      int year,
      double mileage,
      List<String> imageUrls) {
    super(
        id,
        createdAt,
        updatedAt,
        name,
        description,
        startingPrice,
        ItemCategory.VEHICLE,
        seller,
        imageUrls);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  // ── Getters ───────────────────────────────────────────────────────────────

  public String getManufacturer() {
    return manufacturer;
  }

  public int getYear() {
    return year;
  }

  public double getMileage() {
    return mileage;
  }

  @Override
  public void printInfo() {
    log.info("THÔNG TIN SẢN PHẨM - VEHICLE");
    log.info("Tên          : {}", getName());
    log.info("Hãng         : {}", manufacturer);
    log.info("Năm sản xuất : {}", year);
    log.info("Số km        : {}", String.format("%.0f", mileage));
    log.info("Giá khởi điểm: {}", getStartingPrice());
    log.info("Số ảnh       : {}", getImageUrls().size());
    log.info("======================================");
  }
}
