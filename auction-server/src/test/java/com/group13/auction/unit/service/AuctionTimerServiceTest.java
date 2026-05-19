package com.group13.auction.unit.service;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.AuctionTimerService;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IScheduler;
import com.group13.auction.service.scheduler.TaskScheduler;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionTimerService")
class AuctionTimerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 10, 10, 0);

    @Mock
    private IAuctionService auctionService;
    @Mock
    private IPaymentService paymentService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private IScheduler scheduler;

    private AuctionTimerService sut;

    @BeforeEach
    void setUp() throws Exception {
        resetAuctionManager();
        clearLockRegistry();
        clearAutoBidRegistry();

        sut = AuctionTimerService.getInstance();
        inject("auctionService", auctionService);
        inject("paymentService", paymentService);
        inject("sessionManager", sessionManager);
        inject("scheduler", null);
        inject("running", false);
    }

    @AfterEach
    void tearDown() throws Exception {
        sut.stop();
        inject("auctionService", null);
        inject("paymentService", null);
        inject("sessionManager", null);
        inject("scheduler", null);
        inject("running", false);
        resetAuctionManager();
        clearLockRegistry();
        clearAutoBidRegistry();
    }

    @Nested
    @DisplayName("scheduling")
    class Scheduling {

        @Test
        @DisplayName("start creates TaskScheduler and schedules scan at 1s interval")
        void start_usesTaskScheduler() throws Exception {
            sut.start(auctionService, paymentService, sessionManager);

            assertThat(readFieldObject("scheduler")).isInstanceOf(TaskScheduler.class);
            assertThat(readBoolean("running")).isTrue();

            sut.stop();
            assertThat(readBoolean("running")).isFalse();
        }

        @Test
        @DisplayName("start while running does not replace scheduler")
        void start_alreadyRunning_skipsDuplicateScheduling() throws Exception {
            inject("running", true);
            Object before = readFieldObject("scheduler");

            sut.start(auctionService, paymentService, sessionManager);

            assertThat(readFieldObject("scheduler")).isSameAs(before);
        }

        @Test
        @DisplayName("stop shuts down active scheduler")
        void stop_runningScheduler_shutdownNow() throws Exception {
            inject("running", true);
            inject("scheduler", scheduler);

            sut.stop();

            verify(scheduler).shutdownNow();
            assertThat(readBoolean("running")).isFalse();
        }

        @Test
        @DisplayName("start with injected scheduler uses provided instance")
        void start_withInjectedScheduler_usesProvidedInstance() throws Exception {
            sut.start(auctionService, paymentService, sessionManager, scheduler);

            assertThat(readFieldObject("scheduler")).isSameAs(scheduler);
            assertThat(readBoolean("running")).isTrue();
            verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(1L), eq(TimeUnit.SECONDS));

            sut.stop();
        }
    }

    @Nested
    @DisplayName("expire pending winner payments")
    class ExpirePendingWinnerPayments {

        @Test
        @DisplayName("FINISHED + winner payment expired → expirePayment on payment service")
        void expiredWinner_callsExpirePayment() throws Exception {
            Auction auction = finishedAuctionExpiredWinner();
            register(auction);

            invokeExpirePendingWinnerPayments();

            verify(paymentService).expirePayment(auction);
        }

        @Test
        @DisplayName("FINISHED + winner still in 24h window → no expirePayment")
        void pendingWinner_notExpired_skipsExpirePayment() throws Exception {
            Auction auction = reconstitutedAuction(NOW.minusHours(2), NOW.minusHours(1), Auction.AuctionStatus.FINISHED);
            NormalUser w = normalBidder("winner-pending-pay");
            AuctionWinner aw = AuctionWinner.create(w, auction.getId(), 5_000_000L, 300_000L, false);
            auction.setWinner(aw);
            register(auction);

            invokeExpirePendingWinnerPayments();

            verify(paymentService, never()).expirePayment(any());
        }
    }

    @Nested
    @DisplayName("start pending auctions")
    class StartPendingAuctions {

        @Test
        @DisplayName("OPEN auction with startTime before now starts and broadcasts")
        void openAuction_startTimeBeforeNow_startsAndBroadcasts() throws Exception {
            Auction auction = openAuction(NOW.minusMinutes(1), NOW.plusHours(1));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToRunning();
                return null;
            }).when(auctionService).startAuction(auction);

            invokeStartPending(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
            verify(auctionService).startAuction(auction);
            verifyBroadcast(auction, PacketType.AUCTION_STARTED_UPDATE);
        }

        @Test
        @DisplayName("OPEN auction with startTime equal now starts")
        void openAuction_startTimeEqualNow_starts() throws Exception {
            Auction auction = openAuction(NOW, NOW.plusHours(1));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToRunning();
                return null;
            }).when(auctionService).startAuction(auction);

            invokeStartPending(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
            verify(auctionService).startAuction(auction);
        }

        @Test
        @DisplayName("OPEN auction with future startTime is ignored")
        void openAuction_startTimeAfterNow_ignored() throws Exception {
            Auction auction = openAuction(NOW.plusNanos(1), NOW.plusHours(1));
            register(auction);

            invokeStartPending(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);
            verify(auctionService, never()).startAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("OPEN auction with null startTime is ignored")
        void openAuction_nullStartTime_ignored() throws Exception {
            Auction auction = reconstitutedAuction(null, NOW.plusHours(1), Auction.AuctionStatus.OPEN);
            register(auction);

            invokeStartPending(NOW);

            verify(auctionService, never()).startAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("auction no longer OPEN after lock is ignored")
        void auctionStatusChangedBeforeStart_doubleCheckSkips() throws Exception {
            Auction auction = openAuction(NOW.minusMinutes(1), NOW.plusHours(1));
            auction.transitionToCancel();
            register(auction);

            invokeStartPending(NOW);

            verify(auctionService, never()).startAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("start exception does not stop processing next auction")
        void startException_continuesWithNextAuction() throws Exception {
            Auction failing = openAuction(NOW.minusMinutes(2), NOW.plusHours(1));
            Auction succeeding = openAuction(NOW.minusMinutes(1), NOW.plusHours(1));
            register(failing);
            register(succeeding);
            doThrow(new IllegalStateException("invalid transition"))
                    .when(auctionService).startAuction(failing);
            doAnswer(invocation -> {
                succeeding.transitionToRunning();
                return null;
            }).when(auctionService).startAuction(succeeding);

            invokeStartPending(NOW);

            assertThat(failing.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);
            assertThat(succeeding.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
            verify(auctionService).startAuction(failing);
            verify(auctionService).startAuction(succeeding);
            verifyBroadcast(succeeding, PacketType.AUCTION_STARTED_UPDATE);
        }
    }

    @Nested
    @DisplayName("close expired auctions")
    class CloseExpiredAuctions {

        @Test
        @DisplayName("RUNNING auction with endTime before now closes")
        void runningAuction_endTimeBeforeNow_closes() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusMinutes(1));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
            verify(auctionService).closeAuction(auction);
        }

        @Test
        @DisplayName("RUNNING auction with endTime equal now closes")
        void runningAuction_endTimeEqualNow_closes() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW);
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            verify(auctionService).closeAuction(auction);
        }

        @Test
        @DisplayName("RUNNING auction with future endTime is ignored")
        void runningAuction_endTimeAfterNow_ignored() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.plusNanos(1));
            register(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
            verify(auctionService, never()).closeAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("RUNNING auction with null endTime is ignored")
        void runningAuction_nullEndTime_ignored() throws Exception {
            Auction auction = reconstitutedAuction(NOW.minusHours(1), null, Auction.AuctionStatus.RUNNING);
            register(auction);

            invokeCloseExpired(NOW);

            verify(auctionService, never()).closeAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("expired auction finished by close service broadcasts ended update")
        void expiredAuction_finished_broadcastsEndedUpdate() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            NormalUser bidder = normalBidder("bidder-finished");
            auction.updateBid(2_500_000L, bidder);
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToClose(true);
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
            verifyBroadcast(auction, PacketType.AUCTION_ENDED_UPDATE);
            verify(paymentService, never()).refundDeposits(auction);
        }

        @Test
        @DisplayName("expired auction without leader broadcasts no-winner and refunds deposits")
        void expiredAuction_noLeader_broadcastsNoWinnerAndRefunds() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
            verifyBroadcast(auction, PacketType.AUCTION_NO_WINNER_UPDATE);
            verify(paymentService).refundDeposits(auction);
        }

        @Test
        @DisplayName("expired auction below reserve broadcasts reserve-not-met and refunds deposits")
        void expiredAuction_reserveNotMet_broadcastsReserveNotMetAndRefunds() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            auction.updateBid(1_200_000L, normalBidder("bidder-low"));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
            verifyBroadcast(auction, PacketType.AUCTION_RESERVE_NOT_MET_UPDATE);
            verify(paymentService).refundDeposits(auction);
        }

        @Test
        @DisplayName("refund exception does not prevent cancel broadcast")
        void refundException_stillBroadcastsCloseResult() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);
            doThrow(new RuntimeException("refund failed")).when(paymentService).refundDeposits(auction);

            invokeCloseExpired(NOW);

            verify(paymentService).refundDeposits(auction);
            verifyBroadcast(auction, PacketType.AUCTION_NO_WINNER_UPDATE);
        }

        @Test
        @DisplayName("close exception does not broadcast or refund")
        void closeException_noBroadcastNoRefund() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            register(auction);
            doThrow(new IllegalStateException("close failed"))
                    .when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
            verify(auctionService).closeAuction(auction);
            verifyNoInteractions(sessionManager);
            verify(paymentService, never()).refundDeposits(auction);
        }
    }

    @Nested
    @DisplayName("state consistency and cleanup")
    class StateConsistencyAndCleanup {

        @Test
        @DisplayName("auction extended before close is skipped by anti-sniping double-check")
        void antiSnipingExtensionBeforeLockClose_skipsClose() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            auction.extendEndTime(Duration.ofMinutes(5));
            register(auction);

            invokeCloseExpired(NOW);

            assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
            verify(auctionService, never()).closeAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("already canceled auction is ignored by close scan")
        void alreadyCanceledAuction_ignoredByCloseScan() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            auction.transitionToCancel();
            register(auction);

            invokeCloseExpired(NOW);

            verify(auctionService, never()).closeAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("already finished auction is ignored by close scan")
        void alreadyFinishedAuction_ignoredByCloseScan() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            auction.updateBid(2_500_000L, normalBidder("winner"));
            auction.transitionToClose(true);
            register(auction);

            invokeCloseExpired(NOW);

            verify(auctionService, never()).closeAuction(auction);
            verifyNoInteractions(sessionManager);
        }

        @Test
        @DisplayName("repeated close scan is safe after first close")
        void repeatedCloseScan_afterFirstClose_doesNotCloseAgain() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            register(auction);
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);
            invokeCloseExpired(NOW);

            verify(auctionService, times(1)).closeAuction(auction);
            verify(paymentService, times(1)).refundDeposits(auction);
            verify(sessionManager, times(1)).broadcastToAuction(eq(auction.getId()), any(Packet.class));
        }

        @Test
        @DisplayName("successful close clears auto-bid entries and releases lock")
        void successfulClose_cleansAutoBidAndLockRegistries() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            register(auction);
            AutoBidRegistry.getInstance().register(normalBidder("auto-1").getId(), auction.getId(), 3_000_000L);
            AutoBidRegistry.getInstance().register(normalBidder("auto-2").getId(), auction.getId(), 4_000_000L);
            AuctionLockRegistry.getInstance().getLock(auction.getId());
            doAnswer(invocation -> {
                auction.transitionToCancel();
                return null;
            }).when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(AutoBidRegistry.getInstance().getEntriesForAuction(auction.getId())).isEmpty();
            assertThat(AuctionLockRegistry.getInstance().size()).isZero();
        }

        @Test
        @DisplayName("failed close keeps lock registered for later retry")
        void failedClose_keepsLockForRetry() throws Exception {
            Auction auction = runningAuction(NOW.minusHours(1), NOW.minusSeconds(1));
            register(auction);
            doThrow(new RuntimeException("temporary failure"))
                    .when(auctionService).closeAuction(auction);

            invokeCloseExpired(NOW);

            assertThat(AuctionLockRegistry.getInstance().size()).isEqualTo(1);
        }
    }

    private void invokeStartPending(LocalDateTime now) throws Exception {
        Method method = AuctionTimerService.class.getDeclaredMethod("startPendingAuctions", LocalDateTime.class);
        method.setAccessible(true);
        method.invoke(sut, now);
    }

    private void invokeCloseExpired(LocalDateTime now) throws Exception {
        Method method = AuctionTimerService.class.getDeclaredMethod("closeExpiredAuctions", LocalDateTime.class);
        method.setAccessible(true);
        method.invoke(sut, now);
    }

    private void invokeExpirePendingWinnerPayments() throws Exception {
        Method method = AuctionTimerService.class.getDeclaredMethod("expirePendingWinnerPayments");
        method.setAccessible(true);
        method.invoke(sut);
    }

    private static Auction finishedAuctionExpiredWinner() {
        Auction auction = reconstitutedAuction(
                NOW.minusHours(48), NOW.minusHours(24), Auction.AuctionStatus.FINISHED);
        NormalUser w = normalBidder("winner-exp-" + UUID.randomUUID());
        auction.setWinner(TestFixture.expiredPendingWinner(w, auction.getId(), 5_000_000L, 300_000L));
        return auction;
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = AuctionTimerService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(sut, value);
    }

    private boolean readBoolean(String fieldName) throws Exception {
        Field field = AuctionTimerService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(sut);
    }

    private Object readFieldObject(String fieldName) throws Exception {
        Field field = AuctionTimerService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(sut);
    }

    private void verifyBroadcast(Auction auction, PacketType packetType) {
        ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(sessionManager).broadcastToAuction(eq(auction.getId()), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(packetType);
    }

    private static void register(Auction auction) {
        AuctionManager.getInstance().registerAuction(auction);
    }

    private static Auction openAuction(LocalDateTime startTime, LocalDateTime endTime) {
        NormalUser seller = normalSeller("seller-" + UUID.randomUUID());
        return Auction.create(art("Art", 1_000_000L, seller), startTime, endTime, 2_000_000L);
    }

    private static Auction runningAuction(LocalDateTime startTime, LocalDateTime endTime) {
        Auction auction = openAuction(startTime, endTime);
        auction.transitionToRunning();
        return auction;
    }

    private static Auction reconstitutedAuction(LocalDateTime startTime,
                                                LocalDateTime endTime,
                                                Auction.AuctionStatus status) {
        NormalUser seller = normalSeller("seller-" + UUID.randomUUID());
        return Auction.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                art("Art", 1_000_000L, seller),
                startTime,
                endTime,
                1_000_000L,
                status,
                2_000_000L
        );
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
                null
        );
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
                null
        );
    }

    private static Art art(String name, long startingPrice, NormalUser seller) {
        return Art.reconstitute(
                UUID.randomUUID().toString(),
                NOW.minusDays(1),
                NOW.minusDays(1),
                name,
                "Test art",
                startingPrice,
                seller,
                "Artist",
                2020,
                "Oil"
        );
    }

    private static void resetAuctionManager() throws Exception {
        Field allAuctionsField = AuctionManager.class.getDeclaredField("allAuctions");
        allAuctionsField.setAccessible(true);
        ((Map<?, ?>) allAuctionsField.get(AuctionManager.getInstance())).clear();

        Field allUsersField = AuctionManager.class.getDeclaredField("allUsers");
        allUsersField.setAccessible(true);
        ((Map<?, ?>) allUsersField.get(AuctionManager.getInstance())).clear();

        Field globalObserversField = AuctionManager.class.getDeclaredField("globalObservers");
        globalObserversField.setAccessible(true);
        ((java.util.List<?>) globalObserversField.get(AuctionManager.getInstance())).clear();

        Field staffObserversField = AuctionManager.class.getDeclaredField("staffObservers");
        staffObserversField.setAccessible(true);
        ((java.util.List<?>) staffObserversField.get(AuctionManager.getInstance())).clear();
    }

    private static void clearLockRegistry() throws Exception {
        Field locksField = AuctionLockRegistry.class.getDeclaredField("locks");
        locksField.setAccessible(true);
        ((Map<?, ?>) locksField.get(AuctionLockRegistry.getInstance())).clear();
    }

    private static void clearAutoBidRegistry() throws Exception {
        Field registryField = AutoBidRegistry.class.getDeclaredField("registry");
        registryField.setAccessible(true);
        ((Map<?, ?>) registryField.get(AutoBidRegistry.getInstance())).clear();
    }
}
