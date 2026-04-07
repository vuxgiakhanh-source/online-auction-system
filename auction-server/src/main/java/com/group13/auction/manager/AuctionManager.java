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
 * Singleton điều phối kỹ thuật toàn bộ hệ thống đấu giá.
 *
 * <p>Phân biệt vai trò:
 * AuctionManager = điều phối kỹ thuật (quản lý danh sách, routing).
 * Phiên đấu giá do Seller quyết định tạo; {@link com.group13.auction.service.AuctionService}
 * thực thi nghiệp vụ rồi gọi {@link #registerAuction(Auction)} để lưu vào registry.
 * Admin ra lệnh → các Service thực thi → AuctionManager phản ánh trạng thái (in-memory).
 *
 * <p>globalObservers: chứa AdminObserver của tất cả admin.
 * Admin sẽ nhận thông báo về gian lận / lỗi hệ thống / phiên không có winner
 * của TOÀN BỘ hệ thống, KHÔNG cần joinAuction.
 * Chỉ khi admin joinAuction thì mới nhận thêm notify chi tiết theo phiên đó.
 */
public class AuctionManager {

  // FIX: dùng eager initialization → thread-safe, tránh tạo nhiều instance
  private static final AuctionManager instance = new AuctionManager();

  /**
   * Danh sách tất cả auction — lọc theo status khi cần.
   * TODO: sau này sync với DB qua AuctionDAO.
   *
   * FIX: dùng synchronizedList để tránh race condition khi nhiều thread add/remove
   */
  private final List<Auction> allAuctions;

  /**
   * Danh sách tất cả user đã đăng ký.
   * TODO: sau này sync với DB qua UserDAO.
   *
   * FIX: thread-safe collection
   */
  private final List<User> allUsers;

  /**
   * Global observers — tự động thêm AdminObserver của mọi Admin vào đây.
   * Nhận notify về: gian lận, lỗi hệ thống, phiên không có winner,
   * reserve not met.
   *
   * FIX: thread-safe collection cho observer
   */
  private final List<AuctionObserver> globalObservers;

  /** Private constructor — ngăn tạo instance từ bên ngoài. */
  private AuctionManager() {
    this.allAuctions    = Collections.synchronizedList(new ArrayList<>());
    this.allUsers       = Collections.synchronizedList(new ArrayList<>());
    this.globalObservers = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * Lấy instance duy nhất của AuctionManager.
   *
   * @return instance AuctionManager
   *
   * FIX: instance đã được khởi tạo sẵn → không cần check null
   */
  public static AuctionManager getInstance() {
    return instance;
  }

  // ── User management ────────────────────────────────────────────────────

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
   *
   * FIX: phải synchronized khi iterate (stream)
   */
  public User findUserByUsername(String username) {
    synchronized (allUsers) {
      return allUsers.stream()
              .filter(u -> u.getUsername().equals(username))
              .findFirst()
              .orElse(null);
    }
  }

  /**
   * Xóa user khỏi danh sách hệ thống (soft-delete hoặc hard-delete).
   * TODO: sau này gọi UserDAO.delete(user).
   *
   * @param user user cần xóa
   */
  public void removeUser(User user) {
    allUsers.remove(user);
  }

  // ── Auction management ─────────────────────────────────────────────────

  /**
   * Đăng ký một phiên đã được tạo qua nghiệp vụ.
   *
   * @param auction phiên cần đưa vào registry
   *
   * FIX:
   * - check + add phải nằm trong cùng 1 synchronized block (atomic)
   */
  public void registerAuction(Auction auction) {
    if (auction == null) {
      throw new IllegalArgumentException("Auction không được null.");
    }

    synchronized (allAuctions) {
      if (allAuctions.stream().anyMatch(a -> a.getId().equals(auction.getId()))) {
        return;
      }
      allAuctions.add(auction);
    }

    System.out.println("[MANAGER] Đăng ký auction: " + auction.getId());
  }

  // ── Global observer management ─────────────────────────────────────────

  /**
   * Đăng ký global observer (Admin).
   * Chỉ AdminObserver của Admin được thêm vào đây.
   *
   * FIX: cần synchronized vì có contains + add
   */
  public void addGlobalObserver(AuctionObserver observer) {
    if (observer == null) return;

    synchronized (globalObservers) {
      if (!globalObservers.contains(observer)) {
        globalObservers.add(observer);
      }
    }
  }

  /**
   * Gỡ global observer (khi admin bị xóa hoặc ban).
   */
  public void removeGlobalObserver(AuctionObserver observer) {
    globalObservers.remove(observer);
  }

  /**
   * Fan-out event tới global observer (Admin).
   * Chỉ gửi các loại event hệ thống: FRAUD, RESERVE_NOT_MET, NO_WINNER.
   *
   * FIX: phải synchronized khi iterate
   */
  public void notifyGlobalObservers(AuctionEvent event) {
    if (event == null) return;

    synchronized (globalObservers) {
      AuctionEvent.AuctionEventType type = event.getEventType();
      for (AuctionObserver observer : globalObservers) {
        if (type == AuctionEvent.AuctionEventType.BID_PLACED) {
          observer.onBidPlaced(event);
        } else {
          observer.onAuctionEnded(event);
        }
      }
    }
  }

  // ── Auction queries ────────────────────────────────────────────────────

  /**
   * Lấy tất cả auction đang RUNNING.
   *
   * FIX: synchronized khi stream
   */
  public List<Auction> getRunningAuctions() {
    synchronized (allAuctions) {
      return allAuctions.stream()
              .filter(a -> a.getStatus() == Auction.AuctionStatus.RUNNING)
              .collect(Collectors.collectingAndThen(
                      Collectors.toList(), Collections::unmodifiableList));
    }
  }

  /**
   * Lấy auction theo trạng thái.
   *
   * FIX: synchronized khi stream
   */
  public List<Auction> getAuctionsByStatus(Auction.AuctionStatus status) {
    synchronized (allAuctions) {
      return allAuctions.stream()
              .filter(a -> a.getStatus() == status)
              .collect(Collectors.collectingAndThen(
                      Collectors.toList(), Collections::unmodifiableList));
    }
  }

  /**
   * Tìm auction theo id.
   *
   * FIX: synchronized khi stream
   */
  public Auction findAuctionById(String id) {
    synchronized (allAuctions) {
      return allAuctions.stream()
              .filter(a -> a.getId().equals(id))
              .findFirst()
              .orElse(null);
    }
  }

  /** @return toàn bộ auction (read-only)
   *
   * FIX: trả về bản copy để tránh bị modify khi đang dùng
   */
  public List<Auction> getAllAuctions() {
    synchronized (allAuctions) {
      return Collections.unmodifiableList(new ArrayList<>(allAuctions));
    }
  }

  /** @return toàn bộ user (read-only) */
  public List<User> getAllUsers() {
    synchronized (allUsers) {
      return Collections.unmodifiableList(new ArrayList<>(allUsers));
    }
  }
}
