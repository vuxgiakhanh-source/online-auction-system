package com.group13.auction.model.item;

import com.group13.auction.model.entity.Entity;
import com.group13.auction.model.user.NormalUser;
import java.time.LocalDateTime;

/** Sản phẩm đưa ra đấu giá — abstract base. */
public abstract class Item extends Entity {

  public enum ItemCategory {
    ART, ELECTRONICS, VEHICLE, OTHER
  }

  private final String name;
  private final String description;
  private final double startingPrice;
  private final ItemCategory category;
  private final NormalUser seller;

  // Constructors

  /** Khai sinh */
  protected Item(String name, String description, double startingPrice,
                 ItemCategory category, NormalUser seller) {
    super();
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
  }

  /** Hồi sinh từ DB */
  protected Item(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                 String name, String description, double startingPrice,
                 ItemCategory category, NormalUser seller) {
    super(id, createdAt, updatedAt);
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.category = category;
    this.seller = seller;
  }

  // Getters
  public String getName() { return name; }
  public String getDescription() { return description; }
  public double getStartingPrice() { return startingPrice; }
  public ItemCategory getCategory() { return category; }
  public NormalUser getSeller() { return seller; }
}