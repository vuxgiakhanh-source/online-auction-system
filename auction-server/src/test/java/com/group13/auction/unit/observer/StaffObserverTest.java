package com.group13.auction.unit.observer;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.StaffObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StaffObserver")
class StaffObserverTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 10, 0);

    private final ByteArrayOutputStream outCaptor = new ByteArrayOutputStream();
    private PrintStream originalOut;

    private Admin staff;
    private NormalUser seller;
    private NormalUser bidder;
    private Auction auction;
    private StaffObserver observer;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        System.setOut(new PrintStream(outCaptor));

        staff = staffAdmin("staff01");
        seller = normalSeller("seller01");
        bidder = normalBidder("bidder01");
        auction = openAuction(seller);
        observer = new StaffObserver(staff);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTest {

        @Test
        @DisplayName("StaffObserver() chấp nhận Admin LEVEL_STAFF")
        void constructor_staffAdmin_succeeds() {
            assertThatCode(() -> new StaffObserver(staffAdmin("staff02")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("StaffObserver() với Admin LEVEL_MASTER thì ném IllegalArgumentException")
        void constructor_masterAdmin_throwsIllegalArgumentException() {
            Admin master = masterAdmin("master01");

            assertThatThrownBy(() -> new StaffObserver(master))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Staff Admin");
        }

        @Test
        @DisplayName("StaffObserver() với staff null thì ném NullPointerException")
        void constructor_nullStaff_throwsNullPointerException() {
            assertThatThrownBy(() -> new StaffObserver(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("moderation event")
    class ModerationEvent {

        @Test
        @DisplayName("AUCTION_CANCELED ghi đúng audit log cho Staff")
        void auctionCanceled_recordsStaffActionLog() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED, auction, null, 0L);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId());
        }

        @Test
        @DisplayName("AUCTION_NO_WINNER ghi đúng auctionId vào audit log")
        void auctionNoWinner_recordsAuctionId() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_NO_WINNER, auction, null, 0L);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId());
        }

        @Test
        @DisplayName("RESERVE_NOT_MET_CLOSED ghi highest bid amount")
        void reserveNotMetClosed_recordsHighestBidAmount() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED, auction, bidder, 1_200_000L);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId())
                    .contains("1200000");
        }

        @Test
        @DisplayName("FRAUD_DETECTED ghi message vào audit log")
        void fraudDetected_recordsMessage() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction,
                    null,
                    0L,
                    "fraud-message");

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId())
                    .contains("fraud-message");
        }

        @Test
        @DisplayName("FRAUD_DETECTED với message null vẫn ghi audit log hợp lệ")
        void fraudDetected_nullMessage_recordsLogWithoutNpe() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED, auction, null, 0L, null);

            assertThatCode(() -> observer.onAuctionEnded(event))
                    .doesNotThrowAnyException();

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId());
        }

        @Test
        @DisplayName("QUALITY_REPORT_APPROVED ghi audit log theo dõi dispute")
        void qualityReportApproved_recordsDisputeLog() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED, auction, bidder, 0L);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId());
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST ghi lý do từ message")
        void sellerCancelRequest_recordsReasonMessage() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
                    auction,
                    null,
                    0L,
                    "seller-cancel-reason");

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId())
                    .contains("seller-cancel-reason");
        }

        @Test
        @DisplayName("SELLER_CANCEL_REQUEST với message null vẫn ghi audit log")
        void sellerCancelRequest_nullMessage_recordsDefaultReason() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST, auction, null, 0L, null);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(1);
            assertThat(lastLog())
                    .contains(staff.getUsername())
                    .contains(auction.getId());
        }
    }

    @Nested
    @DisplayName("event type mismatch method")
    class EventTypeMismatchMethod {

        @ParameterizedTest(name = "onBidPlaced() nhận {0} thì không ghi audit log")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "AUCTION_CANCELED",
                "AUCTION_NO_WINNER",
                "RESERVE_NOT_MET_CLOSED",
                "FRAUD_DETECTED",
                "QUALITY_REPORT_APPROVED",
                "SELLER_CANCEL_REQUEST"
        })
        @DisplayName("onBidPlaced() với moderation event không có side effect")
        void onBidPlaced_withModerationEvent_doesNotWriteLog(AuctionEvent.AuctionEventType type) {
            AuctionEvent event = new AuctionEvent(type, auction, bidder, 1_000_000L, "message");

            observer.onBidPlaced(event);

            assertThat(staff.getActionLog()).isEmpty();
            assertThat(output()).isBlank();
        }

        @ParameterizedTest(name = "onAuctionEnded() nhận bid event {0} thì không ghi audit log")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "BID_PLACED",
                "BID_RESERVE_NOT_MET"
        })
        @DisplayName("onAuctionEnded() với bid event không có side effect")
        void onAuctionEnded_withBidEvent_doesNotWriteLog(AuctionEvent.AuctionEventType type) {
            AuctionEvent event = new AuctionEvent(type, auction, bidder, 1_000_000L);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).isEmpty();
            assertThat(output()).isBlank();
        }
    }

    @Nested
    @DisplayName("invalid event và no side effect")
    class InvalidEventAndNoSideEffect {

        @Test
        @DisplayName("onBidPlaced() với event null không ném và không ghi audit log")
        void onBidPlaced_nullEvent_doesNotThrowAndDoesNotWriteLog() {
            assertThatCode(() -> observer.onBidPlaced(null))
                    .doesNotThrowAnyException();

            assertThat(staff.getActionLog()).isEmpty();
            assertStaffStateUnchanged();
            assertThat(output()).isBlank();
        }

        @Test
        @DisplayName("onAuctionEnded() với event null thì ném NullPointerException và không có side effect")
        void onAuctionEnded_nullEvent_throwsAndDoesNotWriteLog() {
            assertThatThrownBy(() -> observer.onAuctionEnded(null))
                    .isInstanceOf(NullPointerException.class);

            assertThat(staff.getActionLog()).isEmpty();
            assertStaffStateUnchanged();
            assertThat(output()).isBlank();
        }

        @Test
        @DisplayName("handled event với auction null thì ném NullPointerException và không có side effect")
        void onAuctionEnded_nullAuctionForHandledEvent_throwsAndDoesNotWriteLog() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED, null, null, 0L);

            assertThatThrownBy(() -> observer.onAuctionEnded(event))
                    .isInstanceOf(NullPointerException.class);

            assertThat(staff.getActionLog()).isEmpty();
            assertStaffStateUnchanged();
            assertThat(output()).isBlank();
        }

        @ParameterizedTest(name = "{0} không phải moderation event của StaffObserver")
        @EnumSource(value = AuctionEvent.AuctionEventType.class, names = {
                "AUCTION_UPCOMING",
                "AUCTION_STARTED",
                "BID_PLACED",
                "BID_RESERVE_NOT_MET",
                "AUCTION_EXTENDED",
                "AUCTION_ENDED",
                "PAYMENT_COMPLETED",
                "SECOND_CHANCE_OFFERED",
                "SELLER_CANCEL_REQUEST_ACCEPTED"
        })
        @DisplayName("unhandled event type không ghi null log và không có side effect")
        void unhandledEventType_doesNotWriteLogAndHasNoSideEffect(AuctionEvent.AuctionEventType type) {
            Auction.AuctionStatus auctionStatusBefore = auction.getStatus();
            AuctionEvent event = new AuctionEvent(type, auction, bidder, 0L);

            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).isEmpty();
            assertThat(auction.getStatus()).isEqualTo(auctionStatusBefore);
            assertStaffStateUnchanged();
            assertThat(output()).isBlank();
        }
    }

    @Nested
    @DisplayName("duplicate, repeated notification và concurrency-like")
    class DuplicateRepeatedAndConcurrencyLike {

        @Test
        @DisplayName("duplicate AUCTION_CANCELED event ghi hai audit log")
        void duplicateAuctionCanceled_recordsTwoLogs() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED, auction, null, 0L);

            observer.onAuctionEnded(event);
            observer.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(2);
            assertThat(staff.getActionLog().get(0)).contains(auction.getId());
            assertThat(staff.getActionLog().get(1)).contains(auction.getId());
        }

        @Test
        @DisplayName("repeated moderation events giữ đúng thứ tự audit log")
        void repeatedModerationEvents_keepAuditOrder() {
            AuctionEvent fraud = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED, auction, null, 0L, "fraud-1");
            AuctionEvent report = new AuctionEvent(
                    AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED, auction, bidder, 0L);
            AuctionEvent cancel = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST, auction, null, 0L, "cancel-1");

            observer.onAuctionEnded(fraud);
            observer.onAuctionEnded(report);
            observer.onAuctionEnded(cancel);

            assertThat(staff.getActionLog()).hasSize(3);
            assertThat(staff.getActionLog().get(0)).contains("fraud-1");
            assertThat(staff.getActionLog().get(1)).contains(auction.getId());
            assertThat(staff.getActionLog().get(2)).contains("cancel-1");
        }

        @Test
        @DisplayName("hai StaffObserver cùng một Staff nhận cùng event thì audit log có hai bản ghi")
        void twoObserversSameStaff_sameEvent_recordsTwoAuditEntries() {
            StaffObserver observerA = new StaffObserver(staff);
            StaffObserver observerB = new StaffObserver(staff);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED, auction, null, 0L, "same-event");

            observerA.onAuctionEnded(event);
            observerB.onAuctionEnded(event);

            assertThat(staff.getActionLog()).hasSize(2);
            assertThat(staff.getActionLog().get(0)).contains("same-event");
            assertThat(staff.getActionLog().get(1)).contains("same-event");
        }

        @Test
        @DisplayName("hai StaffObserver khác Staff xử lý xen kẽ không làm lẫn audit log")
        void twoObserversDifferentStaff_interleavedEvents_keepAuditLogsIsolated() {
            Admin otherStaff = staffAdmin("staff02");
            StaffObserver otherObserver = new StaffObserver(otherStaff);
            AuctionEvent fraud = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED, auction, null, 0L, "fraud-main");
            AuctionEvent report = new AuctionEvent(
                    AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED, auction, bidder, 0L);

            observer.onAuctionEnded(fraud);
            otherObserver.onAuctionEnded(report);
            observer.onAuctionEnded(report);
            otherObserver.onAuctionEnded(fraud);

            assertThat(staff.getActionLog()).hasSize(2);
            assertThat(otherStaff.getActionLog()).hasSize(2);
            assertThat(staff.getActionLog().get(0)).contains("fraud-main");
            assertThat(staff.getActionLog().get(1)).contains(auction.getId());
            assertThat(otherStaff.getActionLog().get(0)).contains(auction.getId());
            assertThat(otherStaff.getActionLog().get(1)).contains("fraud-main");
        }
    }

    @Nested
    @DisplayName("immutability và state integrity")
    class ImmutabilityAndStateIntegrity {

        @Test
        @DisplayName("onAuctionEnded() không đổi role hoặc permission của Staff")
        void onAuctionEnded_doesNotChangeStaffRoleOrPermission() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED, auction, null, 0L, "fraud");

            observer.onAuctionEnded(event);

            assertThat(staff.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
            assertThat(staff.isStaff()).isTrue();
            assertThat(staff.isMaster()).isFalse();
            assertThat(staff.isSystem()).isFalse();
        }

        @Test
        @DisplayName("onAuctionEnded() không mutate AuctionEvent")
        void onAuctionEnded_doesNotMutateEvent() {
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    auction,
                    bidder,
                    123_000L,
                    "fraud-message");
            AuctionEvent.AuctionEventType typeBefore = event.getEventType();
            Auction auctionBefore = event.getAuction();
            NormalUser bidderBefore = event.getBidder();
            long bidAmountBefore = event.getBidAmount();
            String messageBefore = event.getMessage();

            observer.onAuctionEnded(event);

            assertThat(event.getEventType()).isEqualTo(typeBefore);
            assertThat(event.getAuction()).isSameAs(auctionBefore);
            assertThat(event.getBidder()).isSameAs(bidderBefore);
            assertThat(event.getBidAmount()).isEqualTo(bidAmountBefore);
            assertThat(event.getMessage()).isEqualTo(messageBefore);
        }

        @Test
        @DisplayName("onAuctionEnded() không mutate auction, seller hoặc bidder")
        void onAuctionEnded_doesNotMutateDomainObjects() {
            Auction.AuctionStatus auctionStatusBefore = auction.getStatus();
            User.AccountStatus sellerStatusBefore = seller.getAccountStatus();
            User.AccountStatus bidderStatusBefore = bidder.getAccountStatus();
            double sellerRatingBefore = seller.getRating();
            double bidderRatingBefore = bidder.getRating();
            long sellerBalanceBefore = seller.getBalance();
            long bidderBalanceBefore = bidder.getBalance();
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
                    auction,
                    bidder,
                    0L,
                    "cancel-request");

            observer.onAuctionEnded(event);

            assertThat(auction.getStatus()).isEqualTo(auctionStatusBefore);
            assertThat(seller.getAccountStatus()).isEqualTo(sellerStatusBefore);
            assertThat(bidder.getAccountStatus()).isEqualTo(bidderStatusBefore);
            assertThat(seller.getRating()).isEqualTo(sellerRatingBefore);
            assertThat(bidder.getRating()).isEqualTo(bidderRatingBefore);
            assertThat(seller.getBalance()).isEqualTo(sellerBalanceBefore);
            assertThat(bidder.getBalance()).isEqualTo(bidderBalanceBefore);
        }
    }

    private String output() {
        return outCaptor.toString();
    }

    private String lastLog() {
        return staff.getActionLog().get(staff.getActionLog().size() - 1);
    }

    private void assertStaffStateUnchanged() {
        assertThat(staff.getPrimaryRole()).isEqualTo(User.UserRole.ADMIN);
        assertThat(staff.getAccountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
        assertThat(staff.isStaff()).isTrue();
        assertThat(staff.isMaster()).isFalse();
        assertThat(staff.isSystem()).isFalse();
    }

    private static Admin staffAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                username,
                User.hashPassword("adminPass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                5.0,
                Admin.LEVEL_STAFF,
                null);
    }

    private static Admin masterAdmin(String username) {
        return Admin.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                username,
                User.hashPassword("adminPass1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                5.0,
                Admin.LEVEL_MASTER,
                null);
    }

    private static NormalUser normalSeller(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                10_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false,
                false,
                null);
    }

    private static NormalUser normalBidder(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                10_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    private static Auction openAuction(NormalUser seller) {
        Art item = Art.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                "Tranh kiem thu",
                "Mo ta kiem thu",
                1_000_000L,
                seller,
                "Hoa si",
                2020,
                "Son dau");
        return Auction.create(item, NOW.minusHours(1), NOW.plusHours(1), 2_000_000L);
    }
}
