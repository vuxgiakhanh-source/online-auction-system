package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.AutoBidRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidRegistry")
class AutoBidRegistryTest {

    private static final String USER = "user-a";
    private static final String AUCTION = "auction-1";

    private AutoBidRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = AutoBidRegistry.getInstance();
        clearRegistry();
    }

    @AfterEach
    void tearDown() throws Exception {
        clearRegistry();
    }

    @Test
    void register_thenGetAndHasActive() {
        registry.register(USER, AUCTION, 500_000L);
        assertTrue(registry.hasActiveBid(USER, AUCTION));
        assertEquals(500_000L, registry.get(USER, AUCTION).getMaxBid());
    }

    @Test
    void register_updatesMaxBid_keepsRegisteredAt() throws Exception {
        registry.register(USER, AUCTION, 500_000L);
        var firstAt = registry.get(USER, AUCTION).getRegisteredAt();
        registry.register(USER, AUCTION, 800_000L);
        assertEquals(800_000L, registry.get(USER, AUCTION).getMaxBid());
        assertEquals(firstAt, registry.get(USER, AUCTION).getRegisteredAt());
    }

    @Test
    void cancel_removesEntry() {
        registry.register(USER, AUCTION, 500_000L);
        assertTrue(registry.cancel(USER, AUCTION));
        assertFalse(registry.hasActiveBid(USER, AUCTION));
        assertFalse(registry.cancel(USER, AUCTION));
    }

    @Test
    void clearAuction_removesOnlyMatchingAuction() {
        registry.register(USER, AUCTION, 500_000L);
        registry.register(USER, "auction-2", 600_000L);
        registry.clearAuction(AUCTION);
        assertFalse(registry.hasActiveBid(USER, AUCTION));
        assertTrue(registry.hasActiveBid(USER, "auction-2"));
    }

    @Test
    void getEntriesForAuction_returnsSnapshot() {
        registry.register(USER, AUCTION, 500_000L);
        registry.register("user-b", AUCTION, 700_000L);
        assertEquals(2, registry.getEntriesForAuction(AUCTION).size());
    }

    @SuppressWarnings("unchecked")
    private void clearRegistry() throws Exception {
        Field field = AutoBidRegistry.class.getDeclaredField("registry");
        field.setAccessible(true);
        ConcurrentHashMap<?, ?> map = (ConcurrentHashMap<?, ?>) field.get(registry);
        map.clear();
        Field daoField = AutoBidRegistry.class.getDeclaredField("autoBidDAO");
        daoField.setAccessible(true);
        daoField.set(registry, null);
    }
}
