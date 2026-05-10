package com.group13.auction.manager;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuctionManager.
 *
 * Strategy:
 *  - AuctionManager is a Singleton with private constructor and hardcoded DAO fields.
 *  - We inject mocked DAOs via reflection, then clear all in-memory collections
 *    before each test to guarantee full isolation.
 *  - Domain objects (NormalUser, Art, Auction) are created using their public
 *    reconstitute() factory methods — NormalUser.create() and Art.create() are
 *    protected and cannot be called from outside the model package.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionManager — unit tests")
class AuctionManagerTest {

    // ---------------------------------------------------------------
    // Mocked external dependencies
    // ---------------------------------------------------------------

    @Mock private AuctionDAO auctionDAO;
    @Mock private UserDAO    userDAO;

    private AuctionManager manager;

    // ---------------------------------------------------------------
    // Setup: inject mocks + reset singleton state before every test
    // ---------------------------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        manager = AuctionManager.getInstance();
        injectField("auctionDAO", auctionDAO);
        injectField("userDAO",    userDAO);
        clearMap("allAuctions");
        clearMap("allUsers");
        clearList("globalObservers");
        clearList("staffObservers");
    }

    private void injectField(String name, Object value) throws Exception {
        Field f = AuctionManager.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(manager, value);
    }

    private void clearMap(String name) throws Exception {
        Field f = AuctionManager.class.getDeclaredField(name);
        f.setAccessible(true);
        ((Map<?, ?>) f.get(manager)).clear();
    }

    @SuppressWarnings("unchecked")
    private void clearList(String name) throws Exception {
        Field f = AuctionManager.class.getDeclaredField(name);
        f.setAccessible(true);
        ((List<?>) f.get(manager)).clear();
    }

    // ---------------------------------------------------------------
    // Test-data builders (public reconstitute() APIs only)
    // ---------------------------------------------------------------

    /**
     * NormalUser.create() is protected — not accessible outside the model package.
     * NormalUser.reconstitute() is public static → correct entry-point for tests.
     * We build a user with SELLER role so it can own items.
     */
    private NormalUser buildSeller() {
        Set<User.UserRole> roles = EnumSet.of(User.UserRole.SELLER, User.UserRole.BIDDER);
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                "sellerUser",
                "hashedpassword",
                "seller@test.com",
                User.AccountStatus.ACTIVE,
                3.5,
                50_000L,
                0L,
                roles,
                false,
                false,
                null);
    }

    /**
     * NormalUser with BIDDER role only.
     */
    private NormalUser buildBidder() {
        Set<User.UserRole> roles = EnumSet.of(User.UserRole.BIDDER);
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                "bidderUser",
                "hashedpassword",
                "bidder@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                20_000L,
                0L,
                roles,
                false,
                false,
                null);
    }

    /**
     * Art.create() is protected — not accessible outside the item package.
     * Art.reconstitute() is public static → correct entry-point for tests.
     */
    private Item buildItem(NormalUser seller) {
        return Art.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                "Mona Lisa",
                "Oil on panel",
                1_000L,
                seller,
                "Leonardo da Vinci",
                1503,
                "Oil");
    }

    /** Auction in OPEN state. */
    private Auction buildOpenAuction() {
        NormalUser seller = buildSeller();
        Item item = buildItem(seller);
        return Auction.create(
                item,
                LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusHours(2),
                5_000L);
    }

    /** Auction in RUNNING state. */
    private Auction buildRunningAuction() {
        Auction a = buildOpenAuction();
        a.transitionToRunning();
        return a;
    }

    /** Auction in FINISHED state (closed with winner). */
    private Auction buildFinishedAuction() {
        Auction a = buildRunningAuction();
        a.transitionToClose(true);
        return a;
    }

    /** Auction in CANCELED state. */
    private Auction buildCanceledAuction() {
        Auction a = buildOpenAuction();
        a.transitionToCancel();
        return a;
    }

    // ---------------------------------------------------------------
    // Singleton contract
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Singleton contract")
    class SingletonTests {

        @Test
        @DisplayName("getInstance() always returns the same instance")
        void getInstance_alwaysReturnsSameInstance() {
            AuctionManager first  = AuctionManager.getInstance();
            AuctionManager second = AuctionManager.getInstance();

            assertThat(first).isSameAs(second);
        }
    }

    // ---------------------------------------------------------------
    // registerAuction
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("registerAuction()")
    class RegisterAuctionTests {

        @Test
        @DisplayName("valid auction — stored and findable by id")
        void registerAuction_validAuction_storedInRegistry() {
            // Arrange
            Auction auction = buildOpenAuction();

            // Act
            manager.registerAuction(auction);

            // Assert
            assertThat(manager.findAuctionById(auction.getId())).isSameAs(auction);
        }

        @Test
        @DisplayName("valid auction — appears in getAllAuctions()")
        void registerAuction_validAuction_appearsInGetAllAuctions() {
            // Arrange
            Auction auction = buildOpenAuction();

            // Act
            manager.registerAuction(auction);

            // Assert
            assertThat(manager.getAllAuctions()).contains(auction);
        }

        @Test
        @DisplayName("null auction — throws IllegalArgumentException")
        void registerAuction_null_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> manager.registerAuction(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("duplicate id — second registration ignored, original retained")
        void registerAuction_duplicateId_originalRetained() {
            // Arrange
            Auction original = buildOpenAuction();
            manager.registerAuction(original);

            Auction duplicate = Auction.reconstitute(
                    original.getId(),
                    original.getCreatedAt(),
                    original.getUpdatedAt(),
                    original.getItem(),
                    original.getStartTime(),
                    original.getEndTime(),
                    original.getCurrentPrice(),
                    Auction.AuctionStatus.OPEN,
                    original.getReservePrice());

            // Act
            manager.registerAuction(duplicate);

            // Assert
            assertThat(manager.findAuctionById(original.getId())).isSameAs(original);
            assertThat(manager.getAllAuctions()).hasSize(1);
        }

        @Test
        @DisplayName("multiple distinct auctions — all stored")
        void registerAuction_multipleDistinct_allStored() {
            // Arrange
            Auction a1 = buildOpenAuction();
            Auction a2 = buildOpenAuction();
            Auction a3 = buildRunningAuction();

            // Act
            manager.registerAuction(a1);
            manager.registerAuction(a2);
            manager.registerAuction(a3);

            // Assert
            assertThat(manager.getAllAuctions()).containsExactlyInAnyOrder(a1, a2, a3);
        }
    }

    // ---------------------------------------------------------------
    // findAuctionById
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("findAuctionById()")
    class FindAuctionByIdTests {

        @Test
        @DisplayName("existing id — returns correct auction")
        void findAuctionById_existingId_returnsAuction() {
            // Arrange
            Auction auction = buildOpenAuction();
            manager.registerAuction(auction);

            // Act & Assert
            assertThat(manager.findAuctionById(auction.getId())).isSameAs(auction);
        }

        @Test
        @DisplayName("unknown id — returns null")
        void findAuctionById_unknownId_returnsNull() {
            assertThat(manager.findAuctionById("not-a-real-id")).isNull();
        }

        @Test
        @DisplayName("null id — returns null without exception")
        void findAuctionById_nullId_returnsNull() {
            assertThat(manager.findAuctionById(null)).isNull();
        }
    }

    // ---------------------------------------------------------------
    // getRunningAuctions
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getRunningAuctions()")
    class GetRunningAuctionsTests {

        @Test
        @DisplayName("mixed statuses — only RUNNING auctions returned")
        void getRunningAuctions_mixedStatuses_returnsOnlyRunning() {
            // Arrange
            Auction open     = buildOpenAuction();
            Auction running  = buildRunningAuction();
            Auction finished = buildFinishedAuction();
            Auction canceled = buildCanceledAuction();
            manager.registerAuction(open);
            manager.registerAuction(running);
            manager.registerAuction(finished);
            manager.registerAuction(canceled);

            // Act
            List<Auction> result = manager.getRunningAuctions();

            // Assert
            assertThat(result)
                    .containsExactly(running)
                    .allMatch(a -> a.getStatus() == Auction.AuctionStatus.RUNNING);
        }

        @Test
        @DisplayName("no RUNNING auctions — returns empty list")
        void getRunningAuctions_noneRunning_returnsEmptyList() {
            // Arrange
            manager.registerAuction(buildOpenAuction());

            // Act & Assert
            assertThat(manager.getRunningAuctions()).isEmpty();
        }

        @Test
        @DisplayName("returned list is unmodifiable")
        void getRunningAuctions_returnedList_isUnmodifiable() {
            // Arrange
            manager.registerAuction(buildRunningAuction());

            // Act
            List<Auction> result = manager.getRunningAuctions();

            // Assert
            assertThatThrownBy(() -> result.add(buildRunningAuction()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ---------------------------------------------------------------
    // getAuctionsByStatus
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getAuctionsByStatus()")
    class GetAuctionsByStatusTests {

        @Test
        @DisplayName("OPEN status — returns only OPEN auctions")
        void getAuctionsByStatus_open_returnsOpenOnly() {
            // Arrange
            Auction open    = buildOpenAuction();
            Auction running = buildRunningAuction();
            manager.registerAuction(open);
            manager.registerAuction(running);

            // Act & Assert
            assertThat(manager.getAuctionsByStatus(Auction.AuctionStatus.OPEN))
                    .containsExactly(open);
        }

        @Test
        @DisplayName("FINISHED status — returns only FINISHED auctions")
        void getAuctionsByStatus_finished_returnsFinishedOnly() {
            // Arrange
            Auction finished = buildFinishedAuction();
            Auction running  = buildRunningAuction();
            manager.registerAuction(finished);
            manager.registerAuction(running);

            // Act & Assert
            assertThat(manager.getAuctionsByStatus(Auction.AuctionStatus.FINISHED))
                    .containsExactly(finished);
        }

        @Test
        @DisplayName("CANCELED status — returns only CANCELED auctions")
        void getAuctionsByStatus_canceled_returnsCanceledOnly() {
            // Arrange
            Auction canceled = buildCanceledAuction();
            Auction open     = buildOpenAuction();
            manager.registerAuction(canceled);
            manager.registerAuction(open);

            // Act & Assert
            assertThat(manager.getAuctionsByStatus(Auction.AuctionStatus.CANCELED))
                    .containsExactly(canceled);
        }

        @Test
        @DisplayName("no auctions match given status — returns empty list")
        void getAuctionsByStatus_noMatch_returnsEmpty() {
            // Arrange
            manager.registerAuction(buildOpenAuction());

            // Act & Assert
            assertThat(manager.getAuctionsByStatus(Auction.AuctionStatus.FINISHED)).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // getAllAuctions
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getAllAuctions()")
    class GetAllAuctionsTests {

        @Test
        @DisplayName("empty registry — returns empty list")
        void getAllAuctions_emptyRegistry_returnsEmptyList() {
            assertThat(manager.getAllAuctions()).isEmpty();
        }

        @Test
        @DisplayName("returned list is unmodifiable")
        void getAllAuctions_returnedList_isUnmodifiable() {
            // Arrange
            manager.registerAuction(buildOpenAuction());

            // Act
            List<Auction> result = manager.getAllAuctions();

            // Assert
            assertThatThrownBy(() -> result.add(buildOpenAuction()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ---------------------------------------------------------------
    // User registry: addToUserList / registerUser / findUserByUsername
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("User registry management")
    class UserRegistryTests {

        @Test
        @DisplayName("addToUserList valid user — user appears in getAllUsers()")
        void addToUserList_validUser_storedInRegistry() {
            // Arrange
            NormalUser user = buildBidder();

            // Act
            manager.addToUserList(user);

            // Assert
            assertThat(manager.getAllUsers()).contains(user);
        }

        @Test
        @DisplayName("addToUserList null — silently ignored, registry stays empty")
        void addToUserList_null_silentlyIgnored() {
            // Act & Assert
            assertThatCode(() -> manager.addToUserList(null)).doesNotThrowAnyException();
            assertThat(manager.getAllUsers()).isEmpty();
        }

        @Test
        @DisplayName("addToUserList same user twice — stored only once (putIfAbsent)")
        void addToUserList_sameUserTwice_storedOnlyOnce() {
            // Arrange
            NormalUser user = buildBidder();

            // Act
            manager.addToUserList(user);
            manager.addToUserList(user);

            // Assert
            assertThat(manager.getAllUsers()).hasSize(1);
        }

        @Test
        @DisplayName("registerUser null — throws IllegalArgumentException, DAO not called")
        void registerUser_null_throwsAndNoDAOInteraction() {
            // Act & Assert
            assertThatThrownBy(() -> manager.registerUser(null))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(userDAO);
        }

        @Test
        @DisplayName("registerUser valid user — delegates to UserDAO.save()")
        void registerUser_validUser_callsUserDAOSave() {
            // Arrange
            NormalUser user = buildSeller();

            // Act
            manager.registerUser(user);

            // Assert
            verify(userDAO, times(1)).save(user);
        }

        @Test
        @DisplayName("registerUser valid user — user is findable afterwards")
        void registerUser_validUser_userStoredInRegistry() {
            // Arrange
            NormalUser user = buildSeller();

            // Act
            manager.registerUser(user);

            // Assert
            assertThat(manager.getAllUsers()).contains(user);
        }

        @Test
        @DisplayName("findUserByUsername — found in memory, DAO not queried")
        void findUserByUsername_inMemory_noDAOCall() {
            // Arrange
            NormalUser user = buildBidder();
            manager.addToUserList(user);

            // Act
            User found = manager.findUserByUsername(user.getUsername());

            // Assert
            assertThat(found).isSameAs(user);
            verifyNoInteractions(userDAO);
        }

        @Test
        @DisplayName("findUserByUsername — not in memory, falls back to UserDAO")
        void findUserByUsername_notInMemory_fallsBackToDAO() {
            // Arrange
            NormalUser dbUser = buildBidder();
            when(userDAO.findUserByUsername(dbUser.getUsername())).thenReturn(dbUser);

            // Act
            User found = manager.findUserByUsername(dbUser.getUsername());

            // Assert
            assertThat(found).isEqualTo(dbUser);
            verify(userDAO).findUserByUsername(dbUser.getUsername());
        }

        @Test
        @DisplayName("findUserByUsername — not in memory, not in DB, returns null")
        void findUserByUsername_notFound_returnsNull() {
            // Arrange
            when(userDAO.findUserByUsername("ghost_user")).thenReturn(null);

            // Act & Assert
            assertThat(manager.findUserByUsername("ghost_user")).isNull();
        }
    }

    // ---------------------------------------------------------------
    // loadDataFromDatabase
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("loadDataFromDatabase()")
    class LoadDataFromDatabaseTests {

        @Test
        @DisplayName("DAO returns auctions — all loaded into registry")
        void loadDataFromDatabase_withAuctions_populatesAuctionRegistry() {
            // Arrange
            Auction a1 = buildOpenAuction();
            Auction a2 = buildRunningAuction();
            when(auctionDAO.findAll()).thenReturn(List.of(a1, a2));
            when(userDAO.findAll()).thenReturn(Collections.emptyList());

            // Act
            manager.loadDataFromDatabase();

            // Assert
            assertThat(manager.getAllAuctions()).containsExactlyInAnyOrder(a1, a2);
        }

        @Test
        @DisplayName("DAO returns users — all loaded into registry")
        void loadDataFromDatabase_withUsers_populatesUserRegistry() {
            // Arrange
            NormalUser u = buildBidder();
            when(auctionDAO.findAll()).thenReturn(Collections.emptyList());
            when(userDAO.findAll()).thenReturn(List.of(u));

            // Act
            manager.loadDataFromDatabase();

            // Assert
            assertThat(manager.getAllUsers()).contains(u);
        }

        @Test
        @DisplayName("DAO returns null — no NPE, registries remain empty")
        void loadDataFromDatabase_daoReturnsNull_noException() {
            // Arrange
            when(auctionDAO.findAll()).thenReturn(null);
            when(userDAO.findAll()).thenReturn(null);

            // Act & Assert
            assertThatCode(() -> manager.loadDataFromDatabase()).doesNotThrowAnyException();
            assertThat(manager.getAllAuctions()).isEmpty();
            assertThat(manager.getAllUsers()).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // Global observer management
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Global observer management")
    class GlobalObserverTests {

        @Test
        @DisplayName("registered observer receives non-bid event via onAuctionEnded()")
        void addGlobalObserver_nonBidEvent_callsOnAuctionEnded() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);
            manager.addGlobalObserver(observer);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    buildRunningAuction(), null, 0L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verify(observer).onAuctionEnded(event);
            verify(observer, never()).onBidPlaced(any());
        }

        @Test
        @DisplayName("registered observer receives BID_PLACED event via onBidPlaced()")
        void addGlobalObserver_bidPlacedEvent_callsOnBidPlaced() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);
            manager.addGlobalObserver(observer);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    buildRunningAuction(), null, 2_000L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verify(observer).onBidPlaced(event);
            verify(observer, never()).onAuctionEnded(any());
        }

        @Test
        @DisplayName("registered observer receives BID_RESERVE_NOT_MET via onBidPlaced()")
        void addGlobalObserver_bidReserveNotMet_callsOnBidPlaced() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);
            manager.addGlobalObserver(observer);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET,
                    buildRunningAuction(), null, 1_000L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verify(observer).onBidPlaced(event);
            verify(observer, never()).onAuctionEnded(any());
        }

        @Test
        @DisplayName("duplicate observer — registered only once, notified exactly once")
        void addGlobalObserver_duplicate_notifiedOnlyOnce() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);
            manager.addGlobalObserver(observer);
            manager.addGlobalObserver(observer); // duplicate
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    buildFinishedAuction(), null, 0L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verify(observer, times(1)).onAuctionEnded(event);
        }

        @Test
        @DisplayName("null observer — silently ignored")
        void addGlobalObserver_null_silentlyIgnored() {
            assertThatCode(() -> manager.addGlobalObserver(null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("removed observer — not notified after removal")
        void removeGlobalObserver_removedObserver_notNotified() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);
            manager.addGlobalObserver(observer);
            manager.removeGlobalObserver(observer);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    buildCanceledAuction(), null, 0L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verifyNoInteractions(observer);
        }

        @Test
        @DisplayName("null event — no observer method called")
        void notifyGlobalObservers_nullEvent_noObserverCalled() {
            // Arrange
            AuctionObserver observer = mock(AuctionObserver.class);
            manager.addGlobalObserver(observer);

            // Act
            manager.notifyGlobalObservers(null);

            // Assert
            verifyNoInteractions(observer);
        }

        @Test
        @DisplayName("multiple observers — all receive the event")
        void notifyGlobalObservers_multipleObservers_allReceiveEvent() {
            // Arrange
            AuctionObserver obs1 = mock(AuctionObserver.class);
            AuctionObserver obs2 = mock(AuctionObserver.class);
            manager.addGlobalObserver(obs1);
            manager.addGlobalObserver(obs2);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    buildFinishedAuction(), null, 0L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verify(obs1).onAuctionEnded(event);
            verify(obs2).onAuctionEnded(event);
        }
    }

    // ---------------------------------------------------------------
    // Staff observer management
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Staff observer management")
    class StaffObserverTests {

        @Test
        @DisplayName("staff observer notified on AUCTION_CANCELED")
        void notifyStaffObservers_auctionCanceled_observerNotified() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    buildCanceledAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verify(staffObs).onAuctionEnded(event);
        }

        @Test
        @DisplayName("staff observer notified on FRAUD_DETECTED")
        void notifyStaffObservers_fraudDetected_observerNotified() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.FRAUD_DETECTED,
                    buildRunningAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verify(staffObs).onAuctionEnded(event);
        }

        @Test
        @DisplayName("staff observer notified on QUALITY_REPORT_APPROVED")
        void notifyStaffObservers_qualityReportApproved_observerNotified() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED,
                    buildRunningAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verify(staffObs).onAuctionEnded(event);
        }

        @Test
        @DisplayName("staff observer notified on SELLER_CANCEL_REQUEST")
        void notifyStaffObservers_sellerCancelRequest_observerNotified() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.SELLER_CANCEL_REQUEST,
                    buildRunningAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verify(staffObs).onAuctionEnded(event);
        }

        @Test
        @DisplayName("staff observer NOT notified on BID_PLACED (non-staff event)")
        void notifyStaffObservers_bidPlaced_observerNotCalled() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.BID_PLACED,
                    buildRunningAuction(), null, 500L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verifyNoInteractions(staffObs);
        }

        @Test
        @DisplayName("staff observer NOT notified on AUCTION_STARTED (non-staff event)")
        void notifyStaffObservers_auctionStarted_observerNotCalled() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_STARTED,
                    buildRunningAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verifyNoInteractions(staffObs);
        }

        @Test
        @DisplayName("staff observer NOT notified on AUCTION_ENDED (non-staff event)")
        void notifyStaffObservers_auctionEnded_observerNotCalled() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    buildFinishedAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verifyNoInteractions(staffObs);
        }

        @Test
        @DisplayName("duplicate staff observer — notified exactly once")
        void addStaffObserver_duplicate_notifiedOnlyOnce() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            manager.addStaffObserver(staffObs); // duplicate
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    buildCanceledAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verify(staffObs, times(1)).onAuctionEnded(event);
        }

        @Test
        @DisplayName("null event — no staff observer called")
        void notifyStaffObservers_nullEvent_noObserverCalled() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);

            // Act
            manager.notifyStaffObservers(null);

            // Assert
            verifyNoInteractions(staffObs);
        }

        @Test
        @DisplayName("null staff observer — silently ignored")
        void addStaffObserver_null_silentlyIgnored() {
            assertThatCode(() -> manager.addStaffObserver(null))
                    .doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------
    // Global vs Staff observer isolation
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Global vs Staff observer isolation")
    class ObserverIsolationTests {

        @Test
        @DisplayName("notifyStaffObservers does NOT reach global observers")
        void notifyStaffObservers_doesNotNotifyGlobalObservers() {
            // Arrange
            AuctionObserver globalObs = mock(AuctionObserver.class);
            manager.addGlobalObserver(globalObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_CANCELED,
                    buildCanceledAuction(), null, 0L);

            // Act
            manager.notifyStaffObservers(event);

            // Assert
            verifyNoInteractions(globalObs);
        }

        @Test
        @DisplayName("notifyGlobalObservers does NOT reach staff observers")
        void notifyGlobalObservers_doesNotNotifyStaffObservers() {
            // Arrange
            AuctionObserver staffObs = mock(AuctionObserver.class);
            manager.addStaffObserver(staffObs);
            AuctionEvent event = new AuctionEvent(
                    AuctionEvent.AuctionEventType.AUCTION_ENDED,
                    buildFinishedAuction(), null, 0L);

            // Act
            manager.notifyGlobalObservers(event);

            // Assert
            verifyNoInteractions(staffObs);
        }
    }

    // ---------------------------------------------------------------
    // Auction state consistency in registry
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Auction state consistency in registry")
    class AuctionStateConsistencyTests {

        @Test
        @DisplayName("registered OPEN auction status is OPEN in registry")
        void registerAuction_openAuction_statusIsOpen() {
            // Arrange
            Auction auction = buildOpenAuction();
            manager.registerAuction(auction);

            // Act & Assert
            assertThat(manager.findAuctionById(auction.getId()).getStatus())
                    .isEqualTo(Auction.AuctionStatus.OPEN);
        }

        @Test
        @DisplayName("state change on registered instance is reflected via findAuctionById() (same reference)")
        void stateChange_reflectedViaFind_sameReference() {
            // Arrange
            Auction auction = buildOpenAuction();
            manager.registerAuction(auction);

            // Act — mutate the same registered instance
            auction.transitionToRunning();

            // Assert
            assertThat(manager.findAuctionById(auction.getId()).getStatus())
                    .isEqualTo(Auction.AuctionStatus.RUNNING);
        }

        @Test
        @DisplayName("OPEN→RUNNING transition — auction appears in getRunningAuctions()")
        void openAuction_afterTransitionToRunning_appearsInRunning() {
            // Arrange
            Auction auction = buildOpenAuction();
            manager.registerAuction(auction);

            // Act
            auction.transitionToRunning();

            // Assert
            assertThat(manager.getRunningAuctions()).contains(auction);
        }

        @Test
        @DisplayName("RUNNING→FINISHED transition — auction leaves getRunningAuctions()")
        void runningAuction_afterClose_removedFromRunning() {
            // Arrange
            Auction auction = buildRunningAuction();
            manager.registerAuction(auction);

            // Act
            auction.transitionToClose(true);

            // Assert
            assertThat(manager.getRunningAuctions()).doesNotContain(auction);
        }

        @Test
        @DisplayName("OPEN→CANCELED transition — auction not in getRunningAuctions()")
        void openAuction_afterCancel_notInRunning() {
            // Arrange
            Auction auction = buildOpenAuction();
            manager.registerAuction(auction);

            // Act
            auction.transitionToCancel();

            // Assert
            assertThat(manager.getRunningAuctions()).doesNotContain(auction);
        }

        @Test
        @DisplayName("RUNNING→close(false) — status becomes CANCELED (no winner path)")
        void runningAuction_closeWithoutWinner_statusIsCanceled() {
            // Arrange
            Auction auction = buildRunningAuction();
            manager.registerAuction(auction);

            // Act
            auction.transitionToClose(false); // no winner → CANCELED

            // Assert
            assertThat(manager.findAuctionById(auction.getId()).getStatus())
                    .isEqualTo(Auction.AuctionStatus.CANCELED);
        }
    }
}