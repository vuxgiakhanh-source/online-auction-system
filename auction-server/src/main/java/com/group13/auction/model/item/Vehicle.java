package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Phương tiện. */
public class Vehicle extends Item {

  private final String manufacturer;
  private final int year;
  private final double mileage;

  // Static factory method

  protected static Vehicle create(String name, String description, double startingPrice,
                                  NormalUser seller, String manufacturer, int year, double mileage) {
    return new Vehicle(name, description, startingPrice, seller, manufacturer, year, mileage);
  }

  public static Vehicle reconstitute(String id, LocalDateTime createdAt,
                                     LocalDateTime updatedAt, String name, String description, double startingPrice,
                                     NormalUser seller, String manufacturer, int year, double mileage) {
    return new Vehicle(id, createdAt, updatedAt, name, description, startingPrice,
            seller, manufacturer, year, mileage);
  }

  // Private Constructors: Ngăn chặn new cứng

  /** Khai sinh */
  private Vehicle(String name, String description, double startingPrice,
                  NormalUser seller, String manufacturer, int year, double mileage) {
    super(name, description, startingPrice, ItemCategory.VEHICLE, seller);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  /** Hồi sinh từ DB */
  private Vehicle(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                  String name, String description, double startingPrice, NormalUser seller,
                  String manufacturer, int year, double mileage) {
    super(id, createdAt, updatedAt, name, description, startingPrice, ItemCategory.VEHICLE, seller);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  //Getters
  public String getManufacturer() { return manufacturer; }
  public int getYear() { return year; }
  public double getMileage() { return mileage; }

  @Override
  public void printInfo() {
    System.out.println("THÔNG TIN SẢN PHẨM - VEHICLE");
    System.out.printf("Tên : %s%n", getName());
    System.out.printf("Hãng : %s%n", manufacturer);
    System.out.printf("Năm sản xuất : %d%n", year);
    System.out.printf("Số km : %.0f%n", mileage);
    System.out.printf("Giá khởi điểm: %.0f%n", getStartingPrice());
    System.out.println("======================================");
  }
}