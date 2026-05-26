package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.AuctionLockRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link AuctionLockRegistry}.
 *
 * <p>Chiến lược kiểm tra:
 * <ul>
 *   <li>Object identity: cùng auctionId → cùng lock instance (==)</li>
 *   <li>Isolation: khác auctionId → khác lock instance</li>
 *   <li>Repeated retrieval: nhiều lần getLock cùng id → deterministic</li>
 *   <li>Release & recreate: sau release, getLock tạo lock mới</li>
 *   <li>Registry integrity: size() phản ánh đúng số lock đang tồn tại</li>
 *   <li>Edge case: null, empty string, whitespace-only, special char, very long id</li>
 *   <li>Lock usability: lock trả về có thể lock/unlock bình thường</li>
 *   <li>Concurrency contract: nhiều thread cùng getLock cùng id → cùng instance</li>
 * </ul>
 *
 * <p>FIRST compliant: Fast, Independent, Repeatable, Self-validating, Timely.
 * Không DB, không network, không filesystem.
 * Thread test chỉ kiểm tra contract (object identity) — không dùng sleep/timing.
 */
@DisplayName("AuctionLockRegistry")
class AuctionLockRegistryTest {

    private AuctionLockRegistry registry;

    /**
     * Lấy Singleton và dọn sạch toàn bộ lock trước mỗi test để đảm bảo isolation.
     *
     * <p>Dùng {@link AuctionLockRegistry#clearAll()} thay vì release từng id cố định
     * vì Singleton này được chia sẻ với tất cả test khác trong cùng JVM
     * (concurrency tests, load tests…). Các test đó có thể đã tạo lock với UUID
     * ngẫu nhiên mà không cleanup → {@code size()} khác 0 khi chạy tuần tự.
     */
    @BeforeEach
    void setUp() {
        registry = AuctionLockRegistry.getInstance();
        // Reset toàn bộ registry — an toàn vì không có lock nào đang bị giữ
        // trong khoảng giữa các test (JUnit chạy test tuần tự trong cùng thread).
        registry.clearAll();
    }

    // =========================================================================
    // Singleton contract
    // =========================================================================

    @Nested
    @DisplayName("Singleton: getInstance() luôn trả về cùng một instance")
    class SingletonContract {

        @Test
        @DisplayName("getInstance() gọi nhiều lần → cùng object reference")
        void getInstance_calledMultipleTimes_returnsSameInstance() {
            // Act
            AuctionLockRegistry first  = AuctionLockRegistry.getInstance();
            AuctionLockRegistry second = AuctionLockRegistry.getInstance();
            AuctionLockRegistry third  = AuctionLockRegistry.getInstance();

            // Assert
            assertSame(first, second, "getInstance() lần 1 và 2 phải trả về cùng instance");
            assertSame(second, third, "getInstance() lần 2 và 3 phải trả về cùng instance");
        }
    }

    // =========================================================================
    // getLock — object identity
    // =========================================================================

    @Nested
    @DisplayName("getLock: object identity và non-null")
    class GetLockIdentity {

        @Test
        @DisplayName("getLock với auctionId mới → trả về ReentrantLock non-null")
        void getLock_newId_returnsNonNull() {
            // Arrange
            String auctionId = "auction-001";

            // Act
            ReentrantLock lock = registry.getLock(auctionId);

            // Assert
            assertNotNull(lock);

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("getLock cùng auctionId hai lần → cùng object reference (==)")
        void getLock_sameId_returnsSameInstance() {
            // Arrange
            String auctionId = "auction-001";

            // Act
            ReentrantLock first  = registry.getLock(auctionId);
            ReentrantLock second = registry.getLock(auctionId);

            // Assert
            assertSame(first, second, "Cùng auctionId phải trả về cùng lock instance");

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("getLock cùng auctionId nhiều lần → luôn cùng object reference")
        void getLock_sameId_repeatedCalls_alwaysSameInstance() {
            // Arrange
            String auctionId = "auction-001";
            ReentrantLock baseline = registry.getLock(auctionId);

            // Act & Assert — gọi thêm 9 lần
            for (int i = 0; i < 9; i++) {
                ReentrantLock lock = registry.getLock(auctionId);
                assertSame(baseline, lock, "Lần gọi #" + (i + 2) + " phải trả về cùng instance");
            }

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("getLock hai auctionId khác nhau → hai lock instance khác nhau")
        void getLock_differentIds_returnsDifferentInstances() {
            // Arrange
            String idA = "auction-A";
            String idB = "auction-B";

            // Act
            ReentrantLock lockA = registry.getLock(idA);
            ReentrantLock lockB = registry.getLock(idB);

            // Assert
            assertNotSame(lockA, lockB, "Khác auctionId phải trả về lock instance khác nhau");

            // Cleanup
            registry.release(idA);
            registry.release(idB);
        }

        @Test
        @DisplayName("getLock nhiều auctionId khác nhau → tất cả unique instances")
        void getLock_multipleDistinctIds_allUniqueInstances() {
            // Arrange
            List<String> ids = List.of("auction-001", "auction-002", "auction-003");
            List<ReentrantLock> locks = new ArrayList<>();

            // Act
            for (String id : ids) {
                locks.add(registry.getLock(id));
            }

            // Assert — dùng Set để kiểm tra uniqueness theo identity
            Set<ReentrantLock> uniqueLocks = new HashSet<>();
            for (ReentrantLock lock : locks) {
                boolean added = uniqueLocks.add(lock);
                assertTrue(added, "Mỗi auctionId phải có lock instance duy nhất");
            }
            assertEquals(ids.size(), uniqueLocks.size());

            // Cleanup
            ids.forEach(registry::release);
        }
    }

    // =========================================================================
    // getLock — returned lock usability
    // =========================================================================

    @Nested
    @DisplayName("getLock: lock trả về có thể sử dụng bình thường")
    class LockUsability {

        @Test
        @DisplayName("lock trả về có thể lock() và unlock() không ném exception")
        void getLock_returnedLock_canBeLockAndUnlocked() {
            // Arrange
            String auctionId = "auction-001";
            ReentrantLock lock = registry.getLock(auctionId);

            // Act & Assert — không được ném bất kỳ exception nào
            assertDoesNotThrow(() -> {
                lock.lock();
                try {
                    // critical section (empty — chỉ test usability)
                } finally {
                    lock.unlock();
                }
            });

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("lock trả về ban đầu ở trạng thái unlocked (không ai giữ)")
        void getLock_freshLock_isNotLocked() {
            // Arrange
            String auctionId = "auction-002";

            // Act
            ReentrantLock lock = registry.getLock(auctionId);

            // Assert
            assertFalse(lock.isLocked(), "Lock mới tạo phải ở trạng thái unlocked");

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("tryLock() trên lock mới tạo → thành công (trả về true)")
        void getLock_freshLock_tryLockSucceeds() {
            // Arrange
            String auctionId = "auction-003";
            ReentrantLock lock = registry.getLock(auctionId);

            // Act
            boolean acquired = lock.tryLock();

            // Assert
            assertTrue(acquired, "tryLock() trên lock chưa ai giữ phải trả về true");

            // Cleanup — unlock trước khi release
            if (acquired) lock.unlock();
            registry.release(auctionId);
        }

        @Test
        @DisplayName("cùng lock instance sau nhiều lần getLock → trạng thái lock nhất quán")
        void getLock_sameInstance_lockStateConsistent() {
            // Arrange
            String auctionId = "auction-001";
            ReentrantLock lockRef = registry.getLock(auctionId);
            lockRef.lock(); // giữ lock

            // Act — lấy lại cùng instance
            ReentrantLock lockAgain = registry.getLock(auctionId);

            // Assert — cùng instance nên isLocked() phải true
            assertTrue(lockAgain.isLocked(),
                    "Cùng lock instance phải phản ánh trạng thái lock hiện tại");

            // Cleanup
            lockRef.unlock();
            registry.release(auctionId);
        }
    }

    // =========================================================================
    // release()
    // =========================================================================

    @Nested
    @DisplayName("release: xóa lock khỏi registry")
    class ReleaseContract {

        @Test
        @DisplayName("release() trên id tồn tại → size giảm đi 1")
        void release_existingId_decreasesSize() {
            // Arrange
            String auctionId = "auction-001";
            registry.getLock(auctionId);
            int sizeBefore = registry.size();

            // Act
            registry.release(auctionId);

            // Assert
            assertEquals(sizeBefore - 1, registry.size(),
                    "size() phải giảm 1 sau khi release lock tồn tại");
        }

        @Test
        @DisplayName("release() trên id không tồn tại → không ném exception, size không đổi")
        void release_nonExistentId_doesNotThrowAndSizeUnchanged() {
            // Arrange
            String nonExistentId = "non-existent-auction";
            int sizeBefore = registry.size();

            // Act & Assert
            assertDoesNotThrow(() -> registry.release(nonExistentId));
            assertEquals(sizeBefore, registry.size(),
                    "size() không được thay đổi khi release id không tồn tại");
        }

        @Test
        @DisplayName("release() hai lần cùng id → không ném exception (idempotent)")
        void release_calledTwice_isIdempotent() {
            // Arrange
            String auctionId = "auction-001";
            registry.getLock(auctionId);

            // Act & Assert — lần thứ hai không được ném exception
            registry.release(auctionId);
            assertDoesNotThrow(() -> registry.release(auctionId));
        }

        @Test
        @DisplayName("release() rồi getLock() cùng id → tạo lock mới (khác instance cũ)")
        void release_thenGetLock_returnsNewInstance() {
            // Arrange
            String auctionId = "auction-001";
            ReentrantLock oldLock = registry.getLock(auctionId);

            // Act
            registry.release(auctionId);
            ReentrantLock newLock = registry.getLock(auctionId);

            // Assert
            assertNotSame(oldLock, newLock,
                    "Sau khi release và getLock lại, phải nhận được lock instance mới");

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("release() chỉ xóa đúng id đó — các lock khác không bị ảnh hưởng")
        void release_specificId_doesNotAffectOtherLocks() {
            // Arrange
            String idA = "auction-A";
            String idB = "auction-B";
            registry.getLock(idA);
            ReentrantLock lockB = registry.getLock(idB);

            // Act
            registry.release(idA);

            // Assert — lockB vẫn là cùng instance
            ReentrantLock lockBAfter = registry.getLock(idB);
            assertSame(lockB, lockBAfter,
                    "release(idA) không được ảnh hưởng đến lock của idB");

            // Cleanup
            registry.release(idB);
        }
    }

    // =========================================================================
    // size()
    // =========================================================================

    @Nested
    @DisplayName("size: phản ánh đúng số lock đang tồn tại trong registry")
    class SizeContract {

        @Test
        @DisplayName("registry trống sau setUp → size = 0")
        void emptyRegistry_sizeIsZero() {
            // Assert — setUp đã release tất cả known ids
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("getLock một id mới → size tăng 1")
        void getLock_newId_incrementsSize() {
            // Arrange
            int sizeBefore = registry.size();
            String auctionId = "auction-001";

            // Act
            registry.getLock(auctionId);

            // Assert
            assertEquals(sizeBefore + 1, registry.size());

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("getLock cùng id nhiều lần → size chỉ tăng 1 (no duplicate entry)")
        void getLock_sameIdMultipleTimes_sizeIncreasesOnlyOnce() {
            // Arrange
            int sizeBefore = registry.size();
            String auctionId = "auction-001";

            // Act — gọi 5 lần cùng id
            for (int i = 0; i < 5; i++) {
                registry.getLock(auctionId);
            }

            // Assert
            assertEquals(sizeBefore + 1, registry.size(),
                    "Cùng id dù getLock bao nhiêu lần cũng chỉ chiếm 1 slot trong registry");

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("getLock ba id khác nhau → size tăng 3")
        void getLock_threeDistinctIds_sizeIncreasesBy3() {
            // Arrange
            int sizeBefore = registry.size();
            List<String> ids = List.of("auction-001", "auction-002", "auction-003");

            // Act
            ids.forEach(registry::getLock);

            // Assert
            assertEquals(sizeBefore + 3, registry.size());

            // Cleanup
            ids.forEach(registry::release);
        }

        @Test
        @DisplayName("getLock rồi release → size trở về ban đầu")
        void getLockThenRelease_sizeRestored() {
            // Arrange
            int sizeBefore = registry.size();
            String auctionId = "auction-001";

            // Act
            registry.getLock(auctionId);
            registry.release(auctionId);

            // Assert
            assertEquals(sizeBefore, registry.size(),
                    "size() phải trở về giá trị trước khi getLock sau khi release");
        }
    }

    // =========================================================================
    // Edge case: null, empty, whitespace, special chars, very long id
    // =========================================================================

    @Nested
    @DisplayName("Edge case: các giá trị auctionId bất thường")
    class EdgeCases {

        @Test
        @DisplayName("getLock với null auctionId → ném NullPointerException")
        void getLock_nullId_throwsNullPointerException() {
            // Act & Assert
            // ConcurrentHashMap.computeIfAbsent không cho phép null key
            assertThrows(NullPointerException.class,
                    () -> registry.getLock(null),
                    "ConcurrentHashMap không cho phép null key, phải ném NPE");
        }

        @Test
        @DisplayName("release với null auctionId → ném NullPointerException")
        void release_nullId_throwsNullPointerException() {
            // Act & Assert
            assertThrows(NullPointerException.class,
                    () -> registry.release(null),
                    "ConcurrentHashMap.remove(null) phải ném NPE");
        }

        @Test
        @DisplayName("getLock với empty string → trả về lock non-null (valid key)")
        void getLock_emptyString_returnsNonNull() {
            // Act
            ReentrantLock lock = registry.getLock("");

            // Assert
            assertNotNull(lock);

            // Cleanup
            registry.release("");
        }

        @Test
        @DisplayName("getLock với empty string hai lần → cùng instance")
        void getLock_emptyStringTwice_returnsSameInstance() {
            // Act
            ReentrantLock first  = registry.getLock("");
            ReentrantLock second = registry.getLock("");

            // Assert
            assertSame(first, second);

            // Cleanup
            registry.release("");
        }

        @Test
        @DisplayName("getLock với whitespace-only string → trả về lock non-null")
        void getLock_whitespaceString_returnsNonNull() {
            // Act
            ReentrantLock lock = registry.getLock("   ");

            // Assert
            assertNotNull(lock);

            // Cleanup
            registry.release("   ");
        }

        @Test
        @DisplayName("getLock với tab và newline → được coi là key hợp lệ, trả về lock")
        void getLock_tabAndNewlineString_returnsNonNull() {
            // Act
            ReentrantLock tabLock     = registry.getLock("\t");
            ReentrantLock newlineLock = registry.getLock("\n");

            // Assert
            assertNotNull(tabLock);
            assertNotNull(newlineLock);
            assertNotSame(tabLock, newlineLock, "\\t và \\n là hai key khác nhau");

            // Cleanup
            registry.release("\t");
            registry.release("\n");
        }

        @Test
        @DisplayName("getLock với special characters → trả về lock non-null")
        void getLock_specialCharacters_returnsNonNull() {
            // Arrange
            String specialId = "special-!@#$%";

            // Act
            ReentrantLock lock = registry.getLock(specialId);

            // Assert
            assertNotNull(lock);

            // Cleanup
            registry.release(specialId);
        }

        @Test
        @DisplayName("getLock với unicode string → trả về lock non-null")
        void getLock_unicodeString_returnsNonNull() {
            // Arrange
            String unicodeId = "unicode-拍卖";

            // Act
            ReentrantLock lock = registry.getLock(unicodeId);

            // Assert
            assertNotNull(lock);

            // Cleanup
            registry.release(unicodeId);
        }

        @Test
        @DisplayName("getLock với very long auctionId (1000 chars) → trả về lock non-null")
        void getLock_veryLongId_returnsNonNull() {
            // Arrange
            String longId = "x".repeat(1000);

            // Act
            ReentrantLock lock = registry.getLock(longId);

            // Assert
            assertNotNull(lock);

            // Cleanup
            registry.release(longId);
        }

        @Test
        @DisplayName("empty string và whitespace là hai key khác nhau → khác lock instance")
        void getLock_emptyVsWhitespace_differentInstances() {
            // Act
            ReentrantLock emptyLock      = registry.getLock("");
            ReentrantLock whitespaceLock = registry.getLock(" ");

            // Assert
            assertNotSame(emptyLock, whitespaceLock,
                    "\"\" và \" \" là hai key khác nhau trong ConcurrentHashMap");

            // Cleanup
            registry.release("");
            registry.release(" ");
        }
    }

    // =========================================================================
    // Determinism & no side effect
    // =========================================================================

    @Nested
    @DisplayName("Determinism: hành vi nhất quán, không có side effect")
    class DeterminismAndSideEffect {

        @Test
        @DisplayName("getLock với UUID ngẫu nhiên → luôn trả về cùng instance khi gọi lại")
        void getLock_randomUUID_consistentIdentity() {
            // Arrange
            String auctionId = UUID.randomUUID().toString();

            // Act
            ReentrantLock first  = registry.getLock(auctionId);
            ReentrantLock second = registry.getLock(auctionId);
            ReentrantLock third  = registry.getLock(auctionId);

            // Assert
            assertSame(first, second);
            assertSame(second, third);

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("release() rồi getLock() lại → lock mới ở trạng thái unlocked (clean state)")
        void afterRelease_newLock_isUnlocked() {
            // Arrange
            String auctionId = "auction-001";
            ReentrantLock oldLock = registry.getLock(auctionId);
            oldLock.lock(); // giả lập phiên đấu giá đang dùng lock
            oldLock.unlock();
            registry.release(auctionId);

            // Act — tạo lock mới cho phiên khác (cùng id tái sử dụng)
            ReentrantLock newLock = registry.getLock(auctionId);

            // Assert — lock mới phải sạch, không bị ảnh hưởng bởi lock cũ
            assertFalse(newLock.isLocked(),
                    "Lock mới sau release phải ở trạng thái unlocked");
            assertEquals(0, newLock.getHoldCount(),
                    "Lock mới không được có holdCount > 0");

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("getLock một id không ảnh hưởng đến lock của id khác")
        void getLock_oneId_doesNotAffectAnotherId() {
            // Arrange
            String idA = "auction-A";
            String idB = "auction-B";
            ReentrantLock lockA = registry.getLock(idA);

            // Act — lock A
            lockA.lock();
            try {
                // Lấy lockB trong khi lockA đang bị giữ
                ReentrantLock lockB = registry.getLock(idB);

                // Assert — lockB hoàn toàn độc lập với lockA
                assertFalse(lockB.isLocked(),
                        "lockB phải ở trạng thái unlocked, không bị ảnh hưởng bởi lockA");
                assertTrue(lockB.tryLock(),
                        "tryLock() trên lockB phải thành công dù lockA đang bị giữ");
                lockB.unlock();
            } finally {
                lockA.unlock();
            }

            // Cleanup
            registry.release(idA);
            registry.release(idB);
        }
    }

    // =========================================================================
    // Concurrency contract: nhiều thread cùng getLock cùng id
    // =========================================================================

    @Nested
    @DisplayName("Concurrency contract: nhiều thread cùng getLock phải nhận cùng instance")
    class ConcurrencyContract {

        @Test
        @DisplayName("16 thread cùng getLock cùng auctionId → tất cả nhận cùng lock instance")
        void getLock_concurrentAccess_sameIdAlwaysSameInstance() throws Exception {
            // Arrange
            String auctionId = "auction-concurrent";
            int threadCount = 16;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<ReentrantLock>> futures = new ArrayList<>();

            // Act — tất cả thread chờ gate rồi cùng getLock
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return registry.getLock(auctionId);
                }));
            }
            startGate.countDown(); // mở gate, tất cả thread cùng chạy

            // Collect results
            List<ReentrantLock> results = new ArrayList<>();
            for (Future<ReentrantLock> f : futures) {
                results.add(f.get());
            }
            executor.shutdown();

            // Assert — tất cả phải cùng instance
            ReentrantLock expected = results.get(0);
            for (int i = 1; i < results.size(); i++) {
                assertSame(expected, results.get(i),
                        "Thread " + i + " phải nhận cùng lock instance với thread 0");
            }

            // Cleanup
            registry.release(auctionId);
        }

        @Test
        @DisplayName("16 thread cùng getLock với các auctionId khác nhau → tất cả unique instances")
        void getLock_concurrentAccess_differentIds_allUniqueInstances() throws Exception {
            // Arrange
            int threadCount = 16;
            List<String> auctionIds = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                auctionIds.add("auction-concurrent-" + i);
            }
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<ReentrantLock>> futures = new ArrayList<>();

            // Act
            for (int i = 0; i < threadCount; i++) {
                final String id = auctionIds.get(i);
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return registry.getLock(id);
                }));
            }
            startGate.countDown();

            List<ReentrantLock> results = new ArrayList<>();
            for (Future<ReentrantLock> f : futures) {
                results.add(f.get());
            }
            executor.shutdown();

            // Assert — tất cả phải là unique instances
            Set<ReentrantLock> uniqueLocks = new HashSet<>(results);
            assertEquals(threadCount, uniqueLocks.size(),
                    "Mỗi auctionId khác nhau phải có lock instance duy nhất");

            // Cleanup
            auctionIds.forEach(registry::release);
        }
    }
}