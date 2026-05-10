package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Tác phẩm nghệ thuật. */
public class Art extends Item {

  private final String artist;
  private final int yearCreated;
  private final String medium;

  // Static factory method

  protected static Art create(String name, String description, long startingPrice,
                              NormalUser seller, String artist, int yearCreated, String medium) {
    return new Art(name, description, startingPrice, seller, artist, yearCreated, medium);
  }

  public static Art reconstitute(String id, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, String name, String description, long startingPrice,
                                 NormalUser seller, String artist, int yearCreated, String medium) {
    return new Art(id, createdAt, updatedAt, name, description, startingPrice,
            seller, artist, yearCreated, medium);
  }

  // Private Constructors: Ngăn chặn new cứng

  /** Khai sinh */
  private Art(String name, String description, long startingPrice,
              NormalUser seller, String artist, int yearCreated, String medium) {
    super(name, description, startingPrice, ItemCategory.ART, seller);
    this.artist = artist;
    this.yearCreated = yearCreated;
    this.medium = medium;
  }

  /** Hồi sinh từ DB */
  private Art(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
              String name, String description, long startingPrice, NormalUser seller,
              String artist, int yearCreated, String medium) {
    super(id, createdAt, updatedAt, name, description, startingPrice, ItemCategory.ART, seller);
    this.artist = artist;
    this.yearCreated = yearCreated;
    this.medium = medium;
  }

  // Getters
  public String getArtist() { return artist; }
  public int getYearCreated() { return yearCreated; }
  public String getMedium() { return medium; }

  @Override
  public void printInfo() {
    System.out.println("THÔNG TIN SẢN PHẨM - ART");
    System.out.printf("Tên : %s%n", getName());
    System.out.printf("Nghệ sĩ : %s%n", artist);
    System.out.printf("Năm sáng tác : %d%n", yearCreated);
    System.out.printf("Chất liệu : %s%n", medium);
    System.out.printf("Giá khởi điểm: %d%n", getStartingPrice());
    System.out.println("======================================");
  }
}