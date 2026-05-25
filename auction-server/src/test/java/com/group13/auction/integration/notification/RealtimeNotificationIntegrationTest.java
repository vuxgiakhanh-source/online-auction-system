package com.group13.auction.integration.notification;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.observer.BidderObserver;
import com.group13.auction.observer.SellerObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration Test — Realtime Notification System (không cần Docker).
 *
 * Sandwich: AuctionService (real) + BidderObserver/SellerObserver (real) + AuctionDAO (mock).
 *
 * API thực tế đã xác nhận:
 *   AuctionEvent.getEventType()  — KHÔNG phải getType()
 *   AuctionEvent.getBidAmount()  — KHÔNG phải getAmount()
 *   BidderObserver(NormalUser, IRatingService)
 *   SellerObserver(NormalUser, IRatingService)
 *   AuctionService.getObservers(String auctionId) → List<AuctionObserver>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Integration Test — Realtime Notification System")
class RealtimeNotificationIntegrationTest {

    @Mock private AuctionDAO mockAuctionDAO;
    @Mock private com.group13.auction.dao.FinancialTransactionDAO mockFinancialTransactionDAO;
    /**
     * FIX: inject mock AuctionWinnerDAO.
     * closeAuction() TH2 gọi saveWinner() — real DAO sẽ fail vì auction không có trong DB
     * (FK constraint) → throw RuntimeException → test TC_TD_01 crash.
     */
    @Mock private com.group13.auction.dao.AuctionWinnerDAO mockAuctionWinnerDAO;

    private IRatingService ratingService;
    private AuctionService auctionService;

    private NormalUser seller;
    private NormalUser bidder;
    private NormalUser bidder2;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        TestFixture.resetSystemBankBalance();

        ratingService = TestFixture.ratingServiceAllowAll();
        seller  = TestFixture.normalSeller("sellerUser");
        bidder  = TestFixture.bidderWithBalance("bidderUser",  5_000_000L);
        bidder2 = TestFixture.bidderWithBalance("bidder2User", 3_000_000L);

        // FIX: dùng 4-arg constructor để inject mock AuctionWinnerDAO.
        // Constructor 3-arg tự new AuctionWinnerDAO() thật → saveWinner() cố INSERT vào DB
        // với auction_id chưa persist → SQLIntegrityConstraintViolationException (FK fail)
        // → throw RuntimeException → TC_TD_01 (closeAuction với winner) crash.
        auctionService = new AuctionService(ratingService, mockAuctionDAO,
            mockFinancialTransactionDAO, mockAuctionWinnerDAO);

        // Stub DAO calls fired inside AuctionService
        when(mockAuctionDAO.updateAuctionStatus(anyString(), anyString())).thenReturn(true);
        when(mockAuctionDAO.updateAuctionResult(any(Auction.class))).thenReturn(true);
        when(mockFinancialTransactionDAO.saveTransaction(
            any(com.group13.auction.model.bid.FinancialTransaction.class))).thenReturn(true);
        when(mockFinancialTransactionDAO.findLockedDepositAmount(anyString(), anyString())).thenReturn(0L);
        // FIX: stub saveWinner → true để TH2 không crash
        when(mockAuctionWinnerDAO.saveWinner(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        TestFixture.resetSystemAdmin();
        TestFixture.resetSystemBankBalance();
        resetAuctionManagerSingleton();
    }

    // =========================================================================
    // Top-Down — AuctionService dispatch event
    // =========================================================================

    @Nested
    @DisplayName("Top-Down — AuctionService dispatch AuctionEvent")
    class TopDownEventDispatch {

        @Test
        @DisplayName("TC_TD_01: closeAuction() với winner → dispatch AUCTION_ENDED tới observer")
        void closeAuction_withWinner_dispatchesAuctionEnded() {
            // reserve = startingPrice*2 = 1_600_000; bid = 1_800_000 > reserve → isReserveMet
            Auction auction = TestFixture.runningAuction(seller, 800_000L);
            auction.updateBid(1_800_000L, bidder);

            BidderObserver spy = spy(new BidderObserver(bidder, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.closeAuction(auction);

            ArgumentCaptor<AuctionEvent> cap = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(spy, atLeastOnce()).onAuctionEnded(cap.capture());
            List<AuctionEventType> types = cap.getAllValues().stream()
                .map(AuctionEvent::getEventType).toList();
            assertThat(types).contains(AuctionEventType.AUCTION_ENDED);
        }

        @Test
        @DisplayName("TC_TD_02: closeAuction() không có winner → dispatch AUCTION_NO_WINNER")
        void closeAuction_noWinner_dispatchesNoWinner() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            // không updateBid → currentLeader = null

            BidderObserver spy = spy(new BidderObserver(bidder, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.closeAuction(auction);

            ArgumentCaptor<AuctionEvent> cap = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(spy, atLeastOnce()).onAuctionEnded(cap.capture());
            List<AuctionEventType> types = cap.getAllValues().stream()
                .map(AuctionEvent::getEventType).toList();
            assertThat(types).contains(AuctionEventType.AUCTION_NO_WINNER);
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("TC_TD_03: startAuction() dispatch AUCTION_STARTED tới tất cả observer")
        void startAuction_dispatchesAuctionStarted_toAllObservers() {
            Auction auction = TestFixture.openAuction(seller, 500_000L);

            BidderObserver spyB = spy(new BidderObserver(bidder, ratingService));
            SellerObserver spyS = spy(new SellerObserver(seller, ratingService));
            auctionService.addObserver(auction.getId(), spyB);
            auctionService.addObserver(auction.getId(), spyS);

            auctionService.startAuction(auction);

            verify(spyB, times(1)).onAuctionEnded(
                argThat(e -> e.getEventType() == AuctionEventType.AUCTION_STARTED));
            verify(spyS, times(1)).onAuctionEnded(
                argThat(e -> e.getEventType() == AuctionEventType.AUCTION_STARTED));
        }

        @Test
        @DisplayName("TC_TD_04: closeAuction() reserve chưa đạt → dispatch RESERVE_NOT_MET_CLOSED")
        void closeAuction_reserveNotMet_dispatchesCorrectEvent() {
            // reserve = 1_000_000 * 2 = 2_000_000; bid = 200_000 < reserve
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            auction.updateBid(200_000L, bidder);

            SellerObserver spy = spy(new SellerObserver(seller, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.closeAuction(auction);

            ArgumentCaptor<AuctionEvent> cap = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(spy, atLeastOnce()).onAuctionEnded(cap.capture());
            List<AuctionEventType> types = cap.getAllValues().stream()
                .map(AuctionEvent::getEventType).toList();
            assertThat(types).contains(AuctionEventType.RESERVE_NOT_MET_CLOSED);
        }
    }

    // =========================================================================
    // Bottom-Up — Observer xử lý event
    // =========================================================================

    @Nested
    @DisplayName("Bottom-Up — BidderObserver và SellerObserver xử lý event")
    class BottomUpObserverBehavior {

        @Test
        @DisplayName("TC_BU_01: BidderObserver nhận AUCTION_ENDED là winner — không ném exception")
        void bidderObserver_onAuctionEnded_asWinner_noException() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            auction.updateBid(1_100_000L, bidder);
            BidderObserver obs = new BidderObserver(bidder, ratingService);
            AuctionEvent event = new AuctionEvent(AuctionEventType.AUCTION_ENDED,
                auction, bidder, 1_100_000L);

            assertThatCode(() -> obs.onAuctionEnded(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TC_BU_02: BidderObserver nhận AUCTION_ENDED là loser — không ném exception")
        void bidderObserver_onAuctionEnded_asLoser_noException() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            auction.updateBid(1_100_000L, bidder2);
            BidderObserver obs = new BidderObserver(bidder, ratingService);
            AuctionEvent event = new AuctionEvent(AuctionEventType.AUCTION_ENDED,
                auction, bidder2, 1_100_000L);

            assertThatCode(() -> obs.onAuctionEnded(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TC_BU_03: SellerObserver.onAuctionEnded() nhận AUCTION_ENDED — không ném exception")
        void sellerObserver_onAuctionEnded_noException() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            auction.updateBid(1_100_000L, bidder);
            SellerObserver obs = new SellerObserver(seller, ratingService);
            AuctionEvent event = new AuctionEvent(AuctionEventType.AUCTION_ENDED,
                auction, bidder, 1_100_000L);

            assertThatCode(() -> obs.onAuctionEnded(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TC_BU_04: BidderObserver.onBidPlaced() nhận BID_PLACED — không ném exception")
        void bidderObserver_onBidPlaced_noException() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            BidderObserver obs = new BidderObserver(bidder, ratingService);
            AuctionEvent event = new AuctionEvent(AuctionEventType.BID_PLACED,
                auction, bidder2, 600_000L);

            assertThatCode(() -> obs.onBidPlaced(event)).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // Sandwich — AuctionService + Observer thực tích hợp
    // =========================================================================

    @Nested
    @DisplayName("Sandwich — AuctionService + Observer thực tích hợp")
    class SandwichIntegration {

        @Test
        @DisplayName("TC_SW_01: closeAuction() → observer.onAuctionEnded() được gọi đúng 1 lần với AUCTION_ENDED")
        void closeAuction_callsObserverOnce_withCorrectEvent() {
            Auction auction = TestFixture.runningAuction(seller, 800_000L);
            auction.updateBid(1_800_000L, bidder); // vượt reserve

            SellerObserver spy = spy(new SellerObserver(seller, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.closeAuction(auction);

            verify(spy, times(1)).onAuctionEnded(
                argThat(e -> e.getEventType() == AuctionEventType.AUCTION_ENDED));
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
        }

        @Test
        @DisplayName("TC_SW_02: notify() BID_PLACED → onBidPlaced() gọi, KHÔNG gọi onAuctionEnded()")
        void notify_bidPlaced_callsOnlyOnBidPlaced() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            BidderObserver spy = spy(new BidderObserver(bidder, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.notify(auction, AuctionEventType.BID_PLACED, bidder2, 600_000L);

            verify(spy, times(1)).onBidPlaced(any(AuctionEvent.class));
            verify(spy, never()).onAuctionEnded(any(AuctionEvent.class));
        }

        @Test
        @DisplayName("TC_SW_03: notify() AUCTION_ENDED → onAuctionEnded() gọi, KHÔNG gọi onBidPlaced()")
        void notify_auctionEnded_callsOnlyOnAuctionEnded() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            SellerObserver spy = spy(new SellerObserver(seller, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.notify(auction, AuctionEventType.AUCTION_ENDED, bidder, 700_000L);

            verify(spy, times(1)).onAuctionEnded(any(AuctionEvent.class));
            verify(spy, never()).onBidPlaced(any(AuctionEvent.class));
        }

        @Test
        @DisplayName("TC_SW_04: startAuction() chỉ broadcast tới đúng auctionId — phiên khác không nhận")
        void startAuction_onlyNotifiesCorrectAuction() {
            Auction a1 = TestFixture.openAuction(seller, 500_000L);
            Auction a2 = TestFixture.openAuction(seller, 500_000L);

            BidderObserver spyA1 = spy(new BidderObserver(bidder, ratingService));
            BidderObserver spyA2 = spy(new BidderObserver(bidder2, ratingService));
            auctionService.addObserver(a1.getId(), spyA1);
            auctionService.addObserver(a2.getId(), spyA2);

            auctionService.startAuction(a1);

            verify(spyA1, times(1)).onAuctionEnded(any());
            verify(spyA2, never()).onAuctionEnded(any());
        }

        @Test
        @DisplayName("TC_SW_05: addObserver() idempotent — đăng ký 3 lần chỉ nhận 1 event")
        void addObserver_idempotent_receivesEventOnce() {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            BidderObserver spy = spy(new BidderObserver(bidder, ratingService));

            auctionService.addObserver(auction.getId(), spy);
            auctionService.addObserver(auction.getId(), spy); // lần 2
            auctionService.addObserver(auction.getId(), spy); // lần 3

            auctionService.startAuction(auction);

            verify(spy, times(1)).onAuctionEnded(any());
        }
    }

    // =========================================================================
    // Big Bang — End-to-End
    // =========================================================================

    @Nested
    @DisplayName("Big Bang — End-to-End notification pipeline")
    class BigBangEndToEnd {

        @Test
        @DisplayName("TC_BB_01: closeAuction() notify toàn bộ observer chain với winner info")
        void closeAuction_notifiesAllObservers() {
            Auction auction = TestFixture.runningAuction(seller, 800_000L);
            auction.updateBid(1_800_000L, bidder); // vượt reserve

            BidderObserver spyWinner  = spy(new BidderObserver(bidder,  ratingService));
            BidderObserver spyLoser   = spy(new BidderObserver(bidder2, ratingService));
            SellerObserver spySeller  = spy(new SellerObserver(seller,  ratingService));
            auctionService.addObserver(auction.getId(), spyWinner);
            auctionService.addObserver(auction.getId(), spyLoser);
            auctionService.addObserver(auction.getId(), spySeller);

            auctionService.closeAuction(auction);

            verify(spyWinner, atLeastOnce()).onAuctionEnded(any());
            verify(spyLoser,  atLeastOnce()).onAuctionEnded(any());
            verify(spySeller, atLeastOnce()).onAuctionEnded(any());
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
        }

        @Test
        @DisplayName("TC_BB_02: startAuction() + closeAuction() — observer nhận ít nhất 2 event")
        void startThenClose_observerReceivesMultipleEvents() {
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            auction.updateBid(1_100_000L, bidder); // updateBid trước khi start

            BidderObserver spy = spy(new BidderObserver(bidder, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            auctionService.startAuction(auction);
            auctionService.closeAuction(auction);

            verify(spy, atLeast(2)).onAuctionEnded(any());
        }

        @Test
        @DisplayName("TC_BB_03: Observer isolation — observer phiên A không nhận event phiên B")
        void observerIsolation_noLeakBetweenAuctions() {
            Auction a1 = TestFixture.runningAuction(seller, 500_000L);
            Auction a2 = TestFixture.runningAuction(seller, 500_000L);

            BidderObserver spyA = spy(new BidderObserver(bidder, ratingService));
            auctionService.addObserver(a1.getId(), spyA);
            // không đăng ký observer cho a2

            auctionService.closeAuction(a2);

            verify(spyA, never()).onAuctionEnded(any());
            verify(spyA, never()).onBidPlaced(any());
        }

        @Test
        @DisplayName("TC_BB_04: AuctionEvent chứa đúng type, auction, bidder, bidAmount")
        void auctionEvent_containsCorrectFields() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            long amount = 600_000L;

            AuctionEvent event = new AuctionEvent(AuctionEventType.BID_PLACED,
                auction, bidder, amount);

            assertAll(
                () -> assertThat(event.getEventType()).isEqualTo(AuctionEventType.BID_PLACED),
                () -> assertThat(event.getAuction().getId()).isEqualTo(auction.getId()),
                () -> assertThat(event.getBidder()).isEqualTo(bidder),
                () -> assertThat(event.getBidAmount()).isEqualTo(amount)
            );
        }
    }

    // =========================================================================
    // Boundary & Error
    // =========================================================================

    @Nested
    @DisplayName("Boundary & Error — edge cases")
    class BoundaryAndError {

        @Test
        @DisplayName("TC_BE_01: closeAuction() với 0 observer — không ném exception")
        void closeAuction_noObservers_noException() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            auction.updateBid(1_100_000L, bidder);

            assertThatCode(() -> auctionService.closeAuction(auction)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("TC_BE_02: closeAuction() phiên OPEN → ném IllegalStateException")
        void closeAuction_onOpenAuction_throwsIllegalState() {
            Auction open = TestFixture.openAuction(seller, 500_000L);

            assertThatThrownBy(() -> auctionService.closeAuction(open))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("TC_BE_03: addObserver(null) — không ném exception, list vẫn empty")
        void addObserver_null_doesNotThrow() {
            String auctionId = "test-null-observer";

            assertThatCode(() -> auctionService.addObserver(auctionId, null))
                .doesNotThrowAnyException();
            assertThat(auctionService.getObservers(auctionId)).isEmpty();
        }

        @Test
        @DisplayName("TC_BE_04: notify() với message null — không ném NPE")
        void notify_nullMessage_noException() {
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            BidderObserver spy = spy(new BidderObserver(bidder, ratingService));
            auctionService.addObserver(auction.getId(), spy);

            assertThatCode(() ->
                auctionService.notify(auction, AuctionEventType.AUCTION_EXTENDED,
                    bidder, 0L, null))
                .doesNotThrowAnyException();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Reset trạng thái nội tại của AuctionManager singleton sau mỗi test.
     *
     * <p><b>KHÔNG</b> set instance = null vì AuctionService.notify() gọi
     * {@code AuctionManager.getInstance()} trực tiếp mà không kiểm tra null —
     * nếu null thì NPE sẽ lan sang toàn bộ test chạy sau trong cùng JVM.
     *
     * <p>Thay vào đó: clear 4 collection nội bộ (allAuctions, allUsers,
     * globalObservers, staffObservers) để đạt test isolation mà không phá singleton.
     */
    @SuppressWarnings("unchecked")
    private void resetAuctionManagerSingleton() throws Exception {
        Class<?> cls = Class.forName("com.group13.auction.manager.AuctionManager");
        Object manager = cls.getDeclaredMethod("getInstance").invoke(null);

        for (String fieldName : new String[]{"allAuctions", "allUsers",
            "globalObservers", "staffObservers"}) {
            Field f = cls.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object col = f.get(manager);
            if (col instanceof java.util.Map) {
                ((java.util.Map<?,?>) col).clear();
            } else if (col instanceof java.util.List) {
                ((java.util.List<?>) col).clear();
            }
        }
    }
}