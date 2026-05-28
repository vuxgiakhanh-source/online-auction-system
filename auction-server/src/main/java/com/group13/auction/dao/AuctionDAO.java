package com.group13.auction.dao;

import com.group13.auction.model.auction.Auction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuctionDAO {
  private static final Logger log = LoggerFactory.getLogger(AuctionDAO.class);

  public AuctionDAO() {
    // Constructor rỗng, lấy Connection cục bộ trong từng hàm
  }

  /** Lưu phiên đấu giá mới vào DB. ID được sinh từ tầng Entity (Java) và truyền xuống. */
  public boolean createAuction(Auction auction) {
    String sql =
        "INSERT INTO auctions (id, item_id, start_time, end_time, status, reserve_price,"
            + " current_price, current_leader_id, current_highest_price, winning_bidder_id,"
            + " viewer_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auction.getId());
      pstmt.setString(2, auction.getItem().getId());
      pstmt.setTimestamp(3, Timestamp.valueOf(auction.getStartTime()));
      pstmt.setTimestamp(4, Timestamp.valueOf(auction.getEndTime()));
      pstmt.setString(5, auction.getStatus().name());
      pstmt.setLong(6, auction.getReservePrice());
      pstmt.setLong(7, auction.getCurrentPrice());
      if (auction.getCurrentLeader() != null) {
        pstmt.setString(8, auction.getCurrentLeader().getId());
      } else {
        pstmt.setNull(8, java.sql.Types.VARCHAR);
      }
      pstmt.setLong(9, auction.getCurrentPrice());
      if (auction.getCurrentLeader() != null) {
        pstmt.setString(10, auction.getCurrentLeader().getId());
      } else {
        pstmt.setNull(10, java.sql.Types.VARCHAR);
      }
      pstmt.setInt(11, auction.getViewerCount());

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Lỗi tạo phiên đấu giá: auctionId={}", auction != null ? auction.getId() : null, e);
      return false;
    }
  }

  /** Cập nhật trạng thái của phiên (OPEN -> RUNNING, FINISHED, CANCELED...) */
  public boolean updateAuctionStatus(String auctionId, String status) {
    String sql = "UPDATE auctions SET status = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, status);
      pstmt.setString(2, auctionId);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Lỗi cập nhật trạng thái phiên: auctionId={}, status={}", auctionId, status, e);
      return false;
    }
  }

  /** Cập nhật toàn bộ kết quả khi phiên kết thúc. */
  public boolean updateAuctionResult(Auction auction) {
    String sql =
        "UPDATE auctions SET status = ?, current_price = ?, current_leader_id = ?,"
            + " current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auction.getStatus().name());
      pstmt.setLong(2, auction.getCurrentPrice());
      if (auction.getCurrentLeader() != null) {
        pstmt.setString(3, auction.getCurrentLeader().getId());
      } else {
        pstmt.setNull(3, java.sql.Types.VARCHAR);
      }
      pstmt.setLong(4, auction.getCurrentPrice());
      if (auction.getCurrentLeader() != null) {
        pstmt.setString(5, auction.getCurrentLeader().getId());
      } else {
        pstmt.setNull(5, java.sql.Types.VARCHAR);
      }
      pstmt.setString(6, auction.getId());

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi cập nhật kết quả phiên: auctionId={}", auction != null ? auction.getId() : null, e);
      return false;
    }
  }

  /**
   * Cập nhật giá cao nhất với điều kiện atomicity: chỉ update khi newPrice > current_price trong
   * DB.
   */
  public boolean updateHighestPrice(String auctionId, long newPrice, String bidderId) {
    String sql =
        "UPDATE auctions SET current_price = ?, current_leader_id = ?, "
            + "current_highest_price = ?, winning_bidder_id = ? "
            + "WHERE id = ? AND current_price < ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setLong(1, newPrice);
      pstmt.setString(2, bidderId);
      pstmt.setLong(3, newPrice);
      pstmt.setString(4, bidderId);
      pstmt.setString(5, auctionId);
      pstmt.setLong(6, newPrice);

      int rows = pstmt.executeUpdate();
      if (rows == 0) {
        log.warn(
            "updateHighestPrice no-op (stale write skipped): auctionId={}, newPrice={}",
            auctionId,
            newPrice);
      }
      return rows > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi cập nhật giá đấu: auctionId={}, newPrice={}, bidderId={}",
          auctionId,
          newPrice,
          bidderId,
          e);
      return false;
    }
  }

  /** Cập nhật số lượng người theo dõi (viewer_count) của phiên đấu giá. */
  public boolean updateViewerCount(String auctionId, int count) {
    String sql = "UPDATE auctions SET viewer_count = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setInt(1, count);
      pstmt.setString(2, auctionId);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi cập nhật số lượt xem phiên đấu giá: auctionId={}, count={}", auctionId, count, e);
      return false;
    }
  }

  /** Cập nhật end_time của phiên (phục vụ anti-sniping). */
  public boolean updateEndTime(String auctionId, java.time.LocalDateTime endTime) {
    String sql = "UPDATE auctions SET end_time = ? WHERE id = ?";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setTimestamp(1, Timestamp.valueOf(endTime));
      pstmt.setString(2, auctionId);
      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Lỗi cập nhật end_time của phiên: auctionId={}, endTime={}", auctionId, endTime, e);
      return false;
    }
  }

  /**
   * Tìm kiếm và hồi sinh một phiên đấu giá (Auction) dựa vào ID.
   *
   * <p>FIX: Đọc cột viewer_count từ ResultSet và truyền vào Auction.reconstitute() để khôi phục
   * đúng viewerCount khi server restart, thay vì luôn reset về 0.
   */
  public com.group13.auction.model.auction.Auction findAuctionById(String auctionId) {
    String sql = "SELECT * FROM auctions WHERE id = ?";

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);

      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          final String id = rs.getString("id");
          final String statusStr = rs.getString("status");
          final String itemId = rs.getString("item_id");
          final String leaderId = rs.getString("current_leader_id");
          final long currentPrice = rs.getLong("current_price");

          java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
          java.time.LocalDateTime createdAt =
              (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

          java.sql.Timestamp updatedTs = rs.getTimestamp("updated_at");
          final java.time.LocalDateTime updatedAt =
              (updatedTs != null) ? updatedTs.toLocalDateTime() : createdAt;

          java.sql.Timestamp startTs = rs.getTimestamp("start_time");
          final java.time.LocalDateTime startTime =
              (startTs != null) ? startTs.toLocalDateTime() : null;

          java.sql.Timestamp endTs = rs.getTimestamp("end_time");
          final java.time.LocalDateTime endTime = (endTs != null) ? endTs.toLocalDateTime() : null;

          // FIX: đọc viewer_count từ DB để restore đúng giá trị khi server restart.
          // Trước đây cột này được đọc nhưng không truyền vào reconstitute(),
          // nên viewerCount trong memory luôn = 0 sau mỗi lần khởi động lại.
          final int savedViewerCount = rs.getInt("viewer_count");

          final ItemDAO itemDAO = new ItemDAO();
          final com.group13.auction.model.item.Item item = itemDAO.findItemById(itemId);

          UserDAO userDAO = new UserDAO();
          com.group13.auction.model.user.NormalUser currentLeader = null;
          if (leaderId != null && !leaderId.trim().isEmpty()) {
            currentLeader = userDAO.findNormalUserById(leaderId);
          }

          long reservePrice = rs.getLong("reserve_price");
          if (reservePrice <= 0) {
            reservePrice = currentPrice > 0 ? currentPrice : 1L;
          }

          com.group13.auction.model.auction.Auction.AuctionStatus status =
              com.group13.auction.model.auction.Auction.AuctionStatus.OPEN;
          if (statusStr != null && !statusStr.trim().isEmpty()) {
            try {
              status =
                  com.group13.auction.model.auction.Auction.AuctionStatus.valueOf(
                      statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
              // Keep default OPEN status when DB value is invalid.
            }
          }

          // FIX: dùng overload có savedViewerCount để restore viewerCount từ DB
          com.group13.auction.model.auction.Auction auction =
              com.group13.auction.model.auction.Auction.reconstitute(
                  id,
                  createdAt,
                  updatedAt,
                  item,
                  startTime,
                  endTime,
                  currentPrice,
                  status,
                  reservePrice,
                  savedViewerCount); // <-- FIX: truyền viewer_count vào

          if (currentLeader != null) {
            auction.updateBid(currentPrice, currentLeader);
          }

          return auction;
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi tìm Auction theo ID: auctionId={}", auctionId, e);
    }
    return null;
  }

  /** Lấy toàn bộ danh sách phiên đấu giá từ Database (phục vụ khởi động hệ thống). */
  public java.util.List<com.group13.auction.model.auction.Auction> findAll() {
    java.util.List<com.group13.auction.model.auction.Auction> auctions =
        new java.util.ArrayList<>();
    String sql = "SELECT * FROM auctions";

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
        java.sql.ResultSet rs = pstmt.executeQuery()) {

      while (rs.next()) {
        String id = rs.getString("id");
        com.group13.auction.model.auction.Auction auction = findAuctionById(id);
        if (auction != null) {
          auctions.add(auction);
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi lấy danh sách Auction", e);
    }
    return auctions;
  }

  /** Lấy danh sách auctionId của Seller đang ở trạng thái OPEN hoặc RUNNING trực tiếp từ DB. */
  public java.util.List<String> findUnfinishedAuctionIdsBySellerId(String sellerId) {
    java.util.List<String> ids = new java.util.ArrayList<>();
    String sql =
        "SELECT a.id FROM auctions a "
            + "JOIN items i ON a.item_id = i.id "
            + "WHERE i.seller_id = ? AND a.status IN ('OPEN', 'RUNNING')";

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, sellerId);
      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getString("id"));
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi tìm phiên chưa kết thúc của seller: sellerId={}", sellerId, e);
    }
    return ids;
  }

  /** Lấy danh sách tất cả auctionId của Seller (mọi trạng thái) từ DB. */
  public java.util.List<String> findAuctionIdsBySellerId(String sellerId) {
    java.util.List<String> ids = new java.util.ArrayList<>();
    String sql =
        "SELECT a.id FROM auctions a "
            + "JOIN items i ON a.item_id = i.id "
            + "WHERE i.seller_id = ?";

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, sellerId);
      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getString("id"));
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi lấy danh sách auctionId của seller: sellerId={}", sellerId, e);
    }
    return ids;
  }

  // ── SCOPE-AWARE LIST ──────────────────────────────────────────────────────

  /**
   * Lấy danh sách auction theo scopeFilter + statusFilter.
   *
   * <ul>
   *   <li>{@code OWNED} — lọc theo seller_id = sellerId (dùng sellerId param).
   *   <li>{@code JOINED} — lọc auction_id thuộc user_auction_activity JOINED của userId.
   *   <li>{@code WATCHING} — lọc auction_id thuộc user_auction_activity WATCHING của userId.
   *   <li>Mặc định ({@code ALL} / null) — không lọc thêm theo user.
   * </ul>
   *
   * @param userId id user hiện tại (dùng cho JOINED / WATCHING)
   * @param sellerId id seller hiện tại (dùng cho OWNED)
   * @param scopeFilter ALL | OWNED | JOINED | WATCHING (null = ALL)
   * @param statusFilter trạng thái auction (null = tất cả)
   * @param page trang bắt đầu từ 0
   * @param size số bản ghi mỗi trang
   * @return danh sách Auction khớp
   */
  public java.util.List<com.group13.auction.model.auction.Auction> findByScope(
      String userId, String sellerId, String scopeFilter, String statusFilter, int page, int size) {

    StringBuilder sql =
        new StringBuilder("SELECT a.id FROM auctions a JOIN items i ON a.item_id = i.id ");

    String scope = (scopeFilter == null) ? "ALL" : scopeFilter.trim().toUpperCase();
    switch (scope) {
      case "JOINED", "WATCHING" ->
          sql.append("JOIN user_auction_activity uaa ")
              .append("ON uaa.auction_id = a.id AND uaa.user_id = ? AND uaa.activity_type = '")
              .append(scope)
              .append("' ");
      default -> {
        // ALL — không join thêm
      }
    }

    sql.append("WHERE 1=1 ");
    if ("OWNED".equals(scope)) {
      sql.append("AND i.seller_id = ? ");
    }
    if (statusFilter != null && !statusFilter.isEmpty()) {
      sql.append("AND a.status = ? ");
    }
    sql.append("ORDER BY a.created_at DESC LIMIT ? OFFSET ?");

    java.util.List<com.group13.auction.model.auction.Auction> result = new java.util.ArrayList<>();
    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

      int idx = 1;
      if ("JOINED".equals(scope) || "WATCHING".equals(scope)) {
        pstmt.setString(idx++, userId);
      }
      if ("OWNED".equals(scope)) {
        pstmt.setString(idx++, sellerId);
      }
      if (statusFilter != null && !statusFilter.isEmpty()) {
        pstmt.setString(idx, statusFilter);
      }
      pstmt.setInt(idx++, size);
      pstmt.setInt(idx, page * size);

      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          com.group13.auction.model.auction.Auction a = findAuctionById(rs.getString("id"));
          if (a != null) {
            result.add(a);
          }
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi lấy danh sách auction theo scope: scope={}, userId={}", scope, userId, e);
    }
    return result;
  }

  /** Đếm số auction theo scopeFilter + statusFilter (dùng để tính totalPages). */
  public long countByScope(
      String userId, String sellerId, String scopeFilter, String statusFilter) {

    StringBuilder sql =
        new StringBuilder("SELECT COUNT(*) FROM auctions a JOIN items i ON a.item_id = i.id ");

    String scope = (scopeFilter == null) ? "ALL" : scopeFilter.trim().toUpperCase();
    switch (scope) {
      case "JOINED", "WATCHING" ->
          sql.append("JOIN user_auction_activity uaa ")
              .append("ON uaa.auction_id = a.id AND uaa.user_id = ? AND uaa.activity_type = '")
              .append(scope)
              .append("' ");
      default -> {
        // no-op
      }
    }

    sql.append("WHERE 1=1 ");
    if ("OWNED".equals(scope)) {
      sql.append("AND i.seller_id = ? ");
    }
    if (statusFilter != null && !statusFilter.isEmpty()) {
      sql.append("AND a.status = ? ");
    }

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

      int idx = 1;
      if ("JOINED".equals(scope) || "WATCHING".equals(scope)) {
        pstmt.setString(idx++, userId);
      }
      if ("OWNED".equals(scope)) {
        pstmt.setString(idx++, sellerId);
      }
      if (statusFilter != null && !statusFilter.isEmpty()) {
        pstmt.setString(idx, statusFilter);
      }

      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi đếm auction theo scope: scope={}, userId={}", scope, userId, e);
    }
    return 0L;
  }

  // ── SCOPE-AWARE SEARCH ────────────────────────────────────────────────────

  /**
   * Tìm kiếm phiên đấu giá theo keyword + scopeFilter. Kết hợp LIKE tên sản phẩm với lọc theo user
   * activity.
   *
   * @param keyword từ khóa tìm kiếm
   * @param userId id user hiện tại
   * @param sellerId id seller hiện tại (dùng cho OWNED)
   * @param scopeFilter ALL | OWNED | JOINED | WATCHING
   * @param page trang
   * @param size kích thước trang
   * @param sortBy cột sắp xếp
   * @param sortDir ASC | DESC
   * @return danh sách Auction khớp
   */
  public java.util.List<com.group13.auction.model.auction.Auction> searchByItemNameAndScope(
      String keyword,
      String userId,
      String sellerId,
      String scopeFilter,
      int page,
      int size,
      String sortBy,
      String sortDir) {

    String scope = (scopeFilter == null) ? "ALL" : scopeFilter.trim().toUpperCase();
    final String orderClause = buildOrderClause(sortBy, sortDir);

    StringBuilder sql =
        new StringBuilder("SELECT a.id FROM auctions a JOIN items i ON a.item_id = i.id ");

    switch (scope) {
      case "JOINED", "WATCHING" ->
          sql.append("JOIN user_auction_activity uaa ")
              .append("ON uaa.auction_id = a.id AND uaa.user_id = ? AND uaa.activity_type = '")
              .append(scope)
              .append("' ");
      default -> {
        // no-op
      }
    }

    sql.append("WHERE LOWER(i.name) LIKE LOWER(?) ");
    if ("OWNED".equals(scope)) {
      sql.append("AND i.seller_id = ? ");
    }
    sql.append("ORDER BY ").append(orderClause).append(" LIMIT ? OFFSET ?");

    java.util.List<com.group13.auction.model.auction.Auction> result = new java.util.ArrayList<>();
    String likeKeyword = "%" + keyword.trim() + "%";
    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

      int idx = 1;
      if ("JOINED".equals(scope) || "WATCHING".equals(scope)) {
        pstmt.setString(idx++, userId);
      }
      pstmt.setString(idx++, likeKeyword);
      if ("OWNED".equals(scope)) {
        pstmt.setString(idx, sellerId);
      }
      pstmt.setInt(idx++, size);
      pstmt.setInt(idx, page * size);

      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          com.group13.auction.model.auction.Auction a = findAuctionById(rs.getString("id"));
          if (a != null) {
            result.add(a);
          }
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi tìm kiếm auction theo scope: scope={}, keyword={}", scope, keyword, e);
    }
    return result;
  }

  /** Đếm tổng số kết quả tìm kiếm theo keyword + scopeFilter. */
  public long countByItemNameAndScope(
      String keyword, String userId, String sellerId, String scopeFilter) {

    String scope = (scopeFilter == null) ? "ALL" : scopeFilter.trim().toUpperCase();

    StringBuilder sql =
        new StringBuilder("SELECT COUNT(*) FROM auctions a JOIN items i ON a.item_id = i.id ");

    switch (scope) {
      case "JOINED", "WATCHING" ->
          sql.append("JOIN user_auction_activity uaa ")
              .append("ON uaa.auction_id = a.id AND uaa.user_id = ? AND uaa.activity_type = '")
              .append(scope)
              .append("' ");
      default -> {
        // no-op
      }
    }

    sql.append("WHERE LOWER(i.name) LIKE LOWER(?) ");
    if ("OWNED".equals(scope)) {
      sql.append("AND i.seller_id = ? ");
    }

    String likeKeyword = "%" + keyword.trim() + "%";
    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

      int idx = 1;
      if ("JOINED".equals(scope) || "WATCHING".equals(scope)) {
        pstmt.setString(idx++, userId);
      }
      pstmt.setString(idx++, likeKeyword);
      if ("OWNED".equals(scope)) {
        pstmt.setString(idx, sellerId);
      }

      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi đếm auction theo scope+keyword: scope={}, keyword={}", scope, keyword, e);
    }
    return 0L;
  }

  /**
   * Tìm phiên đấu giá theo tên sản phẩm (LIKE, không phân biệt hoa thường). Hỗ trợ phân trang
   * (LIMIT/OFFSET) và sắp xếp theo whitelist cột.
   *
   * <p>SQL injection được ngăn bằng cách whitelist {@code sortBy}/{@code sortDir} — chỉ các giá trị
   * đã biết mới được dùng trực tiếp trong câu SQL.
   *
   * @param keyword từ khóa tìm kiếm (không cần thêm %)
   * @param page trang, bắt đầu từ 0
   * @param size số bản ghi mỗi trang
   * @param sortBy cột sắp xếp: currentPrice | endTime | createdAt | itemName
   * @param sortDir chiều: ASC | DESC
   * @return danh sách Auction khớp, đã được reconstitute từ DB
   */
  public java.util.List<com.group13.auction.model.auction.Auction> searchByItemName(
      String keyword, int page, int size, String sortBy, String sortDir) {

    String orderClause = buildOrderClause(sortBy, sortDir);
    String sql =
        "SELECT a.id FROM auctions a "
            + "JOIN items i ON a.item_id = i.id "
            + "WHERE LOWER(i.name) LIKE LOWER(?) "
            + "ORDER BY "
            + orderClause
            + " "
            + "LIMIT ? OFFSET ?";

    java.util.List<com.group13.auction.model.auction.Auction> result = new java.util.ArrayList<>();
    int offset = page * size;
    String likeKeyword = "%" + keyword.trim() + "%";

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, likeKeyword);
      pstmt.setInt(2, size);
      pstmt.setInt(3, offset);

      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          String id = rs.getString("id");
          com.group13.auction.model.auction.Auction auction = findAuctionById(id);
          if (auction != null) {
            result.add(auction);
          }
        }
      }
    } catch (java.sql.SQLException e) {
      log.error(
          "Lỗi tìm kiếm auction theo tên sản phẩm: keyword={}, page={}, size={}",
          keyword,
          page,
          size,
          e);
    }
    return result;
  }

  /**
   * Đếm tổng số phiên đấu giá khớp với keyword (dùng để tính totalPages).
   *
   * @param keyword từ khóa tìm kiếm
   * @return tổng số bản ghi khớp
   */
  public long countByItemName(String keyword) {
    String sql =
        "SELECT COUNT(*) FROM auctions a "
            + "JOIN items i ON a.item_id = i.id "
            + "WHERE LOWER(i.name) LIKE LOWER(?)";

    String likeKeyword = "%" + keyword.trim() + "%";

    try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, likeKeyword);
      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      log.error("Lỗi đếm auction theo tên sản phẩm: keyword={}", keyword, e);
    }
    return 0L;
  }

  /** Xây ORDER BY clause từ tên cột và chiều sắp xếp. Whitelist để tránh SQL injection. */
  private String buildOrderClause(String sortBy, String sortDir) {
    final String normalizedSortBy = sortBy == null ? "" : sortBy.trim();
    final String col;
    switch (normalizedSortBy) {
      case "currentPrice":
        col = "a.current_price";
        break;
      case "endTime":
        col = "a.end_time";
        break;
      case "itemName":
        col = "i.name";
        break;
      default:
        col = "a.created_at";
        break;
    }
    String dir = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
    return col + " " + dir;
  }
}
