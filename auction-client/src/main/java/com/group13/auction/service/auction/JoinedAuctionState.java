package com.group13.auction.service.auction;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cache trạng thái tham gia phiên đấu giá của user hiện tại trong vòng đời client.
 *
 * <p>Server là nơi xác thực nghiệp vụ cuối cùng. Cache này chỉ giúp UI tránh gửi request không cần
 * thiết, giữ đúng trạng thái sau khi user chuyển màn, và phản ánh trạng thái đã hủy tham gia trong
 * cùng phiên chạy app.
 */
public final class JoinedAuctionState {

  private static final JoinedAuctionState INSTANCE = new JoinedAuctionState();

  private final Map<String, Set<String>> joinedAuctionIdsByUser = new HashMap<>();
  private final Map<String, Set<String>> leftAuctionIdsByUser = new HashMap<>();

  private JoinedAuctionState() {
    // Singleton.
  }

  /**
   * Lấy cache dùng chung của client.
   *
   * @return singleton joined auction state
   */
  public static JoinedAuctionState getInstance() {
    return INSTANCE;
  }

  /**
   * Đánh dấu user hiện tại đã tham gia phiên đấu giá.
   *
   * <p>Khi user join thành công, trạng thái LEFT cục bộ của phiên đó sẽ được xóa để tránh UI hiểu sai
   * trong các trường hợp server cho phép join lại ở tương lai.
   *
   * @param auctionId id phiên đấu giá
   */
  public synchronized void markJoined(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return;
    }

    String normalizedAuctionId = auctionId.trim();

    joinedAuctionIdsByUser
        .computeIfAbsent(userKey, ignored -> new HashSet<>())
        .add(normalizedAuctionId);

    Set<String> leftAuctionIds = leftAuctionIdsByUser.get(userKey);
    if (leftAuctionIds != null) {
      leftAuctionIds.remove(normalizedAuctionId);
    }
  }

  /**
   * Đánh dấu user hiện tại đã hủy tham gia phiên đấu giá.
   *
   * <p>Server hiện xử lý LEAVE_AUCTION như hành động hủy tham gia thật và có thể chặn join lại. Vì
   * vậy client cần nhớ trạng thái LEFT để không hiển thị lại nút tham gia trong cùng phiên app.
   *
   * @param auctionId id phiên đấu giá
   */
  public synchronized void markLeft(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return;
    }

    String normalizedAuctionId = auctionId.trim();

    leftAuctionIdsByUser
        .computeIfAbsent(userKey, ignored -> new HashSet<>())
        .add(normalizedAuctionId);

    Set<String> joinedAuctionIds = joinedAuctionIdsByUser.get(userKey);
    if (joinedAuctionIds != null) {
      joinedAuctionIds.remove(normalizedAuctionId);
    }
  }

  /**
   * Xóa dấu join cục bộ khi server xác nhận user không còn quyền đặt giá.
   *
   * @param auctionId id phiên đấu giá
   */
  public synchronized void forgetJoined(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return;
    }

    Set<String> joinedAuctionIds = joinedAuctionIdsByUser.get(userKey);
    if (joinedAuctionIds != null) {
      joinedAuctionIds.remove(auctionId.trim());
    }
  }

  /**
   * Xóa dấu LEFT cục bộ nếu cần đồng bộ lại trạng thái từ server.
   *
   * @param auctionId id phiên đấu giá
   */
  public synchronized void forgetLeft(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return;
    }

    Set<String> leftAuctionIds = leftAuctionIdsByUser.get(userKey);
    if (leftAuctionIds != null) {
      leftAuctionIds.remove(auctionId.trim());
    }
  }

  /**
   * Kiểm tra user hiện tại đã tham gia phiên này trong vòng đời client chưa.
   *
   * @param auctionId id phiên đấu giá
   * @return true nếu client đã ghi nhận user tham gia phiên này
   */
  public synchronized boolean hasJoined(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return false;
    }

    return joinedAuctionIdsByUser
        .getOrDefault(userKey, Set.of())
        .contains(auctionId.trim());
  }

  /**
   * Kiểm tra user hiện tại đã hủy tham gia phiên này trong vòng đời client chưa.
   *
   * @param auctionId id phiên đấu giá
   * @return true nếu client đã ghi nhận user hủy tham gia phiên này
   */
  public synchronized boolean hasLeft(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return false;
    }

    return leftAuctionIdsByUser
        .getOrDefault(userKey, Set.of())
        .contains(auctionId.trim());
  }

  /** Xóa toàn bộ cache, dùng khi logout nếu cần. */
  public synchronized void clear() {
    joinedAuctionIdsByUser.clear();
    leftAuctionIdsByUser.clear();
  }

  private String currentUserKey() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(JoinedAuctionState::sessionKey)
        .orElse("");
  }

  private static String sessionKey(UserSession session) {
    if (!isBlank(session.getUserId())) {
      return session.getUserId().trim();
    }

    if (!isBlank(session.getUsername())) {
      return session.getUsername().trim();
    }

    return "";
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}