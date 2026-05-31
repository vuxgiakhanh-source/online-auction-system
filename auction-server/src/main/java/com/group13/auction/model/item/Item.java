package com.group13.auction.model.item;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** Sản phẩm đưa ra đấu giá — abstract base. */
public abstract class Item extends Entity {

  public enum ItemCategory {
    ART,
    ELECTRONICS,
    VEHICLE,
    OTHER
  }

  /** Tối đa số ảnh được upload mỗi sản phẩm. */
  public static final int MAX_IMAGES = 3;

  /** Kích thước tối đa mỗi ảnh sau khi lưu trên disk (bytes) = 2 MB. */
  public static final long MAX_IMAGE_BYTES = 2_000_000L;

  private final String name;
  private final String description;
  private final long startingPrice;
  private final ItemCategory category;
  private final NormalUser seller;

  /**
   * Danh sách URL ảnh của sản phẩm. Mỗi phần tử là URL dạng "/uploads/items/{uuid}.jpg" do
   * ImageUploadServer cấp. Immutable sau khi tạo. Không bao giờ null.
   */
  private final List<String> imageUrls;

  // Constructors (khai sinh — không ảnh)

  protected Item(
      String name,
      String description,
      long startingPrice,
      ItemCategory category,
      NormalUser seller) {
    super();
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
    this.imageUrls = List.of();
  }

  // Constructors (khai sinh — có ảnh)

  protected Item(
      String name,
      String description,
      long startingPrice,
      ItemCategory category,
      NormalUser seller,
      List<String> imageUrls) {
    super();
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
    this.imageUrls = imageUrls != null ? Collections.unmodifiableList(imageUrls) : List.of();
  }

  // Constructors (hồi sinh — không ảnh)

  protected Item(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      ItemCategory category,
      NormalUser seller) {
    super(id, createdAt, updatedAt);
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
    this.imageUrls = List.of();
  }

  // Constructors (hồi sinh — có ảnh)

  protected Item(
      String id,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      String name,
      String description,
      long startingPrice,
      ItemCategory category,
      NormalUser seller,
      List<String> imageUrls) {
    super(id, createdAt, updatedAt);
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
    this.imageUrls = imageUrls != null ? Collections.unmodifiableList(imageUrls) : List.of();
  }

  // Getters

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public long getStartingPrice() {
    return startingPrice;
  }

  public ItemCategory getCategory() {
    return category;
  }

  public NormalUser getSeller() {
    return seller;
  }

  /** Danh sách URL ảnh. Trả về list rỗng nếu chưa có ảnh, không bao giờ null. */
  public List<String> getImageUrls() {
    return imageUrls;
  }

  /** true nếu sản phẩm có ít nhất 1 ảnh. */
  public boolean hasImages() {
    return !imageUrls.isEmpty();
  }
}
