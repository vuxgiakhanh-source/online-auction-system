package com.group13.auction.unit.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.unit.TestFixture;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServerBroadcastNotifier — auction ended push")
class ServerBroadcastNotifierEndedTest {

  @Mock private SessionManager sessionManager;
  @Mock private com.group13.auction.dao.UserDAO userDAO;

  private NormalUser seller;
  private NormalUser bidder;
  private Auction auction;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.silenceGlobalSingletons();
    seller = TestFixture.normalSeller("sb_seller");
    bidder = TestFixture.bidderWithBalance("sb_bidder", 10_000_000L);
    Art item = TestFixture.art("SB Item", 800_000L, seller);
    auction = TestFixture.runningAuction(seller, 800_000L);
    auction.updateBid(900_000L, bidder);
    AuctionManager.getInstance().registerAuction(auction);

    Field userDaoField = ServerBroadcastNotifier.class.getDeclaredField("userDAO");
    userDaoField.setAccessible(true);
    userDaoField.set(ServerBroadcastNotifier.getInstance(), userDAO);

    Field smField = ServerBroadcastNotifier.class.getDeclaredField("sessionManager");
    smField.setAccessible(true);
    smField.set(ServerBroadcastNotifier.getInstance(), sessionManager);
  }

  @Test
  @DisplayName("notifyAuctionEnded dùng deliverAuctionLifecyclePacket, không broadcast room")
  void notifyAuctionEnded_deliversLifecycleWithoutRoomBroadcast() throws Exception {
    org.mockito.Mockito.when(userDAO.findJoinedUserIdsByAuctionId(auction.getId()))
        .thenReturn(Set.of(bidder.getId()));

    ServerBroadcastNotifier.getInstance().notifyAuctionEnded(auction);

    verify(sessionManager)
        .deliverAuctionLifecyclePacket(
            eq(auction.getId()),
            argThat(
                p -> p != null && p.getType() == PacketType.AUCTION_ENDED_UPDATE),
            argThat(s -> s != null && s.contains(bidder.getId()) && s.contains(seller.getId())));
    verify(sessionManager, never()).broadcastToAuction(eq(auction.getId()), any());
  }
}
