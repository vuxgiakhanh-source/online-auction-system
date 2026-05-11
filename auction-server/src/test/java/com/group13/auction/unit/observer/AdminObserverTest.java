package com.group13.auction.unit.observer;

import com.group13.auction.observer.AdminObserver;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link AdminObserver}.
 *
 * <p><b>Chiến lược test:</b>
 * <ul>
 *   <li>AdminObserver không có return value — observable output là log ra stdout.</li>
 *   <li>Capture {@code System.out} để assert notification đúng admin, đúng content.</li>
 *   <li>Admin được dùng là object thật (qua {@code Admin.reconstitute}) — không mock.</li>
 *   <li>Chỉ verify behavior quan trọng: routing event type đúng, filter đúng,
 *       content notification đúng, không mutate state admin.</li>
 * </ul>
 *
 * <p>Không DB, không network, không filesystem, không integration test.
 */
@DisplayName("AdminObserver")
class AdminObserverTest {

    // =========================================================================
    // Test infrastructure — capture stdout
    // =========================================================================

    private final ByteArrayOutputStream outCaptor = new ByteArrayOutputStream();
    private PrintStream originalOut;

    private Admin admin;
    private NormalUser seller;
    private NormalUser bidder;
    private Auction auction;
    private AdminObserver observer;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        System.setOut(new PrintStream(outCaptor));

        admin   = Admin.reconstitute(
                java.util.UUID.randomUUID().toString(),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now(),
                "adminStaff1",
                com.group13.auction.model.user.User.hashPassword("adminpass1"),
                "admin@test.com",
                com.group13.auction.model.user.User.AccountStatus.ACTIVE,
                5.0,
                Admin.LEVEL_STAFF,
                null);

        seller  = TestFixture.normalSeller("sellerAA1");
        bidder  = TestFixture.bidderWithBalance("bidderBB2", 5_000_000L);
        auction = TestFixture.runningAuction(seller, 1_000_000L);

        observer = new AdminObserver(admin);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    private String captured() {
        return outCaptor.toString();
    }

    private void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected),
                "Expected output to contain: [" + expected + "], actual:\n" + actual);
    }

    // =========================================================================
    // onBidPlaced — BID_PLACED
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — BID_PLACED")
    class OnBidPlaced {

        @Test
        @DisplayName("BID_PLACED: notification chứa username của admin")
        void bidPlaced_notificationContainsAdminUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(captured(), admin.getUsername());
        }

        @Test
        @DisplayName("BID_PLACED: notification chứa username của bidder đặt giá")
        void bidPlaced_notificationContainsBidderUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(captured(), bidder.getUsername());
        }

        @Test
        @DisplayName("BID_PLACED: notification chứa bid amount")
        void bidPlaced_notificationContainsBidAmount() {
            // Arrange
            long bidAmount = 2_000_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, bidAmount);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(captured(), "2000000");
        }

        @Test
        @DisplayName("BID_PLACED: notification chứa auction id")
        void bidPlaced_notificationContainsAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(captured(), auction.getId());
        }

        @Test
        @DisplayName("BID_PLACED: notification KHÔNG chứa nhãn [RESERVE CHƯA ĐẠT]")
        void bidPlaced_normalBid_noReserveWarningLabel() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — label reserve chỉ xuất hiện với BID_RESERVE_NOT_MET
            assertFalse(captured().contains("RESERVE CHƯA ĐẠT"),
                    "BID_PLACED thường không được chứa label [RESERVE CHƯA ĐẠT]");
        }

        @Test
        @DisplayName("BID_PLACED: event.getBidder() = null → dùng ký tự '?' thay thế, không ném NPE")
        void bidPlaced_nullBidderInEvent_usesFallbackPlaceholder() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, null, 1_000_000L);

            // Act & Assert — không ném NPE
            assertDoesNotThrow(() -> observer.onBidPlaced(event));
            assertContains(captured(), "?");
        }

        @Test
        @DisplayName("BID_PLACED: duplicate event → notification emit hai lần (model không dedup)")
        void bidPlaced_duplicateEvent_emittedTwice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);
            observer.onBidPlaced(event);

            // Assert — dedup là trách nhiệm upstream, model không tự filter
            long count = captured().lines()
                    .filter(l -> l.contains(admin.getUsername())
                            && l.contains(bidder.getUsername()))
                    .count();
            assertEquals(2, count,
                    "duplicate BID_PLACED phải emit notification 2 lần");
        }
    }

    // =========================================================================
    // onBidPlaced — BID_RESERVE_NOT_MET
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — BID_RESERVE_NOT_MET")
    class OnBidReserveNotMet {

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: admin nhận notification với label [RESERVE CHƯA ĐẠT]")
        void reserveNotMet_notificationContainsReserveLabel() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, bidder, 900_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — label reserve là thông tin quan trọng về moderation
            assertContains(captured(), "RESERVE CHƯA ĐẠT");
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: notification chứa admin username và auction id")
        void reserveNotMet_notificationContainsAdminAndAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, bidder, 900_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: notification chứa bid amount chưa đạt reserve")
        void reserveNotMet_notificationContainsBidAmount() {
            // Arrange
            long underReserveAmount = 900_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, bidder, underReserveAmount);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertContains(captured(), "900000");
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: event.getBidder() = null → '?' thay thế, không NPE")
        void reserveNotMet_nullBidder_noNpe() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    auction, null, 800_000L);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onBidPlaced(event));
            assertContains(captured(), "?");
        }
    }

    // =========================================================================
    // onBidPlaced — Filter: event không liên quan bị ignore
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — filter event không thuộc bid flow")
    class OnBidPlacedFilter {

        @ParameterizedTest(name = "{0} → bị filter, không emit notification trong onBidPlaced")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "AUCTION_STARTED", "AUCTION_ENDED", "AUCTION_CANCELED",
                "AUCTION_NO_WINNER", "RESERVE_NOT_MET_CLOSED", "AUCTION_EXTENDED",
                "AUCTION_UPCOMING", "PAYMENT_COMPLETED", "SECOND_CHANCE_OFFERED",
                "QUALITY_REPORT_APPROVED", "FRAUD_DETECTED",
                "SELLER_CANCEL_REQUEST", "SELLER_CANCEL_REQUEST_ACCEPTED"
        })
        @DisplayName("event type không phải bid → onBidPlaced im lặng")
        void nonBidEventType_noOutputFromOnBidPlaced(AuctionEvent.AuctionEventType type) {
            // Arrange
            AuctionEvent event = new AuctionEvent(type, auction, bidder, 0L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertTrue(captured().isBlank(),
                    "onBidPlaced phải im lặng với event type " + type);
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_STARTED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_STARTED")
    class OnAuctionStarted {

        @Test
        @DisplayName("AUCTION_STARTED: admin nhận notification phiên bắt đầu")
        void auctionStarted_notificationContainsAdminUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(captured(), admin.getUsername());
        }

        @Test
        @DisplayName("AUCTION_STARTED: notification chứa auction id")
        void auctionStarted_notificationContainsAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(captured(), auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_ENDED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_ENDED")
    class OnAuctionEnded {

        @Test
        @DisplayName("AUCTION_ENDED với winner: notification chứa username winner")
        void auctionEnded_withWinner_notificationContainsWinnerUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, bidder, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertContains(captured(), bidder.getUsername());
        }

        @Test
        @DisplayName("AUCTION_ENDED với winner null: dùng 'Không có' thay thế, không NPE")
        void auctionEnded_nullWinner_fallbackLabel() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, null, 0L);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));
            assertContains(captured(), "Không có");
        }

        @Test
        @DisplayName("AUCTION_ENDED: notification chứa admin username và auction id")
        void auctionEnded_notificationContainsAdminAndAuction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    auction, bidder, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_NO_WINNER
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_NO_WINNER")
    class OnAuctionNoWinner {

        @Test
        @DisplayName("AUCTION_NO_WINNER: admin nhận notification phiên không có người đặt giá")
        void auctionNoWinner_notificationContainsAdminAndAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — RESERVE_NOT_MET_CLOSED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — RESERVE_NOT_MET_CLOSED")
    class OnReserveNotMetClosed {

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: notification chứa highest bid amount")
        void reserveNotMetClosed_notificationContainsHighestBid() {
            // Arrange
            long highestBid = 1_200_000L;
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, highestBid);

            // Act
            observer.onAuctionEnded(event);

            // Assert — admin cần biết giá cao nhất để ra quyết định moderation
            assertContains(captured(), "1200000");
        }

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: notification chứa admin username và auction id")
        void reserveNotMetClosed_notificationContainsAdminAndAuction() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, 1_200_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — PAYMENT_COMPLETED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — PAYMENT_COMPLETED")
    class OnPaymentCompleted {

        @Test
        @DisplayName("PAYMENT_COMPLETED: admin nhận notification thanh toán thành công")
        void paymentCompleted_notificationContainsAdminAndAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
                    auction, bidder, 3_000_000L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — AUCTION_CANCELED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — AUCTION_CANCELED")
    class OnAuctionCanceled {

        @Test
        @DisplayName("AUCTION_CANCELED: admin nhận notification phiên bị hủy")
        void auctionCanceled_notificationContainsAdminAndAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — FRAUD_DETECTED (moderation critical path)
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — FRAUD_DETECTED: moderation critical path")
    class OnFraudDetected {

        @Test
        @DisplayName("FRAUD_DETECTED: notification chứa cảnh báo với admin")
        void fraudDetected_notificationContainsWarningAndAdminUsername() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, bidder, 0L, "Bid sniping pattern detected");

            // Act
            observer.onAuctionEnded(event);

            // Assert — admin phải nhận cảnh báo rõ ràng về fraud
            String output = captured();
            assertContains(output, admin.getUsername());
            assertTrue(
                    output.contains("GIAN") || output.contains("CẢNH BÁO") || output.contains("fraud"),
                    "FRAUD_DETECTED phải chứa từ cảnh báo, actual: " + output);
        }

        @Test
        @DisplayName("FRAUD_DETECTED: notification chứa fraud message")
        void fraudDetected_notificationContainsFraudMessage() {
            // Arrange
            String fraudMessage = "Bid sniping pattern detected";
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, bidder, 0L, fraudMessage);

            // Act
            observer.onAuctionEnded(event);

            // Assert — message là nội dung quan trọng để admin điều tra
            assertContains(captured(), fraudMessage);
        }

        @Test
        @DisplayName("FRAUD_DETECTED: notification chứa auction id")
        void fraudDetected_notificationContainsAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, null, 0L, "Suspicious activity");

            // Act
            observer.onAuctionEnded(event);

            // Assert — admin cần biết phiên nào để hành động
            assertContains(captured(), auction.getId());
        }

        @Test
        @DisplayName("FRAUD_DETECTED: message = null → không ném NPE, emit notification")
        void fraudDetected_nullMessage_noNpe() {
            // Arrange — message = null (null coalesced thành chuỗi rỗng trong impl)
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, null, 0L, null);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));
            assertContains(captured(), admin.getUsername());
        }

        @Test
        @DisplayName("FRAUD_DETECTED: duplicate event → emit hai lần (model không dedup)")
        void fraudDetected_duplicateEvent_emittedTwice() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, bidder, 0L, "Duplicate bid pattern");

            // Act
            observer.onAuctionEnded(event);
            observer.onAuctionEnded(event);

            // Assert — model layer không dedup; upstream chịu trách nhiệm
            long count = captured().lines()
                    .filter(l -> l.contains(admin.getUsername())
                            && l.contains(auction.getId()))
                    .count();
            assertEquals(2, count,
                    "duplicate FRAUD_DETECTED phải emit 2 lần");
        }
    }

    // =========================================================================
    // onAuctionEnded — QUALITY_REPORT_APPROVED
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — QUALITY_REPORT_APPROVED")
    class OnQualityReportApproved {

        @Test
        @DisplayName("QUALITY_REPORT_APPROVED: admin nhận notification báo cáo được duyệt")
        void qualityReportApproved_notificationContainsAdminAndAuctionId() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED,
                    auction, null, 0L);

            // Act
            observer.onAuctionEnded(event);

            // Assert
            String output = captured();
            assertContains(output, admin.getUsername());
            assertContains(output, auction.getId());
        }
    }

    // =========================================================================
    // onAuctionEnded — default branch: event types không được xử lý
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — default branch (event không được handle)")
    class OnAuctionEndedDefaultBranch {

        @ParameterizedTest(name = "{0} → default branch, không emit notification")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "BID_PLACED", "BID_RESERVE_NOT_MET",
                "AUCTION_EXTENDED", "AUCTION_UPCOMING",
                "SECOND_CHANCE_OFFERED",
                "SELLER_CANCEL_REQUEST", "SELLER_CANCEL_REQUEST_ACCEPTED"
        })
        @DisplayName("event type không được handle → default branch, không crash, không emit")
        void unhandledEventType_defaultBranch_noOutputNoException(
                AuctionEvent.AuctionEventType type) {
            // Arrange
            AuctionEvent event = new AuctionEvent(type, auction, null, 0L);

            // Act & Assert — không ném exception
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));

            // Assert — default branch im lặng
            assertTrue(captured().isBlank(),
                    "default branch phải im lặng với event type: " + type);
        }
    }

    // =========================================================================
    // Multi-admin isolation — mỗi observer notify đúng admin của mình
    // =========================================================================

    @Nested
    @DisplayName("Multi-admin isolation — observer targeting đúng admin")
    class MultiAdminIsolation {

        @Test
        @DisplayName("hai AdminObserver cho hai admin khác nhau → mỗi observer notify đúng admin")
        void twoObservers_sameEvent_eachTargetsOwnAdmin() {
            // Arrange
            Admin adminA = Admin.reconstitute(
                    java.util.UUID.randomUUID().toString(),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
                    "adminAlpha1",
                    com.group13.auction.model.user.User.hashPassword("pass"),
                    "alpha@test.com",
                    com.group13.auction.model.user.User.AccountStatus.ACTIVE,
                    5.0, Admin.LEVEL_STAFF, null);

            Admin adminB = Admin.reconstitute(
                    java.util.UUID.randomUUID().toString(),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
                    "adminBeta11",
                    com.group13.auction.model.user.User.hashPassword("pass"),
                    "beta@test.com",
                    com.group13.auction.model.user.User.AccountStatus.ACTIVE,
                    5.0, Admin.LEVEL_STAFF, null);

            AdminObserver observerA = new AdminObserver(adminA);
            AdminObserver observerB = new AdminObserver(adminB);

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            outCaptor.reset();
            observerA.onBidPlaced(event);
            String outputA = captured();

            outCaptor.reset();
            observerB.onBidPlaced(event);
            String outputB = captured();

            // Assert — A chứa username adminA, B chứa username adminB; không lẫn lộn
            assertContains(outputA, adminA.getUsername());
            assertContains(outputB, adminB.getUsername());

            assertFalse(outputA.contains(adminB.getUsername()),
                    "outputA không được chứa username của adminB");
            assertFalse(outputB.contains(adminA.getUsername()),
                    "outputB không được chứa username của adminA");
        }

        @Test
        @DisplayName("FRAUD_DETECTED → hai admin cùng nhận notification của riêng mình")
        void fraudDetected_twoAdmins_eachReceivesOwnNotification() {
            // Arrange
            Admin adminX = Admin.reconstitute(
                    java.util.UUID.randomUUID().toString(),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
                    "adminXray11",
                    com.group13.auction.model.user.User.hashPassword("pass"),
                    "x@test.com",
                    com.group13.auction.model.user.User.AccountStatus.ACTIVE,
                    5.0, Admin.LEVEL_STAFF, null);

            Admin adminY = Admin.reconstitute(
                    java.util.UUID.randomUUID().toString(),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
                    "adminYank11",
                    com.group13.auction.model.user.User.hashPassword("pass"),
                    "y@test.com",
                    com.group13.auction.model.user.User.AccountStatus.ACTIVE,
                    5.0, Admin.LEVEL_STAFF, null);

            AdminObserver obsX = new AdminObserver(adminX);
            AdminObserver obsY = new AdminObserver(adminY);

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, bidder, 0L, "Suspicious activity");

            // Act
            outCaptor.reset();
            obsX.onAuctionEnded(event);
            String outputX = captured();

            outCaptor.reset();
            obsY.onAuctionEnded(event);
            String outputY = captured();

            // Assert
            assertContains(outputX, adminX.getUsername());
            assertContains(outputY, adminY.getUsername());
        }
    }

    // =========================================================================
    // State consistency — observer không mutate state của admin
    // =========================================================================

    @Nested
    @DisplayName("State consistency — observer không mutate admin state")
    class StateConsistency {

        @Test
        @DisplayName("onBidPlaced không thay đổi rating admin (admin luôn 5.0)")
        void onBidPlaced_doesNotMutateAdminRating() {
            // Arrange
            double ratingBefore = admin.getRating();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertEquals(ratingBefore, admin.getRating(), 1e-9);
        }

        @Test
        @DisplayName("onBidPlaced không thay đổi account status của admin")
        void onBidPlaced_doesNotMutateAdminStatus() {
            // Arrange
            var statusBefore = admin.getAccountStatus();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert
            assertEquals(statusBefore, admin.getAccountStatus());
        }

        @Test
        @DisplayName("onAuctionEnded FRAUD_DETECTED không thay đổi rating admin")
        void onAuctionEnded_fraudDetected_doesNotMutateAdminRating() {
            // Arrange
            double ratingBefore = admin.getRating();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, bidder, 0L, "Fraud!");

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertEquals(ratingBefore, admin.getRating(), 1e-9);
        }

        @Test
        @DisplayName("onAuctionEnded FRAUD_DETECTED không thay đổi account status admin")
        void onAuctionEnded_fraudDetected_doesNotMutateAdminStatus() {
            // Arrange
            var statusBefore = admin.getAccountStatus();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, null, 0L, "Fraud!");

            // Act
            observer.onAuctionEnded(event);

            // Assert
            assertEquals(statusBefore, admin.getAccountStatus());
        }

        @Test
        @DisplayName("nhiều sự kiện liên tiếp → admin username không thay đổi")
        void multipleEvents_adminUsernameUnchanged() {
            // Arrange
            String usernameBefore = admin.getUsername();

            // Act — fire một loạt event khác nhau
            observer.onBidPlaced(new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, 1_500_000L));
            observer.onAuctionEnded(new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED, auction, null, 0L, "x"));
            observer.onAuctionEnded(new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED, auction, bidder, 3_000_000L));
            observer.onAuctionEnded(new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED, auction, null, 0L));

            // Assert
            assertEquals(usernameBefore, admin.getUsername());
        }

        @Test
        @DisplayName("observer không thay đổi state auction sau khi nhận event")
        void onBidPlaced_doesNotMutateAuctionState() {
            // Arrange
            Auction.AuctionStatus statusBefore = auction.getStatus();
            long priceBefore = auction.getCurrentPrice();

            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(event);

            // Assert — observer chỉ log, không được thay đổi state auction
            assertEquals(statusBefore, auction.getStatus());
            assertEquals(priceBefore,  auction.getCurrentPrice());
        }
    }

    // =========================================================================
    // Invalid event payload
    // =========================================================================

    @Nested
    @DisplayName("Invalid event payload — robustness")
    class InvalidEventPayload {

        @Test
        @DisplayName("bidAmount = 0 trong BID_PLACED → emit notification, không crash")
        void bidPlaced_zeroBidAmount_noException() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, 0L);

            // Act & Assert — format %.0f của 0 → "0"
            assertDoesNotThrow(() -> observer.onBidPlaced(event));
            assertContains(captured(), admin.getUsername());
        }

        @Test
        @DisplayName("bidAmount âm trong RESERVE_NOT_MET_CLOSED → emit notification, không crash")
        void reserveNotMetClosed_negativeBidAmount_noException() {
            // Arrange — invalid payload từ upstream bug; observer phải graceful
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED,
                    auction, null, -1L);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));
            assertFalse(captured().isBlank(), "notification vẫn phải được emit");
        }

        @Test
        @DisplayName("FRAUD_DETECTED với message rỗng → emit notification, không crash")
        void fraudDetected_emptyMessage_noException() {
            // Arrange
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction, null, 0L, "");

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(event));
            assertContains(captured(), admin.getUsername());
        }
    }
}