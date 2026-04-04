package com.group13.auction.model.item;

import com.group13.auction.model.user.Seller;
import java.time.LocalDateTime;

/** Tác phẩm nghệ thuật. */
public class Art extends Item {

  private final String artist;
  private final int yearCreated;
  private final String medium;

  // ── Constructor khai sinh ──────────────────────────────────────────────────

  public Art(String name, String description, double startingPrice,
      Seller seller, String artist, int yearCreated, String medium) {
    super(name, description, startingPrice, ItemCategory.ART, seller);
    this.artist = artist;
    this.yearCreated = yearCreated;
    this.medium = medium;
  }

  // ── Constructor hồi sinh ──────────────────────────────────────────────────

  public Art(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String name, String description, double startingPrice, Seller seller,
      String artist, int yearCreated, String medium) {
    super(id, createdAt, updatedAt, name, description, startingPrice,
        ItemCategory.ART, seller);
    this.artist = artist;
    this.yearCreated = yearCreated;
    this.medium = medium;
  }

  public String getArtist() {
    return artist;
  }

  public int getYearCreated() {
    return yearCreated;
  }

  public String getMedium() {
    return medium;
  }

  @Override
  public void printInfo() {
    System.out.println("=== ART ==============================");
    System.out.printf("Tên           : %s%n", getName());
    System.out.printf("Nghệ sĩ       : %s%n", artist);
    System.out.printf("Năm sáng tác  : %d%n", yearCreated);
    System.out.printf("Chất liệu     : %s%n", medium);
    System.out.printf("Giá khởi điểm : %.0f%n", getStartingPrice());
    System.out.println("======================================");
  }
}