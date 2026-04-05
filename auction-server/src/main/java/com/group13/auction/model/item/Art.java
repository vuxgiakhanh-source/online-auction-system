package com.group13.auction.model.item;

import com.group13.auction.model.user.Seller;
import java.time.LocalDateTime;

/** Tác phẩm nghệ thuật. */
public class Art extends Item {

  private final String artist;
  private final int yearCreated;
  private final String medium;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh tác phẩm nghệ thuật mới.
   *
   * @param name          tên tác phẩm
   * @param description   mô tả
   * @param startingPrice giá khởi điểm
   * @param seller        người bán
   * @param artist        nghệ sĩ sáng tác
   * @param yearCreated   năm sáng tác
   * @param medium        chất liệu nghệ thuật
   * @return Art mới
   */
  protected static Art create(String name, String description, double startingPrice,
                              Seller seller, String artist, int yearCreated, String medium) {
    return new Art(name, description, startingPrice, seller, artist, yearCreated, medium);
  }

  /**
   * Hồi sinh Art từ DB — chỉ DAO được gọi method này.
   *
   * @param id             id gốc
   * @param createdAt      thời gian tạo gốc
   * @param updatedAt      thời gian cập nhật gốc
   * @param name           tên tác phẩm
   * @param description    mô tả
   * @param startingPrice  giá khởi điểm
   * @param seller         người bán
   * @param artist         nghệ sĩ
   * @param yearCreated    năm sáng tác
   * @param medium         chất liệu
   * @return Art được phục hồi
   */
  public static Art reconstitute(String id, LocalDateTime createdAt,
                                    LocalDateTime updatedAt, String name, String description, double startingPrice,
                                    Seller seller, String artist, int yearCreated, String medium) {
    return new Art(id, createdAt, updatedAt, name, description, startingPrice,
            seller, artist, yearCreated, medium);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Art(String name, String description, double startingPrice,
              Seller seller, String artist, int yearCreated, String medium) {
    super(name, description, startingPrice, ItemCategory.ART, seller);
    this.artist = artist;
    this.yearCreated = yearCreated;
    this.medium = medium;
  }

  private Art(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
              String name, String description, double startingPrice, Seller seller,
              String artist, int yearCreated, String medium) {
    super(id, createdAt, updatedAt, name, description, startingPrice,
            ItemCategory.ART, seller);
    this.artist = artist;
    this.yearCreated = yearCreated;
    this.medium = medium;
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public String getArtist() { return artist; }
  public int getYearCreated() { return yearCreated; }
  public String getMedium() { return medium; }

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