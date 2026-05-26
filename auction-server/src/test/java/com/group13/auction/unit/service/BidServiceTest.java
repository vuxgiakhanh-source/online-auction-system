package com.group13.auction.unit.service;

import com.group13.auction.unit.TestFixture;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link BidService}.
 *
 * <p>Mock toàn bộ external dependency:
 * <ul>
 *   <li>{@link IAuctionService} — observer/notify</li>
 *   <li>{@link IRatingService} — eligibility check</li>
 *   <li>{@link IWalletService} — deposit lock</li>
 *   <li>{@link BidTransactionDAO} — persist transaction</li>
 *   <li>{@link AuctionDAO} — persist auction state</li>
 *   <li>{@link UserDAO} — persist user activity</li>
 * </ul>
 *
 * <p>Dùng object thật cho domain model (Auction, NormalUser, BidStrategy).
 * Không DB, không network, không filesystem.
 *
 * <p>Yêu cầu mockito-inline trong pom.xml để mock concrete DAO classes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BidService")
class BidServiceTest {

    // =========================================================================
    // Mocks — toàn bộ external dependency
    // =========================================================================

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
        bidService = new BidService(
                auctionService,
                ratingService,
                walletService,
                bidTransactionDAO,
                auctionDAO,
                userDAO);

        seller        = TestFixture.normalSeller("sellerUser1");
        bidder        = TestFixture.bidderWithBalance("bidderUser1", 10_000_000L);
        runningAuction = TestFixture.runningAuction(seller, STARTING_PRICE);
        strategy      = new StandardBidStrategy();

        // bidder đã join phiên mặc định trong hầu hết test
        bidder.addJoinedAuction(runningAuction.getId());

        lenient().when(bidTransactionDAO.saveTransactionAndUpdatePrice(
                any(), anyString(), anyLong(), anyString())).thenReturn(true);
    }

    // =========================================================================
    // placeBid — happy path
    // =========================================================================

    @Nested
    @DisplayName("placeBid — happy path")
    class PlaceBidHappyPath {

        @Test
        @DisplayName("bid hợp lệ vượt reserve → state, notify, persist, history")
        void placeBid_validAboveReserve_fullOrchestration() {
            when(ratingService.isEligible(bidder)).thenReturn(true);
            long bidAmount = runningAuction.getReservePrice() + 500_000L;
            int prevTxCount = runningAuction.getBidTransactionIds().size();

            bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

            assertThat(runningAuction.getCurrentPrice()).isEqualTo(bidAmount);
            assertThat(runningAuction.getCurrentLeader()).isSameAs(bidder);
            assertThat(runningAuction.getBidTransactionIds()).hasSize(prevTxCount + 1);
            assertThat(bidder.getBidHistory()).hasSize(1);
            assertThat(bidder.getBidHistory().get(0).getAmount()).isEqualTo(bidAmount);

            verify(auctionService).notify(
                    eq(runningAuction),
                    eq(AuctionEvent.AuctionEventType.BID_PLACED),
                    eq(bidder),
                    eq(bidAmount));

            ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
            verify(bidTransactionDAO).saveTransactionAndUpdatePrice(
                    captor.capture(), eq(runningAuction.getId()), eq(bidAmount), eq(bidder.getId()));
            BidTransaction saved = captor.getValue();
            assertThat(saved.getResult()).isEqualTo(BidResult.ACCEPTED);
            assertThat(saved.getAmount()).isEqualTo(bidAmount);
            assertThat(saved.getBidder()).isSameAs(bidder);
        }
    }

    // =========================================================================
    // placeBid — reserve price chưa đạt
    // =========================================================================

    @Nested
    @DisplayName("placeBid — reserve price chưa đạt")
    class PlaceBidReserveNotMet {

        @Test
        @DisplayName("bid hợp lệ dưới reserve → notify, ACCEPTED_RESERVE_NOT_MET, state cập nhật")
        void placeBid_validBidBelowReserve_fullBehavior() {
            when(ratingService.isEligible(bidder)).thenReturn(true);
            long bidAmount = STARTING_PRICE + 200_000L;

            bidService.placeBid(bidder, runningAuction, bidAmount, strategy);

            assertThat(runningAuction.getCurrentPrice()).isEqualTo(bidAmount);
            assertThat(runningAuction.getCurrentLeader()).isSameAs(bidder);
            verify(auctionService).notify(
                    eq(runningAuction),
                    eq(AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET),
                    eq(bidder),
                    eq(bidAmount));
            ArgumentCaptor<BidTransaction> captor = ArgumentCaptor.forClass(BidTransaction.class);
            verify(bidTransactionDAO).saveTransactionAndUpdatePrice(
                    captor.capture(), eq(runningAuction.getId()), eq(bidAmount), eq(bidder.getId()));
            assertThat(captor.getValue().getResult()).isEqualTo(BidResult.ACCEPTED_RESERVE_NOT_MET);
        }
    }

    // =========================================================================
    // placeBid — auction không nhận bid (closed / canceled / open)
    // =========================================================================

    @Nested
    @DisplayName("placeBid — auction không ở trạng thái RUNNING")
    class PlaceBidClosedAuction {

        static Stream<Arguments> nonRunningAuctions() {
            NormalUser winner = TestFixture.bidderWithBalance("bidderWW1", 5_000_000L);
            NormalUser seller = TestFixture.normalSeller("sellerClosed");
            return Stream.of(
                    Arguments.of(
                            TestFixture.finishedAuction(seller, winner, STARTING_PRICE, 2_100_000L),
                            Auction.AuctionStatus.FINISHED),
                    Arguments.of(
                            TestFixture.canceledFromRunningAuction(seller, STARTING_PRICE),
                            Auction.AuctionStatus.CANCELED),
                    Arguments.of(
                            TestFixture.openAuction(seller, STARTING_PRICE),
                            Auction.AuctionStatus.OPEN));
        }

        @ParameterizedTest(name = "status={1} → AuctionClosedException, không persist TX")
        @MethodSource("nonRunningAuctions")
        @DisplayName("auction không RUNNING → ném AuctionClosedException, không lưu TX")
        void placeBid_nonRunningAuction_rejects(Auction auction, Auction.AuctionStatus expectedStatus) {
            when(ratingService.isEligible(bidder)).thenReturn(true);
            bidder.addJoinedAuction(auction.getId());

            AuctionClosedException ex = assertThrows(AuctionClosedException.class,
                    () -> bidService.placeBid(bidder, auction, 2_000_000L, strategy));
            assertThat(ex.getCurrentStatus()).isEqualTo(expectedStatus);
            verify(bidTransactionDAO, never()).saveTransactionAndUpdatePrice(
                    any(), anyString(), anyLong(), anyString());
        }
    }

    // =========================================================================
    // placeBid — user chưa join auction
    // =========================================================================

    @Nested
    @DisplayName("placeBid — user chưa join auction")
    class PlaceBidNotJoined {

        @Test
        @DisplayName("bidder chưa join → NOT_JOINED_AUCTION, không persist, state giữ nguyên")
        void placeBid_bidderNotJoined_rejectsWithoutSideEffects() {
            NormalUser stranger = TestFixture.bidderWithBalance("strangerBB", 10_000_000L);
            when(ratingService.isEligible(stranger)).thenReturn(true);
            long priceBefore = runningAuction.getCurrentPrice();

            AuctionBusinessException ex = assertThrows(AuctionBusinessException.class,
                    () -> bidService.placeBid(stranger, runningAuction, 2_000_000L, strategy));

            assertThat(ex.getReason()).isEqualTo(AuctionBusinessException.Reason.NOT_JOINED_AUCTION);
            verify(bidTransactionDAO, never()).saveTransactionAndUpdatePrice(
                    any(), anyString(), anyLong(), anyString());
            assertThat(runningAuction.getCurrentPrice()).isEqualTo(priceBefore);
            assertThat(runningAuction.getCurrentLeader()).isNull();
        }
    }

    // =========================================================================
    // placeBid — invalid bid (không đủ bước giá)
    // =========================================================================

    @Nested
    @DisplayName("placeBid — invalid bid amount")
    class PlaceBidInvalidAmount {

        static Stream<Long> invalidBidAmounts() {
            long current = STARTING_PRICE;
            long increment = 200_000L;
            return Stream.of(
                    current,
                    current - 1,
                    current + increment - 1,
                    0L,
                    -1L);
        }

        @ParameterizedTest(name = "amount={0}")
        @MethodSource("invalidBidAmounts")
        @DisplayName("bid không hợp lệ → InvalidBidException, không persist, state giữ nguyên")
        void placeBid_invalidAmount_rejects(long invalidAmount) {
            when(ratingService.isEligible(bidder)).thenReturn(true);
            long priceBefore = runningAuction.getCurrentPrice();

            assertThrows(InvalidBidException.class,
                    () -> bidService.placeBid(bidder, runningAuction, invalidAmount, strategy));

            verify(bidTransactionDAO, never()).saveTransactionAndUpdatePrice(
                    any(), anyString(), anyLong(), anyString());
            assertThat(runningAuction.getCurrentPrice()).isEqualTo(priceBefore);
            assertThat(runningAuction.getCurrentLeader()).isNull();
        }
    }

    // =========================================================================
    // placeBid — user không đủ điều kiện (ineligible)
    // =========================================================================

    @Nested
    @DisplayName("placeBid — user không đủ điều kiện")
    class PlaceBidIneligibleUser {

        static Stream<Arguments> ineligibleBidders() {
            return Stream.of(
                    Arguments.of(TestFixture.bannedBidder("bannedUser1"),
                            AuthenticationException.Reason.ACCOUNT_BANNED),
                    Arguments.of(TestFixture.suspendedBidder("suspendUser1"),
                            AuthenticationException.Reason.ACCOUNT_SUSPENDED),
                    Arguments.of(TestFixture.bidderWithRating("lowRateUsr", 1.0),
                            AuthenticationException.Reason.INSUFFICIENT_RATING));
        }

        @ParameterizedTest(name = "reason={1}")
        @MethodSource("ineligibleBidders")
        @DisplayName("bidder không eligible → AuthenticationException, không persist")
        void placeBid_ineligibleBidder_rejects(NormalUser user,
                AuthenticationException.Reason expectedReason) {
            user.addJoinedAuction(runningAuction.getId());
            when(ratingService.isEligible(user)).thenReturn(false);

            AuthenticationException ex = assertThrows(AuthenticationException.class,
                    () -> bidService.placeBid(user, runningAuction, 2_000_000L, strategy));

            assertThat(ex.getReason()).isEqualTo(expectedReason);
            verify(bidTransactionDAO, never()).saveTransactionAndUpdatePrice(
                    any(), anyString(), anyLong(), anyString());
            verify(ratingService).isEligible(user);
        }
    }

    // =========================================================================
    // placeBid — anti-sniping behavior
    // =========================================================================

    @Nested
    @DisplayName("placeBid — anti-sniping")
    class PlaceBidAntiSniping {

        private Auction snipingAuction(long secondsUntilEnd) {
            return Auction.reconstitute(
                    java.util.UUID.randomUUID().toString(),
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now(),
                    runningAuction.getItem(),
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now().plusSeconds(secondsUntilEnd),
                    STARTING_PRICE,
                    Auction.AuctionStatus.RUNNING,
                    STARTING_PRICE * 2);
        }

        @ParameterizedTest(name = "secondsLeft={0}")
        @ValueSource(longs = {10, 20, 30})
        @DisplayName("bid trong cửa sổ 30s → gia hạn 60s, persist, notify AUCTION_EXTENDED")
        void placeBid_withinSnipingWindow_extends(long secondsUntilEnd) {
            when(ratingService.isEligible(bidder)).thenReturn(true);
            Auction sniping = snipingAuction(secondsUntilEnd);
            bidder.addJoinedAuction(sniping.getId());
            LocalDateTime endTimeBefore = sniping.getEndTime();

            bidService.placeBid(bidder, sniping, STARTING_PRICE + 200_000L, strategy);

            assertThat(sniping.getEndTime()).isEqualTo(endTimeBefore.plusSeconds(60));
            verify(auctionDAO).updateEndTime(eq(sniping.getId()), any(LocalDateTime.class));
            verify(auctionService).notify(
                    eq(sniping),
                    eq(AuctionEvent.AuctionEventType.AUCTION_EXTENDED),
                    eq(bidder),
                    anyLong(),
                    anyString());
        }

        @Test
        @DisplayName("bid ngoài cửa sổ 30s → endTime giữ nguyên, không persist")
        void placeBid_outsideSnipingWindow_noExtension() {
            when(ratingService.isEligible(bidder)).thenReturn(true);
            LocalDateTime endTimeBefore = runningAuction.getEndTime();

            bidService.placeBid(bidder, runningAuction, STARTING_PRICE + 200_000L, strategy);

            assertThat(runningAuction.getEndTime()).isEqualTo(endTimeBefore);
            verify(auctionDAO, never()).updateEndTime(any(), any());
        }
    }

    // =========================================================================
    // leaveAuction — anti-sniping khi leader rời gần cuối phiên
    // =========================================================================

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
            Auction auction = Auction.reconstitute(
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
        @DisplayName("leader rời trong 30s cuối → gia hạn 60s, persist, notify AUCTION_EXTENDED")
        void leaderLeave_withinSnipingWindow_extendsAndNotifies() {
            Auction auction = snipingAuctionWithLeader();
            LocalDateTime endBefore = auction.getEndTime();

            when(bidTransactionDAO.cancelBidsByBidder(auction.getId(), leader.getId())).thenReturn(1);
            when(bidTransactionDAO.findHighestValidBidExcept(auction.getId(), leader.getId()))
                    .thenReturn(BidTransaction.create(
                            runnerUp, auction.getId(), STARTING_PRICE + 300_000L, BidResult.ACCEPTED));

            bidService.leaveAuction(leader, auction);

            assertThat(auction.getEndTime()).isEqualTo(endBefore.plusSeconds(60));
            verify(auctionDAO).updateEndTime(eq(auction.getId()), any(LocalDateTime.class));
            verify(auctionService).notify(
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

            bidService.leaveAuction(runnerUp, auction);

            assertThat(auction.getEndTime()).isEqualTo(endBefore);
            verify(auctionDAO, never()).updateEndTime(any(), any());
        }

        @Test
        @DisplayName("leader rời khi còn > 30s → không gia hạn")
        void leaderLeave_outsideSnipingWindow_doesNotExtend() {
            Auction auction = Auction.reconstitute(
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
            when(bidTransactionDAO.findHighestValidBidExcept(any(), any()))
                    .thenReturn(null);

            bidService.leaveAuction(leader, auction);

            assertThat(auction.getEndTime()).isEqualTo(endBefore);
            verify(auctionDAO, never()).updateEndTime(any(), any());
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

    // =========================================================================
    // joinAuction — NormalUser happy path
    // =========================================================================

    @Nested
    @DisplayName("joinAuction — NormalUser")
    class JoinAuctionNormalUser {

        private NormalUser freshBidder;

        @BeforeEach
        void setUpFreshBidder() {
            freshBidder = TestFixture.bidderWithBalance("freshBidr1", 10_000_000L);
        }

        @Test
        @DisplayName("join (lần đầu hoặc rejoin) → khóa cọc, observer, persist JOINED")
        void joinAuction_happyPath_fullFlow() {
            freshBidder.addLeftAuction(runningAuction.getId());
            when(ratingService.isEligible(freshBidder)).thenReturn(true);
            doNothing().when(walletService).lockDeposit(any(), anyLong(), any());
            long expectedDeposit = runningAuction.getItem().getStartingPrice() * 3 / 10;

            bidService.joinAuction(freshBidder, runningAuction, observer);

            assertThat(freshBidder.hasJoined(runningAuction.getId())).isTrue();
            assertThat(freshBidder.hasLeft(runningAuction.getId())).isFalse();
            verify(walletService).lockDeposit(freshBidder, expectedDeposit, runningAuction.getId());
            verify(auctionService).addObserver(runningAuction.getId(), observer);
            verify(userDAO).saveUserAuctionActivity(
                    freshBidder.getId(), runningAuction.getId(), "JOINED");
        }

        @Test
        @DisplayName("bidder không eligible → ném AuthenticationException ngay tại join")
        void joinAuction_ineligibleBidder_throwsAuthException() {
            // Arrange
            NormalUser banned = TestFixture.bannedBidder("bannedUser4");
            when(ratingService.isEligible(banned)).thenReturn(false);

            // Act & Assert
            assertThrows(AuthenticationException.class,
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
            AuctionBusinessException ex = assertThrows(AuctionBusinessException.class,
                    () -> bidService.joinAuction(seller, runningAuction, observer));
            assertThat(ex.getReason()).isEqualTo(AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
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
            verify(userDAO, never()).saveUserAuctionActivity(
                    bidder.getId(), runningAuction.getId(), "WATCHING");
        }
    }
}