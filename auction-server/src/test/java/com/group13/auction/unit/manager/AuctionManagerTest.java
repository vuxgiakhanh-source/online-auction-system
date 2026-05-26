package com.group13.auction.unit.manager;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link AuctionManager} — registry in-memory.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionManager")
class AuctionManagerTest {

    @Mock private AuctionDAO auctionDAO;
    @Mock private UserDAO userDAO;

    private AuctionManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = AuctionManager.getInstance();
        inject("auctionDAO", auctionDAO);
        inject("userDAO", userDAO);
        clear("allAuctions");
        clear("allUsers");
        clearList("globalObservers");
        clearList("staffObservers");
    }

    @Test
    void singleton_sameInstance() {
        assertThat(AuctionManager.getInstance()).isSameAs(manager);
    }

    @Test
    void registerAuction_findById() {
        NormalUser seller = TestFixture.normalSeller("mgrSeller1");
        Auction auction = TestFixture.openAuction(seller, 1_000_000L);
        manager.registerAuction(auction);
        assertThat(manager.findAuctionById(auction.getId())).isSameAs(auction);
    }

    @Test
    void registerAuction_null_throws() {
        assertThatThrownBy(() -> manager.registerAuction(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRunningAuctions_onlyRunning() {
        NormalUser seller = TestFixture.normalSeller("mgrSeller2");
        Auction open = TestFixture.openAuction(seller, 1_000_000L);
        Auction running = TestFixture.runningAuction(seller, 2_000_000L);
        manager.registerAuction(open);
        manager.registerAuction(running);

        List<Auction> runningOnly = manager.getRunningAuctions();
        assertThat(runningOnly).containsExactly(running);
    }

    @Test
    void registerUser_delegatesToDao() {
        NormalUser user = TestFixture.normalBidder("mgrUser01");
        when(userDAO.findUserByUsername(user.getUsername())).thenReturn(user);
        manager.registerUser(user);
        verify(userDAO).save(user);
        assertThat(manager.findUserByUsername(user.getUsername())).isSameAs(user);
    }

    @Test
    void notifyGlobalObservers_dispatchesBidEvent() {
        AuctionObserver observer = mock(AuctionObserver.class);
        manager.addGlobalObserver(observer);
        NormalUser seller = TestFixture.normalSeller("mgrSeller3");
        Auction auction = TestFixture.runningAuction(seller, 1_000_000L);

        manager.notifyGlobalObservers(new com.group13.auction.observer.AuctionEvent(
                com.group13.auction.observer.AuctionEvent.AuctionEventType.BID_PLACED,
                auction, null, 1_000_000L));

        verify(observer).onBidPlaced(any());
    }

    private void inject(String name, Object value) throws Exception {
        Field f = AuctionManager.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(manager, value);
    }

    private void clear(String name) throws Exception {
        Field f = AuctionManager.class.getDeclaredField(name);
        f.setAccessible(true);
        ((Map<?, ?>) f.get(manager)).clear();
    }

    private void clearList(String name) throws Exception {
        Field f = AuctionManager.class.getDeclaredField(name);
        f.setAccessible(true);
        ((List<?>) f.get(manager)).clear();
    }
}
