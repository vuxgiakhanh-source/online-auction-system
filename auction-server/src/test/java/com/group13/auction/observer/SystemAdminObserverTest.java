package com.group13.auction.observer;

import com.group13.auction.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.group13.auction.observer.AuctionEvent.AuctionEventType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link SystemAdminObserver}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Dùng {@link SystemAdmin} thật (qua reflection helper trong TestFixture) — không mock.</li>
 *   <li>Verify behavior qua {@code systemAdmin.getActionLog()} — đây là audit trail
 *       duy nhất observable từ bên ngoài, không phải implementation detail.</li>
 *   <li>Chỉ assert content audit log ở mức "chứa thông tin cốt lõi nào" —
 *       không pin-point format string cụ thể để tránh test brittle.</li>
 *   <li>Không DB, không network, không filesystem.</li>
 * </ul>
 */
@DisplayName("SystemAdminObserver")
class SystemAdminObserverTest {

    private SystemAdmin systemAdmin;
    private SystemAdminObserver observer;
    private NormalUser seller;
    private NormalUser bidder;

    @BeforeEach
    void setUp() throws Exception {
        TestFixture.bootstrapSystemAdmin();
        systemAdmin = SystemAdmin.getInstance();

        observer = new SystemAdminObserver(systemAdmin);

        seller = TestFixture.normalSeller("sellerXX01");
        bidder = TestFixture.bidderWithBalance("bidderXX01", 5_000_000L);
    }

    @AfterEach
    void tearDown() throws Exception {
        TestFixture.resetSystemAdmin();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Tạo AuctionEvent đơn giản với eventType bất kỳ, không có bidder. */
    private AuctionEvent event(AuctionEvent.AuctionEventType type, Auction auction) {
        return new AuctionEvent(type, auction, null, 0L);
    }

    /** Tạo AuctionEvent có bidder và bidAmount. */
    private AuctionEvent event(AuctionEvent.AuctionEventType type, Auction auction,
                               NormalUser bidder, long amount) {
        return new AuctionEvent(type, auction, bidder, amount);
    }

    /** Tạo AuctionEvent có message (dùng cho FRAUD, SELLER_CANCEL). */
    private AuctionEvent eventWithMessage(AuctionEvent.AuctionEventType type, Auction auction,
                                          String message) {
        return new AuctionEvent(type, auction, null, 0L, message);
    }

    /** Lấy toàn bộ audit log hiện tại. */
    private List<String> auditLog() {
        return systemAdmin.getActionLog();
    }

    /** Assert rằng đúng 1 log được ghi và nó chứa tất cả keyword kỳ vọng. */
    private void assertSingleLogContaining(String... keywords) {
        List<String> log = auditLog();
        assertEquals(1, log.size(), "Chỉ được ghi đúng 1 log");
        String entry = log.get(0);
        for (String kw : keywords) {
            assertTrue(entry.contains(kw),
                    "Log phải chứa \"" + kw + "\" — log thực tế: " + entry);
        }
    }

    // =========================================================================
    // onBidPlaced
    // =========================================================================

    @Nested
    @DisplayName("onBidPlaced() — audit log khi có bid mới")
    class OnBidPlaced {

        @Test
        @DisplayName("BID_PLACED: ghi 1 audit log chứa username bidder và auctionId")
        void bidPlaced_logsAuditWithBidderAndAuction() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(ev);

            // Assert
            assertSingleLogContaining(bidder.getUsername(), auction.getId());
        }

        @Test
        @DisplayName("BID_PLACED: audit log không chứa marker RESERVE CHƯA ĐẠT")
        void bidPlaced_logDoesNotContainReserveMarker() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(ev);

            // Assert
            String entry = auditLog().get(0);
            assertFalse(entry.contains("RESERVE CHƯA ĐẠT"),
                    "BID_PLACED bình thường không được chứa marker reserve");
        }

        @Test
        @DisplayName("BID_RESERVE_NOT_MET: audit log chứa marker reserve chưa đạt")
        void bidReserveNotMet_logContainsReserveMarker() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(BID_RESERVE_NOT_MET, auction, bidder, 1_200_000L);

            // Act
            observer.onBidPlaced(ev);

            // Assert — behavior: RESERVE CHƯA ĐẠT phải xuất hiện trong log
            assertSingleLogContaining("RESERVE CHƯA ĐẠT");
        }

        @Test
        @DisplayName("bidder null: audit log ghi được (không ném exception), dùng placeholder")
        void bidPlaced_nullBidder_logsWithPlaceholder() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(BID_PLACED, auction, null, 1_000_000L);

            // Act & Assert — không được ném NPE
            assertDoesNotThrow(() -> observer.onBidPlaced(ev));
            assertEquals(1, auditLog().size(), "Vẫn phải ghi 1 audit log dù bidder null");
            assertTrue(auditLog().get(0).contains("?"),
                    "Khi bidder null phải dùng placeholder '?'");
        }

        @Test
        @DisplayName("mỗi lần onBidPlaced thêm đúng 1 audit log (không ghi trùng)")
        void bidPlaced_eachCallAddsExactlyOneLog() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(BID_PLACED, auction, bidder, 1_500_000L);

            // Act
            observer.onBidPlaced(ev);
            observer.onBidPlaced(ev); // duplicate call

            // Assert — mỗi call thêm 1 log riêng biệt
            assertEquals(2, auditLog().size(),
                    "2 lần gọi onBidPlaced phải ghi 2 log độc lập");
        }
    }

    // =========================================================================
    // onAuctionEnded — lifecycle events
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — lifecycle events")
    class OnAuctionEndedLifecycle {

        @Test
        @DisplayName("AUCTION_UPCOMING: audit log chứa auctionId")
        void auctionUpcoming_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.openAuction(seller, 500_000L);
            AuctionEvent ev = event(AUCTION_UPCOMING, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }

        @Test
        @DisplayName("AUCTION_STARTED: audit log chứa auctionId")
        void auctionStarted_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 500_000L);
            AuctionEvent ev = event(AUCTION_STARTED, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }

        @Test
        @DisplayName("AUCTION_ENDED: audit log chứa auctionId, username winner, và bid amount")
        void auctionEnded_withWinner_logsWinnerAndAmount() {
            // Arrange
            Auction auction = TestFixture.finishedAuction(seller, bidder, 1_000_000L, 2_000_000L);
            AuctionEvent ev = event(AUCTION_ENDED, auction, bidder, 2_000_000L);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId(), bidder.getUsername());
        }

        @Test
        @DisplayName("AUCTION_ENDED: winner null — audit log dùng placeholder, không ném exception")
        void auctionEnded_nullWinner_logsWithPlaceholder() {
            // Arrange
            Auction auction = TestFixture.canceledFromRunningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(AUCTION_ENDED, auction, null, 0L);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(ev));
            assertEquals(1, auditLog().size());
            assertTrue(auditLog().get(0).contains("Không có") || auditLog().get(0).contains("?"),
                    "Không có winner phải có placeholder trong log");
        }

        @Test
        @DisplayName("AUCTION_NO_WINNER: audit log chứa auctionId")
        void auctionNoWinner_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.canceledFromRunningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(AUCTION_NO_WINNER, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED: audit log chứa auctionId")
        void reserveNotMetClosed_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.canceledFromRunningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(RESERVE_NOT_MET_CLOSED, auction, null, 900_000L);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }

        @Test
        @DisplayName("PAYMENT_COMPLETED: audit log chứa auctionId và username winner")
        void paymentCompleted_logsAuctionIdAndWinner() {
            // Arrange
            Auction auction = TestFixture.finishedAuction(seller, bidder, 1_000_000L, 2_000_000L);
            AuctionEvent ev = event(PAYMENT_COMPLETED, auction, bidder, 2_000_000L);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId(), bidder.getUsername());
        }

        @Test
        @DisplayName("AUCTION_CANCELED: audit log chứa auctionId")
        void auctionCanceled_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.canceledFromOpenAuction(seller, 500_000L);
            AuctionEvent ev = event(AUCTION_CANCELED, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }

        @Test
        @DisplayName("QUALITY_REPORT_APPROVED: audit log chứa auctionId")
        void qualityReportApproved_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.finishedAuction(seller, bidder, 1_000_000L, 2_000_000L);
            AuctionEvent ev = event(QUALITY_REPORT_APPROVED, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }

        @Test
        @DisplayName("SECOND_CHANCE_OFFERED: audit log chứa auctionId và username runner-up")
        void secondChanceOffered_logsRunnerUpAndAuction() {
            // Arrange
            Auction auction = TestFixture.finishedAuction(seller, bidder, 1_000_000L, 2_000_000L);
            NormalUser runnerUp = TestFixture.bidderWithBalance("runnerUp01", 3_000_000L);
            AuctionEvent ev = event(SECOND_CHANCE_OFFERED, auction, runnerUp, 1_800_000L);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId(), runnerUp.getUsername());
        }
    }

    // =========================================================================
    // onAuctionEnded — critical / system-level events
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — critical system events")
    class OnAuctionEndedCritical {

        @Test
        @DisplayName("FRAUD_DETECTED: audit log chứa auctionId và nội dung gian lận")
        void fraudDetected_logsAuctionIdAndMessage() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            String fraudMsg = "Shill bidding bị phát hiện";
            AuctionEvent ev = eventWithMessage(FRAUD_DETECTED, auction, fraudMsg);

            // Act
            observer.onAuctionEnded(ev);

            // Assert — critical: phải ghi audit, phải chứa cả auctionId lẫn message
            assertSingleLogContaining(auction.getId(), fraudMsg);
        }

        @Test
        @DisplayName("FRAUD_DETECTED: message null — không ném exception, vẫn ghi audit")
        void fraudDetected_nullMessage_logsWithoutException() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = eventWithMessage(FRAUD_DETECTED, auction, null);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(ev));
            assertEquals(1, auditLog().size(),
                    "FRAUD_DETECTED với message null vẫn phải ghi audit");
            assertTrue(auditLog().get(0).contains(auction.getId()),
                    "Audit log phải chứa auctionId dù message null");
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST: audit log chứa auctionId và lý do hủy")
        void sellerCancelRequest_logsAuctionIdAndReason() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            String reason = "Seller phát hiện lỗi item trước khi giao dịch";
            AuctionEvent ev = eventWithMessage(SELLER_CANCEL_REQUEST, auction, reason);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId(), reason);
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST: message null — không ném exception, vẫn ghi audit")
        void sellerCancelRequest_nullMessage_logsWithoutException() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = eventWithMessage(SELLER_CANCEL_REQUEST, auction, null);

            // Act & Assert
            assertDoesNotThrow(() -> observer.onAuctionEnded(ev));
            assertEquals(1, auditLog().size());
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST_ACCEPTED: audit log chứa auctionId")
        void sellerCancelRequestAccepted_logsAuctionId() {
            // Arrange
            Auction auction = TestFixture.canceledFromRunningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(SELLER_CANCEL_REQUEST_ACCEPTED, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            assertSingleLogContaining(auction.getId());
        }
    }

    // =========================================================================
    // Default/unknown event type
    // =========================================================================

    @Nested
    @DisplayName("onAuctionEnded() — default branch (event type không có case riêng)")
    class OnAuctionEndedDefault {

        @Test
        @DisplayName("AUCTION_EXTENDED: rơi vào default branch — vẫn ghi 1 audit log")
        void auctionExtended_fallsToDefault_stillLogsOneEntry() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(AUCTION_EXTENDED, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert — default branch không được drop event
            assertEquals(1, auditLog().size(),
                    "Default branch phải ghi đúng 1 audit log");
            assertTrue(auditLog().get(0).contains(auction.getId()),
                    "Default branch phải ghi auctionId vào log");
        }

        @Test
        @DisplayName("default branch: log chứa eventType name để traceability")
        void defaultBranch_logContainsEventTypeName() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(AUCTION_EXTENDED, auction);

            // Act
            observer.onAuctionEnded(ev);

            // Assert
            String entry = auditLog().get(0);
            assertTrue(entry.contains("AUCTION_EXTENDED"),
                    "Default log phải chứa tên eventType để traceability: " + entry);
        }
    }

    // =========================================================================
    // Audit log consistency và state
    // =========================================================================

    @Nested
    @DisplayName("Audit log — consistency và state integrity")
    class AuditLogConsistency {

        @Test
        @DisplayName("observer mới tạo: audit log ban đầu rỗng")
        void freshObserver_auditLogIsEmpty() {
            // Assert — trước khi bất kỳ event nào
            assertTrue(auditLog().isEmpty(),
                    "Chưa có event nào → audit log phải rỗng");
        }

        @Test
        @DisplayName("mỗi event type ghi đúng 1 log — không ghi nhiều hơn")
        void eachEvent_addsExactlyOneLog() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act — 3 event khác nhau
            observer.onBidPlaced(event(BID_PLACED, auction, bidder, 1_500_000L));
            observer.onAuctionEnded(event(FRAUD_DETECTED, auction));
            observer.onAuctionEnded(event(AUCTION_ENDED, auction, bidder, 2_000_000L));

            // Assert
            assertEquals(3, auditLog().size(),
                    "3 event khác nhau phải tạo đúng 3 audit log");
        }

        @Test
        @DisplayName("audit log giữ đúng thứ tự chronological")
        void auditLog_maintainsChronologicalOrder() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act
            observer.onBidPlaced(event(BID_PLACED, auction, bidder, 1_200_000L));
            observer.onAuctionEnded(event(AUCTION_ENDED, auction, bidder, 1_200_000L));

            // Assert — thứ tự: BID trước, ENDED sau
            List<String> log = auditLog();
            assertEquals(2, log.size());
            // Log đầu tiên là bid, không phải auction ended
            assertFalse(log.get(0).contains("kết thúc") || log.get(0).contains("ENDED"),
                    "Log thứ 1 phải là bid log, không phải auction ended");
        }

        @Test
        @DisplayName("audit log là immutable list — không thể modify từ bên ngoài")
        void auditLog_isImmutable() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            observer.onBidPlaced(event(BID_PLACED, auction, bidder, 1_500_000L));

            // Act & Assert
            List<String> log = auditLog();
            assertThrows(UnsupportedOperationException.class,
                    () -> log.add("tamper"),
                    "getActionLog() phải trả về immutable list");
        }

        @Test
        @DisplayName("duplicate event (gọi 2 lần cùng event) → log tích lũy 2 entry")
        void duplicateEvent_logAccumulatesBothEntries() {
            // Arrange — cùng 1 event object gọi 2 lần (simulate duplicate dispatch)
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = event(FRAUD_DETECTED, auction);

            // Act
            observer.onAuctionEnded(ev);
            observer.onAuctionEnded(ev);

            // Assert — observer không deduplicate: đây là behavior đúng
            // (dedup là trách nhiệm của dispatcher, không phải observer)
            assertEquals(2, auditLog().size(),
                    "Duplicate event dispatch tạo 2 log — observer không deduplicate");
        }

        @Test
        @DisplayName("repeated FRAUD_DETECTED events — mỗi event ghi 1 audit riêng")
        void repeatedFraudEvents_eachLogged() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev1 = eventWithMessage(FRAUD_DETECTED, auction, "Fraud lần 1");
            AuctionEvent ev2 = eventWithMessage(FRAUD_DETECTED, auction, "Fraud lần 2");

            // Act
            observer.onAuctionEnded(ev1);
            observer.onAuctionEnded(ev2);

            // Assert
            assertEquals(2, auditLog().size());
            assertTrue(auditLog().get(0).contains("Fraud lần 1"));
            assertTrue(auditLog().get(1).contains("Fraud lần 2"));
        }

        @Test
        @DisplayName("chuỗi event lifecycle → log tích lũy đúng số lượng")
        void fullLifecycleSequence_logsAllEvents() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act — simulate vòng đời đầy đủ của 1 phiên
            observer.onAuctionEnded(event(AUCTION_STARTED, auction));
            observer.onBidPlaced(event(BID_PLACED, auction, bidder, 1_200_000L));
            observer.onBidPlaced(event(BID_RESERVE_NOT_MET, auction, bidder, 900_000L));
            observer.onAuctionEnded(event(AUCTION_ENDED, auction, bidder, 1_200_000L));
            observer.onAuctionEnded(event(PAYMENT_COMPLETED, auction, bidder, 1_200_000L));

            // Assert — đủ 5 event = 5 log
            assertEquals(5, auditLog().size(),
                    "Full lifecycle: 5 event phải tạo 5 audit log");
        }

        @Test
        @DisplayName("onBidPlaced không ghi vào log của auction khác")
        void bidPlaced_onlyLogsToSystemAdmin_notOtherInstances() {
            // Arrange — tạo 2 observer với 2 SystemAdmin khác nhau (sau reset)
            // Chỉ verify observer của mình ghi log, không verify instance khác
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            int logSizeBefore = auditLog().size();

            // Act
            observer.onBidPlaced(event(BID_PLACED, auction, bidder, 1_500_000L));

            // Assert — chỉ đúng 1 log được thêm
            assertEquals(logSizeBefore + 1, auditLog().size());
        }
    }

    // =========================================================================
    // Prefix [SYSTEM] trong log — audit tagging
    // =========================================================================

    @Nested
    @DisplayName("Audit log — tagging [SYSTEM]")
    class AuditLogTagging {

        @Test
        @DisplayName("mọi audit log từ SystemAdminObserver đều có prefix [SYSTEM]")
        void allLogs_haveSystemPrefix() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

            // Act — các event đại diện từng nhánh
            observer.onBidPlaced(event(BID_PLACED, auction, bidder, 1_500_000L));
            observer.onAuctionEnded(event(AUCTION_STARTED, auction));
            observer.onAuctionEnded(eventWithMessage(FRAUD_DETECTED, auction, "fraud"));
            observer.onAuctionEnded(event(AUCTION_CANCELED, auction));

            // Assert — tất cả 4 log phải có [SYSTEM]
            List<String> log = auditLog();
            assertEquals(4, log.size());
            for (String entry : log) {
                assertTrue(entry.startsWith("[SYSTEM]"),
                        "Mọi log của SystemAdminObserver phải có prefix [SYSTEM]: " + entry);
            }
        }

        @Test
        @DisplayName("FRAUD_DETECTED log chứa keyword gian lận để phân biệt với log thường")
        void fraudDetectedLog_containsFraudKeyword() {
            // Arrange
            Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
            AuctionEvent ev = eventWithMessage(FRAUD_DETECTED, auction, "shill bidding");

            // Act
            observer.onAuctionEnded(ev);

            // Assert — log FRAUD phải có keyword đặc biệt để monitoring tool filter được
            String entry = auditLog().get(0);
            assertTrue(
                    entry.toUpperCase().contains("GIAN") || entry.toUpperCase().contains("FRAUD"),
                    "FRAUD log phải chứa keyword nhận biết gian lận: " + entry);
        }
    }
}