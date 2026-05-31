package com.group13.auction.dao;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BidTransactionDAO {
  private static final Logger log = LoggerFactory.getLogger(BidTransactionDAO.class);

  private final UserDAO userDAO = new UserDAO();

  public BidTransactionDAO() {}

  /** 1. Lưu lại lịch sử một lượt đặt giá vào Database */
  public boolean saveTransaction(BidTransaction tx) {
    // bid_time sử dụng DEFAULT CURRENT_TIMESTAMP(3) của DB để đảm bảo precision ms và thứ tự đúng.
    // Không truyền bid_time từ Java vì TIMESTAMP precision chỉ đến giây khi dùng DEFAULT
    // CURRENT_TIMESTAMP.
    String sql =
        "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, result) VALUES (?, ?,"
            + " ?, ?, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, tx.getId());
      pstmt.setString(2, tx.getAuctionId());
      pstmt.setString(3, tx.getBidder().getId());
      pstmt.setLong(4, tx.getAmount());
      pstmt.setString(5, tx.getResult().name());

      boolean saved = pstmt.executeUpdate() > 0;
      log.debug(
          "Bid transaction saved: txId={}, auctionId={}, bidderId={}, amount={}, result={}",
          tx.getId(),
          tx.getAuctionId(),
          tx.getBidder().getId(),
          tx.getAmount(),
          tx.getResult());
      return saved;

    } catch (SQLException e) {
      log.error(
          "Failed to save bid transaction: txId={}, auctionId={}, bidderId={}",
          tx != null ? tx.getId() : null,
          tx != null ? tx.getAuctionId() : null,
          tx != null && tx.getBidder() != null ? tx.getBidder().getId() : null,
          e);
      return false;
    }
  }

  /**
   * Lưu bid và cập nhật giá cao nhất trong một transaction DB. Tránh trạng thái bid đã ghi nhưng
   * current_price chưa cập nhật (hoặc ngược lại).
   *
   * <p>FIX DEADLOCK: thứ tự cũ INSERT bid_tx → UPDATE auctions gây FK deadlock dưới tải cao.
   * InnoDB: INSERT vào bid_transactions (có FK → auctions.id) → acquire SHARED lock trên auctions
   * row để kiểm tra FK. Nếu 2 thread cùng INSERT xong rồi cùng UPDATE auctions, cả hai đều đang giữ
   * SHARED lock và đợi EXCLUSIVE lock của nhau → DEADLOCK.
   *
   * <p>Fix: đảo thứ tự → UPDATE auctions TRƯỚC, INSERT bid_tx SAU. UPDATE auctions cạnh tranh
   * EXCLUSIVE lock ngay từ đầu → InnoDB serialize tự nhiên, không có SHARED/EXCLUSIVE conflict. Sau
   * khi commit, INSERT chạy bình thường.
   */
  public boolean saveTransactionAndUpdatePrice(
      BidTransaction tx, String auctionId, long newPrice, String bidderId) {
    String updateSql =
        "UPDATE auctions SET current_price = ?, current_leader_id = ?, "
            + "current_highest_price = ?, winning_bidder_id = ? "
            + "WHERE id = ? AND current_price < ?";
    String insertSql =
        "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, result) "
            + "VALUES (?, ?, ?, ?, ?)";

    Connection conn = null;
    try {
      conn = DatabaseConnection.getInstance().getConnection();
      conn.setAutoCommit(false);

      // FIX: UPDATE trước → EXCLUSIVE lock trên auctions row ngay lập tức
      // → không conflict với SHARED lock từ FK check của INSERT bên dưới.
      try (PreparedStatement update = conn.prepareStatement(updateSql)) {
        update.setLong(1, newPrice);
        update.setString(2, bidderId);
        update.setLong(3, newPrice);
        update.setString(4, bidderId);
        update.setString(5, auctionId);
        update.setLong(6, newPrice);
        update.executeUpdate();
        // Không check rows affected: nếu current_price >= newPrice (stale write),
        // 0 rows updated là đúng — bid đã bị bid khác vượt qua trong RAM nhưng
        // giá DB đã cao hơn → không cần update. TX vẫn commit để INSERT được lưu.
      }

      // INSERT sau UPDATE: lúc này không còn SHARED lock conflict nữa
      try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
        insert.setString(1, tx.getId());
        insert.setString(2, tx.getAuctionId());
        insert.setString(3, tx.getBidder().getId());
        insert.setLong(4, tx.getAmount());
        insert.setString(5, tx.getResult().name());
        if (insert.executeUpdate() <= 0) {
          conn.rollback();
          return false;
        }
      }

      conn.commit();
      log.debug(
          "Bid persisted atomically: txId={}, auctionId={}, amount={}",
          tx.getId(),
          auctionId,
          newPrice);
      return true;
    } catch (SQLException e) {
      if (conn != null) {
        try {
          conn.rollback();
        } catch (SQLException rollbackEx) {
          log.error("Rollback failed after bid persist error: auctionId={}", auctionId, rollbackEx);
        }
      }
      log.error(
          "Atomic bid persist failed: txId={}, auctionId={}",
          tx != null ? tx.getId() : null,
          auctionId,
          e);
      return false;
    } finally {
      if (conn != null) {
        try {
          conn.setAutoCommit(true);
          conn.close();
        } catch (SQLException closeEx) {
          log.warn("Failed to close connection after bid persist: {}", closeEx.getMessage());
        }
      }
    }
  }

  /** 2. Tìm danh sách tất cả những người (bidders) đã tham gia đặt giá hợp lệ trong một phiên. */
  public List<NormalUser> findBiddersByAuction(String auctionId) {
    List<NormalUser> bidders = new ArrayList<>();
    // Chỉ lấy những người có bid được ACCEPTED
    String sql =
        "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = ? AND result !="
            + " 'REJECTED'";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {
        UserDAO userDAO = new UserDAO();

        while (rs.next()) {
          String bidderId = rs.getString("bidder_id");
          NormalUser user = userDAO.findNormalUserById(bidderId);
          if (user != null) {
            bidders.add(user);
          }
        }
      }
    } catch (SQLException e) {
      log.error("Failed to find bidders by auction: auctionId={}", auctionId, e);
    }
    log.debug("Bidders loaded by auction: auctionId={}, count={}", auctionId, bidders.size());
    return bidders;
  }

  /**
   * 3. Lấy toàn bộ lịch sử đặt giá HỢP LỆ theo auctionId, sắp xếp theo thời gian tăng dần. Dùng cho
   * GET_BID_HISTORY (vẽ line chart).
   *
   * <p>FIX BUG #1: Loại REJECTED và CANCELLED_BY_LEAVE — bid hủy khi rời phiên không còn trong
   * bảng xếp hạng, không được vẽ lên chart hay làm lệch giá hiển thị trên client.
   */
  public List<BidTransaction> findByAuctionId(String auctionId) {
    List<BidTransaction> result = new ArrayList<>();

    String sql =
        "SELECT id, auction_id, bidder_id, bid_amount, result, bid_time "
            + "FROM bid_transactions "
            + "WHERE auction_id = ? "
            + "AND result NOT IN ('REJECTED', 'CANCELLED_BY_LEAVE') "
            + "ORDER BY bid_time ASC, seq ASC";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          String id = rs.getString("id");
          String fetchedAuction = rs.getString("auction_id");
          String bidderId = rs.getString("bidder_id");
          long amount = rs.getLong("bid_amount");
          String resultStr = rs.getString("result");

          java.sql.Timestamp ts = rs.getTimestamp("bid_time");
          java.time.LocalDateTime bidTime =
              (ts != null) ? ts.toLocalDateTime() : java.time.LocalDateTime.now();

          NormalUser bidder = userDAO.findNormalUserById(bidderId);

          BidTransaction tx =
              BidTransaction.reconstitute(
                  id,
                  bidTime,
                  bidTime,
                  bidder,
                  fetchedAuction,
                  amount,
                  bidTime,
                  BidTransaction.BidResult.valueOf(resultStr));
          result.add(tx);
        }
      }
    } catch (SQLException e) {
      log.error("Failed to find bid history by auction: auctionId={}", auctionId, e);
    }
    log.debug("Bid history loaded: auctionId={}, count={}", auctionId, result.size());
    return result;
  }

  /**
   * Bid hợp lệ cao nhất còn lại trong phiên (ACCEPTED hoặc ACCEPTED_RESERVE_NOT_MET). Dùng sau khi
   * có người rời phiên để xếp lại bảng xếp hạng.
   */
  public BidTransaction findHighestValidBid(String auctionId) {
    String sql =
        "SELECT * FROM bid_transactions WHERE auction_id = ? "
            + "AND result IN ('ACCEPTED', 'ACCEPTED_RESERVE_NOT_MET') "
            + "ORDER BY bid_amount DESC LIMIT 1";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);

      try (ResultSet rs = pstmt.executeQuery()) {
        return mapHighestValidBidRow(rs);
      }
    } catch (SQLException e) {
      log.error("Failed to find highest valid bid: auctionId={}", auctionId, e);
    }
    log.debug("No valid bid found for auction: auctionId={}", auctionId);
    return null;
  }

  /**
   * Bid hợp lệ cao nhất, ngoại trừ một bidder (runner-up cho Second Chance Offer).
   */
  public BidTransaction findHighestValidBidExcept(String auctionId, String excludedBidderId) {
    String sql =
        "SELECT * FROM bid_transactions WHERE auction_id = ? AND bidder_id != ? "
            + "AND result IN ('ACCEPTED', 'ACCEPTED_RESERVE_NOT_MET') "
            + "ORDER BY bid_amount DESC LIMIT 1";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      pstmt.setString(2, excludedBidderId != null ? excludedBidderId : "");

      try (ResultSet rs = pstmt.executeQuery()) {
        return mapHighestValidBidRow(rs);
      }
    } catch (SQLException e) {
      log.error(
          "Failed to find highest valid bid except bidder: auctionId={}, excludedBidderId={}",
          auctionId,
          excludedBidderId,
          e);
    }
    log.debug(
        "No runner-up bid found: auctionId={}, excludedBidderId={}", auctionId, excludedBidderId);
    return null;
  }

  private BidTransaction mapHighestValidBidRow(ResultSet rs) throws SQLException {
    if (!rs.next()) {
      return null;
    }
    String id = rs.getString("id");
    String bidderId = rs.getString("bidder_id");
    long amount = rs.getLong("bid_amount");
    String resultStr = rs.getString("result");

    java.sql.Timestamp bidTimeTs = rs.getTimestamp("bid_time");
    java.time.LocalDateTime bidTime =
        (bidTimeTs != null) ? bidTimeTs.toLocalDateTime() : java.time.LocalDateTime.now();

    NormalUser bidder = userDAO.findNormalUserById(bidderId);

    return BidTransaction.reconstitute(
        id,
        bidTime,
        bidTime,
        bidder,
        null,
        amount,
        bidTime,
        BidTransaction.BidResult.valueOf(resultStr));
  }

  /**
   * Đánh dấu toàn bộ bid ACCEPTED của một bidder trong một phiên là CANCELLED_BY_LEAVE. Gọi khi
   * leader tự rời phiên — bid của họ bị xóa sổ khỏi lịch sử hợp lệ.
   *
   * @return số rows bị cập nhật (>= 0)
   */
  public int cancelBidsByBidder(String auctionId, String bidderId) {
    String sql =
        "UPDATE bid_transactions SET result = 'CANCELLED_BY_LEAVE' "
            + "WHERE auction_id = ? AND bidder_id = ? "
            + "AND result IN ('ACCEPTED', 'ACCEPTED_RESERVE_NOT_MET')";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, auctionId);
      pstmt.setString(2, bidderId);
      int rows = pstmt.executeUpdate();
      log.info(
          "Bids cancelled by leave: auctionId={}, bidderId={}, rows={}", auctionId, bidderId, rows);
      return rows;
    } catch (SQLException e) {
      log.error(
          "Failed to cancel bids by leave: auctionId={}, bidderId={}", auctionId, bidderId, e);
      return 0;
    }
  }

  /**
   * Cập nhật current_leader_id và current_price sau khi leader rời phiên. Force-update không dùng
   * conditional WHERE current_price < ?.
   *
   * @param newLeaderId null nếu không còn ai dẫn đầu
   * @param newPrice 0 nếu không còn ai dẫn đầu
   */
  public boolean updateLeaderAfterLeave(String auctionId, String newLeaderId, long newPrice) {
    String sql =
        "UPDATE auctions SET current_price = ?, current_leader_id = ?, "
            + "current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, newPrice);
      if (newLeaderId != null) {
        pstmt.setString(2, newLeaderId);
        pstmt.setLong(3, newPrice);
        pstmt.setString(4, newLeaderId);
      } else {
        pstmt.setNull(2, java.sql.Types.VARCHAR);
        pstmt.setLong(3, 0L);
        pstmt.setNull(4, java.sql.Types.VARCHAR);
      }
      pstmt.setString(5, auctionId);
      boolean ok = pstmt.executeUpdate() > 0;
      log.info(
          "Leader updated after leave: auctionId={}, newLeaderId={}, newPrice={}",
          auctionId,
          newLeaderId,
          newPrice);
      return ok;
    } catch (SQLException e) {
      log.error("Failed to update leader after leave: auctionId={}", auctionId, e);
      return false;
    }
  }
}
