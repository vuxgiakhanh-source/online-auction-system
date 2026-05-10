package com.group13.auction.strategy;

import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>Cả hai đặt bid 1_200_000 → cả hai đều pass validate, nhưng chỉ một bid
 *       được ghi cuối cùng → lost update / hai người cùng nghĩ mình đang dẫn đầu.</li>
 * </ul>
 *
 * <h3>Thiết kế:</h3>
 * <ul>
 *   <li>Key = auctionId → 1 ReentrantLock duy nhất per phiên.</li>
 *   <li>{@link ConcurrentHashMap#computeIfAbsent} đảm bảo atomic "create if absent"
 *       → không bao giờ tạo 2 lock cho cùng 1 auction.</li>
 *   <li>Singleton eager init → thread-safe từ đầu.</li>
 *   <li>{@link #release(String)} dọn dẹp sau khi phiên kết thúc để tránh memory leak.</li>
 * </ul>
 *
 * <h3>Cách dùng (trong BidHandler / AutoBidProcessor):</h3>
 * <pre>{@code
 *   ReentrantLock lock = AuctionLockRegistry.getInstance().getLock(auctionId);
 *   lock.lock();
 *   try {
 *       // validate + update auction state an toàn
 *       bidService.placeBid(...);
 *   } finally {
 *       lock.unlock();
 *   }
 * }</pre>
 *
 * <p><b>Không dùng {@code synchronized(auction)}</b> vì object lock không đủ
 * visibility: các thread khác có thể lấy reference auction khác nhau từ cache
 * hoặc DAO, dẫn đến lock trên object khác nhau mà không biết.
 */
public final class AuctionLockRegistry {

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
     * @param auctionId ID phiên đấu giá
     * @return ReentrantLock gắn với phiên đó (non-fair, dùng fair = true nếu
     *         muốn ưu tiên thread đợi lâu hơn nhưng throughput thấp hơn)
     */
    public ReentrantLock getLock(String auctionId) {
        return locks.computeIfAbsent(auctionId, id -> new ReentrantLock());
    }

    /**
     * Xóa lock khi phiên đấu giá đã kết thúc.
     * Gọi từ {@link com.group13.auction.service.AuctionService#closeAuction()} để tránh memory leak.
     *
     * <p><b>An toàn để gọi ngay sau khi phiên kết thúc:</b>
     * Tại thời điểm closeAuction, lock đã được unlock (chạy trong finally),
     * nên không còn thread nào đang giữ lock.
     *
     * @param auctionId ID phiên đã kết thúc
     */
    public void release(String auctionId) {
        locks.remove(auctionId);
    }

    /** Chỉ dùng cho testing — kiểm tra số lock đang tồn tại. */
    public int size() {
        return locks.size();
    }
}
