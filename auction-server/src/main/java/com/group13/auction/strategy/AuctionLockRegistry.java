package com.group13.auction.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry lưu một ReentrantLock riêng biệt cho mỗi phiên đấu giá.
 * Lưu một {@link ReentrantLock} riêng biệt cho mỗi phiên đấu giá.
 *
 * <h3>Tại sao cần lock per-auction?</h3>
 * <p>Khi nhiều client đặt giá cùng lúc (concurrent bidding), nếu không có lock
 * per-auction thì xảy ra race condition:
 * <ul>
 *   <li>Thread A đọc {@code currentPrice = 1_000_000}</li>
 *   <li>Thread B đọc {@code currentPrice = 1_000_000} (cùng lúc)</li>
 *   <li>Cả hai đặt bid → cả hai pass validate → lost update</li>
 * </ul>
 *
 * <h3>⚠️ Giới hạn: SINGLE INSTANCE ONLY</h3>
 * <p>Lock này là in-memory {@link ReentrantLock} — chỉ hoạt động đúng trong
 * <b>một JVM process duy nhất</b>. Nếu deploy nhiều instance (Docker Swarm,
 * Kubernetes horizontal scaling), mỗi instance có lock registry riêng:
 * <pre>
 *   Instance A: lock(auction#1) → bid accepted ✓
 *   Instance B: lock(auction#1) → bid accepted ✓  ← cùng lúc, không biết nhau!
 *   → race condition liên instance → giá DB bị sai
 * </pre>
 *
 * <h3>Khi cần scale (future work):</h3>
 * <p>Thay thế bằng Redis distributed lock (Redisson RLock hoặc SET NX PX pattern).
 * Business logic trong BidService và AuctionTimerService không cần thay đổi —
 * chỉ cần swap implementation ở ServerMain.
 *
 * <h3>Cách dùng (AuctionTimerService):</h3>
 * <pre>{@code
 *   boolean locked = lockRegistry.tryLock(auctionId, 3, TimeUnit.SECONDS);
 *   if (!locked) { // trả lỗi timeout cho client }
 *   try {
 *       auctionService.closeAuction(...);
 *   } finally {
 *       lockRegistry.unlock(auctionId);
 *       lockRegistry.release(auctionId);  // dọn entry sau khi phiên kết thúc
 *   }
 * }</pre>
 */
public final class AuctionLockRegistry {

    private static final Logger log = LoggerFactory.getLogger(AuctionLockRegistry.class);

    private static final AuctionLockRegistry INSTANCE = new AuctionLockRegistry();

    /** Map auctionId → lock của phiên đó. */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private AuctionLockRegistry() {}

    public static AuctionLockRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Lấy lock của một phiên, tạo mới nếu chưa có.
     * Thread-safe nhờ {@link ConcurrentHashMap#computeIfAbsent}.
     *
     * <p><b>FIX PERFORMANCE:</b> dùng {@code fair=false} (unfair lock).
     * Fair lock (FIFO queue) dưới high contention cho throughput thấp hơn ~4×
     * vì phải context-switch giữ thứ tự. Unfair lock tận dụng cache hot path.
     * Starvation không xảy ra trong thực tế vì mỗi bid đến qua network với độ trễ ms.
     */
    public ReentrantLock getLock(String auctionId) {
        return locks.computeIfAbsent(auctionId, id -> new ReentrantLock(false)); // fair=false
    }

    /**
     * Thử acquire lock với timeout. Trả về {@code true} nếu lấy được.
     *
     * @param auctionId ID phiên
     * @param timeout   thời gian chờ tối đa
     * @param unit      đơn vị thời gian
     * @return true nếu lấy được lock, false nếu timeout
     */
    public boolean tryLock(String auctionId, long timeout, TimeUnit unit) {
        ReentrantLock lock = getLock(auctionId);
        try {
            boolean acquired = lock.tryLock(timeout, unit);
            if (!acquired) {
                log.warn("tryLock TIMEOUT: auctionId={} timeout={}{}",
                    auctionId, timeout, unit.name().toLowerCase());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("tryLock INTERRUPTED: auctionId={}", auctionId);
            return false;
        }
    }

    /**
     * Unlock an toàn. Chỉ unlock nếu thread hiện tại đang giữ lock.
     */
    public void unlock(String auctionId) {
        ReentrantLock lock = locks.get(auctionId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Xóa lock khi phiên đấu giá đã kết thúc (tránh memory leak).
     * Gọi từ AuctionTimerService sau khi closeAuction.
     */
    public void release(String auctionId) {
        locks.remove(auctionId);
        log.debug("Lock released: auctionId={}", auctionId);
    }

    /** Chỉ dùng cho testing. */
    public int size() {
        return locks.size();
    }

    /**
     * Xóa toàn bộ lock khỏi registry.
     * <b>CHỈ dùng trong unit/integration test để reset state giữa các test.</b>
     * Không bao giờ gọi trong production code.
     */
    public void clearAll() {
        locks.clear();
        log.debug("AuctionLockRegistry cleared (test-only operation)");
    }
}