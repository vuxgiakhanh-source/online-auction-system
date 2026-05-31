package com.group13.auction.concurrency.bid;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.concurrency.ConcurrencyTestBase;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.strategy.StandardBidStrategy;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;

/**
 * ============================================================================ BidEdgeCasesTest —
 * Group E Guard conditions và edge cases cho BidService.
 * ============================================================================
 */
@DisplayName("Bid: Edge Cases & Guard Conditions")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BidEdgeCasesTest extends ConcurrencyTestBase {

  private BidService bidService;
  private AuctionLockRegistry lockRegistry;
  private AutoBidRegistry autoBidRegistry;
  private Auction auction;

  private IRatingService mockRatingService;
  private IWalletService mockWalletService;
  private BidTransactionDAO mockBidTransactionDAO;
  private AuctionDAO mockAuctionDAO;

  @BeforeEach
  void setUp() {
    IAuctionService mockAuctionService = mock(IAuctionService.class);
    mockRatingService = mock(IRatingService.class);
    mockWalletService = mock(IWalletService.class);
    mockBidTransactionDAO = mock(BidTransactionDAO.class);
    mockAuctionDAO = mock(AuctionDAO.class);
    UserDAO mockUserDAO = mock(UserDAO.class);

    when(mockRatingService.isEligible(any())).thenReturn(true);
    when(mockBidTransactionDAO.saveTransactionAndUpdatePrice(
            any(), anyString(), anyLong(), anyString()))
        .thenReturn(true);
    when(mockAuctionDAO.updateViewerCount(any(), anyInt())).thenReturn(true);
    when(mockAuctionDAO.updateEndTime(any(), any())).thenReturn(true);
    doNothing().when(mockWalletService).lockDeposit(any(), anyLong(), any());

    resetAuctionManagerUsers();

    bidService =
        new BidService(
            mockAuctionService,
            mockRatingService,
            mockWalletService,
            mockBidTransactionDAO,
            mockAuctionDAO,
            mockUserDAO);
    lockRegistry = AuctionLockRegistry.getInstance();
    autoBidRegistry = AutoBidRegistry.getInstance();
    auction = buildRunningAuction();
  }

  @AfterEach
  void tearDown() {
    autoBidRegistry.clearAuction(auction.getId());
    lockRegistry.release(auction.getId());
    resetAuctionManagerUsers();
  }

  // E1

  @Test
  @Order(1)
  @DisplayName("E1: Bidder chưa join auction → bid bị từ chối với AuctionBusinessException")
  void bidder_notJoined_throwsException() {
    NormalUser notJoined = buildUser("notJoined", USER_BALANCE);

    assertThatThrownBy(
            () ->
                bidService.placeBid(
                    notJoined, auction, STARTING_PRICE + 50_000L, new StandardBidStrategy()))
        .isInstanceOf(AuctionBusinessException.class);
  }

  // E2

  @Test
  @Order(2)
  @DisplayName("E2: User bị BANNED → bid bị từ chối với AuthenticationException")
  void bannedBidder_throwsAuthenticationException() {
    NormalUser banned = buildUser("bannedBidder", USER_BALANCE);
    banned.addJoinedAuction(auction.getId());
    banned.setAccountStatus(User.AccountStatus.BANNED);
    when(mockRatingService.isEligible(banned)).thenReturn(false);

    assertThatThrownBy(
            () ->
                bidService.placeBid(
                    banned, auction, STARTING_PRICE + 50_000L, new StandardBidStrategy()))
        .isInstanceOf(AuthenticationException.class);
  }

  // E3

  @Test
  @Order(3)
  @DisplayName("E3: Bid vào phiên FINISHED → AuctionClosedException")
  void bid_onFinishedAuction_throwsAuctionClosedException() {
    Auction finished =
        Auction.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now(),
            auction.getItem(),
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1),
            1_000_000L,
            Auction.AuctionStatus.FINISHED,
            RESERVE_PRICE);

    NormalUser bidder = buildUser("bidderE3", USER_BALANCE);
    bidder.addJoinedAuction(finished.getId());

    assertThatThrownBy(
            () -> bidService.placeBid(bidder, finished, 1_200_000L, new StandardBidStrategy()))
        .isInstanceOf(AuctionClosedException.class);
  }

  // E4

  @Test
  @Order(4)
  @DisplayName("E4: AutoBidStrategy với maxBid <= 0 → IllegalArgumentException tại constructor")
  void autoBidStrategy_negativeMaxBid_throwsIllegalArgument() {
    assertThatThrownBy(() -> new AutoBidStrategy(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBid");

    assertThatThrownBy(() -> new AutoBidStrategy(-1000L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // E5

  @Test
  @Order(5)
  @DisplayName(
      "E5: clearAuction() trên AutoBidRegistry xóa entry phiên, không ảnh hưởng phiên khác")
  void autoBidRegistry_clearAuction_isolatesOtherAuctions() {
    String otherAuctionId = UUID.randomUUID().toString();
    NormalUser userA = buildUser("userA_E5", 0L);
    NormalUser userB = buildUser("userB_E5", 0L);

    autoBidRegistry.register(userA.getId(), auction.getId(), 1_000_000L);
    autoBidRegistry.register(userB.getId(), otherAuctionId, 2_000_000L);

    autoBidRegistry.clearAuction(auction.getId());

    assertThat(autoBidRegistry.hasActiveBid(userA.getId(), auction.getId()))
        .as("userA phải bị xóa sau clearAuction")
        .isFalse();
    assertThat(autoBidRegistry.hasActiveBid(userB.getId(), otherAuctionId))
        .as("userB trong phiên khác phải còn nguyên")
        .isTrue();

    autoBidRegistry.clearAuction(otherAuctionId);
  }
}
