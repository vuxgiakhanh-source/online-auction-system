package com.group13.auction.unit.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.notification.NotificationMessages;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.unit.TestFixture;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServerBroadcastNotifier — auction outcome inbox")
class ServerBroadcastNotifierOutcomeTest {

  @Mock private com.group13.auction.dao.UserDAO userDAO;
  @Mock private com.group13.auction.dao.NotificationDAO notificationDAO;

  private NormalUser seller;
  private NormalUser formerLeader;
  private NormalUser promotedRunnerUp;
  private Auction auction;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.silenceGlobalSingletons();
    seller = TestFixture.normalSeller("outcome_seller");
    formerLeader = TestFixture.bidderWithBalance("outcome_leader", 10_000_000L);
    promotedRunnerUp = TestFixture.bidderWithBalance("outcome_runner", 10_000_000L);
    Art item = TestFixture.art("Outcome Item", 800_000L, seller);
    auction = TestFixture.runningAuction(seller, 800_000L);
    auction.updateBid(1_200_000L, formerLeader);
    auction.updateBid(1_000_000L, promotedRunnerUp);
    // Simulate leader left: runner-up is now current leader at auction end.
    auction.resetLeader(1_000_000L, promotedRunnerUp);
    AuctionWinner auctionWinner =
        AuctionWinner.create(promotedRunnerUp, auction.getId(), 1_000_000L, 240_000L, false);
    auction.setWinner(auctionWinner);
    AuctionManager.getInstance().registerAuction(auction);

    ServerBroadcastNotifier notifier = ServerBroadcastNotifier.getInstance();
    inject(notifier, "userDAO", userDAO);
    inject(notifier, "notificationDAO", notificationDAO);
    when(notificationDAO.save(any(Notification.class))).thenReturn(true);
    when(userDAO.findEverJoinedUserIdsByAuctionId(auction.getId()))
        .thenReturn(Set.of(formerLeader.getId(), promotedRunnerUp.getId()));
  }

  @Test
  @DisplayName("runner-up promoted to leader receives won (not lost) inbox when auction ends")
  void notifyAuctionOutcome_promotedRunnerUpGetsWonPairing() {
    ServerBroadcastNotifier.getInstance().notifyAuctionOutcome(auction);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationDAO, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

    Notification runnerUpNotif =
        captor.getAllValues().stream()
            .filter(n -> promotedRunnerUp.getId().equals(n.getUserId()))
            .findFirst()
            .orElseThrow();

    assertThat(runnerUpNotif.getTitle()).isEqualTo(NotificationMessages.auctionWonTitle());
    assertThat(runnerUpNotif.getBody()).contains("Bạn là người thắng");
    assertThat(runnerUpNotif.getBody()).doesNotContain("Bạn chưa thắng");

    Notification formerLeaderNotif =
        captor.getAllValues().stream()
            .filter(n -> formerLeader.getId().equals(n.getUserId()))
            .findFirst()
            .orElseThrow();

    assertThat(formerLeaderNotif.getTitle()).isEqualTo(NotificationMessages.auctionLostTitle());
    assertThat(formerLeaderNotif.getBody()).contains("Người thắng:");
    assertThat(formerLeaderNotif.getBody()).doesNotContain("Bạn là người thắng");
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
