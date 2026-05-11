package com.group13.auction.unit.observer;

import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent.AuctionEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link AuctionEvent}.
 *
 * <p>Tập trung vào:
 * <ul>
 *   <li>Event integrity — payload được giữ nguyên reference/value.</li>
 *   <li>Constructor contract — cả 4-arg lẫn 5-arg overload.</li>
 *   <li>Null semantics — bidder null, message null, auction null.</li>
 *   <li>Immutability — field {@code final}, không thể thay đổi qua getter.</li>
 *   <li>Enum coverage — mọi {@link AuctionEventType} đều hợp lệ.</li>
 *   <li>State isolation — hai event độc lập không chia sẻ state.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network, không filesystem.
 */
@DisplayName("AuctionEvent")
class AuctionEventTest {

    // ── shared fixtures ──────────────────────────────────────────────────────

    private NormalUser seller;
    private NormalUser bidder;
    private Auction    auction;

    @BeforeEach
    void setUp() {
        seller  = TestFixture.normalSeller("sellerAA01");
        bidder  = TestFixture.bidderWithBalance("bidderBB02", 5_000_000L);
        auction = TestFixture.openAuction(seller, 1_000_000L);
    }

    // =========================================================================
    // Constructor 4-arg  (eventType, auction, bidder, bidAmount)
    // =========================================================================

    @Nested
    @DisplayName("Constructor 4-arg — payload integrity")
    class FourArgConstructor {

        @Test
        @DisplayName("eventType được giữ nguyên")
        void eventType_preservedExactly() {
            // Arrange & Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, 2_000_000L);

            // Assert
            assertSame(AuctionEventType.BID_PLACED, event.getEventType());
        }

        @Test
        @DisplayName("auction reference được giữ nguyên (same instance)")
        void auction_sameReference() {
            // Arrange & Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_STARTED, auction, null, 0L);

            // Assert
            assertSame(auction, event.getAuction());
        }

        @Test
        @DisplayName("bidder reference được giữ nguyên (same instance)")
        void bidder_sameReference() {
            // Arrange & Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, 1_500_000L);

            // Assert
            assertSame(bidder, event.getBidder());
        }

        @Test
        @DisplayName("bidAmount được giữ nguyên giá trị")
        void bidAmount_preservedExactly() {
            // Arrange
            long expectedAmount = 3_750_000L;

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, expectedAmount);

            // Assert
            assertEquals(expectedAmount, event.getBidAmount());
        }

        @Test
        @DisplayName("message mặc định là null khi dùng 4-arg constructor")
        void message_isNullByDefault() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_STARTED, auction, null, 0L);

            // Assert
            assertNull(event.getMessage(),
                    "4-arg constructor phải để message = null");
        }

        @Test
        @DisplayName("bidder = null được chấp nhận (event không liên quan bidder)")
        void nullBidder_accepted() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, auction, null, 0L);

            // Assert
            assertNull(event.getBidder());
        }

        @Test
        @DisplayName("bidAmount = 0 được chấp nhận (event không liên quan bid)")
        void zeroBidAmount_accepted() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_CANCELED, auction, null, 0L);

            // Assert
            assertEquals(0L, event.getBidAmount());
        }
    }

    // =========================================================================
    // Constructor 5-arg  (+ message)
    // =========================================================================

    @Nested
    @DisplayName("Constructor 5-arg — message payload")
    class FiveArgConstructor {

        @Test
        @DisplayName("message được giữ nguyên khi truyền vào")
        void message_preservedExactly() {
            // Arrange
            String msg = "Phiên đấu giá đã gia hạn thêm 5 phút";

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_EXTENDED, auction, bidder, 0L, msg);

            // Assert
            assertEquals(msg, event.getMessage());
        }

        @Test
        @DisplayName("message = null được chấp nhận qua 5-arg constructor")
        void nullMessage_accepted() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_STARTED, auction, null, 0L, null);

            // Assert
            assertNull(event.getMessage());
        }

        @Test
        @DisplayName("message rỗng được giữ nguyên (không trim, không null-coerce)")
        void emptyMessage_preservedAsEmpty() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, auction, null, 0L, "");

            // Assert
            assertEquals("", event.getMessage());
        }

        @Test
        @DisplayName("5-arg constructor delegate đúng: eventType, auction, bidder, bidAmount giống 4-arg")
        void fiveArgConstructor_allOtherFieldsPreserved() {
            // Arrange
            long amount = 2_500_000L;

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, amount, "msg");

            // Assert — tất cả field ngoài message giống 4-arg
            assertSame(AuctionEventType.BID_PLACED, event.getEventType());
            assertSame(auction, event.getAuction());
            assertSame(bidder,  event.getBidder());
            assertEquals(amount, event.getBidAmount());
        }

        @Test
        @DisplayName("message Unicode (tiếng Việt) được giữ nguyên")
        void unicodeMessage_preserved() {
            // Arrange
            String unicodeMsg = "Phát hiện gian lận — đình chỉ phiên đấu giá";

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, auction, null, 0L, unicodeMsg);

            // Assert
            assertEquals(unicodeMsg, event.getMessage());
        }
    }

    // =========================================================================
    // Event type coverage — mọi AuctionEventType đều hợp lệ
    // =========================================================================

    @Nested
    @DisplayName("AuctionEventType — enum coverage")
    class EventTypeCoverage {

        @ParameterizedTest(name = "eventType = {0}")
        @EnumSource(AuctionEventType.class)
        @DisplayName("tất cả AuctionEventType đều có thể tạo event thành công")
        void allEventTypes_canBeCreatedWithoutException(AuctionEventType type) {
            // Act & Assert — không ném exception với bất kỳ type nào
            AuctionEvent event = assertDoesNotThrow(() ->
                    new AuctionEvent(type, auction, null, 0L));

            assertSame(type, event.getEventType());
        }

        @Test
        @DisplayName("enum có đủ 15 loại event (coverage guard)")
        void enumHas15Types() {
            // Nếu ai thêm/xóa event type mà không update test → test này sẽ fail
            // buộc người thêm phải xem xét coverage.
            assertEquals(15, AuctionEventType.values().length,
                    "AuctionEventType phải có đúng 15 giá trị — cập nhật test nếu thêm type mới");
        }
    }

    // =========================================================================
    // Null payload semantics
    // =========================================================================

    @Nested
    @DisplayName("Null payload semantics")
    class NullPayload {

        @Test
        @DisplayName("auction = null được chấp nhận (event cấp global/admin)")
        void nullAuction_accepted() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, null, null, 0L);

            // Assert
            assertNull(event.getAuction());
        }

        @Test
        @DisplayName("auction = null + bidder = null → event vẫn giữ eventType đúng")
        void nullAuctionAndBidder_eventTypeStillCorrect() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, null, null, 0L, "admin alert");

            // Assert
            assertSame(AuctionEventType.FRAUD_DETECTED, event.getEventType());
            assertEquals("admin alert", event.getMessage());
        }

        @Test
        @DisplayName("bidAmount âm được giữ nguyên (model không validate — WalletService guard)")
        void negativeBidAmount_preservedAsIs() {
            // Act — model không throw; guard thuộc WalletService
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, -1L);

            // Assert — document actual behavior
            assertEquals(-1L, event.getBidAmount());
        }

        @Test
        @DisplayName("bidAmount = Long.MAX_VALUE được giữ nguyên")
        void maxLongBidAmount_preserved() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, Long.MAX_VALUE);

            // Assert
            assertEquals(Long.MAX_VALUE, event.getBidAmount());
        }
    }

    // =========================================================================
    // Immutability — final fields, getter không thể ghi đè state
    // =========================================================================

    @Nested
    @DisplayName("Immutability — field final, state không thay đổi sau khi tạo")
    class Immutability {

        @Test
        @DisplayName("getEventType() trả về cùng giá trị qua nhiều lần gọi")
        void getEventType_idempotent() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_EXTENDED, auction, bidder, 0L);

            // Act & Assert — bất biến qua nhiều lần gọi
            assertSame(event.getEventType(), event.getEventType());
        }

        @Test
        @DisplayName("getAuction() trả về cùng reference qua nhiều lần gọi")
        void getAuction_idempotent() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_STARTED, auction, null, 0L);

            // Act & Assert
            assertSame(event.getAuction(), event.getAuction());
        }

        @Test
        @DisplayName("getBidder() trả về cùng reference qua nhiều lần gọi")
        void getBidder_idempotent() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, 1_000_000L);

            // Act & Assert
            assertSame(event.getBidder(), event.getBidder());
        }

        @Test
        @DisplayName("getBidAmount() trả về cùng giá trị qua nhiều lần gọi")
        void getBidAmount_idempotent() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, 999_000L);

            // Act & Assert
            assertEquals(event.getBidAmount(), event.getBidAmount());
        }

        @Test
        @DisplayName("getMessage() trả về cùng reference qua nhiều lần gọi")
        void getMessage_idempotent() {
            // Arrange
            String msg = "test message";
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_EXTENDED, auction, bidder, 0L, msg);

            // Act & Assert
            assertSame(event.getMessage(), event.getMessage());
        }

        @Test
        @DisplayName("thay đổi auction state bên ngoài phản ánh vào event (shallow reference — expected behavior)")
        void auctionMutation_reflectedInEvent_byDesign() {
            // Arrange — AuctionEvent giữ reference, không snapshot
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_STARTED, auction, null, 0L);
            Auction.AuctionStatus statusBefore = event.getAuction().getStatus();

            // Act — mutate auction bên ngoài (transition hợp lệ)
            auction.transitionToRunning();

            // Assert — event phản ánh sự thay đổi (mutable reference — documented behavior)
            assertNotEquals(statusBefore, event.getAuction().getStatus(),
                    "AuctionEvent giữ shallow reference; mutation auction ngoài phản ánh vào event — đây là behavior cần document");
        }
    }

    // =========================================================================
    // State isolation — hai event độc lập
    // =========================================================================

    @Nested
    @DisplayName("State isolation — hai event không chia sẻ state")
    class StateIsolation {

        @Test
        @DisplayName("hai event cùng type nhưng khác auction → payload độc lập")
        void twoEvents_differentAuctions_independentPayload() {
            // Arrange
            NormalUser seller2  = TestFixture.normalSeller("sellerCC03");
            Auction    auction2 = TestFixture.openAuction(seller2, 2_000_000L);

            // Act
            AuctionEvent event1 = new AuctionEvent(AuctionEventType.AUCTION_STARTED, auction,  null, 0L);
            AuctionEvent event2 = new AuctionEvent(AuctionEventType.AUCTION_STARTED, auction2, null, 0L);

            // Assert
            assertNotSame(event1.getAuction(), event2.getAuction());
        }

        @Test
        @DisplayName("hai event cùng auction nhưng khác bidder → bidder độc lập")
        void twoEvents_differentBidders_independentBidder() {
            // Arrange
            NormalUser bidder2 = TestFixture.bidderWithBalance("bidderDD04", 3_000_000L);

            // Act
            AuctionEvent event1 = new AuctionEvent(AuctionEventType.BID_PLACED, auction, bidder,  1_000_000L);
            AuctionEvent event2 = new AuctionEvent(AuctionEventType.BID_PLACED, auction, bidder2, 1_200_000L);

            // Assert
            assertNotSame(event1.getBidder(), event2.getBidder());
            assertNotEquals(event1.getBidAmount(), event2.getBidAmount());
        }

        @Test
        @DisplayName("hai event cùng auction nhưng khác message → message độc lập")
        void twoEvents_differentMessages_independentMessage() {
            // Arrange
            String msg1 = "Phiên gia hạn lần 1";
            String msg2 = "Phiên gia hạn lần 2";

            // Act
            AuctionEvent event1 = new AuctionEvent(AuctionEventType.AUCTION_EXTENDED, auction, null, 0L, msg1);
            AuctionEvent event2 = new AuctionEvent(AuctionEventType.AUCTION_EXTENDED, auction, null, 0L, msg2);

            // Assert
            assertNotEquals(event1.getMessage(), event2.getMessage());
        }

        @Test
        @DisplayName("tạo event với null auction, event khác với non-null auction → không xung đột")
        void nullVsNonNullAuction_noConflict() {
            // Act
            AuctionEvent withAuction    = new AuctionEvent(AuctionEventType.FRAUD_DETECTED, auction, null, 0L);
            AuctionEvent withoutAuction = new AuctionEvent(AuctionEventType.FRAUD_DETECTED, null,    null, 0L);

            // Assert
            assertNotNull(withAuction.getAuction());
            assertNull(withoutAuction.getAuction());
        }
    }

    // =========================================================================
    // Business-semantic consistency — event đúng context
    // =========================================================================

    @Nested
    @DisplayName("Business-semantic consistency — event phù hợp context")
    class BusinessSemanticConsistency {

        @Test
        @DisplayName("BID_PLACED event: bidder không null, bidAmount > 0")
        void bidPlaced_bidderAndAmountPresent() {
            // Arrange
            long bidAmount = 1_800_000L;

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.BID_PLACED, auction, bidder, bidAmount);

            // Assert
            assertSame(AuctionEventType.BID_PLACED, event.getEventType());
            assertNotNull(event.getBidder(),
                    "BID_PLACED phải có bidder");
            assertTrue(event.getBidAmount() > 0,
                    "BID_PLACED phải có bidAmount > 0");
        }

        @Test
        @DisplayName("AUCTION_STARTED event: bidder null, bidAmount = 0")
        void auctionStarted_noBidderNoAmount() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_STARTED, auction, null, 0L);

            // Assert
            assertSame(AuctionEventType.AUCTION_STARTED, event.getEventType());
            assertNull(event.getBidder(),
                    "AUCTION_STARTED không liên quan đến bidder cụ thể");
            assertEquals(0L, event.getBidAmount(),
                    "AUCTION_STARTED không có bidAmount");
        }

        @Test
        @DisplayName("AUCTION_ENDED event: giữ đúng auction reference (để Observer xác định winner)")
        void auctionEnded_auctionReferenceIntact() {
            // Arrange
            NormalUser winner = TestFixture.bidderWithBalance("winnerEE05", 10_000_000L);
            Auction finished  = TestFixture.finishedAuction(seller, winner, 1_000_000L, 2_500_000L);

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_ENDED, finished, winner, 2_500_000L);

            // Assert
            assertSame(finished, event.getAuction());
            assertSame(winner,   event.getBidder());
            assertEquals(2_500_000L, event.getBidAmount());
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED event: chứa đúng auction và bidAmount thanh toán")
        void paymentCompleted_auctionAndAmountCorrect() {
            // Arrange
            long paymentAmount = 2_000_000L;

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.PAYMENT_COMPLETED, auction, bidder, paymentAmount,
                    "Thanh toán thành công");

            // Assert
            assertSame(AuctionEventType.PAYMENT_COMPLETED, event.getEventType());
            assertSame(auction, event.getAuction());
            assertEquals(paymentAmount, event.getBidAmount());
            assertNotNull(event.getMessage());
        }

        @Test
        @DisplayName("FRAUD_DETECTED event: auction có thể null (alert toàn cục)")
        void fraudDetected_auctionCanBeNull() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.FRAUD_DETECTED, null, null, 0L, "Phát hiện bot bidding");

            // Assert
            assertSame(AuctionEventType.FRAUD_DETECTED, event.getEventType());
            assertNull(event.getAuction());
            assertNotNull(event.getMessage());
        }

        @Test
        @DisplayName("SECOND_CHANCE_OFFERED event: bidder là runner-up, bidAmount là offer price")
        void secondChanceOffered_runnerUpAndOfferPriceCorrect() {
            // Arrange
            NormalUser runnerUp   = TestFixture.bidderWithBalance("runnerFF06", 8_000_000L);
            long       offerPrice = 1_900_000L;

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.SECOND_CHANCE_OFFERED, auction, runnerUp, offerPrice,
                    "Bạn được đề nghị mua với giá " + offerPrice);

            // Assert
            assertSame(AuctionEventType.SECOND_CHANCE_OFFERED, event.getEventType());
            assertSame(runnerUp, event.getBidder());
            assertEquals(offerPrice, event.getBidAmount());
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST event: seller là bidder field, message không null")
        void sellerCancelRequest_sellerInBidderField() {
            // Arrange — seller được truyền vào bidder field theo convention
            NormalUser sellerUser = TestFixture.normalSeller("sellerGG07");
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.SELLER_CANCEL_REQUEST, auction, sellerUser, 0L,
                    "Seller yêu cầu hủy phiên");

            // Assert
            assertSame(AuctionEventType.SELLER_CANCEL_REQUEST, event.getEventType());
            assertSame(sellerUser, event.getBidder());
            assertNotNull(event.getMessage());
        }

        @Test
        @DisplayName("AUCTION_EXTENDED event: message mô tả thời gian gia hạn")
        void auctionExtended_messageDescribesExtension() {
            // Arrange
            String extensionMsg = "Phiên được gia hạn thêm 5 phút do anti-sniping";

            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.AUCTION_EXTENDED, auction, bidder, 0L, extensionMsg);

            // Assert
            assertSame(AuctionEventType.AUCTION_EXTENDED, event.getEventType());
            assertEquals(extensionMsg, event.getMessage());
        }

        @Test
        @DisplayName("QUALITY_REPORT_APPROVED event: auction và bidder (reporter) đều present")
        void qualityReportApproved_auctionAndReporterPresent() {
            // Act
            AuctionEvent event = new AuctionEvent(
                    AuctionEventType.QUALITY_REPORT_APPROVED, auction, bidder, 0L,
                    "Báo cáo chất lượng đã được duyệt");

            // Assert
            assertSame(AuctionEventType.QUALITY_REPORT_APPROVED, event.getEventType());
            assertNotNull(event.getAuction());
            assertNotNull(event.getBidder());
            assertNotNull(event.getMessage());
        }
    }
}