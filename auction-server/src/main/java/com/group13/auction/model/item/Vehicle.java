package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Phương tiện. */
public class Vehicle extends Item {

  private final String manufacturer;
  private final int year;
  private final double mileage;

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

  private Vehicle(String name, String description, double startingPrice,
                  NormalUser seller, String manufacturer, int year, double mileage) {
    super(name, description, startingPrice, ItemCategory.VEHICLE, seller);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  private Vehicle(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                  String name, String description, double startingPrice, NormalUser seller,
                  String manufacturer, int year, double mileage) {
    super(id, createdAt, updatedAt, name, description, startingPrice, ItemCategory.VEHICLE, seller);
    this.manufacturer = manufacturer;
    this.year = year;
    this.mileage = mileage;
  }

  public String getManufacturer() { return manufacturer; }
  public int getYear() { return year; }
  public double getMileage() { return mileage; }

  @Override
  public void printInfo() {
    System.out.println("=== VEHICLE ==========================");
    System.out.printf("Tên : %s%n", getName());
    System.out.printf("Hãng : %s%n", manufacturer);
    System.out.printf("Năm sản xuất : %d%n", year);
    System.out.printf("Số km : %.0f%n", mileage);
    System.out.printf("Giá khởi điểm: %.0f%n", getStartingPrice());
    System.out.println("======================================");
  }
}