package com.group13.auction.dao;

import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.user.NormalUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuctionWinnerDAO {
  private static final Logger log = LoggerFactory.getLogger(AuctionWinnerDAO.class);

  public AuctionWinnerDAO() {}

  /** Lưu thông tin người thắng cuộc mới vào bảng auction_winners */
  public boolean saveWinner(AuctionWinner winner) {
    String sql =
        "INSERT INTO auction_winners (id, auction_id, winner_id, final_price, deposit_paid,"
            + " payment_status, payment_deadline) VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, winner.getId());
      pstmt.setString(2, winner.getAuctionId());
      pstmt.setString(3, winner.getWinner().getId());
      pstmt.setLong(4, winner.getFinalPrice());
      pstmt.setLong(5, winner.getDepositPaid());
      pstmt.setString(6, winner.getPaymentStatus().name());

      pstmt.setTimestamp(
          7,
          winner.getPaymentDeadline() != null
              ? java.sql.Timestamp.valueOf(winner.getPaymentDeadline())
              : null);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi lưu thông tin người thắng cuộc: winnerId={}, auctionId={}",
          winner != null ? winner.getId() : null,
          winner != null ? winner.getAuctionId() : null,
          e);
      return false;
    }
  }

  /** Cập nhật trạng thái thanh toán (PENDING, COMPLETED, EXPIRED, FUNDS_HELD). */
  public boolean updatePaymentStatus(String winnerId, String status) {
    String sql = "UPDATE auction_winners SET payment_status = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, status);
      pstmt.setString(2, winnerId);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error("Lỗi cập nhật trạng thái thanh toán: winnerId={}, status={}", winnerId, status, e);
      return false;
    }
  }

  /** Update payment status using the caller transaction. */
  public boolean updatePaymentStatus(Connection conn, String winnerId, String status)
      throws SQLException {
    String sql = "UPDATE auction_winners SET payment_status = ? WHERE id = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, status);
      pstmt.setString(2, winnerId);
      return pstmt.executeUpdate() > 0;
    }
  }

  /** Update FUNDS_HELD status and confirm-receipt deadline after winner payment. */
  public boolean updateFundsHeld(
      String winnerId, String status, java.time.LocalDateTime confirmReceiptDeadline) {
    String sql =
        "UPDATE auction_winners SET payment_status = ?, confirm_receipt_deadline = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, status);
      pstmt.setTimestamp(2, java.sql.Timestamp.valueOf(confirmReceiptDeadline));
      pstmt.setString(3, winnerId);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi cập nhật FUNDS_HELD: winnerId={}, status={}, confirmReceiptDeadline={}",
          winnerId,
          status,
          confirmReceiptDeadline,
          e);
      return false;
    }
  }

  /** Cập nhật hạn report sau khi winner xác nhận nhận hàng. */
  public boolean updateReportDeadline(String winnerId, java.time.LocalDateTime reportDeadline) {
    String sql = "UPDATE auction_winners SET report_deadline = ? WHERE id = ?";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setTimestamp(1, java.sql.Timestamp.valueOf(reportDeadline));
      pstmt.setString(2, winnerId);

      return pstmt.executeUpdate() > 0;
    } catch (SQLException e) {
      log.error(
          "Lỗi cập nhật report_deadline: winnerId={}, reportDeadline={}",
          winnerId,
          reportDeadline,
          e);
      return false;
    }
  }

  /**
   * Khôi phục AuctionWinner từ DB theo auction_id. Dùng khi server restart để restore winner vào
   * in-memory Auction object.
   *
   * @param auctionId ID phiên đấu giá
   * @param userDAO DAO để load NormalUser theo winner_id
   * @return AuctionWinner nếu tồn tại, null nếu không có
   */
  public AuctionWinner findByAuctionId(String auctionId, UserDAO userDAO) {
    String sql =
        "SELECT id, auction_id, winner_id, final_price, deposit_paid, "
            + "payment_status, payment_deadline, confirm_receipt_deadline, "
            + "report_deadline, is_second_offer, created_at, updated_at "
            + "FROM auction_winners WHERE auction_id = ? LIMIT 1";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, auctionId);
      try (ResultSet rs = pstmt.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        String winnerId = rs.getString("winner_id");
        NormalUser winner = userDAO.findNormalUserById(winnerId);
        if (winner == null) {
          log.warn("findByAuctionId: winner user not found in DB: userId={}", winnerId);
          return null;
        }

        Timestamp paymentDeadlineTs = rs.getTimestamp("payment_deadline");
        Timestamp confirmTs = rs.getTimestamp("confirm_receipt_deadline");
        Timestamp reportTs = rs.getTimestamp("report_deadline");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        return AuctionWinner.reconstitute(
            rs.getString("id"),
            createdTs != null ? createdTs.toLocalDateTime() : null,
            updatedTs != null ? updatedTs.toLocalDateTime() : null,
            winner,
            rs.getString("auction_id"),
            rs.getLong("final_price"),
            rs.getLong("deposit_paid"),
            paymentDeadlineTs != null ? paymentDeadlineTs.toLocalDateTime() : null,
            confirmTs != null ? confirmTs.toLocalDateTime() : null,
            reportTs != null ? reportTs.toLocalDateTime() : null,
            PaymentStatus.valueOf(rs.getString("payment_status")),
            rs.getBoolean("is_second_offer"));
      }
    } catch (SQLException e) {
      log.error("Lỗi load AuctionWinner từ DB: auctionId={}", auctionId, e);
      return null;
    }
  }

  /**
   * Dùng trong AccountService.deleteAccount() để chặn xóa tài khoản khi user chưa hoàn tất thanh
   * toán phiên đấu giá.
   *
   * @param userId ID của user cần kiểm tra
   * @return true nếu có ít nhất 1 AuctionWinner ở trạng thái PENDING
   */
  public boolean hasPendingPayment(String userId) {
    String sql =
        "SELECT COUNT(*) FROM auction_winners WHERE winner_id = ? AND payment_status = 'PENDING'";

    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql)) {

      pstmt.setString(1, userId);
      try (java.sql.ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1) > 0;
        }
      }
    } catch (SQLException e) {
      log.error("Lỗi kiểm tra pending payment: userId={}", userId, e);
    }
    return false;
  }
}
