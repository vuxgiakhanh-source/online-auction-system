package com.group13.auction.observer;

import com.group13.auction.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link SellerObserver}.
 *
 * <h3>Chiến lược test</h3>
 * <ul>
 *   <li>SellerObserver không có return value — observable output là log ra stdout
 *       và side-effect duy nhất mang business value:
 *       {@code ratingService.rewardSeller()} chỉ được gọi khi {@code PAYMENT_COMPLETED}.</li>
 *   <li>Capture {@code System.out} để verify notification routing đúng seller,
 *       đúng content theo event type.</li>
 *   <li>{@code IRatingService} được mock bằng Mockito — đây là dependency bên ngoài
 *       và {@code rewardSeller()} là side-effect quan trọng cần verify tường minh.</li>
 *   <li>Chỉ verify interaction với ratingService khi thật sự ảnh hưởng business behavior
 *       ({@code rewardSeller} tại PAYMENT_COMPLETED).
 *       Các event khác verify bằng stdout content, không verify mock call.</li>
 * </ul>
 *
 * <p>Không DB, không network, không filesystem, không integration test.
 */
@DisplayName("SellerObserver")
class SellerObserverTest {

    // =========================================================================
    // Test infrastructure
    // =========================================================================

    private final ByteArrayOutputStream outCaptor = new ByteArrayOutputStream();
    private PrintStream originalOut;

    private NormalUser seller;
    private NormalUser winner;
    private NormalUser otherBidder;
    private Auction auction;
    private IRatingService ratingService;
    private SellerObserver observer;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        System.setOut(new PrintStream(outCaptor));

        seller      = TestFixture.normalSeller("sellerAA1");
        winner      = TestFixture.bidderWithBalance("bidderBB2", 5_000_000L);
        otherBidder = TestFixture.bidderWithBalance("bidderCC3", 5_000_000L);
        auction     = TestFixture.runningAuction(seller, 1_000_000L);
        ratingService = Mockito.mock(IRatingService.class);

        observer = new SellerObserver(seller, ratingService);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    /** Lấy nội dung stdout đã capture. */
    private String output() {
        return outCaptor.toString();
    }

    /** Reset bộ đệm capture. */
    private void resetCapture() {
        outCaptor.reset();
    }

    private static void assertContains(String actual, String fragment) {
        assertTrue(actual.contains(fragment),
                "Expected output to contain: [" + fragment + "], but was:\n" + actual);
    }

    private static void assertNotContains(String actual, String fragment) {
        assertFalse(actual.contains(fragment),
                "Expected output NOT to contain: [" + fragment + "], but was:\n" + actual);
    }

    // =========================================================================
    // onBidPlaced — BID_PLACED
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — BID_PLACED")
    class OnBidPlaced {

        @Test
        @DisplayName("BID_PLACED: seller nhận notification chứa username của mình")
        void bidPlaced_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, winner, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("BID_PLACED: notification chứa bid amount")
        void bidPlaced_notificationContainsBidAmount() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, winner, 2_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — định dạng %.0f
            assertContains(output(), "2500000");
        }

        @Test
        @DisplayName("BID_PLACED: notification chứa auction id")
        void bidPlaced_notificationContainsAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, winner, 1_200_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(output(), auction.getId());
        }

        @Test
        @DisplayName("BID_PLACED: duplicate event → notification emitted 2 lần (model không dedup)")
        void bidPlaced_duplicateEvent_emittedTwice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, winner, 1_500_000L);

            // Act
            observer.onBidPlaced(event);
            observer.onBidPlaced(event);

            // Assert — dedup là trách nhiệm của upstream, không phải model
            long lineCount = output().lines()
                    .filter(l -> l.contains(seller.getUsername()) && l.contains(auction.getId()))
                    .count();
            assertEquals(2, lineCount,
                    "duplicate BID_PLACED phải emit 2 lần — model không tự dedup");
        }

        @Test
        @DisplayName("BID_PLACED: không gọi ratingService (bid event không tác động rating)")
        void bidPlaced_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, winner, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — không có interaction nào với ratingService
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onBidPlaced — BID_RESERVE_NOT_MET
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — BID_RESERVE_NOT_MET")
    class OnBidReserveNotMet {

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: seller nhận notification chứa username của mình")
        void reserveNotMet_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, winner, 800_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: notification chứa bid amount chưa đạt reserve")
        void reserveNotMet_notificationContainsBidAmount() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, winner, 800_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(output(), "800000");
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: notification chứa reserve price của auction")
        void reserveNotMet_notificationContainsReservePrice() {
            // Arrange — reservePrice = startingPrice * 2 = 2_000_000
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, winner, 800_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — reserve price xuất hiện trong log
            assertContains(output(), "2000000");
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: không gọi ratingService")
        void reserveNotMet_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, winner, 800_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onBidPlaced — default branch (event không thuộc bid flow)
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — event types không được xử lý trong bid flow")
    class OnBidPlacedDefaultBranch {

        @ParameterizedTest(name = "event type {0} → default branch, im lặng")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "AUCTION_STARTED", "AUCTION_UPCOMING", "AUCTION_ENDED",
                "AUCTION_NO_WINNER", "RESERVE_NOT_MET_CLOSED", "AUCTION_EXTENDED",
                "PAYMENT_COMPLETED", "AUCTION_CANCELED", "SECOND_CHANCE_OFFERED",
                "QUALITY_REPORT_APPROVED", "FRAUD_DETECTED",
                "SELLER_CANCEL_REQUEST", "SELLER_CANCEL_REQUEST_ACCEPTED"
        })
        @DisplayName("non-bid event type → không emit notification trong onBidPlaced")
        void nonBidEventType_noOutput(AuctionEvent.AuctionEventType type) {
            // Arrange
            AuctionEvent event = new AuctionEvent(type, auction, winner, 0L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertTrue(output().isBlank(),
                    "onBidPlaced phải im lặng với event type: " + type);
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_STARTED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_STARTED")
    class OnAuctionStarted {

        @Test
        @DisplayName("AUCTION_STARTED: seller nhận notification phiên bắt đầu")
        void auctionStarted_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("AUCTION_STARTED: không gọi ratingService")
        void auctionStarted_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_ENDED (có winner)
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_ENDED")
    class OnAuctionEnded {

        @Test
        @DisplayName("AUCTION_ENDED: notification chứa username seller và username winner")
        void auctionEnded_notificationContainsSellerAndWinner() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String out = output();
            assertContains(out, seller.getUsername());
            assertContains(out, winner.getUsername());
        }

        @Test
        @DisplayName("AUCTION_ENDED: notification chứa winning price")
        void auctionEnded_notificationContainsWinningPrice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, winner, 3_500_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), "3500000");
        }

        @Test
        @DisplayName("AUCTION_ENDED: event.getBidder() = null → im lặng (không có winner)")
        void auctionEnded_nullWinner_silent() {
            // Arrange — getBidder() = null: branch `if (event.getBidder() != null)` = false
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — branch bị skip, không emit gì
            assertTrue(output().isBlank(),
                    "AUCTION_ENDED với null bidder phải im lặng (seller chờ PAYMENT_COMPLETED)");
        }

        @Test
        @DisplayName("AUCTION_ENDED: không gọi ratingService (chưa thanh toán)")
        void auctionEnded_noRatingRewardYet() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — rewardSeller chỉ trigger ở PAYMENT_COMPLETED
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_NO_WINNER
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_NO_WINNER")
    class OnAuctionNoWinner {

        @Test
        @DisplayName("AUCTION_NO_WINNER: seller nhận notification phiên hủy không có người đặt")
        void noWinner_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("AUCTION_NO_WINNER: notification đề cập phiên bị hủy")
        void noWinner_notificationMentionsCancellation() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — "hủy" là thông tin critical với seller
            assertContains(output(), "hủy");
        }

        @Test
        @DisplayName("AUCTION_NO_WINNER: không gọi ratingService")
        void noWinner_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — RESERVE_NOT_MET_CLOSED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — RESERVE_NOT_MET_CLOSED")
    class OnReserveNotMetClosed {

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: seller nhận notification chứa tên item")
        void reserveNotMetClosed_notificationContainsItemName() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, 1_200_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — tên item trong notification
            assertContains(output(), auction.getItem().getName());
        }

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: notification chứa highest bid amount")
        void reserveNotMetClosed_notificationContainsHighestBid() {
            // Arrange
            long highestBid = 1_300_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, highestBid);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), "1300000");
        }

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: notification chứa tên seller")
        void reserveNotMetClosed_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, 1_200_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: phiên bị hủy — không gọi ratingService")
        void reserveNotMetClosed_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, 1_200_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — PAYMENT_COMPLETED  ← business-critical
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — PAYMENT_COMPLETED [business-critical]")
    class OnPaymentCompleted {

        @Test
        @DisplayName("PAYMENT_COMPLETED: ratingService.rewardSeller() được gọi đúng 1 lần")
        void paymentCompleted_rewardSellerCalledOnce() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — side effect quan trọng nhất của PAYMENT_COMPLETED
            verify(ratingService, times(1)).rewardSeller(seller);
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: rewardSeller nhận đúng seller instance")
        void paymentCompleted_rewardSellerReceivesCorrectSeller() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — verify exact argument
            verify(ratingService).rewardSeller(seller);
            verify(ratingService, never()).rewardBidder(any());
            verify(ratingService, never()).penalizeSeller(any());
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: seller nhận notification bán thành công")
        void paymentCompleted_sellerReceivesSuccessNotification() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: notification chứa tên item và giá bán")
        void paymentCompleted_notificationContainsItemNameAndPrice() {
            // Arrange
            long salePrice = 4_000_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, salePrice);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String out = output();
            assertContains(out, auction.getItem().getName());
            assertContains(out, "4000000");
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: repeated event → rewardSeller gọi 2 lần (model không dedup)")
        void paymentCompleted_repeatedEvent_rewardCalledTwice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);
            observer.onAuctionEnded(event);

            // Assert — dedup là trách nhiệm của AuctionService, không phải observer
            verify(ratingService, times(2)).rewardSeller(seller);
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: chỉ rewardSeller được gọi — không penalize, không rewardBidder")
        void paymentCompleted_onlyRewardSellerCalled_noOtherRatingCalls() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — chỉ một method được gọi
            verify(ratingService, times(1)).rewardSeller(seller);
            verifyNoMoreInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_CANCELED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_CANCELED")
    class OnAuctionCanceled {

        @Test
        @DisplayName("AUCTION_CANCELED: seller nhận notification phiên bị hủy")
        void auctionCanceled_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("AUCTION_CANCELED: notification đề cập phiên bị hủy")
        void auctionCanceled_notificationMentionsCancellation() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), "hủy");
        }

        @Test
        @DisplayName("AUCTION_CANCELED: không gọi ratingService")
        void auctionCanceled_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — SECOND_CHANCE_OFFERED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — SECOND_CHANCE_OFFERED")
    class OnSecondChanceOffered {

        @Test
        @DisplayName("SECOND_CHANCE_OFFERED: seller nhận notification second-chance")
        void secondChanceOffered_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED,
                    auction, otherBidder, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("SECOND_CHANCE_OFFERED: không gọi ratingService")
        void secondChanceOffered_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED,
                    auction, otherBidder, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — SELLER_CANCEL_REQUEST_ACCEPTED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — SELLER_CANCEL_REQUEST_ACCEPTED")
    class OnSellerCancelRequestAccepted {

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST_ACCEPTED: seller nhận notification yêu cầu hủy được duyệt")
        void cancelRequestAccepted_notificationContainsSellerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST_ACCEPTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(output(), seller.getUsername());
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST_ACCEPTED: notification đề cập chấp thuận")
        void cancelRequestAccepted_notificationMentionsApproval() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST_ACCEPTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — "chấp thuận" là keyword quan trọng với seller
            assertContains(output(), "chấp thuận");
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST_ACCEPTED: không gọi ratingService")
        void cancelRequestAccepted_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST_ACCEPTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_UPCOMING (TODO branch, im lặng)
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_UPCOMING (chưa hoàn thiện)")
    class OnAuctionUpcoming {

        @Test
        @DisplayName("AUCTION_UPCOMING: branch chưa implement → không crash, không emit notification")
        void auctionUpcoming_silentNoCrash() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_UPCOMING,
                    auction, null, 0L);

            // Act & Assert — không ném exception
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));

            // Assert — branch TODO → không emit gì
            assertTrue(output().isBlank(),
                    "AUCTION_UPCOMING phải im lặng (branch chưa implement)");
        }

        @Test
        @DisplayName("AUCTION_UPCOMING: không gọi ratingService")
        void auctionUpcoming_noRatingServiceInteraction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_UPCOMING,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // onAuctionEnded — event types không được xử lý (default branch)
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — default branch: event không thuộc seller flow")
    class OnAuctionEndedDefaultBranch {

        @ParameterizedTest(name = "event type {0} → default branch, im lặng, không crash")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "BID_PLACED", "BID_RESERVE_NOT_MET",
                "AUCTION_EXTENDED",
                "QUALITY_REPORT_APPROVED", "FRAUD_DETECTED",
                "SELLER_CANCEL_REQUEST"
        })
        @DisplayName("event type không được handle → default branch, không emit, không crash")
        void unhandledEventType_silentNoException(AuctionEvent.AuctionEventType type) {
            // Arrange
            AuctionEvent event = new AuctionEvent(type, auction, winner, 0L);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));
            assertTrue(output().isBlank(),
                    "default branch phải im lặng với event type: " + type);
            verifyNoInteractions(ratingService);
        }
    }

    // =========================================================================
    // Observer isolation — state consistency và instance independence
    // =========================================================================

    @Nested
    @DisplayName("Observer isolation — state consistency")
    class ObserverIsolation {

        @Test
        @DisplayName("hai SellerObserver với auction khác nhau: mỗi seller nhận đúng notification của mình")
        void twoObservers_differentSellers_eachReceivesOwnNotification() {
            // Arrange
            NormalUser sellerB  = TestFixture.normalSeller("sellerDD4");
            Auction   auctionB  = TestFixture.runningAuction(sellerB, 2_000_000L);
            IRatingService rsB  = Mockito.mock(IRatingService.class);

            SellerObserver observerA = new SellerObserver(seller, ratingService);
            SellerObserver observerB = new SellerObserver(sellerB, rsB);

            AuctionEvent eventA = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, winner, 1_500_000L);
            AuctionEvent eventB = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auctionB, winner, 2_500_000L);

            // Act
            observerA.onBidPlaced(eventA);
            String outA = output();
            resetCapture();

            observerB.onBidPlaced(eventB);
            String outB = output();

            // Assert — A chứa seller A, B chứa seller B
            assertContains(outA, seller.getUsername());
            assertNotContains(outA, sellerB.getUsername());

            assertContains(outB, sellerB.getUsername());
            assertNotContains(outB, seller.getUsername());
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: rewardSeller chỉ gọi trên đúng seller instance — không lẫn sang seller khác")
        void paymentCompleted_rewardOnlyOwnSeller_notOtherSeller() {
            // Arrange
            NormalUser sellerB  = TestFixture.normalSeller("sellerEE5");
            IRatingService rsB  = Mockito.mock(IRatingService.class);
            SellerObserver observerB = new SellerObserver(sellerB, rsB);

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, winner, 3_000_000L);

            // Act — chỉ observerA nhận event
            observer.onAuctionEnded(event);

            // Assert — ratingService của A được gọi với seller A
            verify(ratingService, times(1)).rewardSeller(seller);
            // ratingService của B không được động đến
            verifyNoInteractions(rsB);
        }

        @Test
        @DisplayName("onBidPlaced không thay đổi state của seller (notification-only)")
        void onBidPlaced_doesNotMutateSellerState() {
            // Arrange
            long balanceBefore = seller.getBalance();
            double ratingBefore = seller.getRating();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, winner, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — observer không được thay đổi state seller
            assertEquals(balanceBefore, seller.getBalance());
            assertEquals(ratingBefore, seller.getRating(), 1e-9);
        }

        @Test
        @DisplayName("onAuctionEnded(AUCTION_CANCELED) không thay đổi state seller")
        void onAuctionEnded_canceled_doesNotMutateSellerState() {
            // Arrange
            long balanceBefore   = seller.getBalance();
            double ratingBefore  = seller.getRating();
            var statusBefore     = seller.getAccountStatus();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertEquals(balanceBefore, seller.getBalance());
            assertEquals(ratingBefore,  seller.getRating(), 1e-9);
            assertEquals(statusBefore,  seller.getAccountStatus());
        }
    }

    // =========================================================================
    // Auction lifecycle integrity — chuỗi event đúng thứ tự
    // =========================================================================

    @Nested
    @DisplayName("Auction lifecycle integrity — chuỗi event đúng thứ tự")
    class AuctionLifecycleIntegrity {

        @Test
        @DisplayName("lifecycle thành công: STARTED → BID_PLACED → ENDED → PAYMENT_COMPLETED")
        void successfulLifecycle_rewardSellerCalledExactlyOnce() {
            // Arrange
            AuctionEvent started = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED, auction, null, 0L);
            AuctionEvent bidPlaced = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, winner, 2_000_000L);
            AuctionEvent ended = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED, auction, winner, 2_000_000L);
            AuctionEvent paymentDone = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED, auction, winner, 2_000_000L);

            // Act — lifecycle đầy đủ
            observer.onAuctionEnded(started);
            observer.onBidPlaced(bidPlaced);
            observer.onAuctionEnded(ended);
            observer.onAuctionEnded(paymentDone);

            // Assert — rewardSeller chỉ gọi đúng 1 lần tại PAYMENT_COMPLETED
            verify(ratingService, times(1)).rewardSeller(seller);
            verifyNoMoreInteractions(ratingService);
        }

        @Test
        @DisplayName("lifecycle thất bại: STARTED → AUCTION_NO_WINNER → không rewardSeller")
        void failedLifecycle_noWinner_noReward() {
            // Arrange
            AuctionEvent started = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED, auction, null, 0L);
            AuctionEvent noWinner = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER, auction, null, 0L);

            // Act
            observer.onAuctionEnded(started);
            observer.onAuctionEnded(noWinner);

            // Assert
            verifyNoInteractions(ratingService);
        }

        @Test
        @DisplayName("lifecycle bị hủy: STARTED → AUCTION_CANCELED → không rewardSeller")
        void canceledLifecycle_noReward() {
            // Arrange
            AuctionEvent started = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED, auction, null, 0L);
            AuctionEvent canceled = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED, auction, null, 0L);

            // Act
            observer.onAuctionEnded(started);
            observer.onAuctionEnded(canceled);

            // Assert
            verifyNoInteractions(ratingService);
        }

        @Test
        @DisplayName("lifecycle reserve không đạt: RESERVE_NOT_MET_CLOSED → không rewardSeller")
        void reserveNotMetLifecycle_noReward() {
            // Arrange
            AuctionEvent reserveNotMet = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, 1_300_000L);

            // Act
            observer.onAuctionEnded(reserveNotMet);

            // Assert
            verifyNoInteractions(ratingService);
        }

        @Test
        @DisplayName("SECOND_CHANCE lifecycle: ENDED → SECOND_CHANCE_OFFERED → PAYMENT_COMPLETED → rewardSeller 1 lần")
        void secondChanceLifecycle_rewardSellerOnce() {
            // Arrange
            AuctionEvent ended = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED, auction, winner, 3_000_000L);
            AuctionEvent secondChance = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED, auction, otherBidder, 0L);
            AuctionEvent paymentDone = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED, auction, otherBidder, 2_500_000L);

            // Act
            observer.onAuctionEnded(ended);
            observer.onAuctionEnded(secondChance);
            observer.onAuctionEnded(paymentDone);

            // Assert — chỉ 1 lần rewardSeller tại payment
            verify(ratingService, times(1)).rewardSeller(seller);
            verifyNoMoreInteractions(ratingService);
        }
    }
}