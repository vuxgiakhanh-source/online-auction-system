package com.group13.auction.model.user;

import com.group13.auction.model.bid.BidTransaction;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Người tham gia đấu giá — chỉ lưu data. */
public class Bidder extends User {

  private double balance;
  private final List<BidTransaction> bidHistory;
  private final Set<String> joinedAuctionIds;
  private final List<String> watchListAuctionIds;

  // ── Static factory methods ─────────────────────────────────────────────────

  /**
   * Khai sinh Bidder mới với balance = 0, rating = 3.0.
   * Hàm này chỉ được sử dụng trong Factory, không được tạo
   * đối tượng trực tiếp từ method này
   *
   * @param username   tên đăng nhập
   * @param password   mật khẩu thô
   * @param email      địa chỉ email
   * @return Bidder mới
   */
  protected static Bidder create(String username, String password, String email) {
    return new Bidder(username, password, email);
  }

  /**
   * Hồi sinh Bidder từ DB — CHÚ Ý: chỉ DAO được gọi method này.
   *
   * @param id             id gốc từ DB
   * @param createdAt      thời gian tạo gốc
   * @param updatedAt      thời gian cập nhật gốc
   * @param username       tên đăng nhập
   * @param hashedPassword password đã hash
   * @param email          email
   * @param accountStatus  trạng thái tài khoản
   * @param rating         rating hiện tại
   * @param balance        số dư hiện tại
   * @return Bidder được phục hồi
   */
  protected static Bidder reconstitute(String id, LocalDateTime createdAt,
      LocalDateTime updatedAt, String username, String hashedPassword,
      String email, AccountStatus accountStatus, double rating,
      double balance) {
    return new Bidder(id, createdAt, updatedAt, username, hashedPassword,
        email, accountStatus, rating, balance);
  }

  // ── Private constructors ───────────────────────────────────────────────────

  private Bidder(String username, String password, String email) {
    super(username, password, email, UserRole.BIDDER);
    this.balance = 0.0;
    this.bidHistory = new ArrayList<>();
    this.joinedAuctionIds = new HashSet<>();
    this.watchListAuctionIds = new ArrayList<>();
  }

  private Bidder(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
      String username, String hashedPassword, String email,
      AccountStatus accountStatus, double rating, double balance) {
    super(id, createdAt, updatedAt, username, hashedPassword, email,
        UserRole.BIDDER, accountStatus, rating);
    this.balance = balance;
    this.bidHistory = new ArrayList<>();
    this.joinedAuctionIds = new HashSet<>();
    this.watchListAuctionIds = new ArrayList<>();
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  public double getBalance() { return balance; }

  public List<BidTransaction> getBidHistory() {
    return Collections.unmodifiableList(bidHistory);
  }

  public Set<String> getJoinedAuctionIds() {
    return Collections.unmodifiableSet(joinedAuctionIds);
  }

  public List<String> getWatchListAuctionIds() {
    return Collections.unmodifiableList(watchListAuctionIds);
  }

  public boolean hasJoined(String auctionId) {
    return joinedAuctionIds.contains(auctionId);
  }

  // ── Setters — chỉ Service gọi ──────────────────────────────────────────────

  public void setBalance(double balance) {
    this.balance = balance;
    markUpdated();
  }

  public void addBidToHistory(BidTransaction tx) {
    bidHistory.add(tx);
  }

  public void addJoinedAuction(String auctionId) {
    joinedAuctionIds.add(auctionId);
  }

  public void addToWatchList(String auctionId) {
    if (!watchListAuctionIds.contains(auctionId)) {
      watchListAuctionIds.add(auctionId);
    }
  }

  @Override
  public void printInfo() {
    System.out.println("=== BIDDER ===========================");
    System.out.printf("Username  : %s%n", getUsername());
    System.out.printf("Email     : %s%n", getEmail());
    System.out.printf("Balance   : %.0f%n", balance);
    System.out.printf("Rating    : %.1f%n", getRating());
    System.out.printf("Status    : %s%n", getAccountStatus());
    System.out.printf("Số lần bid: %d%n", bidHistory.size());
    System.out.println("======================================");
  }
}