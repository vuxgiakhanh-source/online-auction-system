package com.group13.auction.manager;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Singleton điều phối kỹ thuật toàn bộ hệ thống đấu giá (lỗi #10).
 *
 * <p>Phân biệt vai trò:
 * AuctionManager = điều phối kỹ thuật (quản lý danh sách, routing).
 * Phiên đấu giá do Seller quyết định tạo; {@link com.group13.auction.service.AuctionService}
 * thực thi nghiệp vụ rồi gọi {@link #registerAuction(Auction)} để lưu vào registry.
 * Admin ra lệnh → các Service thực thi → AuctionManager phản ánh trạng thái (in-memory).
 */
public class AuctionManager {

  private static AuctionManager instance;

  /**
   * Danh sách tất cả auction — lọc theo status khi cần (lỗi #6, #7).
   * TODO: sau này sync với DB qua AuctionDAO.
   */
  private final List<Auction> allAuctions;

  /**
   * Danh sách tất cả user đã đăng ký.
   * TODO: sau này sync với DB qua UserDAO.
   */
  private final List<User> allUsers;

  private final List<AuctionObserver> globalObservers;

  /** Private constructor — ngăn tạo instance từ bên ngoài. */
  private AuctionManager() {
    this.allAuctions = new ArrayList<>();
    this.allUsers = new ArrayList<>();
    this.globalObservers = new ArrayList<>();
  }

  /**
   * Lấy instance duy nhất của AuctionManager.
   *
   * @return instance AuctionManager
   */
  public static AuctionManager getInstance() {
    if (instance == null) {
      return instance;
  }

  // ── User management ────────────────────────────────────────────────────────

  /**
   * Đăng ký người dùng mới vào hệ thống.
   * TODO: sau khi tạo → lưu vào DB qua UserDAO.save(user).
   *
   * @param user người dùng cần đăng ký
   */
  public void registerUser(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User không được null.");
    }
    allUsers.add(user);
    System.out.println("[MANAGER] Đăng ký thành công: " + user.getUsername());
  }

  /**
   * Tìm user theo username.
   * TODO: sau này truy vấn DB qua UserDAO.findByUsername(username).
   *
   * @param username tên đăng nhập cần tìm
   * @return User nếu tìm thấy, null nếu không
   */
  public User findUserByUsername(String username) {
    return allUsers.stream()
        .filter(u -> u.getUsername().equals(username))
        .findFirst()
        .orElse(null);
  }

  // ── Auction management ─────────────────────────────────────────────────────

  /**
   * Đăng ký một phiên đã được tạo qua nghiệp vụ (vd. {@code AuctionService.createAuction}).
   * Không tạo {@link Auction} tại đây — chỉ lưu reference để tra cứu/lọc.
   * TODO: sau này sync với DB qua AuctionDAO.
   *
   * @param auction phiên cần đưa vào registry (không null, id duy nhất)
   */
  public void registerAuction(Auction auction) {
    if (auction == null) {
      throw new IllegalArgumentException("Auction không được null.");
    }
    if (allAuctions.stream().anyMatch(a -> a.getId().equals(auction.getId()))) {
      return;
    }
    allAuctions.add(auction);
    System.out.println("[MANAGER] Đăng ký auction: " + auction.getId());
  }

  /**
   * Đăng ký global observer (theo dõi toàn hệ thống).
   *
   * @param observer observer cần thêm
   */
  public void addGlobalObserver(AuctionObserver observer) {
    if (observer != null && !globalObservers.contains(observer)) {
      globalObservers.add(observer);
    }
  }

  /**
   * Fan-out cùng một {@link AuctionEvent} tới mọi global observer (song song với observer theo phiên).
   */
  public void notifyGlobalObservers(AuctionEvent event) {
    if (event == null) {
      return;
    }
    AuctionEvent.AuctionEventType type = event.getEventType();
    for (AuctionObserver observer : globalObservers) {
      if (type == AuctionEvent.AuctionEventType.BID_PLACED) {
        observer.onBidPlaced(event);
      } else {
        observer.onAuctionEnded(event);
      }
    }
  }

  /**
   * Lấy tất cả auction đang RUNNING (lỗi #7 — lọc theo status).
   * TODO: sau này truy vấn DB qua AuctionDAO.findByStatus(RUNNING).
   *
   * @return danh sách auction đang chạy (read-only)
   */
  public List<Auction> getRunningAuctions() {
    return allAuctions.stream()
        .filter(a -> a.getStatus() == Auction.AuctionStatus.RUNNING)
        .collect(Collectors.collectingAndThen(
            Collectors.toList(), Collections::unmodifiableList));
  }

  /**
   * Lấy auction theo trạng thái (lỗi #7).
   * TODO: sau này truy vấn DB qua AuctionDAO.findByStatus(status).
   *
   * @param status trạng thái cần lọc
   * @return danh sách auction (read-only)
   */
  public List<Auction> getAuctionsByStatus(Auction.AuctionStatus status) {
    return allAuctions.stream()
        .filter(a -> a.getStatus() == status)
        .collect(Collectors.collectingAndThen(
            Collectors.toList(), Collections::unmodifiableList));
  }

  /**
   * Tìm auction theo id.
   * TODO: sau này truy vấn DB qua AuctionDAO.findById(id).
   *
   * @param id id cần tìm
   * @return Auction nếu tìm thấy, null nếu không
   */
  public Auction findAuctionById(String id) {
    return allAuctions.stream()
        .filter(a -> a.getId().equals(id))
        .findFirst()
        .orElse(null);
  }

  /** @return toàn bộ auction (read-only) */
  public List<Auction> getAllAuctions() {
    return Collections.unmodifiableList(allAuctions);
  }

  /** @return toàn bộ user (read-only) */
  public List<User> getAllUsers() {
    return Collections.unmodifiableList(allUsers);
  }
}