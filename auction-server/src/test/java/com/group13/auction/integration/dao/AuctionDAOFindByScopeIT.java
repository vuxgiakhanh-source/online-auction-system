package com.group13.auction.integration.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * IT regression #5a: findByScope(JOINED, RUNNING) phải bind đúng LIMIT/OFFSET (không LIMIT 0).
 */
@EnabledIfEnvironmentVariable(named = "OMNIBID_IT_DB", matches = "true")
@DisplayName("AuctionDAO.findByScope — JOINED + status")
class AuctionDAOFindByScopeIT {

  private final AuctionDAO auctionDAO = new AuctionDAO();
  private final UserDAO userDAO = new UserDAO();
  private final ItemDAO itemDAO = new ItemDAO();

  @Test
  @DisplayName("JOINED + RUNNING trả về phiên đã join đang chạy")
  void findByScope_joinedRunning_returnsAuction() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String bidderId =
        userDAO.registerUser(
            "bidder_scope_" + suffix, hashPassword("pass"), "bidder_scope_" + suffix + "@test.local");
    String sellerId =
        userDAO.registerUser(
            "seller_scope_" + suffix, hashPassword("pass"), "seller_scope_" + suffix + "@test.local");

    String itemId = UUID.randomUUID().toString();
    itemDAO.addItem(
        itemId, sellerId, "Scope Item " + suffix, "desc", 1_000_000L, "ELECTRONICS");
    Item item = itemDAO.findItemById(itemId);
    Auction auction =
        Auction.create(
            item,
            LocalDateTime.now().plusMinutes(1),
            LocalDateTime.now().plusHours(2),
            1_500_000L);
    auctionDAO.createAuction(auction);
    userDAO.saveUserAuctionActivity(bidderId, auction.getId(), "JOINED");

    List<Auction> result =
        auctionDAO.findByScope(bidderId, sellerId, "JOINED", "RUNNING", 0, 20);

    assertThat(result).extracting(Auction::getId).contains(auction.getId());
  }

  private static String hashPassword(String raw) {
    return com.group13.auction.model.user.User.hashPassword(raw);
  }
}
