package com.group13.auction.integration.base;

import com.group13.auction.dao.DatabaseConnection;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base class for integration tests using a real DB via Testcontainers.
 *
 * <p>Uses {@link DatabaseConnection#reconfigure(String, String, String)} (HikariCP) instead of the
 * old reflection-based hack, which was fragile and caused CommunicationsExceptions under concurrent
 * load.
 *
 * <p><b>Note:</b> {@link com.group13.auction.service.AuctionService} (and some other components)
 * call {@link com.group13.auction.model.user.SystemAdmin#getInstance()} during initialization. Any
 * test that creates {@code new AuctionService(...)} must call {@code
 * com.group13.auction.unit.TestFixture.bootstrapSystemAdmin()} first, and {@code
 * TestFixture.resetSystemAdmin()} in tear-down for isolation.
 */
public abstract class IntegrationTestBase {

  private final List<String> trackedUserIds = new ArrayList<>();
  private final List<String> trackedItemIds = new ArrayList<>();
  private final List<String> trackedAuctionIds = new ArrayList<>();
  private final List<String> trackedBidTxIds = new ArrayList<>();
  private final List<String> trackedFinTxIds = new ArrayList<>();
  private final List<String> trackedWinnerIds = new ArrayList<>();
  private final List<String> trackedSecondChanceIds = new ArrayList<>();
  private final List<String> trackedQualityIds = new ArrayList<>();

  /**
   * Reconfigure the HikariCP pool to point at the given Testcontainer.
   *
   * <p>Call once in a {@code @BeforeAll} of each load/integration test class:
   *
   * <pre>
   *   {@literal @}BeforeAll
   *   static void configureDataSource() throws Exception {
   *       configureTestcontainer(mysql);
   *   }
   * </pre>
   *
   * <p>Uses the public {@code reconfigure()} API on the HikariCP-backed singleton, completely
   * replacing the old reflection hack that broke under concurrent load.
   */
  protected static void configureTestcontainer(MySQLContainer mysql) {
    DatabaseConnection.getInstance()
        .reconfigure(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
  }

  protected void resetTracking() {
    trackedUserIds.clear();
    trackedItemIds.clear();
    trackedAuctionIds.clear();
    trackedBidTxIds.clear();
    trackedFinTxIds.clear();
    trackedWinnerIds.clear();
    trackedSecondChanceIds.clear();
    trackedQualityIds.clear();
  }

  protected void trackUser(String id) {
    trackedUserIds.add(id);
  }

  protected void trackItem(String id) {
    trackedItemIds.add(id);
  }

  protected void trackAuction(String id) {
    trackedAuctionIds.add(id);
  }

  protected void trackBidTx(String id) {
    trackedBidTxIds.add(id);
  }

  protected void trackFinTx(String id) {
    trackedFinTxIds.add(id);
  }

  protected void trackWinner(String id) {
    trackedWinnerIds.add(id);
  }

  protected void trackSecondChance(String id) {
    trackedSecondChanceIds.add(id);
  }

  protected void trackQualityReport(String id) {
    trackedQualityIds.add(id);
  }

  protected void cleanupDB() throws Exception {
    try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
      conn.setAutoCommit(false);
      try {
        delete(conn, "quality_reports", "id", trackedQualityIds);
        delete(conn, "second_chance_offers", "id", trackedSecondChanceIds);
        delete(conn, "auction_winners", "id", trackedWinnerIds);
        delete(conn, "bid_transactions", "id", trackedBidTxIds);
        delete(conn, "financial_transactions", "id", trackedFinTxIds);
        delete(conn, "user_auction_activity", "auction_id", trackedAuctionIds);
        delete(conn, "auctions", "id", trackedAuctionIds);
        delete(conn, "items", "id", trackedItemIds);
        delete(conn, "sellers", "user_id", trackedUserIds);
        delete(conn, "users", "id", trackedUserIds);
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  private void delete(Connection conn, String table, String col, List<String> ids)
      throws SQLException {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
    try (PreparedStatement ps =
        conn.prepareStatement("DELETE FROM " + table + " WHERE " + col + " IN (" + ph + ")")) {
      for (int i = 0; i < ids.size(); i++) {
        ps.setString(i + 1, ids.get(i));
      }
      ps.executeUpdate();
    }
  }

  protected NormalUser buildUserWithBalance(String username, long balance, UserDAO userDAO) {
    String email = username + "_" + UUID.randomUUID().toString().substring(0, 6) + "@test.vn";
    String userId = userDAO.registerUser(username, User.hashPassword("test_pass"), email);
    if (userId == null) {
      throw new IllegalStateException("Cannot create user: " + username);
    }
    trackedUserIds.add(userId);
    if (balance > 0) {
      userDAO.addBalance(userId, balance);
    }
    NormalUser user = userDAO.findNormalUserById(userId);
    if (user == null) {
      throw new IllegalStateException("Cannot find user: " + username);
    }
    return user;
  }

  protected String buildItem(String sellerId, String name, long startingPrice, ItemDAO itemDAO) {
    ensureSellerRecord(sellerId);
    String itemId = UUID.randomUUID().toString();
    itemDAO.addItem(itemId, sellerId, name, "Integration test item", startingPrice, "ELECTRONICS");
    trackedItemIds.add(itemId);
    return itemId;
  }

  protected void ensureSellerRecord(String userId) {
    String sql =
        "INSERT IGNORE INTO sellers (user_id, approval_status, approved_date) "
            + "VALUES (?, 'APPROVED', CURRENT_TIMESTAMP)";
    try (Connection conn = DatabaseConnection.getInstance().getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, userId);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Cannot create seller record for: " + userId, e);
    }
  }
}
