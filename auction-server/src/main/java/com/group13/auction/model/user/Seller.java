package com.group13.auction.model.user;

import com.group13.auction.model.item.Item;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Người bán — chỉ lưu data. */
public class Seller extends User {

  private final List<Item> listedItems;
  private final List<String> allAuctionIds;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh Seller mới.
   * Hàm này chỉ được sử dụng trong Factory, không được tạo
   * đối tượng trực tiếp từ method này
   *
   * @param username   tên đăng nhập
   * @param password   mật khẩu thô
   * @param email      địa chỉ email
   * @return Seller mới
   */
  protected static Seller create(String username, String password, String email) {
    return new Seller(username, password, email);
  }

  /**
   * Hồi sinh Seller từ DB — CHÚ Ý: chỉ DAO được gọi method này.
   *
   * @param id             id gốc
   * @param createdAt      thời gian tạo gốc
   * @param updatedAt      thời gian cập nhật gốc
   * @param username       tên đăng nhập
   * @param hashedPassword password đã hash
   * @param email          email
   * @param accountStatus  trạng thái tài khoản
   * @param rating         rating hiện tại
   * @return Seller được phục hồi
   */
  protected static Seller reconstitute(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, String username, String hashedPassword,
      String email, AccountStatus accountStatus, double rating) {
    return new Seller(id, createdAt, updatedAt, username, hashedPassword,
        email, accountStatus, rating);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Seller(String username, String password, String email) {
    super(username, password, email, UserRole.SELLER);
    this.listedItems = new ArrayList<>();
    this.allAuctionIds = new ArrayList<>();
  }

  private Seller(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String username, String hashedPassword, String email,
      AccountStatus accountStatus, double rating) {
    super(id, createdAt, updatedAt, username, hashedPassword, email,
        UserRole.SELLER, accountStatus, rating);
    this.listedItems = new ArrayList<>();
    this.allAuctionIds = new ArrayList<>();
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public List<Item> getListedItems() {
    return Collections.unmodifiableList(listedItems);
  }

  public List<String> getAllAuctionIds() {
    return Collections.unmodifiableList(allAuctionIds);
  }

  // ── Setters — chỉ Service gọi ──────────────────────────────────────────────

  public void addListedItem(Item item) {
    listedItems.add(item);
    markUpdated();
  }

  public void removeListedItem(Item item) {
    listedItems.remove(item);
    markUpdated();
  }

  public void addAuctionId(String auctionId) {
    allAuctionIds.add(auctionId);
    markUpdated();
  }

  @Override
  public void printInfo() {
    System.out.println("=== SELLER ============================");
    System.out.printf("Username     : %s%n", getUsername());
    System.out.printf("Email        : %s%n", getEmail());
    System.out.printf("Rating       : %.1f%n", getRating());
    System.out.printf("Status       : %s%n", getAccountStatus());
    System.out.printf("Sản phẩm     : %d%n", listedItems.size());
    System.out.printf("Tổng auction : %d%n", allAuctionIds.size());
    System.out.println("======================================");
  }
}