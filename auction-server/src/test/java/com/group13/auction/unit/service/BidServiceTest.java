package com.group13.auction.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.BidService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.BidStrategy;
import com.group13.auction.strategy.StandardBidStrategy;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests cho {@link BidService}.
 *
 * <p>Mock toàn bộ external dependency:
 *
 * <ul>
 *   <li>{@link IAuctionService} — observer/notify
 *   <li>{@link IRatingService} — eligibility check
 *   <li>{@link IWalletService} — deposit lock
 *   <li>{@link BidTransactionDAO} — persist transaction
 *   <li>{@link AuctionDAO} — persist auction state
 *   <li>{@link UserDAO} — persist user activity
 * </ul>
 *
 * <p>Dùng object thật cho domain model (Auction, NormalUser, BidStrategy). Không DB, không network,
 * không filesystem.
 *
 * <p>Yêu cầu mockito-inline trong pom.xml để mock concrete DAO classes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BidService")
class BidServiceTest {
  // Mocks — toàn bộ external dependency
  @Mock private IAuctionService auctionService;
  @Mock private IRatingService ratingService;
  @Mock private IWalletService walletService;
  @Mock private BidTransactionDAO bidTransactionDAO;
  @Mock private AuctionDAO auctionDAO;
  @Mock private UserDAO userDAO;
  @Mock private AuctionObserver observer;

  // SUT
  private BidService bidService;

  // Fixtures thật — không mock domain model
  private NormalUser seller;
  private NormalUser bidder;
  private Auction runningAuction;
  private BidStrategy strategy;

  // Constant dùng chung
  private static final long STARTING_PRICE = 1_000_000L;

  @BeforeEach
  void setUp() {
    bidService =
        new BidService(
            auctionService, ratingService, walletService, bidTransactionDAO, auctionDAO, userDAO);

    seller = TestFixture.normalSeller("sellerUser1");
    bidder = TestFixture.bidderWithBalance("bidderUser1", 10_000_000L);
    runningAuction = TestFixture.runningAuction(seller, STARTING_PRICE);
    strategy = new StandardBidStrategy();

    // bidder đã join phiên mặc định trong hầu hết test
    bidder.addJoinedAuction(runningAuction.getId());

    lenient()
        .when(
            bidTransactionDAO.saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString()))
        .thenReturn(true);
  }
  // placeBid — happy path
  @Nested
  @DisplayName("placeBid — happy path")
  class PlaceBidHappyPath {

    @Test
    @DisplayName("bid hợp lệ vượt reserve → currentPrice & leader được cập nhật")
    void placeBid_validBidAboveReserve_updatesAuctionState() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = runningAuction.getReservePrice() + 500_000L; // chắc chắn > reserve
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert — state auction được cập nhật
      assertThat(runningAuction.getCurrentPrice()).isEqualTo(bidAmount);
      assertThat(runningAuction.getCurrentLeader()).isSameAs(bidder);
    }

    @Test
    @DisplayName("bid hợp lệ vượt reserve → BID_PLACED event được notify")
    void placeBid_validBidAboveReserve_notifiesBidPlacedEvent() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = runningAuction.getReservePrice() + 500_000L;
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert
      verify(auctionService)
          .notify(
              eq(runningAuction),
              eq(AuctionEvent.AuctionEventType.BID_PLACED),
              eq(bidder),
              eq(bidAmount));
    }

    @Test
    @DisplayName("bid hợp lệ vượt reserve → transaction ACCEPTED được lưu")
    void placeBid_validBidAboveReserve_savesAcceptedTransaction() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = runningAuction.getReservePrice() + 500_000L;
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert — capture và kiểm tra nội dung transaction
      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDAO)
          .saveTransactionAndUpdatePrice(
              captor.capture(), eq(runningAuction.getId()), eq(bidAmount), eq(bidder.getId()));
      BidTransaction saved = captor.getValue();
      assertThat(saved.getResult()).isEqualTo(BidResult.ACCEPTED);
      assertThat(saved.getAmount()).isEqualTo(bidAmount);
      assertThat(saved.getBidder()).isSameAs(bidder);
    }

    @Test
    @DisplayName("bid hợp lệ vượt reserve → auctionDAO.updateHighestPrice được gọi")
    void placeBid_validBidAboveReserve_persistsHighestPrice() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = runningAuction.getReservePrice() + 500_000L;
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert
      verify(bidTransactionDAO)
          .saveTransactionAndUpdatePrice(
              any(), eq(runningAuction.getId()), eq(bidAmount), eq(bidder.getId()));
    }

    @Test
    @DisplayName("bid hợp lệ vượt reserve → transaction id được thêm vào auction")
    void placeBid_validBidAboveReserve_addsBidTransactionIdToAuction() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = runningAuction.getReservePrice() + 500_000L;
      int prevSize = runningAuction.getBidTransactionIds().size();

      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert
      assertThat(runningAuction.getBidTransactionIds()).hasSize(prevSize + 1);
    }

    @Test
    @DisplayName("bid hợp lệ vượt reserve → transaction được thêm vào bidHistory của bidder")
    void placeBid_validBidAboveReserve_addsToBidderHistory() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = runningAuction.getReservePrice() + 500_000L;
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert
      assertThat(bidder.getBidHistory()).hasSize(1);
      assertThat(bidder.getBidHistory().get(0).getAmount()).isEqualTo(bidAmount);
    }
  }
  // placeBid — reserve price chưa đạt
  @Nested
  @DisplayName("placeBid — reserve price chưa đạt")
  class PlaceBidReserveNotMet {

    @Test
    @DisplayName("bid hợp lệ nhưng dưới reserve → BID_RESERVE_NOT_MET event")
    void placeBid_validBidBelowReserve_notifiesReserveNotMetEvent() {
      // Arrange
      // Reserve = startingPrice * 2 = 2_000_000
      // Bid hợp lệ nhưng < reserve: startingPrice + increment = 1_200_000
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = STARTING_PRICE + 200_000L; // 1_200_000 < reserve(2_000_000)
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert
      verify(auctionService)
          .notify(
              eq(runningAuction),
              eq(AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET),
              eq(bidder),
              eq(bidAmount));
    }

    @Test
    @DisplayName("bid hợp lệ nhưng dưới reserve → transaction ACCEPTED_RESERVE_NOT_MET được lưu")
    void placeBid_validBidBelowReserve_savesReserveNotMetTransaction() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = STARTING_PRICE + 200_000L;
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert
      ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
      verify(bidTransactionDAO)
          .saveTransactionAndUpdatePrice(
              captor.capture(), eq(runningAuction.getId()), eq(bidAmount), eq(bidder.getId()));
      assertThat(captor.getValue().getResult()).isEqualTo(BidResult.ACCEPTED_RESERVE_NOT_MET);
    }

    @Test
    @DisplayName("bid hợp lệ nhưng dưới reserve → auction state vẫn cập nhật đúng")
    void placeBid_validBidBelowReserve_stillUpdatesAuctionState() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = STARTING_PRICE + 200_000L;
      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert — giá và leader vẫn được cập nhật dù reserve chưa đạt
      assertThat(runningAuction.getCurrentPrice()).isEqualTo(bidAmount);
      assertThat(runningAuction.getCurrentLeader()).isSameAs(bidder);
    }
  }
  // placeBid — auction không nhận bid (closed / canceled / open)
  @Nested
  @DisplayName("placeBid — auction không ở trạng thái RUNNING")
  class PlaceBidClosedAuction {

    @Test
    @DisplayName("auction FINISHED → ném AuctionClosedException")
    void placeBid_finishedAuction_throwsAuctionClosedException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      NormalUser winner = TestFixture.bidderWithBalance("bidderWW1", 5_000_000L);
      Auction finished =
          TestFixture.finishedAuction(
              seller, winner, STARTING_PRICE, runningAuction.getReservePrice() + 100_000L);
      bidder.addJoinedAuction(finished.getId());

      // Act & Assert
      AuctionClosedException ex =
          assertThrows(
              AuctionClosedException.class,
              () -> bidService.placeBid(bidder, finished, 5_000_000L, strategy));
      assertThat(ex.getCurrentStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
    }

    @Test
    @DisplayName("auction CANCELED → ném AuctionClosedException")
    void placeBid_canceledAuction_throwsAuctionClosedException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      Auction canceled = TestFixture.canceledFromRunningAuction(seller, STARTING_PRICE);
      bidder.addJoinedAuction(canceled.getId());

      // Act & Assert
      AuctionClosedException ex =
          assertThrows(
              AuctionClosedException.class,
              () -> bidService.placeBid(bidder, canceled, 2_000_000L, strategy));
      assertThat(ex.getCurrentStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
    }

    @Test
    @DisplayName("auction OPEN (chưa RUNNING) → ném AuctionClosedException")
    void placeBid_openAuction_throwsAuctionClosedException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      Auction open = TestFixture.openAuction(seller, STARTING_PRICE);
      bidder.addJoinedAuction(open.getId());

      // Act & Assert
      assertThrows(
          AuctionClosedException.class,
          () -> bidService.placeBid(bidder, open, 2_000_000L, strategy));
    }

    @Test
    @DisplayName(
        "auction FINISHED → không lưu transaction bình thường (exception thrown trước khi tạo TX)")
    void placeBid_finishedAuction_doesNotSaveNormalTransaction() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      NormalUser winner = TestFixture.bidderWithBalance("bidderWW2", 5_000_000L);
      Auction finished =
          TestFixture.finishedAuction(
              seller, winner, STARTING_PRICE, runningAuction.getReservePrice() + 100_000L);
      bidder.addJoinedAuction(finished.getId());

      // Act
      assertThrows(
          AuctionClosedException.class,
          () -> bidService.placeBid(bidder, finished, 5_000_000L, strategy));

      // Assert — closed auction: exception ném trước khi TX được tạo (FIX #2 + refactor)
      verify(bidTransactionDAO, never())
          .saveTransactionAndUpdatePrice(
              argThat(
                  (BidTransaction tx) ->
                      tx.getResult() == BidResult.ACCEPTED
                          || tx.getResult() == BidResult.ACCEPTED_RESERVE_NOT_MET),
              anyString(),
              anyLong(),
              anyString());
    }
  }
  // placeBid — user chưa join auction
  @Nested
  @DisplayName("placeBid — user chưa join auction")
  class PlaceBidNotJoined {

    @Test
    @DisplayName("bidder chưa join → ném AuctionBusinessException với reason NOT_JOINED_AUCTION")
    void placeBid_bidderNotJoined_throwsAuctionBusinessException() {
      // Arrange
      NormalUser stranger = TestFixture.bidderWithBalance("strangerBB", 10_000_000L);
      when(ratingService.isEligible(stranger)).thenReturn(true);
      // stranger chưa join runningAuction
      // Act & Assert
      AuctionBusinessException ex =
          assertThrows(
              AuctionBusinessException.class,
              () -> bidService.placeBid(stranger, runningAuction, 2_000_000L, strategy));
      assertThat(ex.getReason()).isEqualTo(AuctionBusinessException.Reason.NOT_JOINED_AUCTION);
    }

    @Test
    @DisplayName("bidder chưa join → throw exception, KHÔNG lưu transaction (FIX #2)")
    void placeBid_bidderNotJoined_savesRejectedTransaction() {
      // Arrange
      NormalUser stranger = TestFixture.bidderWithBalance("strangerCC", 10_000_000L);
      when(ratingService.isEligible(stranger)).thenReturn(true);

      // Act
      assertThrows(
          AuctionBusinessException.class,
          () -> bidService.placeBid(stranger, runningAuction, 2_000_000L, strategy));

      // Assert — FIX #2: REJECTED bid không ghi DB
      verify(bidTransactionDAO, never())
          .saveTransactionAndUpdatePrice(any(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("bidder chưa join → auction state không bị thay đổi")
    void placeBid_bidderNotJoined_doesNotChangeAuctionState() {
      // Arrange
      NormalUser stranger = TestFixture.bidderWithBalance("strangerDD", 10_000_000L);
      when(ratingService.isEligible(stranger)).thenReturn(true);
      long priceBefore = runningAuction.getCurrentPrice();

      // Act
      assertThrows(
          AuctionBusinessException.class,
          () -> bidService.placeBid(stranger, runningAuction, 2_000_000L, strategy));

      // Assert
      assertThat(runningAuction.getCurrentPrice()).isEqualTo(priceBefore);
      assertThat(runningAuction.getCurrentLeader()).isNull();
    }
  }
  // placeBid — invalid bid (không đủ bước giá)
  @Nested
  @DisplayName("placeBid — invalid bid amount")
  class PlaceBidInvalidAmount {

    @Test
    @DisplayName("bid bằng đúng currentPrice (thiếu increment) → ném InvalidBidException")
    void placeBid_equalCurrentPrice_throwsInvalidBidException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long invalidAmount = runningAuction.getCurrentPrice(); // thiếu increment

      // Act & Assert
      InvalidBidException ex =
          assertThrows(
              InvalidBidException.class,
              () -> bidService.placeBid(bidder, runningAuction, invalidAmount, strategy));
      assertThat(ex.getAttemptedAmount()).isEqualTo(invalidAmount);
    }

    @Test
    @DisplayName("bid thấp hơn currentPrice → ném InvalidBidException")
    void placeBid_belowCurrentPrice_throwsInvalidBidException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long invalidAmount = runningAuction.getCurrentPrice() - 1;

      // Act & Assert
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(bidder, runningAuction, invalidAmount, strategy));
    }

    @Test
    @DisplayName("bid thiếu increment (currentPrice + increment - 1) → ném InvalidBidException")
    void placeBid_justBelowMinIncrement_throwsInvalidBidException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long increment = 200_000L; // tier 1-10tr
      long invalidAmount = runningAuction.getCurrentPrice() + increment - 1;

      // Act & Assert
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(bidder, runningAuction, invalidAmount, strategy));
    }

    @Test
    @DisplayName("bid không hợp lệ → throw exception, KHÔNG lưu transaction (FIX #2)")
    void placeBid_invalidAmount_savesRejectedTransaction() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long invalidAmount = runningAuction.getCurrentPrice();

      // Act
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(bidder, runningAuction, invalidAmount, strategy));

      // Assert — FIX #2: REJECTED bid không ghi DB
      verify(bidTransactionDAO, never())
          .saveTransactionAndUpdatePrice(any(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("bid không hợp lệ → auction state không bị thay đổi")
    void placeBid_invalidAmount_doesNotChangeAuctionState() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long invalidAmount = runningAuction.getCurrentPrice();
      long priceBefore = runningAuction.getCurrentPrice();

      // Act
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(bidder, runningAuction, invalidAmount, strategy));

      // Assert
      assertThat(runningAuction.getCurrentPrice()).isEqualTo(priceBefore);
      assertThat(runningAuction.getCurrentLeader()).isNull();
    }

    @Test
    @DisplayName("bid = 0 → ném InvalidBidException")
    void placeBid_zeroBid_throwsInvalidBidException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);

      // Act & Assert
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(bidder, runningAuction, 0L, strategy));
    }

    @Test
    @DisplayName("bid âm → ném InvalidBidException")
    void placeBid_negativeBid_throwsInvalidBidException() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);

      // Act & Assert
      assertThrows(
          InvalidBidException.class,
          () -> bidService.placeBid(bidder, runningAuction, -1L, strategy));
    }
  }
  // placeBid — user không đủ điều kiện (ineligible)
  @Nested
  @DisplayName("placeBid — user không đủ điều kiện")
  class PlaceBidIneligibleUser {

    @Test
    @DisplayName("bidder bị BAN → ném AuthenticationException với reason ACCOUNT_BANNED")
    void placeBid_bannedBidder_throwsAccountBannedException() {
      // Arrange
      NormalUser banned = TestFixture.bannedBidder("bannedUser1");
      banned.addJoinedAuction(runningAuction.getId());
      when(ratingService.isEligible(banned)).thenReturn(false);

      // Act & Assert
      AuthenticationException ex =
          assertThrows(
              AuthenticationException.class,
              () -> bidService.placeBid(banned, runningAuction, 2_000_000L, strategy));
      assertThat(ex.getReason()).isEqualTo(AuthenticationException.Reason.ACCOUNT_BANNED);
    }

    @Test
    @DisplayName("bidder bị SUSPENDED → ném AuthenticationException với reason ACCOUNT_SUSPENDED")
    void placeBid_suspendedBidder_throwsAccountSuspendedException() {
      // Arrange
      NormalUser suspended = TestFixture.suspendedBidder("suspendUser1");
      suspended.addJoinedAuction(runningAuction.getId());
      when(ratingService.isEligible(suspended)).thenReturn(false);

      // Act & Assert
      AuthenticationException ex =
          assertThrows(
              AuthenticationException.class,
              () -> bidService.placeBid(suspended, runningAuction, 2_000_000L, strategy));
      assertThat(ex.getReason()).isEqualTo(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName(
        "bidder rating thấp (ACTIVE nhưng không eligible) → ném AuthenticationException"
            + " INSUFFICIENT_RATING")
    void placeBid_lowRatingBidder_throwsInsufficientRatingException() {
      // Arrange
      NormalUser lowRating = TestFixture.bidderWithRating("lowRateUsr", 1.0);
      lowRating.addJoinedAuction(runningAuction.getId());
      when(ratingService.isEligible(lowRating)).thenReturn(false);

      // Act & Assert
      AuthenticationException ex =
          assertThrows(
              AuthenticationException.class,
              () -> bidService.placeBid(lowRating, runningAuction, 2_000_000L, strategy));
      assertThat(ex.getReason()).isEqualTo(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }

    @Test
    @DisplayName("bidder không eligible → throw exception, KHÔNG lưu transaction (FIX #2)")
    void placeBid_ineligibleBidder_savesRejectedTransaction() {
      // Arrange
      NormalUser banned = TestFixture.bannedBidder("bannedUser2");
      banned.addJoinedAuction(runningAuction.getId());
      when(ratingService.isEligible(banned)).thenReturn(false);

      // Act
      assertThrows(
          AuthenticationException.class,
          () -> bidService.placeBid(banned, runningAuction, 2_000_000L, strategy));

      // Assert — FIX #2: REJECTED bid không ghi DB
      verify(bidTransactionDAO, never())
          .saveTransactionAndUpdatePrice(any(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName(
        "bidder không eligible → kiểm tra eligibility là bước đầu tiên (trước mọi check khác)")
    void placeBid_ineligibleBidder_checksEligibilityFirst() {
      // Arrange — auction FINISHED, nhưng ineligible check phải xảy ra trước
      NormalUser banned = TestFixture.bannedBidder("bannedUser3");
      // Không join auction cũng không quan trọng — eligibility check first
      when(ratingService.isEligible(banned)).thenReturn(false);

      // Act & Assert
      assertThrows(
          AuthenticationException.class,
          () -> bidService.placeBid(banned, runningAuction, 2_000_000L, strategy));

      // isEligible phải được gọi
      verify(ratingService).isEligible(banned);
    }
  }
  // placeBid — anti-sniping behavior
  @Nested
  @DisplayName("placeBid — anti-sniping")
  class PlaceBidAntiSniping {

    @Test
    @DisplayName("bid trong 30s cuối → endTime được gia hạn thêm 60s")
    void placeBid_bidWithin30sWindow_extendsEndTime() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      // Tạo auction kết thúc trong 20 giây nữa (nằm trong anti-sniping window 30s)
      Auction snipingAuction =
          TestFixture.auctionWithStatus(
              seller, STARTING_PRICE, STARTING_PRICE, Auction.AuctionStatus.RUNNING);
      // Thiết lập endTime = 20s từ bây giờ bằng cách reconstitute
      Auction sniping =
          Auction.reconstitute(
              snipingAuction.getId(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now(),
              snipingAuction.getItem(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().plusSeconds(20), // 20s còn lại → kích hoạt anti-sniping
              STARTING_PRICE,
              Auction.AuctionStatus.RUNNING,
              STARTING_PRICE * 2);
      bidder.addJoinedAuction(sniping.getId());

      LocalDateTime endTimeBefore = sniping.getEndTime();
      long bidAmount = STARTING_PRICE + 200_000L;

      // Act
      bidService.placeBid(bidder, sniping, bidAmount, strategy);

      // Assert — endTime phải được gia hạn thêm đúng 60s
      assertThat(sniping.getEndTime()).isEqualTo(endTimeBefore.plusSeconds(60));
    }

    @Test
    @DisplayName("bid trong 30s cuối → auctionDAO.updateEndTime được gọi")
    void placeBid_bidWithin30sWindow_persistsNewEndTime() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      Auction sniping =
          Auction.reconstitute(
              java.util.UUID.randomUUID().toString(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now(),
              runningAuction.getItem(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().plusSeconds(15), // 15s → trong window
              STARTING_PRICE,
              Auction.AuctionStatus.RUNNING,
              STARTING_PRICE * 2);
      bidder.addJoinedAuction(sniping.getId());

      // Act
      bidService.placeBid(bidder, sniping, STARTING_PRICE + 200_000L, strategy);

      // Assert
      verify(auctionDAO).updateEndTime(eq(sniping.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("bid trong 30s cuối → AUCTION_EXTENDED event được notify")
    void placeBid_bidWithin30sWindow_notifiesAuctionExtendedEvent() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      Auction sniping =
          Auction.reconstitute(
              java.util.UUID.randomUUID().toString(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now(),
              runningAuction.getItem(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().plusSeconds(10), // 10s → trong window
              STARTING_PRICE,
              Auction.AuctionStatus.RUNNING,
              STARTING_PRICE * 2);
      bidder.addJoinedAuction(sniping.getId());

      // Act
      bidService.placeBid(bidder, sniping, STARTING_PRICE + 200_000L, strategy);

      // Assert
      verify(auctionService)
          .notify(
              eq(sniping),
              eq(AuctionEvent.AuctionEventType.AUCTION_EXTENDED),
              eq(bidder),
              anyLong(),
              anyString());
    }

    @Test
    @DisplayName("bid với hơn 30s còn lại → endTime KHÔNG được gia hạn")
    void placeBid_bidOutsideSnipingWindow_doesNotExtendEndTime() {
      // Arrange — auction còn 5 phút nữa mới kết thúc, không trong window
      when(ratingService.isEligible(bidder)).thenReturn(true);
      long bidAmount = STARTING_PRICE + 200_000L;
      LocalDateTime endTimeBefore = runningAuction.getEndTime(); // +1 giờ (từ fixture)

      // Act
      bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

      // Assert — endTime phải giữ nguyên
      assertThat(runningAuction.getEndTime()).isEqualTo(endTimeBefore);
    }

    @Test
    @DisplayName("bid với hơn 30s còn lại → auctionDAO.updateEndTime KHÔNG được gọi")
    void placeBid_bidOutsideSnipingWindow_doesNotPersistEndTime() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      // Act
      bidService.placeBid(bidder, runningAuction, STARTING_PRICE + 200_000L, strategy);

      // Assert
      verify(auctionDAO, never()).updateEndTime(any(), any());
    }

    @Test
    @DisplayName("bid đúng tại mốc 30s → endTime được gia hạn (boundary inclusive)")
    void placeBid_bidAtExactSnipingBoundary_extendsEndTime() {
      // Arrange
      when(ratingService.isEligible(bidder)).thenReturn(true);
      Auction sniping =
          Auction.reconstitute(
              java.util.UUID.randomUUID().toString(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now(),
              runningAuction.getItem(),
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().plusSeconds(30), // đúng 30s — boundary
              STARTING_PRICE,
              Auction.AuctionStatus.RUNNING,
              STARTING_PRICE * 2);
      bidder.addJoinedAuction(sniping.getId());
      LocalDateTime endTimeBefore = sniping.getEndTime();

      // Act
      bidService.placeBid(bidder, sniping, STARTING_PRICE + 200_000L, strategy);

      // Assert — boundary inclusive (secondsLeft <= 30)
      assertThat(sniping.getEndTime()).isEqualTo(endTimeBefore.plusSeconds(60));
    }
  }
  // leaveAuction — anti-sniping khi leader rời gần cuối phiên
  @Nested
  @DisplayName("leaveAuction — anti-sniping (leader rời)")
  class LeaveLeaderAntiSniping {

    private NormalUser leader;
    private NormalUser runnerUp;

    @BeforeEach
    void setUpLeaders() {
      leader = TestFixture.bidderWithBalance("leaderUser", 10_000_000L);
      runnerUp = TestFixture.bidderWithBalance("runnerUpUser", 10_000_000L);
    }

    private Auction snipingAuctionWithLeader() {
      Auction auction =
          Auction.reconstitute(
              java.util.UUID.randomUUID().toString(),
              LocalDateTime.now().minusMinutes(10),
              LocalDateTime.now(),
              runningAuction.getItem(),
              LocalDateTime.now().minusMinutes(10),
              LocalDateTime.now().plusSeconds(20),
              STARTING_PRICE,
              Auction.AuctionStatus.RUNNING,
              STARTING_PRICE * 2);
      auction.updateBid(STARTING_PRICE + 500_000L, leader);
      leader.addJoinedAuction(auction.getId());
      return auction;
    }

    @Test
    @DisplayName("leader rời trong 30s cuối → gia hạn thêm 60s")
    void leaderLeave_withinSnipingWindow_extendsEndTime() {
      Auction auction = snipingAuctionWithLeader();
      LocalDateTime endBefore = auction.getEndTime();

      when(bidTransactionDAO.cancelBidsByBidder(auction.getId(), leader.getId())).thenReturn(1);
      when(bidTransactionDAO.findHighestValidBid(auction.getId()))
          .thenReturn(
              BidTransaction.create(
                  runnerUp, auction.getId(), STARTING_PRICE + 300_000L, BidResult.ACCEPTED));

      bidService.leaveAuction(leader, auction);

      assertThat(auction.getEndTime()).isEqualTo(endBefore.plusSeconds(60));
    }

    @Test
    @DisplayName("leader rời trong 30s cuối → persist endTime + notify AUCTION_EXTENDED")
    void leaderLeave_withinSnipingWindow_persistsAndNotifies() {
      Auction auction = snipingAuctionWithLeader();

      when(bidTransactionDAO.cancelBidsByBidder(any(), any())).thenReturn(1);
      when(bidTransactionDAO.findHighestValidBid(any()))
          .thenReturn(
              BidTransaction.create(
                  runnerUp, auction.getId(), STARTING_PRICE + 300_000L, BidResult.ACCEPTED));

      bidService.leaveAuction(leader, auction);

      verify(auctionDAO).updateEndTime(eq(auction.getId()), any(LocalDateTime.class));
      verify(auctionService)
          .notify(
              eq(auction),
              eq(AuctionEvent.AuctionEventType.AUCTION_EXTENDED),
              eq(leader),
              anyLong(),
              contains("người dẫn đầu rời phiên"));
    }

    @Test
    @DisplayName("không phải leader rời trong 30s cuối → không gia hạn")
    void nonLeaderLeave_withinSnipingWindow_doesNotExtend() {
      Auction auction = snipingAuctionWithLeader();
      runnerUp.addJoinedAuction(auction.getId());
      LocalDateTime endBefore = auction.getEndTime();

      when(bidTransactionDAO.cancelBidsByBidder(auction.getId(), runnerUp.getId())).thenReturn(0);
      when(bidTransactionDAO.findHighestValidBid(auction.getId()))
          .thenReturn(
              BidTransaction.create(
                  leader, auction.getId(), STARTING_PRICE + 500_000L, BidResult.ACCEPTED));

      bidService.leaveAuction(runnerUp, auction);

      assertThat(auction.getCurrentPrice()).isEqualTo(STARTING_PRICE + 500_000L);
      assertThat(auction.getCurrentLeader()).isSameAs(leader);
      assertThat(auction.getEndTime()).isEqualTo(endBefore);
      verify(auctionDAO, never()).updateEndTime(any(), any());
    }

    @Test
    @DisplayName("leader rời khi còn > 30s → không gia hạn")
    void leaderLeave_outsideSnipingWindow_doesNotExtend() {
      Auction auction =
          Auction.reconstitute(
              java.util.UUID.randomUUID().toString(),
              LocalDateTime.now().minusMinutes(10),
              LocalDateTime.now(),
              runningAuction.getItem(),
              LocalDateTime.now().minusMinutes(10),
              LocalDateTime.now().plusMinutes(5),
              STARTING_PRICE,
              Auction.AuctionStatus.RUNNING,
              STARTING_PRICE * 2);
      auction.updateBid(STARTING_PRICE + 500_000L, leader);
      leader.addJoinedAuction(auction.getId());
      LocalDateTime endBefore = auction.getEndTime();

      when(bidTransactionDAO.cancelBidsByBidder(any(), any())).thenReturn(1);
      when(bidTransactionDAO.findHighestValidBid(any())).thenReturn(null);

      bidService.leaveAuction(leader, auction);

      assertThat(auction.getCurrentPrice()).isEqualTo(STARTING_PRICE);
      assertThat(auction.getCurrentLeader()).isNull();
      assertThat(auction.getEndTime()).isEqualTo(endBefore);
      verify(auctionDAO, never()).updateEndTime(any(), any());
    }

    @Test
    @DisplayName("leader rời → rollback giá và leader về người đứng thứ 2")
    void leaderLeave_recalculatesRankingToRunnerUp() {
      Auction auction = snipingAuctionWithLeader();
      long runnerUpPrice = STARTING_PRICE + 300_000L;

      when(bidTransactionDAO.cancelBidsByBidder(auction.getId(), leader.getId())).thenReturn(1);
      when(bidTransactionDAO.findHighestValidBid(auction.getId()))
          .thenReturn(
              BidTransaction.create(runnerUp, auction.getId(), runnerUpPrice, BidResult.ACCEPTED));

      bidService.leaveAuction(leader, auction);

      assertThat(auction.getCurrentPrice()).isEqualTo(runnerUpPrice);
      assertThat(auction.getCurrentLeader()).isSameAs(runnerUp);
      verify(bidTransactionDAO)
          .updateLeaderAfterLeave(auction.getId(), runnerUp.getId(), runnerUpPrice);
    }

    @Test
    @DisplayName("leave → gỡ observer, watchlist và đánh dấu LEFT")
    void leaveAuction_cleansParticipationState() {
      bidder.addJoinedAuction(runningAuction.getId());
      bidder.addToWatchList(runningAuction.getId());
      when(bidTransactionDAO.cancelBidsByBidder(any(), any())).thenReturn(0);

      bidService.leaveAuction(bidder, runningAuction);

      assertThat(bidder.hasJoined(runningAuction.getId())).isFalse();
      assertThat(bidder.hasLeft(runningAuction.getId())).isTrue();
      verify(auctionService).removeObserversForUser(runningAuction.getId(), bidder.getId());
      verify(userDAO).markUserLeftAuction(bidder.getId(), runningAuction.getId());
    }
  }
  // joinAuction — NormalUser happy path
  @Nested
  @DisplayName("joinAuction — NormalUser")
  class JoinAuctionNormalUser {

    private NormalUser freshBidder;

    @BeforeEach
    void setUpFreshBidder() {
      freshBidder = TestFixture.bidderWithBalance("freshBidr1", 10_000_000L);
    }

    @Test
    @DisplayName("đã leave trước đó → join lại phiên OPEN thành công và xóa trạng thái LEFT")
    void joinAuction_afterLeave_rejoinsAndClearsLeft() {
      freshBidder.addLeftAuction(runningAuction.getId());
      when(ratingService.isEligible(freshBidder)).thenReturn(true);
      doNothing().when(walletService).lockDeposit(any(), anyLong(), any());

      bidService.joinAuction(freshBidder, runningAuction, observer);

      assertThat(freshBidder.hasJoined(runningAuction.getId())).isTrue();
      assertThat(freshBidder.hasLeft(runningAuction.getId())).isFalse();
      verify(userDAO)
          .saveUserAuctionActivity(freshBidder.getId(), runningAuction.getId(), "JOINED");
    }

    @Test
    @DisplayName("join lần đầu → khóa cọc, đăng ký observer, persist JOINED")
    void joinAuction_firstJoin_fullFlow() {
      when(ratingService.isEligible(freshBidder)).thenReturn(true);
      doNothing().when(walletService).lockDeposit(any(), anyLong(), any());
      long expectedDeposit = runningAuction.getItem().getStartingPrice() * 3 / 10;

      bidService.joinAuction(freshBidder, runningAuction, observer);

      assertThat(freshBidder.hasJoined(runningAuction.getId())).isTrue();
      verify(walletService).lockDeposit(freshBidder, expectedDeposit, runningAuction.getId());
      verify(auctionService).addObserver(runningAuction.getId(), observer);
      verify(userDAO)
          .saveUserAuctionActivity(freshBidder.getId(), runningAuction.getId(), "JOINED");
    }

    @Test
    @DisplayName("DB đã JOINED nhưng RAM mất state → restore join, không khóa cọc lần 2")
    void joinAuction_dbAlreadyJoined_restoresStateWithoutLockingDepositAgain() {
      freshBidder.addLeftAuction(runningAuction.getId());
      when(userDAO.isActiveJoinedParticipant(freshBidder.getId(), runningAuction.getId()))
          .thenReturn(true);

      bidService.joinAuction(freshBidder, runningAuction, observer);

      assertThat(freshBidder.hasJoined(runningAuction.getId())).isTrue();
      assertThat(freshBidder.hasLeft(runningAuction.getId())).isFalse();
      verify(walletService, never()).lockDeposit(any(), anyLong(), any());
      verify(userDAO, never()).saveUserAuctionActivity(anyString(), anyString(), eq("JOINED"));
    }

    @Test
    @DisplayName("bidder không eligible → ném AuthenticationException ngay tại join")
    void joinAuction_ineligibleBidder_throwsAuthException() {
      // Arrange
      NormalUser banned = TestFixture.bannedBidder("bannedUser4");
      when(ratingService.isEligible(banned)).thenReturn(false);

      // Act & Assert
      assertThrows(
          AuthenticationException.class,
          () -> bidService.joinAuction(banned, runningAuction, observer));
    }
  }

  @Nested
  @DisplayName("joinAuction — seller own item")
  class JoinAuctionSellerBidOwnItem {

    @Test
    @DisplayName("seller join phiên của chính mình → SELLER_CANNOT_BID_OWN_ITEM")
    void joinAuction_sellerJoinsOwnAuction_throws() {
      when(ratingService.isEligible(seller)).thenReturn(true);
      AuctionBusinessException ex =
          assertThrows(
              AuctionBusinessException.class,
              () -> bidService.joinAuction(seller, runningAuction, observer));
      assertThat(ex.getReason())
          .isEqualTo(AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
      verify(walletService, never()).lockDeposit(any(), anyLong(), any());
    }
  }

  @Nested
  @DisplayName("watchAuction — regression")
  class WatchAuctionRegression {

    @Test
    @DisplayName("đã join — watch không ghi đè JOINED bằng WATCHING")
    void watchAuction_alreadyJoined_doesNotPersistWatching() {
      bidService.watchAuction(bidder, runningAuction, observer);
      verify(userDAO, never())
          .saveUserAuctionActivity(bidder.getId(), runningAuction.getId(), "WATCHING");
    }
  }
}