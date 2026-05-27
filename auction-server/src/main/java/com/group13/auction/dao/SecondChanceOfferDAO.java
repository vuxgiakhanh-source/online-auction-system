package com.group13.auction.dao;

import com.group13.auction.model.auction.SecondChanceOffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecondChanceOfferDAO {
  private static final Logger log = LoggerFactory.getLogger(SecondChanceOfferDAO.class);

  private final UserDAO userDAO;

  public SecondChanceOfferDAO() {
    this.userDAO = new UserDAO();
  }

  /** Constructor dùng trong test hoặc khi cần inject UserDAO tùy chỉnh. */
  public SecondChanceOfferDAO(UserDAO userDAO) {
    this.userDAO = userDAO;
  }

  /** Lưu một đề nghị Second Chance mới xuống DB */
  public boolean saveOffer(SecondChanceOffer offer) {
    String sql =
        "INSERT INTO second_chance_offers (id, auction_id, runner_up_id, offer_price, deposit_paid,"
            + " status, deadline) VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, offer.getId());
      pstmt.setString(2, offer.getAuctionId());
      pstmt.setString(3, offer.getRunnerUp().getId());
      pstmt.setLong(4, offer.getOfferPrice());
      pstmt.setLong(5, offer.getDepositPaid());
      pstmt.setString(6, offer.getStatus().name());
      pstmt.setTimestamp(7, Timestamp.valueOf(offer.getDeadline()));

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Lỗi lưu Second Chance Offer", e);
      return false;
    }
  }

  /** Cập nhật trạng thái đề nghị (PENDING, ACCEPTED, DECLINED, EXPIRED) */
  public boolean updateOfferStatus(String offerId, String status) {
    String sql = "UPDATE second_chance_offers SET status = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, status);
      pstmt.setString(2, offerId);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Lỗi cập nhật trạng thái Second Chance Offer", e);
      return false;
    }
  }

  /**
   * Tìm SecondChanceOffer đang PENDING cho một phiên đấu giá (FIX Bug #5). PaymentHandler dùng để
   * lấy offer thật trước khi accept/decline.
   *
   * @param auctionId UUID của auction
   * @return SecondChanceOffer đang PENDING, hoặc null nếu không có
   */
  public SecondChanceOffer findPendingOfferByAuctionId(String auctionId) {
    String sql =
        "SELECT id, runner_up_id, auction_id, offer_price, deposit_paid, "
            + "status, deadline, created_at FROM second_chance_offers "
            + "WHERE auction_id = ? AND status = 'PENDING' "
            + "ORDER BY deadline DESC LIMIT 1";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          String offerId = rs.getString("id");
          String runnerUpId = rs.getString("runner_up_id");
          long offerPrice = rs.getLong("offer_price");
          long depositPaid = rs.getLong("deposit_paid");
          Timestamp deadlineTs = rs.getTimestamp("deadline");
          Timestamp createdTs = rs.getTimestamp("created_at");

          LocalDateTime deadline =
              deadlineTs != null ? deadlineTs.toLocalDateTime() : LocalDateTime.now().plusHours(24);
          LocalDateTime createdAt =
              createdTs != null ? createdTs.toLocalDateTime() : LocalDateTime.now();

          com.group13.auction.model.user.NormalUser runnerUp =
              userDAO.findNormalUserById(runnerUpId);
          if (runnerUp == null) {
            return null;
          }

          return SecondChanceOffer.reconstitute(
              offerId,
              createdAt,
              createdAt,
              runnerUp,
              auctionId,
              offerPrice,
              depositPaid,
              deadline,
              SecondChanceOffer.OfferStatus.PENDING);
        }
      }
    } catch (SQLException e) {
      log.error("Lỗi tìm Second Chance Offer PENDING", e);
    }
    return null;
  }

  /** Các phiên có offer PENDING đã quá hạn — dùng cho scheduler. */
  /** Tất cả Second Chance Offer PENDING thuộc phiên của Seller (mọi trạng thái phiên). */
  public List<SecondChanceOffer> findPendingOffersBySellerId(String sellerId) {
    List<SecondChanceOffer> offers = new ArrayList<>();
    if (sellerId == null || sellerId.isBlank()) {
      return offers;
    }
    String sql =
        "SELECT sco.id, sco.runner_up_id, sco.auction_id, sco.offer_price, sco.deposit_paid, "
            + "sco.status, sco.deadline, sco.created_at "
            + "FROM second_chance_offers sco "
            + "INNER JOIN auctions a ON sco.auction_id = a.id "
            + "INNER JOIN items i ON a.item_id = i.id "
            + "WHERE i.seller_id = ? AND sco.status = 'PENDING'";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, sellerId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          SecondChanceOffer offer = mapOfferRow(rs);
          if (offer != null) {
            offers.add(offer);
          }
        }
      }
    } catch (SQLException e) {
      log.error("Lỗi tìm Second Chance Offer PENDING theo seller: sellerId={}", sellerId, e);
    }
    return offers;
  }

  private SecondChanceOffer mapOfferRow(ResultSet rs) throws SQLException {
    String offerId = rs.getString("id");
    String runnerUpId = rs.getString("runner_up_id");
    String auctionId = rs.getString("auction_id");
    long offerPrice = rs.getLong("offer_price");
    long depositPaid = rs.getLong("deposit_paid");
    Timestamp deadlineTs = rs.getTimestamp("deadline");
    Timestamp createdTs = rs.getTimestamp("created_at");

    LocalDateTime deadline =
        deadlineTs != null ? deadlineTs.toLocalDateTime() : LocalDateTime.now().plusHours(24);
    LocalDateTime createdAt = createdTs != null ? createdTs.toLocalDateTime() : LocalDateTime.now();

    com.group13.auction.model.user.NormalUser runnerUp = userDAO.findNormalUserById(runnerUpId);
    if (runnerUp == null) {
      return null;
    }

    return SecondChanceOffer.reconstitute(
        offerId,
        createdAt,
        createdAt,
        runnerUp,
        auctionId,
        offerPrice,
        depositPaid,
        deadline,
        SecondChanceOffer.OfferStatus.PENDING);
  }

  public List<String> findAuctionIdsWithExpiredPendingOffers(LocalDateTime asOf) {
    String sql =
        "SELECT DISTINCT auction_id FROM second_chance_offers "
            + "WHERE status = 'PENDING' AND deadline < ?";
    List<String> ids = new ArrayList<>();
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setTimestamp(1, Timestamp.valueOf(asOf));
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getString("auction_id"));
        }
      }
    } catch (SQLException e) {
      log.error("Lỗi quét second_chance_offers hết hạn", e);
    }
    return ids;
  }
}
