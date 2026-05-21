package com.group13.auction.service.auction;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cache trạng thái các phiên mà user hiện tại đã tham gia trong vòng đời client.
 *
 * <p>Server hiện chưa trả trạng thái {@code joinedByCurrentUser} trong auction detail/list. Vì vậy
 * client cần lưu cục bộ sau khi {@code JOIN_AUCTION} thành công để tránh gửi lại join khi user
 * chỉ chuyển màn rồi quay lại live bidding. Cache này không thay thế xác thực nghiệp vụ của server.
 */
public final class JoinedAuctionState {

  private static final JoinedAuctionState INSTANCE = new JoinedAuctionState();

  private final Map<String, Set<String>> joinedAuctionIdsByUser = new HashMap<>();

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
   * @param auctionId id phiên đấu giá
   */
  public synchronized void markJoined(String auctionId) {
    String userKey = currentUserKey();
    if (userKey.isBlank() || isBlank(auctionId)) {
      return;
    }

    joinedAuctionIdsByUser
        .computeIfAbsent(userKey, ignored -> new HashSet<>())
        .add(auctionId.trim());
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

  /** Xóa toàn bộ cache, dùng khi logout nếu cần. */
  public synchronized void clear() {
    joinedAuctionIdsByUser.clear();
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