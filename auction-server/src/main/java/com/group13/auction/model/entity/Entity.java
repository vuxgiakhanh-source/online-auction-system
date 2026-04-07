package com.group13.auction.model.entity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lớp abstract gốc — mọi thực thể đều có id UUID và timestamp.
 *
 * <p>Dùng static factory method thay vì overloaded constructor:
 * {@code create()} cho object mới, {@code reconstitute()} cho object từ DB.
 */
public abstract class Entity {

  private final String        id;
  private final LocalDateTime createdAt;
  private       LocalDateTime updatedAt;

  /** Khai sinh — UUID tự động, timestamp = now. */
  protected Entity() {
    this.id        = UUID.randomUUID().toString();
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * Hồi sinh — giữ nguyên id và timestamp từ DB.
   * Chỉ được gọi từ static factory method {@code reconstitute()} của lớp con,
   * và chỉ DAO mới được gọi reconstitute().
   */
  protected Entity(String id, LocalDateTime createdAt, LocalDateTime updatedAt) {
    this.id        = id;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Ghi nhận thời điểm thay đổi — gọi mỗi khi setter thay đổi field. */
  protected void markUpdated() {
    this.updatedAt = LocalDateTime.now();
  }

  public String        getId()        { return id; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }

  public abstract void printInfo();
}
