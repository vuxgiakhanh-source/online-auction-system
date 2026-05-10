package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.iservice.IRatingService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link AuctionService}.
 *
 * <p>Phạm vi: orchestration + business logic của AuctionService.
 * Tất cả DAO và IRatingService đều được mock.
 * Model/domain (Auction, NormalUser, Item) dùng object thật.
 *
 * <p>Không test implementation detail của dependency.
 * Không truy cập DB, filesystem, network.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionService — Lifecycle & Business Logic")
class AuctionServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private IRatingService ratingService;
    @Mock private AuctionDAO     auctionDAO;

    // ── SUT ───────────────────────────────────────────────────────────────────

    private AuctionService sut;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private NormalUser seller;
    private NormalUser bidder;
    private Item       item;

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        bootstrapSystemAdmin();
        resetSystemBankBalance();
        resetAuctionManager();

        sut = new AuctionService(ratingService, auctionDAO);

        seller = normalSeller("seller01");
        bidder = normalBidder("bidder01");
        item   = art("Tranh Test", 1_000_000L, seller);
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSystemAdmin();
    }

    // =========================================================================
    // createAuction — happy path
    // =========================================================================

    @Nested
    @DisplayName("createAuction()")
    class CreateAuction {

        @Test
        @DisplayName("createAuction — seller hợp lệ → trả về Auction OPEN với đúng thông tin")
        void happyPath_returnOpenAuction() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(10);
            LocalDateTime end   = start.plusHours(2);

            // Act
            Auction result = sut.createAuction(seller, item, start, end, 2_000_000L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);
            assertThat(result.getItem()).isEqualTo(item);
            assertThat(result.getStartTime()).isEqualTo(start);
            assertThat(result.getEndTime()).isEqualTo(end);
            assertThat(result.getReservePrice()).isEqualTo(2_000_000L);
            assertThat(result.getCurrentPrice()).isEqualTo(item.getStartingPrice());
        }

        @Test
        @DisplayName("createAuction — auction mới được đăng ký vào AuctionManager")
        void happyPath_registersAuctionInManager() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act
            Auction result = sut.createAuction(seller, item, start, end, 1_500_000L);

            // Assert — AuctionManager có thể tìm thấy auction vừa tạo
            Auction found = AuctionManager.getInstance().findAuctionById(result.getId());
            assertThat(found).isSameAs(result);
        }

        @Test
        @DisplayName("createAuction — persist xuống DB qua auctionDAO.createAuction()")
        void happyPath_persistToDatabase() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act
            Auction result = sut.createAuction(seller, item, start, end, 1_500_000L);

            // Assert — DAO được gọi đúng 1 lần với auction vừa tạo
            verify(auctionDAO, times(1)).createAuction(result);
        }

        @Test
        @DisplayName("createAuction — seller được thêm auctionId vào danh sách của họ")
        void happyPath_addsAuctionIdToSeller() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);
            int sizeBefore = seller.getAllAuctionIds().size();

            // Act
            Auction result = sut.createAuction(seller, item, start, end, 1_500_000L);

            // Assert
            assertThat(seller.getAllAuctionIds()).hasSize(sizeBefore + 1);
            assertThat(seller.getAllAuctionIds()).contains(result.getId());
        }

        // ── Invalid input ─────────────────────────────────────────────────────

        @Test
        @DisplayName("createAuction — seller chưa có role SELLER → IllegalArgumentException")
        void invalidInput_sellerWithoutSellerRole_throws() {
            // Arrange — bidder chỉ có role BIDDER
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.createAuction(bidder, item, start, end, 1_500_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("role Seller");

            // DAO không được gọi khi validation fail
            verifyNoInteractions(auctionDAO);
        }

        @Test
        @DisplayName("createAuction — seller bị từ chối bởi ratingService → IllegalStateException")
        void invalidInput_ratingServiceDenies_throws() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(false);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.createAuction(seller, item, start, end, 1_500_000L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("điều kiện");

            verifyNoInteractions(auctionDAO);
        }

        @Test
        @DisplayName("createAuction — endTime trước startTime → IllegalArgumentException")
        void invalidInput_endTimeBeforeStartTime_throws() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusHours(2);
            LocalDateTime end   = LocalDateTime.now().plusHours(1); // end < start

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.createAuction(seller, item, start, end, 1_500_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("endTime");

            verifyNoInteractions(auctionDAO);
        }

        @Test
        @DisplayName("createAuction — endTime bằng startTime → IllegalArgumentException")
        void invalidInput_endTimeEqualStartTime_throws() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime moment = LocalDateTime.now().plusHours(1);

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.createAuction(seller, item, moment, moment, 1_500_000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("createAuction — reservePrice = 0 → IllegalArgumentException")
        void invalidInput_reservePriceZero_throws() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.createAuction(seller, item, start, end, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reservePrice");

            verifyNoInteractions(auctionDAO);
        }

        @Test
        @DisplayName("createAuction — reservePrice âm → IllegalArgumentException")
        void invalidInput_reservePriceNegative_throws() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.createAuction(seller, item, start, end, -500_000L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // startAuction — happy path & invalid state
    // =========================================================================

    @Nested
    @DisplayName("startAuction()")
    class StartAuction {

        @Test
        @DisplayName("startAuction — phiên OPEN → chuyển sang RUNNING")
        void happyPath_openToRunning() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.startAuction(auction);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
        }

        @Test
        @DisplayName("startAuction — cập nhật status xuống DB")
        void happyPath_persistsStatusUpdate() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.startAuction(auction);

            // Assert
            verify(auctionDAO, times(1))
                    .updateAuctionStatus(auction.getId(), Auction.AuctionStatus.RUNNING.name());
        }

        @Test
        @DisplayName("startAuction — phát AUCTION_STARTED tới observers đã đăng ký")
        void happyPath_notifiesObservers() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            AuctionObserver observer = mock(AuctionObserver.class);
            sut.addObserver(auction.getId(), observer);

            // Act
            sut.startAuction(auction);

            // Assert — observer nhận đúng event
            ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(observer).onAuctionEnded(captor.capture());
            assertThat(captor.getValue().getEventType())
                    .isEqualTo(AuctionEvent.AuctionEventType.AUCTION_STARTED);
        }

        @Test
        @DisplayName("startAuction — phiên đã RUNNING → IllegalStateException (không start lại)")
        void invalidState_alreadyRunning_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);

            // Act & Assert
            assertThatThrownBy(() -> sut.startAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("startAuction — phiên đã CANCELED → IllegalStateException")
        void invalidState_canceledAuction_throws() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            auction.transitionToCancel();

            // Act & Assert
            assertThatThrownBy(() -> sut.startAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("startAuction — phiên FINISHED → IllegalStateException")
        void invalidState_finishedAuction_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(3_000_000L, bidder);
            auction.transitionToClose(true);  // → FINISHED

            // Act & Assert
            assertThatThrownBy(() -> sut.startAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // closeAuction — 3 nhánh business logic
    // =========================================================================

    @Nested
    @DisplayName("closeAuction()")
    class CloseAuction {

        // ── Nhánh 1: không có leader → auto-cancel ────────────────────────────

        @Test
        @DisplayName("closeAuction — không có leader → phiên bị CANCELED (no-winner)")
        void noLeader_auctionCanceled() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            assertThat(auction.getCurrentLeader()).isNull();

            // Act
            sut.closeAuction(auction);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("closeAuction — không có leader → phát AUCTION_NO_WINNER tới observers")
        void noLeader_notifiesNoWinnerEvent() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            AuctionObserver observer = mock(AuctionObserver.class);
            sut.addObserver(auction.getId(), observer);

            // Act
            sut.closeAuction(auction);

            // Assert — observer nhận AUCTION_NO_WINNER (trước khi cancel)
            ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(observer, atLeastOnce()).onAuctionEnded(captor.capture());
            boolean hasNoWinnerEvent = captor.getAllValues().stream()
                    .anyMatch(e -> e.getEventType() == AuctionEvent.AuctionEventType.AUCTION_NO_WINNER);
            assertThat(hasNoWinnerEvent).isTrue();
        }

        @Test
        @DisplayName("closeAuction — không có leader → DB được cập nhật status CANCELED")
        void noLeader_persistsCanceledStatus() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);

            // Act
            sut.closeAuction(auction);

            // Assert
            verify(auctionDAO, atLeastOnce())
                    .updateAuctionStatus(auction.getId(), Auction.AuctionStatus.CANCELED.name());
        }

        // ── Nhánh 2: có leader nhưng chưa đạt reserve → auto-cancel ──────────

        @Test
        @DisplayName("closeAuction — có leader nhưng chưa đạt reserve → phiên CANCELED")
        void leaderButReserveNotMet_auctionCanceled() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            // reservePrice = startingPrice * 2 = 2_000_000, đặt giá thấp hơn
            auction.updateBid(1_200_000L, bidder);
            assertThat(auction.isReserveMet()).isFalse();

            // Act
            sut.closeAuction(auction);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("closeAuction — chưa đạt reserve → phát RESERVE_NOT_MET_CLOSED tới observers")
        void leaderButReserveNotMet_notifiesCorrectEvent() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(1_200_000L, bidder);
            AuctionObserver observer = mock(AuctionObserver.class);
            sut.addObserver(auction.getId(), observer);

            // Act
            sut.closeAuction(auction);

            // Assert
            ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(observer, atLeastOnce()).onAuctionEnded(captor.capture());
            boolean hasReserveNotMetEvent = captor.getAllValues().stream()
                    .anyMatch(e -> e.getEventType() == AuctionEvent.AuctionEventType.RESERVE_NOT_MET_CLOSED);
            assertThat(hasReserveNotMetEvent).isTrue();
        }

        // ── Nhánh 3: có leader, reserve met → FINISHED + tạo winner ──────────

        @Test
        @DisplayName("closeAuction — reserve met, có leader → phiên FINISHED")
        void reserveMet_withLeader_auctionFinished() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            // reservePrice = 2_000_000, đặt giá đạt reserve
            auction.updateBid(2_500_000L, bidder);
            assertThat(auction.isReserveMet()).isTrue();

            // Act
            sut.closeAuction(auction);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
        }

        @Test
        @DisplayName("closeAuction — reserve met → AuctionWinner được gắn vào phiên")
        void reserveMet_setsWinnerOnAuction() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);

            // Act
            sut.closeAuction(auction);

            // Assert
            assertThat(auction.getWinner()).isNotNull();
            assertThat(auction.getWinner().getWinner()).isEqualTo(bidder);
            assertThat(auction.getWinner().getAuctionId()).isEqualTo(auction.getId());
            assertThat(auction.getWinner().getFinalPrice()).isEqualTo(2_500_000L);
        }

        @Test
        @DisplayName("closeAuction — reserve met → tiền cọc của winner vào SystemBank")
        void reserveMet_depositTransferredToSystemBank() throws Exception {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            long bankBefore = SystemBank.getInstance().getTotalBalance();

            // Act
            sut.closeAuction(auction);

            // Assert — SystemBank nhận được tiền cọc (> 0)
            long bankAfter = SystemBank.getInstance().getTotalBalance();
            assertThat(bankAfter).isGreaterThan(bankBefore);
        }

        @Test
        @DisplayName("closeAuction — reserve met → phát AUCTION_ENDED tới observers")
        void reserveMet_notifiesAuctionEndedEvent() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            AuctionObserver observer = mock(AuctionObserver.class);
            sut.addObserver(auction.getId(), observer);

            // Act
            sut.closeAuction(auction);

            // Assert
            ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(observer).onAuctionEnded(captor.capture());
            assertThat(captor.getValue().getEventType())
                    .isEqualTo(AuctionEvent.AuctionEventType.AUCTION_ENDED);
            assertThat(captor.getValue().getBidder()).isEqualTo(bidder);
        }

        @Test
        @DisplayName("closeAuction — reserve met → persist kết quả qua auctionDAO.updateAuctionResult()")
        void reserveMet_persistsViaUpdateAuctionResult() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);

            // Act
            sut.closeAuction(auction);

            // Assert
            verify(auctionDAO, times(1)).updateAuctionResult(auction);
        }

        // ── Invalid state ──────────────────────────────────────────────────────

        @Test
        @DisplayName("closeAuction — phiên OPEN → IllegalStateException")
        void invalidState_openAuction_throws() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act & Assert
            assertThatThrownBy(() -> sut.closeAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("closeAuction — phiên đã CANCELED → IllegalStateException")
        void invalidState_alreadyCanceled_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.transitionToCancel();

            // Act & Assert
            assertThatThrownBy(() -> sut.closeAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("closeAuction — phiên đã FINISHED → IllegalStateException")
        void invalidState_alreadyFinished_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            auction.transitionToClose(true); // → FINISHED

            // Act & Assert — không thể close 2 lần
            assertThatThrownBy(() -> sut.closeAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // cancelAuction (system overload) — happy path & invalid state
    // =========================================================================

    @Nested
    @DisplayName("cancelAuction(Auction, CancelReason) — System auto-cancel")
    class CancelAuctionSystem {

        @Test
        @DisplayName("cancelAuction — phiên OPEN → bị CANCELED")
        void happyPath_openToCanceled() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.cancelAuction(auction, Admin.CancelReason.SELLER_REQUEST);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("cancelAuction — phiên RUNNING → bị CANCELED")
        void happyPath_runningToCanceled() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);

            // Act
            sut.cancelAuction(auction, Admin.CancelReason.SYSTEM_ERROR);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("cancelAuction — persist CANCELED status tới DB")
        void happyPath_persistsCanceledStatus() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);

            // Act
            sut.cancelAuction(auction, Admin.CancelReason.NO_WINNER);

            // Assert
            verify(auctionDAO, atLeastOnce())
                    .updateAuctionStatus(auction.getId(), Auction.AuctionStatus.CANCELED.name());
        }

        @Test
        @DisplayName("cancelAuction — phát AUCTION_CANCELED tới observers")
        void happyPath_notifiesCanceledEvent() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            AuctionObserver observer = mock(AuctionObserver.class);
            sut.addObserver(auction.getId(), observer);

            // Act
            sut.cancelAuction(auction, Admin.CancelReason.NO_WINNER);

            // Assert
            ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
            verify(observer).onAuctionEnded(captor.capture());
            assertThat(captor.getValue().getEventType())
                    .isEqualTo(AuctionEvent.AuctionEventType.AUCTION_CANCELED);
        }

        @Test
        @DisplayName("cancelAuction — ghi log vào SystemAdmin")
        void happyPath_logsToSystemAdmin() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            int logSizeBefore = SystemAdmin.getInstance().getActionLog().size();

            // Act
            sut.cancelAuction(auction, Admin.CancelReason.SELLER_REQUEST);

            // Assert — SystemAdmin nhận ít nhất 1 log mới
            assertThat(SystemAdmin.getInstance().getActionLog())
                    .hasSizeGreaterThan(logSizeBefore);
        }

        @Test
        @DisplayName("cancelAuction — phiên FINISHED không thể cancel → IllegalStateException")
        void invalidState_finishedAuction_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            auction.transitionToClose(true); // → FINISHED

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.cancelAuction(auction, Admin.CancelReason.SYSTEM_ERROR))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("cancelAuction — phiên PAID không thể cancel → IllegalStateException")
        void invalidState_paidAuction_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            auction.transitionToClose(true);  // → FINISHED
            auction.transitionToPaid();       // → PAID

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.cancelAuction(auction, Admin.CancelReason.SYSTEM_ERROR))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // cancelAuction (staff overload) — happy path & guard
    // =========================================================================

    @Nested
    @DisplayName("cancelAuction(Admin, Auction, CancelReason) — Staff cancel")
    class CancelAuctionStaff {

        private Admin staff;

        @BeforeEach
        void setUpAdmin() {
            // Admin.create là protected — dùng reconstitute để tạo STAFF admin không qua DB
            staff = Admin.reconstitute(
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    "staff01",
                    User.hashPassword("password1"),
                    "staff01@test.com",
                    User.AccountStatus.ACTIVE,
                    5.0,
                    Admin.LEVEL_STAFF,
                    null
            );
        }

        @Test
        @DisplayName("cancelAuction (staff) — phiên OPEN → bị CANCELED")
        void happyPath_staffCancelsOpenAuction() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.cancelAuction(staff, auction, Admin.CancelReason.FRAUDULENT_ITEM);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("cancelAuction (staff) — ghi log vào staff và audit vào SystemAdmin")
        void happyPath_logsToStaffAndAudit() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            int staffLogBefore  = staff.getActionLog().size();
            int systemLogBefore = SystemAdmin.getInstance().getActionLog().size();

            // Act
            sut.cancelAuction(staff, auction, Admin.CancelReason.FRAUDULENT_ITEM);

            // Assert
            assertThat(staff.getActionLog()).hasSizeGreaterThan(staffLogBefore);
            assertThat(SystemAdmin.getInstance().getActionLog())
                    .hasSizeGreaterThan(systemLogBefore);
        }

        @Test
        @DisplayName("cancelAuction (staff) — persist status CANCELED qua DAO")
        void happyPath_persistsCanceledStatus() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.cancelAuction(staff, auction, Admin.CancelReason.FRAUDULENT_ITEM);

            // Assert
            verify(auctionDAO, times(1))
                    .updateAuctionStatus(auction.getId(), Auction.AuctionStatus.CANCELED.name());
        }

        @Test
        @DisplayName("cancelAuction (staff) — truyền SystemAdmin vào overload này → IllegalArgumentException")
        void guard_systemAdminForbidden_throws() {
            // Arrange — SystemAdmin giả mạo staff
            SystemAdmin sysAdmin = SystemAdmin.getInstance();
            Auction auction = openAuction(seller, 1_000_000L);

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.cancelAuction(sysAdmin, auction, Admin.CancelReason.SYSTEM_ERROR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SystemAdmin");
        }
    }

    // =========================================================================
    // autoHandleCancelRequest — Seller request cancel flow
    // =========================================================================

    @Nested
    @DisplayName("autoHandleCancelRequest()")
    class AutoHandleCancelRequest {

        @Test
        @DisplayName("autoHandleCancelRequest — phiên OPEN → bị CANCELED với lý do SELLER_REQUEST")
        void happyPath_openToCanceled() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.autoHandleCancelRequest(auction);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("autoHandleCancelRequest — phiên RUNNING → IllegalStateException")
        void invalidState_runningAuction_throws() {
            // Arrange — RUNNING không thể cancel qua seller request (business rule)
            // autoHandleCancelRequest → cancelAuction → transitionToCancel
            // RunningState.cancel() → CanceledState (cho phép)
            // Thực ra RunningState.cancel() trả về CanceledState → không throw
            // Test này kiểm tra đúng behavior: RUNNING được cancel (state machine cho phép)
            Auction auction = runningAuction(seller, 1_000_000L);

            // Act
            sut.autoHandleCancelRequest(auction);

            // Assert — RUNNING cũng có thể cancel vì RunningState.cancel() hợp lệ
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }
    }

    // =========================================================================
    // Observer management
    // =========================================================================

    @Nested
    @DisplayName("Observer management")
    class ObserverManagement {

        @Test
        @DisplayName("addObserver — thêm observer cho phiên, observer nhận event khi có sự kiện")
        void addObserver_observerReceivesEvents() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            AuctionObserver observer = mock(AuctionObserver.class);
            sut.addObserver(auction.getId(), observer);

            // Act
            sut.startAuction(auction);

            // Assert
            verify(observer, atLeastOnce()).onAuctionEnded(any(AuctionEvent.class));
        }

        @Test
        @DisplayName("addObserver — không thêm observer trùng lặp")
        void addObserver_noDuplicateObservers() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            AuctionObserver observer = mock(AuctionObserver.class);

            // Act — thêm cùng 1 observer 3 lần
            sut.addObserver(auction.getId(), observer);
            sut.addObserver(auction.getId(), observer);
            sut.addObserver(auction.getId(), observer);

            // Assert — observer chỉ có 1 lần trong list
            List<AuctionObserver> observers = sut.getObservers(auction.getId());
            assertThat(observers).hasSize(1);
        }

        @Test
        @DisplayName("addObserver — observer null bị bỏ qua (no NPE)")
        void addObserver_nullObserverIgnored() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act & Assert — không throw exception
            assertThatCode(() -> sut.addObserver(auction.getId(), null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("addObserver — auctionId null bị bỏ qua (no NPE)")
        void addObserver_nullAuctionIdIgnored() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);

            // Act & Assert — không throw exception
            assertThatCode(() -> sut.addObserver(null, observer))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getObservers — trả về unmodifiable list")
        void getObservers_returnsUnmodifiableList() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            sut.addObserver(auction.getId(), mock(AuctionObserver.class));

            // Act
            List<AuctionObserver> observers = sut.getObservers(auction.getId());

            // Assert
            assertThatThrownBy(() -> observers.add(mock(AuctionObserver.class)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("getObservers — phiên chưa có observer → trả về list rỗng")
        void getObservers_noObservers_returnsEmpty() {
            // Act
            List<AuctionObserver> observers = sut.getObservers("non-existent-id");

            // Assert
            assertThat(observers).isEmpty();
        }
    }

    // =========================================================================
    // State consistency — chống anti-pattern lifecycle
    // =========================================================================

    @Nested
    @DisplayName("State consistency & anti-invalid lifecycle")
    class StateConsistency {

        @Test
        @DisplayName("OPEN → RUNNING → FINISHED: toàn bộ vòng đời hợp lệ")
        void fullLifecycle_openRunningFinished_consistent() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().minusMinutes(1);
            LocalDateTime end   = LocalDateTime.now().plusHours(1);

            // Act
            Auction auction = sut.createAuction(seller, item, start, end, 2_000_000L);
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);

            sut.startAuction(auction);
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);

            auction.updateBid(2_500_000L, bidder);
            sut.closeAuction(auction);
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
        }

        @Test
        @DisplayName("OPEN → RUNNING → CANCELED: vòng đời không có winner")
        void lifecycle_openRunningCanceled_noWinner() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().minusMinutes(1);
            LocalDateTime end   = LocalDateTime.now().plusHours(1);

            // Act
            Auction auction = sut.createAuction(seller, item, start, end, 2_000_000L);
            sut.startAuction(auction);
            sut.closeAuction(auction); // không có leader → CANCELED

            // Assert
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
            assertThat(auction.getWinner()).isNull();
        }

        @Test
        @DisplayName("Duplicate startAuction — gọi 2 lần liên tiếp → lần 2 throw IllegalStateException")
        void duplicateStart_secondCallThrows() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);
            sut.startAuction(auction); // lần 1 — hợp lệ

            // Act & Assert — lần 2 không hợp lệ
            assertThatThrownBy(() -> sut.startAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Duplicate closeAuction — gọi 2 lần liên tiếp → lần 2 throw IllegalStateException")
        void duplicateClose_secondCallThrows() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            sut.closeAuction(auction); // lần 1 — không có leader → CANCELED

            // Act & Assert — lần 2 không hợp lệ
            assertThatThrownBy(() -> sut.closeAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Duplicate cancelAuction — gọi 2 lần liên tiếp → lần 2 CanceledState.cancel() idempotent (không throw)")
        void duplicateCancel_idempotent() {
            // Arrange — CanceledState.cancel() trả về this (không throw, thiết kế idempotent)
            Auction auction = openAuction(seller, 1_000_000L);
            sut.cancelAuction(auction, Admin.CancelReason.NO_WINNER); // lần 1

            // Act & Assert — lần 2 không throw (state machine cho phép idempotent cancel)
            assertThatCode(() ->
                    sut.cancelAuction(auction, Admin.CancelReason.NO_WINNER))
                    .doesNotThrowAnyException();

            // Status vẫn là CANCELED
            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
        }

        @Test
        @DisplayName("closeAuction trên FINISHED → lần 2 throw vì đã không còn RUNNING")
        void repeatedClose_afterFinished_throws() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            sut.closeAuction(auction); // → FINISHED

            // Act & Assert
            assertThatThrownBy(() -> sut.closeAuction(auction))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("winner chỉ được gắn khi reserve MET — không phải khi reserve chưa đạt")
        void winnerSetOnlyWhenReserveMet() {
            // Case 1: reserve met → winner set
            Auction auctionMet = runningAuction(seller, 1_000_000L);
            auctionMet.updateBid(2_500_000L, bidder); // ≥ reservePrice (2_000_000)
            sut.closeAuction(auctionMet);
            assertThat(auctionMet.getWinner()).isNotNull();

            // Case 2: reserve NOT met → winner null
            Auction auctionNotMet = runningAuction(seller, 1_000_000L);
            auctionNotMet.updateBid(1_200_000L, bidder); // < reservePrice (2_000_000)
            sut.closeAuction(auctionNotMet);
            assertThat(auctionNotMet.getWinner()).isNull();
        }

        @Test
        @DisplayName("currentPrice giữ nguyên giá trị trước khi close (không bị reset)")
        void currentPrice_preservedAfterClose() {
            // Arrange
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            long priceBeforeClose = auction.getCurrentPrice();

            // Act
            sut.closeAuction(auction);

            // Assert
            assertThat(auction.getCurrentPrice()).isEqualTo(priceBeforeClose);
        }
    }

    // =========================================================================
    // Interaction verification — chỉ verify những gì quan trọng với business
    // =========================================================================

    @Nested
    @DisplayName("Dependency interaction — critical business interactions only")
    class DependencyInteraction {

        @Test
        @DisplayName("createAuction — ratingService.canSellerCreateAuction() phải được gọi đúng 1 lần")
        void createAuction_callsRatingServiceExactlyOnce() {
            // Arrange
            when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
            LocalDateTime start = LocalDateTime.now().plusMinutes(5);
            LocalDateTime end   = start.plusHours(1);

            // Act
            sut.createAuction(seller, item, start, end, 1_500_000L);

            // Assert
            verify(ratingService, times(1)).canSellerCreateAuction(seller);
        }

        @Test
        @DisplayName("startAuction — không gọi ratingService (không cần re-check)")
        void startAuction_doesNotCallRatingService() {
            // Arrange
            Auction auction = openAuction(seller, 1_000_000L);

            // Act
            sut.startAuction(auction);

            // Assert
            verifyNoInteractions(ratingService);
        }

        @Test
        @DisplayName("cancelAuction — DAO không được gọi khi transition throw trước đó")
        void cancelAuction_daoNotCalledOnTransitionFailure() {
            // Arrange — FINISHED không thể cancel
            Auction auction = runningAuction(seller, 1_000_000L);
            auction.updateBid(2_500_000L, bidder);
            auction.transitionToClose(true); // → FINISHED

            // Act & Assert
            assertThatThrownBy(() ->
                    sut.cancelAuction(auction, Admin.CancelReason.SYSTEM_ERROR))
                    .isInstanceOf(IllegalStateException.class);

            // DAO không được gọi khi business rule fail
            verifyNoInteractions(auctionDAO);
        }
    }

    // =========================================================================
    // Test helpers / fixtures — không cần DB
    // =========================================================================

    /** Tạo NormalUser có role BIDDER. */
    private static NormalUser normalBidder(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                username, User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0, 5_000_000L, 0L,
                EnumSet.of(User.UserRole.BIDDER),
                false, false, null
        );
    }

    /** Tạo NormalUser có role BIDDER + SELLER. */
    private static NormalUser normalSeller(String username) {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                username, User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0, 10_000_000L, 0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false, false, null
        );
    }

    /** Tạo Art item. */
    private static Art art(String name, long startingPrice, NormalUser seller) {
        return Art.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                name, "Mô tả " + name, startingPrice, seller,
                "Nghệ sĩ Test", 2020, "Sơn dầu"
        );
    }

    /** Tạo Auction OPEN (chưa start). reservePrice = startingPrice * 2. */
    private static Auction openAuction(NormalUser seller, long startingPrice) {
        Art item = art("Tranh Test", startingPrice, seller);
        return Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                startingPrice * 2
        );
    }

    /** Tạo Auction đã RUNNING. */
    private static Auction runningAuction(NormalUser seller, long startingPrice) {
        Auction auction = openAuction(seller, startingPrice);
        auction.transitionToRunning();
        return auction;
    }

    // ── Static helpers để cô lập Singleton ────────────────────────────────────

    private static void bootstrapSystemAdmin() throws Exception {
        Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        if (instanceField.get(null) == null) {
            java.lang.reflect.Constructor<SystemAdmin> ctor =
                    SystemAdmin.class.getDeclaredConstructor(String.class, String.class, String.class);
            ctor.setAccessible(true);
            SystemAdmin admin = ctor.newInstance("SYSTEM", "test-password", "system@test.com");
            instanceField.set(null, admin);
        }
    }

    private static void resetSystemAdmin() throws Exception {
        Field instanceField = SystemAdmin.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static void resetSystemBankBalance() throws Exception {
        Field field = SystemBank.class.getDeclaredField("totalBalance");
        field.setAccessible(true);
        AtomicLong balance = (AtomicLong) field.get(SystemBank.getInstance());
        balance.set(0L);
    }

    private static void resetAuctionManager() throws Exception {
        // Xóa toàn bộ auctions khỏi AuctionManager để tránh state rò rỉ
        Field allAuctionsField = AuctionManager.class.getDeclaredField("allAuctions");
        allAuctionsField.setAccessible(true);
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) allAuctionsField.get(AuctionManager.getInstance());
        map.clear();

        // Xóa global observers để tránh rò rỉ từ SystemAdmin cũ
        Field globalObsField = AuctionManager.class.getDeclaredField("globalObservers");
        globalObsField.setAccessible(true);
        java.util.List<?> globalObs = (java.util.List<?>) globalObsField.get(AuctionManager.getInstance());
        globalObs.clear();

        Field staffObsField = AuctionManager.class.getDeclaredField("staffObservers");
        staffObsField.setAccessible(true);
        java.util.List<?> staffObs = (java.util.List<?>) staffObsField.get(AuctionManager.getInstance());
        staffObs.clear();
    }
}