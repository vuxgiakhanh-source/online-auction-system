package com.group13.auction.manager;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton điều phối kỹ thuật toàn bộ hệ thống đấu giá.
 *
 * <h3>Cải tiến v2:</h3>
 *
 * <ul>
 *   <li>Logging chuẩn SLF4J (xóa System.out.println).
 * </ul>
 */
public class AuctionManager {

  private static final Logger log = LoggerFactory.getLogger(AuctionManager.class);

  private static final AuctionManager instance = new AuctionManager();

  private final AuctionDAO auctionDAO;
  private final UserDAO userDAO;

  private final Map<String, Auction> allAuctions;
  private final Map<String, User> allUsers;
  private final List<AuctionObserver> globalObservers;
  private final List<AuctionObserver> staffObservers;

  private AuctionManager() {
    this.auctionDAO = new AuctionDAO();
    this.userDAO = new UserDAO();
    this.allAuctions = new ConcurrentHashMap<>();
    this.allUsers = new ConcurrentHashMap<>();
    this.globalObservers = new CopyOnWriteArrayList<>();
    this.staffObservers = new CopyOnWriteArrayList<>();
  }

  public static AuctionManager getInstance() {
    return instance;
  }

  /** Load dữ liệu từ DB khi app khởi động. */
  public void loadDataFromDatabase() {
    List<Auction> dbAuctions = auctionDAO.findAll();
    if (dbAuctions != null) {
      for (Auction a : dbAuctions) {
        allAuctions.put(a.getId(), a);
      }
    }
    List<User> dbUsers = userDAO.findAll();
    if (dbUsers != null) {
      for (User u : dbUsers) {
        allUsers.put(u.getId(), u);
      }
    }

    // FIX Bug #1 (winner restore): Sau server restart, auction.getWinner() = null dù DB có winner.
    // Load lại AuctionWinner cho các phiên đã FINISHED/PAID/RUNNING để PaymentHandler không báo
    // "chưa có winner".
    // RUNNING cũng cần restore vì server có thể crash ngay sau khi closeAuction() set winner nhưng
    // trước khi status được flush xuống DB đúng.
    AuctionWinnerDAO auctionWinnerDAO = new AuctionWinnerDAO();
    int restoredCount = 0;
    int missingWinnerCount = 0;
    if (dbAuctions != null) {
      for (Auction a : dbAuctions) {
        if (a.getStatus() == Auction.AuctionStatus.FINISHED
            || a.getStatus() == Auction.AuctionStatus.PAID
            || a.getStatus() == Auction.AuctionStatus.RUNNING) {
          try {
            AuctionWinner winner = auctionWinnerDAO.findByAuctionId(a.getId(), userDAO);
            if (winner != null) {
              a.setWinner(winner);
              restoredCount++;
              log.debug(
                  "Winner restored: auctionId={}, winnerId={}, status={}",
                  a.getId(),
                  winner.getWinner().getId(),
                  a.getStatus());
            } else {
              missingWinnerCount++;
              log.warn(
                  "No winner in DB for {} auction: auctionId={} — payment flow bị ảnh hưởng",
                  a.getStatus(),
                  a.getId());
            }
          } catch (Exception e) {
            log.error(
                "Failed to restore winner: auctionId={}, status={} — skipping",
                a.getId(),
                a.getStatus(),
                e);
          }
        }
      }
    }

    log.info(
        "Đã đồng bộ dữ liệu từ Database: auctions={} users={} winners_restored={}"
            + " missing_winners={}",
        allAuctions.size(),
        allUsers.size(),
        restoredCount,
        missingWinnerCount);
    if (missingWinnerCount > 0) {
      log.warn(
          "{} phiên FINISHED/PAID/RUNNING không có winner trong DB — kiểm tra bảng auction_winners",
          missingWinnerCount);
    }
  }

  // User management

  /** Thêm user vào in-memory mà KHÔNG persist DB. */
  public void addToUserList(User user) {
    if (user == null) {
      return;
    }
    allUsers.putIfAbsent(user.getId(), user);
  }

  /**
   * Ghi đè user trong bộ nhớ bằng bản mới từ DB (login, reload profile). Khác {@link
   * #addToUserList}: luôn cập nhật roles/balance thay vì giữ object cũ.
   */
  public void refreshUser(User user) {
    if (user == null) {
      return;
    }
    allUsers.put(user.getId(), user);
  }

  /** Đăng ký user mới (persist DB + in-memory). */
  public void registerUser(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User không được null.");
    }
    userDAO.save(user);
    allUsers.putIfAbsent(user.getId(), user);
    log.info("User registered: username={}", user.getUsername());
  }

  /**
   * Tìm user theo username CHỈ trong bộ nhớ (không truy vấn DB). Dùng bởi UserService.login() để
   * kiểm tra in-memory trước khi hit DB.
   */
  public User findUserByUsernameInMemoryOnly(String username) {
    for (User u : allUsers.values()) {
      if (u.getUsername().equals(username)) {
        return u;
      }
    }
    return null;
  }

  /** Tìm user theo username (ưu tiên DB, fallback in-memory). */
  public User findUserByUsername(String username) {
    User dbUser = userDAO.findUserByUsername(username);
    if (dbUser != null) {
      allUsers.put(dbUser.getId(), dbUser);
      return dbUser;
    }
    for (User u : allUsers.values()) {
      if (u.getUsername().equals(username)) {
        return u;
      }
    }
    return null;
  }

  // Auction management

  /** Đăng ký auction vào bộ nhớ runtime để phục vụ truy vấn nhanh. */
  public void registerAuction(Auction auction) {
    if (auction == null) {
      throw new IllegalArgumentException("Auction không được null.");
    }
    allAuctions.putIfAbsent(auction.getId(), auction);
    log.info("Auction registered: auctionId={}", auction.getId());
  }

  // Observer management

  /** Thêm observer nhận toàn bộ sự kiện global của hệ thống. */
  public void addGlobalObserver(AuctionObserver observer) {
    if (observer != null && !globalObservers.contains(observer)) {
      globalObservers.add(observer);
    }
  }

  /** Gỡ observer khỏi danh sách global observers. */
  public void removeGlobalObserver(AuctionObserver observer) {
    globalObservers.remove(observer);
  }

  /** Thêm observer dành riêng cho nhóm sự kiện staff. */
  public void addStaffObserver(AuctionObserver observer) {
    if (observer != null && !staffObservers.contains(observer)) {
      staffObservers.add(observer);
    }
  }

  /** Phát sự kiện tới toàn bộ global observers. */
  public void notifyGlobalObservers(AuctionEvent event) {
    if (event == null) {
      return;
    }
    for (AuctionObserver observer : globalObservers) {
      dispatchEvent(observer, event);
    }
  }

  /** Phát các sự kiện staff-relevant tới staff observers. */
  public void notifyStaffObservers(AuctionEvent event) {
    if (event == null) {
      return;
    }
    AuctionEvent.AuctionEventType type = event.getEventType();
    boolean isStaffRelevant =
        type == AuctionEvent.AuctionEventType.AUCTION_CANCELED
            || type == AuctionEvent.AuctionEventType.FRAUD_DETECTED
            || type == AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED
            || type == AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST;
    if (!isStaffRelevant) {
      return;
    }
    for (AuctionObserver observer : staffObservers) {
      dispatchEvent(observer, event);
    }
  }

  private void dispatchEvent(AuctionObserver observer, AuctionEvent event) {
    if (event.getEventType() == AuctionEvent.AuctionEventType.BID_PLACED
        || event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      observer.onBidPlaced(event);
    } else {
      observer.onAuctionEnded(event);
    }
  }

  // Auction queries

  /** Lấy danh sách các phiên đang ở trạng thái RUNNING. */
  public List<Auction> getRunningAuctions() {
    return allAuctions.values().stream()
        .filter(a -> a.getStatus() == Auction.AuctionStatus.RUNNING)
        .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
  }

  /** Lấy danh sách các phiên theo trạng thái chỉ định. */
  public List<Auction> getAuctionsByStatus(Auction.AuctionStatus status) {
    return allAuctions.values().stream()
        .filter(a -> a.getStatus() == status)
        .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
  }

  /** Tìm phiên đấu giá theo id trong bộ nhớ. */
  public Auction findAuctionById(String id) {
    if (id == null) {
      return null;
    }
    return allAuctions.get(id);
  }

  public List<Auction> getAllAuctions() {
    return Collections.unmodifiableList(new ArrayList<>(allAuctions.values()));
  }

  public List<User> getAllUsers() {
    return Collections.unmodifiableList(new ArrayList<>(allUsers.values()));
  }
}
