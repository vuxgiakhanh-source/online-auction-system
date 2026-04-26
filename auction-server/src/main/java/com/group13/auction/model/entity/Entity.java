package com.group13.auction.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Lớp abstract gốc — mọi thực thể đều có id UUID và timestamp
 *
 * <p>Dùng static factory method để khởi tạo
 * {@code create()} cho object mới, {@code reconstitute()} cho object từ DB
 */
public abstract class Entity {

  private final String id;
  private final LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Khai sinh — UUID tự động, timestamp = now. */
  protected Entity() {
    this.id = UUID.randomUUID().toString();
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * Hồi sinh — giữ nguyên id và timestamp từ DB.
   * Chỉ được gọi từ static factory method {@code reconstitute()} của lớp con,
   * và chỉ DAO mới được gọi reconstitute().
   */
  protected Entity(String id, LocalDateTime createdAt, LocalDateTime updatedAt) {
    this.id = id;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Ghi nhận thời điểm thay đổi — gọi mỗi khi setter thay đổi field. */
  protected void markUpdated() {
    this.updatedAt = LocalDateTime.now();
  }

  // Getters
  public String getId() { return id; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }

  /**
   * Hai entity bằng nhau khi và chỉ khi cùng {@code id}.
   * Tránh lỗi khi cùng một entity được load từ DB thành nhiều instance khác nhau
   * {@code observers.contains(observer)}, {@code joinedAuctionIds.contains(id)}.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Entity)) return false;
    Entity other = (Entity) o;
    return Objects.equals(this.id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public abstract void printInfo();
}