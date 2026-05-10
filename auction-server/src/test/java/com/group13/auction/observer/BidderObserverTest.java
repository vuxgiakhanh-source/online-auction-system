package com.group13.auction.observer;

import com.group13.auction.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link BidderObserver}.
 *
 * <p><b>Chiến lược test:</b>
 * <ul>
 *   <li>BidderObserver không có return value — toàn bộ observable output là log ra stdout.</li>
 *   <li>Capture {@code System.out} để assert notification đúng bidder, đúng content.</li>
 *   <li>IRatingService được inject nhưng hiện không được gọi trong logic observer
 *       → dùng fake từ TestFixture (không mock), tránh verify implementation detail.</li>
 *   <li>Chỉ verify behavior quan trọng: routing đúng event type, đúng bidder,
 *       winner/loser branch, null bidder field trong event.</li>
 * </ul>
 *
 * <p>Không DB, không network, không filesystem, không integration test.
 */
@DisplayName("BidderObserver")
class BidderObserverTest {

    // =========================================================================
    // Test infrastructure — capture stdout
    // =========================================================================

    private final ByteArrayOutputStream outCaptor = new ByteArrayOutputStream();
    private PrintStream originalOut;

    private NormalUser seller;
    private NormalUser bidder;
    private NormalUser otherBidder;
    private Auction auction;
    private IRatingService ratingService;
    private BidderObserver observer;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        System.setOut(new PrintStream(outCaptor));

        seller      = TestFixture.normalSeller("sellerAA1");
        bidder      = TestFixture.bidderWithBalance("bidderBB2", 5_000_000L);
        otherBidder = TestFixture.bidderWithBalance("bidderCC3", 5_000_000L);
        auction     = TestFixture.runningAuction(seller, 1_000_000L);
        ratingService = TestFixture.ratingServiceAllowAll();

        observer = new BidderObserver(bidder, ratingService);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    /** Lấy nội dung stdout đã capture, trim whitespace. */
    private String capturedOutput() {
        return outCaptor.toString();
    }

    // =========================================================================
    // onBidPlaced — BID_PLACED
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — BID_PLACED event")
    class OnBidPlaced {

        @Test
        @DisplayName("BID_PLACED: bidder nhận thông báo chứa username của mình")
        void bidPlaced_notificationContainsBidderUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, otherBidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(capturedOutput(), bidder.getUsername());
        }

        @Test
        @DisplayName("BID_PLACED: thông báo chứa username người đặt bid")
        void bidPlaced_notificationContainsBidderWhoPlacedBid() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, otherBidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(capturedOutput(), otherBidder.getUsername());
        }

        @Test
        @DisplayName("BID_PLACED: thông báo chứa bid amount")
        void bidPlaced_notificationContainsBidAmount() {
            // Arrange
            long bidAmount = 2_000_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, otherBidder, bidAmount);

            // Act
            observer.onBidPlaced(event);

            // Assert — số tiền xuất hiện trong log (định dạng %.0f)
            assertContains(capturedOutput(), "2000000");
        }

        @Test
        @DisplayName("BID_PLACED: bidder nhận đúng notification của chính mình (self-bid)")
        void bidPlaced_selfBid_stillReceivesNotification() {
            // Arrange — bidder tự đặt, observer cũng là bidder đó
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, 1_200_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — notification vẫn được gửi
            assertFalse(capturedOutput().isBlank(),
                    "observer phải emit notification dù self-bid");
            assertContains(capturedOutput(), bidder.getUsername());
        }

        @Test
        @DisplayName("BID_PLACED: event.getBidder() = null → notification thay bằng ký tự '?'")
        void bidPlaced_nullBidderInEvent_usesPlaceholder() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, null, 1_000_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — '?' thay cho null username, không ném NPE
            assertContains(capturedOutput(), "?");
        }

        @Test
        @DisplayName("BID_PLACED: duplicate event → notification emit hai lần")
        void bidPlaced_duplicateEvent_notificationEmittedTwice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, otherBidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);
            observer.onBidPlaced(event);

            // Assert — log xuất hiện 2 lần (không bị deduplicate ở model layer)
            String output = capturedOutput();
            long count = output.lines()
                    .filter(line -> line.contains(bidder.getUsername())
                            && line.contains(otherBidder.getUsername()))
                    .count();
            assertEquals(2, count,
                    "duplicate BID_PLACED phải emit notification 2 lần — dedup là việc của upstream");
        }
    }

    // =========================================================================
    // onBidPlaced — BID_RESERVE_NOT_MET
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — BID_RESERVE_NOT_MET event")
    class OnBidReserveNotMet {

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: bidder nhận thông báo reserve chưa đạt")
        void reserveNotMet_notificationSentToBidder() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, bidder, 900_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(capturedOutput(), bidder.getUsername());
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: thông báo chứa bid amount chưa đạt reserve")
        void reserveNotMet_notificationContainsBidAmount() {
            // Arrange
            long bidAmount = 900_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, bidder, bidAmount);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(capturedOutput(), "900000");
        }
    }

    // =========================================================================
    // onBidPlaced — ignore event không liên quan
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — ignore event type không thuộc bid flow")
    class OnBidPlacedIgnoreOtherEvents {

        @ParameterizedTest(name = "event type {0} phải bị ignore trong onBidPlaced")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "AUCTION_STARTED", "AUCTION_ENDED", "AUCTION_CANCELED",
                "AUCTION_NO_WINNER", "RESERVE_NOT_MET_CLOSED", "AUCTION_EXTENDED",
                "AUCTION_UPCOMING", "PAYMENT_COMPLETED", "SECOND_CHANCE_OFFERED",
                "QUALITY_REPORT_APPROVED", "FRAUD_DETECTED",
                "SELLER_CANCEL_REQUEST", "SELLER_CANCEL_REQUEST_ACCEPTED"
        })
        @DisplayName("non-bid event type → không emit notification trong onBidPlaced")
        void nonBidEventType_noOutputFromOnBidPlaced(AuctionEvent.AuctionEventType type) {
            // Arrange
            AuctionEvent event = new AuctionEvent(type, auction, otherBidder, 0L);

            // Act
            observer.onBidPlaced(event);

            // Assert — không in gì cả
            assertTrue(capturedOutput().isBlank(),
                    "onBidPlaced phải im lặng với event type " + type);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_STARTED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_STARTED event")
    class OnAuctionStarted {

        @Test
        @DisplayName("AUCTION_STARTED: bidder nhận thông báo phiên bắt đầu")
        void auctionStarted_notificationSentToBidder() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(capturedOutput(), bidder.getUsername());
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_EXTENDED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_EXTENDED event")
    class OnAuctionExtended {

        @Test
        @DisplayName("AUCTION_EXTENDED: bidder nhận thông báo gia hạn phiên")
        void auctionExtended_notificationContainsBidderAndAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_EXTENDED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = capturedOutput();
            assertContains(output, bidder.getUsername());
            assertContains(output, auction.getId());
        }

        @Test
        @DisplayName("AUCTION_EXTENDED với message: thông báo chứa message")
        void auctionExtended_withMessage_notificationContainsMessage() {
            // Arrange
            String customMessage = "Anti-sniping triggered";
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_EXTENDED,
                    auction, null, 0L, customMessage);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(capturedOutput(), customMessage);
        }

        @Test
        @DisplayName("AUCTION_EXTENDED với message = null: không ném NPE, thay bằng chuỗi rỗng")
        void auctionExtended_nullMessage_noNpe() {
            // Arrange — constructor 4-arg → message = null
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_EXTENDED,
                    auction, null, 0L, null);

            // Act & Assert — không ném NPE
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));
            assertContains(capturedOutput(), bidder.getUsername());
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_UPCOMING
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_UPCOMING event")
    class OnAuctionUpcoming {

        @Test
        @DisplayName("AUCTION_UPCOMING: bidder nhận thông báo phiên sắp bắt đầu")
        void auctionUpcoming_notificationSentToBidder() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_UPCOMING,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(capturedOutput(), bidder.getUsername());
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_ENDED (winner / loser branch)
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_ENDED: winner vs loser branch")
    class OnAuctionEnded {

        @Test
        @DisplayName("AUCTION_ENDED: bidder là winner → nhận 'Chúc mừng' và bid amount")
        void auctionEnded_bidderIsWinner_receivesCongrats() {
            // Arrange — event.getBidder() = bidder (chính observer)
            long winAmount = 3_000_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, bidder, winAmount);

            // Act
            observer.onAuctionEnded(event);

            // Assert — winner branch: congratulation + amount
            String output = capturedOutput();
            assertContains(output, bidder.getUsername());
            assertContains(output, "3000000");
            // winner branch nên chứa "thắng" hoặc "Chúc mừng"
            assertTrue(
                    output.contains("thắng") || output.contains("Chúc mừng"),
                    "winner phải nhận congratulation message, actual: " + output);
        }

        @Test
        @DisplayName("AUCTION_ENDED: bidder là winner → không nhận message của loser")
        void auctionEnded_bidderIsWinner_doesNotReceiveLoserMessage() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, bidder, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — loser branch chứa "Winner:", winner branch thì không
            assertFalse(capturedOutput().contains("Winner:"),
                    "winner không nên nhận loser-branch message");
        }

        @Test
        @DisplayName("AUCTION_ENDED: bidder là loser → nhận thông báo winner là người khác")
        void auctionEnded_bidderIsLoser_receivesWinnerUsername() {
            // Arrange — event.getBidder() = otherBidder (người thắng, không phải observer)
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, otherBidder, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — loser branch: tên bidder mình và tên winner
            String output = capturedOutput();
            assertContains(output, bidder.getUsername());
            assertContains(output, otherBidder.getUsername());
        }

        @Test
        @DisplayName("AUCTION_ENDED: bidder là loser → không nhận 'Chúc mừng'")
        void auctionEnded_bidderIsLoser_doesNotReceiveCongrats() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, otherBidder, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertFalse(capturedOutput().contains("Chúc mừng"),
                    "loser không được nhận congratulation message");
        }

        @Test
        @DisplayName("AUCTION_ENDED: event.getBidder() = null → loser branch, không có NPE")
        void auctionEnded_nullWinnerInEvent_noNpe() {
            // Arrange — no winner in event (getBidder() == null)
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, null, 0L);

            // Act & Assert — không ném NPE
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));

            // loser branch với null winner → "Không có"
            assertContains(capturedOutput(), "Không có");
        }

        @Test
        @DisplayName("AUCTION_ENDED: hai observer khác nhau cho cùng event → mỗi người nhận đúng message của mình")
        void auctionEnded_twoObservers_eachReceivesOwnNotification() {
            // Arrange
            BidderObserver observerA = new BidderObserver(bidder, ratingService);
            BidderObserver observerB = new BidderObserver(otherBidder, ratingService);

            // event: otherBidder thắng
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, otherBidder, 2_500_000L);

            // Act
            observerA.onAuctionEnded(event); // loser
            observerB.onAuctionEnded(event); // winner

            // Assert — output gồm cả 2 thông báo
            String output = capturedOutput();
            // bidder (loser) nhận thông báo winner là otherBidder
            assertTrue(output.contains(bidder.getUsername()),
                    "bidder (loser) phải có trong output");
            // otherBidder (winner) nhận chúc mừng
            assertTrue(output.contains(otherBidder.getUsername()),
                    "otherBidder (winner) phải có trong output");
            assertTrue(
                    output.contains("thắng") || output.contains("Chúc mừng"),
                    "winner phải nhận congratulation");
        }

        @Test
        @DisplayName("AUCTION_ENDED: repeated event → notification emit hai lần (model không dedup)")
        void auctionEnded_repeatedEvent_notificationEmittedTwice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, otherBidder, 2_000_000L);

            // Act
            observer.onAuctionEnded(event);
            observer.onAuctionEnded(event);

            // Assert — observer không tự dedup; upstream chịu trách nhiệm
            long lineCount = capturedOutput().lines()
                    .filter(l -> l.contains(bidder.getUsername()))
                    .count();
            assertEquals(2, lineCount,
                    "repeated AUCTION_ENDED phải emit notification 2 lần");
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_NO_WINNER
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_NO_WINNER event")
    class OnAuctionNoWinner {

        @Test
        @DisplayName("AUCTION_NO_WINNER: bidder nhận thông báo hoàn tiền cọc")
        void auctionNoWinner_notificationMentionsDepositRefund() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — "Cọc sẽ được hoàn trả" là behavior quan trọng với bidder
            String output = capturedOutput();
            assertContains(output, bidder.getUsername());
            assertContains(output, "hoàn trả");
        }
    }

    // =========================================================================
    // onAuctionEnded — RESERVE_NOT_MET_CLOSED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — RESERVE_NOT_MET_CLOSED event")
    class OnReserveNotMetClosed {

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: bidder nhận thông báo giá chưa đạt reserve, cọc hoàn trả")
        void reserveNotMetClosed_notificationMentionsRefundAndAmount() {
            // Arrange
            long highestBid = 1_200_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, highestBid);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = capturedOutput();
            assertContains(output, bidder.getUsername());
            assertContains(output, "1200000");
            assertContains(output, "hoàn trả");
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_CANCELED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_CANCELED event")
    class OnAuctionCanceled {

        @Test
        @DisplayName("AUCTION_CANCELED: bidder nhận thông báo phiên bị hủy, cọc hoàn trả")
        void auctionCanceled_notificationMentionsCancelAndRefund() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert — "hủy" và "hoàn trả" là hai thông tin critical với bidder
            String output = capturedOutput();
            assertContains(output, bidder.getUsername());
            assertContains(output, "hủy");
            assertContains(output, "hoàn trả");
        }

        @Test
        @DisplayName("AUCTION_CANCELED: notification đúng tên bidder theo observer của mình")
        void auctionCanceled_notificationTargetsCorrectBidder() {
            // Arrange — hai observer cho hai bidder khác nhau
            BidderObserver observerA = new BidderObserver(bidder, ratingService);
            BidderObserver observerB = new BidderObserver(otherBidder, ratingService);

            // Reset capture
            outCaptor.reset();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observerA.onAuctionEnded(event);
            String outputA = capturedOutput();
            outCaptor.reset();

            observerB.onAuctionEnded(event);
            String outputB = capturedOutput();

            // Assert — mỗi output chứa đúng username của mình
            assertContains(outputA, bidder.getUsername());
            assertContains(outputB, otherBidder.getUsername());
        }
    }

    // =========================================================================
    // onAuctionEnded — event type không xử lý (default branch)
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — event types không được xử lý (default branch)")
    class OnAuctionEndedDefaultBranch {

        @ParameterizedTest(name = "event type {0} → default branch, không emit notification")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "BID_PLACED", "BID_RESERVE_NOT_MET",
                "PAYMENT_COMPLETED", "SECOND_CHANCE_OFFERED",
                "QUALITY_REPORT_APPROVED", "FRAUD_DETECTED",
                "SELLER_CANCEL_REQUEST", "SELLER_CANCEL_REQUEST_ACCEPTED"
        })
        @DisplayName("event type không được handle → default branch, không crash")
        void unhandledEventType_defaultBranch_noOutputNoException(
                AuctionEvent.AuctionEventType type) {
            // Arrange
            AuctionEvent event = new AuctionEvent(type, auction, null, 0L);

            // Act & Assert — không ném exception
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));

            // Assert — không emit notification (default branch im lặng)
            assertTrue(capturedOutput().isBlank(),
                    "default branch phải im lặng với event type: " + type);
        }
    }

    // =========================================================================
    // Observer isolation — state không rò rỉ giữa các instance
    // =========================================================================

    @Nested
    @DisplayName("Observer isolation — state independence")
    class ObserverIsolation {

        @Test
        @DisplayName("hai observer cùng loại event → mỗi observer chỉ notify đúng bidder của mình")
        void twoObservers_sameEvent_eachNotifiesOwnBidder() {
            // Arrange
            BidderObserver observerA = new BidderObserver(bidder, ratingService);
            BidderObserver observerB = new BidderObserver(otherBidder, ratingService);

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, seller, 1_100_000L);

            // Act
            outCaptor.reset();
            observerA.onBidPlaced(event);
            String outputA = capturedOutput();

            outCaptor.reset();
            observerB.onBidPlaced(event);
            String outputB = capturedOutput();

            // Assert — A chứa username của bidder, B chứa username của otherBidder
            assertContains(outputA, bidder.getUsername());
            assertContains(outputB, otherBidder.getUsername());

            // Không lẫn lộn
            assertFalse(outputA.contains(otherBidder.getUsername()),
                    "outputA không được chứa username của otherBidder");
            assertFalse(outputB.contains(bidder.getUsername()),
                    "outputB không được chứa username của bidder");
        }

        @Test
        @DisplayName("gọi onBidPlaced không ảnh hưởng state của bidder (immutable notification)")
        void onBidPlaced_doesNotMutateObservedBidderState() {
            // Arrange
            long balanceBefore = bidder.getBalance();
            double ratingBefore = bidder.getRating();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, otherBidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — observer không được thay đổi state của bidder
            assertEquals(balanceBefore, bidder.getBalance());
            assertEquals(ratingBefore, bidder.getRating(), 1e-9);
        }

        @Test
        @DisplayName("gọi onAuctionEnded không ảnh hưởng state của bidder")
        void onAuctionEnded_doesNotMutateBidderState() {
            // Arrange
            long balanceBefore  = bidder.getBalance();
            double ratingBefore = bidder.getRating();
            var statusBefore    = bidder.getAccountStatus();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertEquals(balanceBefore, bidder.getBalance());
            assertEquals(ratingBefore,  bidder.getRating(), 1e-9);
            assertEquals(statusBefore,  bidder.getAccountStatus());
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected),
                "Expected output to contain: [" + expected + "], but was:\n" + actual);
    }
}