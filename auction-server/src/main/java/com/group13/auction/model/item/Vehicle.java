package com.group13.auction.model.item;

import com.group13.auction.model.user.Seller;
import java.time.LocalDateTime;

/** Phương tiện. */
public class Vehicle extends Item {

  private final String manufacturer;
  private final int year;
  private final double mileage;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh phương tiện mới.
   *
   * @param name          tên phương tiện
   * @param description   mô tả
   * @param startingPrice giá khởi điểm
   * @param seller        người bán
   * @param manufacturer  hãng sản xuất
   * @param year          năm sản xuất
   * @param mileage       số km đã đi (Odometer)
   * @return Vehicle mới
   */
  protected static Vehicle create(String name, String description, double startingPrice,
                                  Seller seller, String manufacturer, int year, double mileage) {
    return new Vehicle(name, description, startingPrice, seller, manufacturer, year, mileage);
  }

  /**
   * Hồi sinh Vehicle từ DB.
   *
   * @param id            id gốc
   * @param createdAt     thời gian tạo gốc
   * @param updatedAt     thời gian cập nhật gốc
   * @param name          tên phương tiện
   * @param description   mô tả
   * @param startingPrice giá khởi điểm
   * @param seller        người bán
   * @param manufacturer  hãng sản xuất
   * @param year          năm sản xuất
   * @param mileage       số km
   * @return Vehicle được phục hồi
   */
  public static Vehicle reconstitute(String id, LocalDateTime createdAt,
                                        LocalDateTime updatedAt, String name, String description, double startingPrice,
                                        Seller seller, String manufacturer, int year, double mileage) {
    return new Vehicle(id, createdAt, updatedAt, name, description, startingPrice,
            seller, manufacturer, year, mileage);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Vehicle(String name, String description, double startingPrice,
                  Seller seller, String manufacturer, int year, double mileage) {
    super(name, description, startingPrice, ItemCategory.VEHICLE, seller);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  private Vehicle(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                  String name, String description, double startingPrice, Seller seller,
                  String manufacturer, int year, double mileage) {
    super(id, createdAt, updatedAt, name, description, startingPrice,
            ItemCategory.VEHICLE, seller);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getManufacturer() { return manufacturer; }
  public int getYear() { return year; }
  public double getMileage() { return mileage; }

  @Override
  public void printInfo() {
    System.out.println("=== VEHICLE ==========================");
    System.out.printf("Tên           : %s%n", getName());
    System.out.printf("Hãng          : %s%n", manufacturer);
    System.out.printf("Năm sản xuất  : %d%n", year);
    System.out.printf("Số km         : %.0f%n", mileage);
    System.out.printf("Giá khởi điểm : %.0f%n", getStartingPrice());
    System.out.println("======================================");
  }
}