package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.AuctionLockRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuctionLockRegistry")
class AuctionLockRegistryTest {

    private AuctionLockRegistry registry;

    @BeforeEach
    void setUp() {
        registry = AuctionLockRegistry.getInstance();
        registry.clearAll();
    }

    @Test
    void getInstance_returnsSameSingleton() {
        assertSame(AuctionLockRegistry.getInstance(), AuctionLockRegistry.getInstance());
    }

    @Test
    void getLock_sameIdSameInstance_differentIdsIsolated() {
        String id1 = "auction-" + UUID.randomUUID();
        String id2 = "auction-" + UUID.randomUUID();
        ReentrantLock lock1a = registry.getLock(id1);
        ReentrantLock lock1b = registry.getLock(id1);
        ReentrantLock lock2 = registry.getLock(id2);

        assertNotNull(lock1a);
        assertSame(lock1a, lock1b);
        assertNotSame(lock1a, lock2);
    }

    @Test
    void getLock_canLockAndUnlock() {
        ReentrantLock lock = registry.getLock("auction-lock-use");
        assertFalse(lock.isLocked());
        lock.lock();
        assertTrue(lock.isLocked());
        lock.unlock();
        assertFalse(lock.isLocked());
    }

    @Test
    void release_removesEntryAndAllowsNewLockInstance() {
        String id = "auction-release";
        ReentrantLock before = registry.getLock(id);
        assertEquals(1, registry.size());
        registry.release(id);
        assertEquals(0, registry.size());
        ReentrantLock after = registry.getLock(id);
        assertNotSame(before, after);
    }

    @Test
    void release_unknownId_idempotent() {
        assertDoesNotThrow(() -> registry.release("never-registered"));
        assertEquals(0, registry.size());
    }

    @Test
    void size_tracksActiveLocks() {
        assertEquals(0, registry.size());
        registry.getLock("a");
        registry.getLock("b");
        assertEquals(2, registry.size());
        registry.release("a");
        assertEquals(1, registry.size());
    }

    @Test
    void getLock_nullId_throws() {
        assertThrows(NullPointerException.class, () -> registry.getLock(null));
    }

    @Test
    void release_nullId_throws() {
        assertThrows(NullPointerException.class, () -> registry.release(null));
    }
}
