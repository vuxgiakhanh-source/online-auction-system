package com.group13.auction.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry lưu một {@link ReentrantLock} riêng biệt cho mỗi phiên đấu giá.
 *
 * <h3>Tại sao cần class này?</h3>
 * <p>Khi nhiều client đặt giá cùng lúc (concurrent bidding), nếu không có lock
 * per-auction thì xảy ra race condition:
 * <ul>
 *   <li>Thread A đọc {@code currentPrice = 1_000_000}</li>
 *   <li>Thread B đọc {@code currentPrice = 1_000_000} (cùng lúc)</li>
 *   <li>Cả hai đặt bid → cả hai pass validate → lost update</li>
 * </ul>
 *
 * <h3>Cải tiến v2 — lock với timeout:</h3>
 * <p>Thay vì {@code lock()} block vô thời hạn, dùng {@link #tryLock(String, long, TimeUnit)}
 * để tránh deadlock hoặc client bị treo quá lâu.
 *
 * <h3>Cách dùng (BidHandler):</h3>
 * <pre>{@code
 *   boolean locked = lockRegistry.tryLock(auctionId, 3, TimeUnit.SECONDS);
 *   if (!locked) { // trả lỗi timeout cho client }
 *   try {
 *       bidService.placeBid(...);
 *   } finally {
 *       lockRegistry.unlock(auctionId);
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
     */
    /**
     * FIX PERFORMANCE: đổi từ fair=true → fair=false.
     *
     * Fair lock (FIFO queue) dưới high contention:
     *   - Mỗi thread phải chờ đúng thứ tự → overhead context-switch + queue management
     *   - Throughput thực tế giảm 3–5× so với unfair
     *
     * Unfair lock (barge-in):
     *   - Thread vừa release có thể acquire lại ngay nếu không có thread nào đang wait
     *   - Tận dụng cache hot path (lock object vẫn còn trong L1/L2 cache)
     *   - Starvation không xảy ra trong thực tế vì mỗi bid đến qua network với độ trễ ms
     *     → không có thread nào bị block vĩnh viễn
     *
     * Benchmark điển hình: unfair lock cho ~4× throughput cao hơn fair lock ở 100+ threads.
     */
    public ReentrantLock getLock(String auctionId) {
        return locks.computeIfAbsent(auctionId, id -> new ReentrantLock(false)); // fair=false
    }

    /**
     * Acquire lock với timeout. Trả về true nếu lấy được trong thời gian cho phép.
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
     *
     * <p>Lưu ý: nếu có lock đang bị giữ khi gọi method này, lock đó vẫn tồn tại
     * trong memory nhưng sẽ không còn được registry quản lý nữa. Đảm bảo tất cả
     * lock đã được unlock trước khi gọi clearAll().
     */
    public void clearAll() {
        locks.clear();
        log.debug("AuctionLockRegistry cleared (test-only operation)");
    }
}