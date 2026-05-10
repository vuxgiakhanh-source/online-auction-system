package com.group13.auction.manager;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
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

/**
 * Singleton điều phối kỹ thuật toàn bộ hệ thống đấu giá.
 * <p>ĐÃ THỰC HIỆN TODO: DB là gốc, mỗi khi AM thay đổi gì thì phải gọi DAO cập nhật lập tức
 * <p>ĐÃ THỰC HIỆN TODO: Cần 1 method gọi auctionDao.findAll() -> nạp toàn bộ vào List của AM
 * khi ứng dụng khởi động
 *
 * <p>AuctionManager = điều phối kỹ thuật (quản lý danh sách, routing).
 * <p>Phiên đấu giá do Seller quyết định tạo; {@link
 * com.group13.auction.service.AuctionService}
 * thực thi nghiệp vụ rồi gọi {@link #registerAuction(Auction)} để lưu vào registry.
 * Admin ra lệnh -> các Service thực thi -> AuctionManager phản ánh trạng thái
 *
 * <p>globalObservers: chứa observer của SystemAdmin (nhận toàn bộ event hệ thống).
 * staffObservers: chứa observer của Staff Admin (nhận event cancel, request, lỗi).
 * Chỉ khi admin joinAuction thì mới nhận thêm notify chi tiết theo phiên đó.
 */
public class AuctionManager {

  // FIX: dùng eager initialization -> thread-safe, tránh tạo nhiều instance
  private static final AuctionManager instance = new AuctionManager();

  // Khai báo các DAO phục vụ cho các TODO bên dưới
  private final AuctionDAO auctionDAO;
  private final UserDAO userDAO;

  /**
   * Danh sách tất cả auction - lọc theo status khi cần.
   * ĐÃ THỰC HIỆN TODO: sau này sync với DB qua AuctionDAO.
   *
   * FIX: sử dụng ConcurrentHashMap làm Identity Map để đảm bảo 1 instance duy nhất per ID.
   */
  private final Map<String, Auction> allAuctions;

  /**
   * Danh sách tất cả user đã đăng ký.
   * ĐÃ THỰC HIỆN TODO: sau này sync với DB qua UserDAO.
   *
   * FIX: sử dụng ConcurrentHashMap làm Identity Map để đảm bảo an toàn cho synchronized(user).
   */
  private final Map<String, User> allUsers;

  /**
   * Global observers - SystemAdmin observer nhận toàn bộ event hệ thống.
   *
   * FIX: sử dụng CopyOnWriteArrayList để tránh ConcurrentModificationException khi notify.
   */
  private final List<AuctionObserver> globalObservers;

  /**
   * Staff observers — Staff Admin nhận event về cancel, seller request, lỗi hệ thống.
   * Staff chỉ nhận event liên quan để có thể can thiệp.
   */
  private final List<AuctionObserver> staffObservers;

  /** Private constructor — ngăn tạo instance từ bên ngoài. */
  private AuctionManager() {
    this.auctionDAO = new AuctionDAO();
    this.userDAO = new UserDAO();

    this.allAuctions = new ConcurrentHashMap<>();
    this.allUsers = new ConcurrentHashMap<>();
    this.globalObservers = new CopyOnWriteArrayList<>();
    this.staffObservers = new CopyOnWriteArrayList<>();
  }

  /**
   * Lấy instance duy nhất của AuctionManager.
   *
   * @return instance AuctionManager
   *
   * FIX: instance đã được khởi tạo sẵn -> không cần check null
   */
  public static AuctionManager getInstance() {
    return instance;
  }

  // --- THỰC HIỆN TODO: Method nạp dữ liệu từ DB khi khởi động ứng dụng ---
  /**
   * Gọi khi ứng dụng bắt đầu khởi động để nạp dữ liệu từ Database lên In-Memory
   */
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
    System.out.println("[MANAGER] Đã đồng bộ dữ liệu từ Database lên bộ nhớ thành công.");
  }

  // User management

  /**
   * Thêm user vào danh sách in-memory mà KHÔNG persist xuống DB.
   * Dùng bởi {@link com.group13.auction.model.user.SystemAdmin#bootstrap(String)}
   * để tránh duplicate insert khi SystemAdmin đã có trong DB.
   *
   * @param user user cần thêm vào danh sách
   */
  public void addToUserList(User user) {
    if (user == null) return;
    allUsers.putIfAbsent(user.getId(), user);
  }

  /**
   * Đăng ký người dùng mới vào hệ thống.
   * ĐÃ THỰC HIỆN TODO: sau khi tạo → lưu vào DB qua UserDAO.save(user).
   *
   * @param user người dùng cần đăng ký
   */
  public void registerUser(User user) {
    if (user == null) {
      throw new IllegalArgumentException("User không được null.");
    }

    // Gọi DAO để lưu user xuống DB
    userDAO.save(user);

    allUsers.putIfAbsent(user.getId(), user);
    System.out.println("[MANAGER] Đăng ký thành công: " + user.getUsername());
  }

  /**
   * Tìm user theo username.
   * ĐÃ THỰC HIỆN TODO: sau này truy vấn DB qua UserDAO.findByUsername(username).
   *
   * @param username tên đăng nhập cần tìm
   * @return User nếu tìm thấy, null nếu không
   *
   * FIX: sử dụng Identity Map (kiểm tra RAM trước, fallback DB sau).
   */
  public User findUserByUsername(String username) {
    // Ưu tiên tìm trong RAM trước để đảm bảo tính duy nhất của instance
    for (User u : allUsers.values()) {
        if (u.getUsername().equals(username)) return u;
    }

    // Fallback: Truy vấn từ Database
    User dbUser = userDAO.findUserByUsername(username);
    if (dbUser != null) {
      User existing = allUsers.putIfAbsent(dbUser.getId(), dbUser);
      return (existing != null) ? existing : dbUser;
    }

    return null;
  }

  // Auction management

  /**
   * Đăng ký một phiên đã được tạo qua nghiệp vụ.
   *
   * @param auction phiên cần đưa vào registry
   *
   * FIX: sử dụng putIfAbsent để đảm bảo tính nguyên tử.
   */
  public void registerAuction(Auction auction) {
    if (auction == null) {
      throw new IllegalArgumentException("Auction không được null.");
    }

    allAuctions.putIfAbsent(auction.getId(), auction);
    System.out.println("[MANAGER] Đăng ký auction: " + auction.getId());
  }

  // Global observer management (SystemAdmin)

  /**
   * Đăng ký global observer (SystemAdmin).
   * Chỉ SystemAdminObserver được thêm vào đây.
   *
   * FIX: không cần synchronized vì dùng CopyOnWriteArrayList.
   */
  public void addGlobalObserver(AuctionObserver observer) {
    if (observer != null && !globalObservers.contains(observer)) {
      globalObservers.add(observer);
    }
  }

  /** Gỡ global observer (khi reset hoặc shutdown). */
  public void removeGlobalObserver(AuctionObserver observer) {
    globalObservers.remove(observer);
  }

  // Staff observer management

  /**
   * Đăng ký staff observer (Staff Admin).
   * Staff chỉ nhận notify về cancel, seller request, lỗi hệ thống.
   *
   * @param observer StaffObserver của Staff Admin
   */
  public void addStaffObserver(AuctionObserver observer) {
    if (observer != null && !staffObservers.contains(observer)) {
      staffObservers.add(observer);
    }
  }

  /**
   * Fan-out event tới global observer (SystemAdmin).
   * SystemAdmin nhận tất cả event.
   *
   * FIX: iteration an toàn trên CopyOnWriteArrayList.
   */
  public void notifyGlobalObservers(AuctionEvent event) {
    if (event == null) return;

    for (AuctionObserver observer : globalObservers) {
      dispatchEvent(observer, event);
    }
  }

  /**
   * Fan-out event tới staff observers.
   * Staff chỉ nhận event: AUCTION_CANCELED, FRAUD_DETECTED, QUALITY_REPORT_APPROVED,
   * SELLER_CANCEL_REQUEST (event liên quan đến việc cần can thiệp thủ công).
   *
   * FIX: iteration an toàn trên CopyOnWriteArrayList.
   */
  public void notifyStaffObservers(AuctionEvent event) {
    if (event == null) return;

    AuctionEvent.AuctionEventType type = event.getEventType();
    // Staff chỉ nhận event mà họ có thể can thiệp
    boolean isStaffRelevant = type == AuctionEvent.AuctionEventType.AUCTION_CANCELED
            || type == AuctionEvent.AuctionEventType.FRAUD_DETECTED
            || type == AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED
            || type == AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST;

    if (!isStaffRelevant) return;

    for (AuctionObserver observer : staffObservers) {
      dispatchEvent(observer, event);
    }
  }

  /** Helper: dispatch event đúng method của observer. */
  private void dispatchEvent(AuctionObserver observer, AuctionEvent event) {
    if (event.getEventType() == AuctionEvent.AuctionEventType.BID_PLACED
            || event.getEventType() == AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET) {
      observer.onBidPlaced(event);
    } else {
      observer.onAuctionEnded(event);
    }
  }

  // Auction queries

  /**
   * Lấy tất cả auction đang RUNNING.
   * Dành cho "đang diễn ra"
   *
   * FIX: stream an toàn trên ConcurrentHashMap.values().
   */
  public List<Auction> getRunningAuctions() {
    return allAuctions.values().stream()
            .filter(a -> a.getStatus() == Auction.AuctionStatus.RUNNING)
            .collect(Collectors.collectingAndThen(
                    Collectors.toList(), Collections::unmodifiableList));
  }

  /**
   * Lấy auction theo trạng thái.
   *
   * FIX: stream an toàn trên ConcurrentHashMap.values().
   */
  public List<Auction> getAuctionsByStatus(Auction.AuctionStatus status) {
    return allAuctions.values().stream()
            .filter(a -> a.getStatus() == status)
            .collect(Collectors.collectingAndThen(
                    Collectors.toList(), Collections::unmodifiableList));
  }

  /**
   * Tìm auction theo id.
   *
   * FIX: tìm kiếm O(1) qua Map.
   */
  public Auction findAuctionById(String id) {
    if (id == null) return null;
    return allAuctions.get(id);
  }

  /** @return toàn bộ auction (read-only)
   *
   * FIX: trả về bản copy an toàn từ Map values.
   */
  public List<Auction> getAllAuctions() {
    return Collections.unmodifiableList(new ArrayList<>(allAuctions.values()));
  }

  /** @return toàn bộ user (chỉ đọc) */
  public List<User> getAllUsers() {
    return Collections.unmodifiableList(new ArrayList<>(allUsers.values()));
  }
}
