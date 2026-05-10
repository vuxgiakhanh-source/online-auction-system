package com.group13.auction.strategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry lưu trữ toàn bộ auto-bid đang hoạt động trong hệ thống.
 *
 * <h3>Tại sao cần class này?</h3>
 * <p>Khi user B đã đăng ký auto-bid với maxBid=5.000.000, rồi user A bid thủ công
 * 3.000.000 vượt B — hệ thống cần biết B có auto-bid không để tự động counter.
 * AutoBidRegistry là nơi duy nhất lưu thông tin đó.
 *
 * <h3>Key design decisions:</h3>
 * <ul>
 *   <li>Key = "{userId}:{auctionId}" — mỗi user chỉ có DUY NHẤT 1 auto-bid/phiên</li>
 *   <li>ConcurrentHashMap → thread-safe không cần synchronized block ngoài</li>
 *   <li>Singleton eager init → an toàn với multi-thread từ đầu</li>
 *   <li>AutoBidEntry là immutable → đọc song song không cần lock</li>
 * </ul>
 *
 * <h3>Vòng đời của một AutoBidEntry:</h3>
 * <pre>
 *   REGISTER → (bị vượt) → HỆ THỐNG TỰ COUNTER-BID → (vẫn còn slot → tiếp tục)
 *           → (maxBid cạn hoặc người dùng CANCEL) → bị xóa khỏi registry
 * </pre>
 */
public class AutoBidRegistry {

    private static final AutoBidRegistry INSTANCE = new AutoBidRegistry();

    /**
     * Map lưu toàn bộ auto-bid đang hoạt động.
     * Key = "{userId}:{auctionId}"
     */
    private final ConcurrentHashMap<String, AutoBidEntry> registry = new ConcurrentHashMap<>();

    private AutoBidRegistry() {}

    public static AutoBidRegistry getInstance() {
        return INSTANCE;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Đăng ký hoặc cập nhật auto-bid cho một user trong một phiên.
     * Nếu đã có entry cũ cho cùng userId+auctionId → ghi đè (update maxBid).
     *
     * @param userId    ID người dùng
     * @param auctionId ID phiên đấu giá
     * @param maxBid    giá tối đa người dùng sẵn sàng trả
     */
    public void register(String userId, String auctionId, long maxBid) {
        String key = buildKey(userId, auctionId);
        AutoBidEntry entry = new AutoBidEntry(userId, auctionId, maxBid, LocalDateTime.now());
        registry.put(key, entry);
        System.out.printf("[AUTO-BID REGISTRY] Đăng ký: userId=%s, auction=%s, maxBid=%d%n",
                userId, auctionId, maxBid);
    }

    /**
     * Hủy auto-bid của một user trong một phiên.
     *
     * @param userId    ID người dùng
     * @param auctionId ID phiên
     * @return true nếu có entry và đã xóa, false nếu không có gì để xóa
     */
    public boolean cancel(String userId, String auctionId) {
        String key = buildKey(userId, auctionId);
        boolean removed = registry.remove(key) != null;
        if (removed) {
            System.out.printf("[AUTO-BID REGISTRY] Hủy: userId=%s, auction=%s%n",
                    userId, auctionId);
        }
        return removed;
    }

    /**
     * Xóa toàn bộ auto-bid của một phiên khi phiên kết thúc.
     * Gọi từ AuctionService.closeAuction() để dọn dẹp bộ nhớ.
     *
     * @param auctionId ID phiên đã kết thúc
     */
    public void clearAuction(String auctionId) {
        int before = registry.size();
        registry.entrySet().removeIf(e -> e.getValue().getAuctionId().equals(auctionId));
        int removed = before - registry.size();
        if (removed > 0) {
            System.out.printf("[AUTO-BID REGISTRY] Xóa %d entry của phiên %s%n",
                    removed, auctionId);
        }
    }

    /**
     * Lấy auto-bid entry của một user trong một phiên.
     *
     * @param userId    ID người dùng
     * @param auctionId ID phiên
     * @return AutoBidEntry nếu đang hoạt động, null nếu không có
     */
    public AutoBidEntry get(String userId, String auctionId) {
        return registry.get(buildKey(userId, auctionId));
    }

    /**
     * Kiểm tra user có đang có auto-bid trong phiên không.
     *
     * @param userId    ID người dùng
     * @param auctionId ID phiên
     * @return true nếu đang có auto-bid hoạt động
     */
    public boolean hasActiveBid(String userId, String auctionId) {
        return registry.containsKey(buildKey(userId, auctionId));
    }

    /**
     * Lấy tất cả auto-bid entry đang hoạt động trong một phiên.
     *
     * <p><b>Dùng để:</b> Khi user A vừa bid thủ công, server iterate danh sách này
     * để tìm những bidder khác có auto-bid và kích hoạt counter-bid của họ.
     *
     * <p><b>Thread-safety:</b> Trả về snapshot (ArrayList mới) thay vì live view
     * → tránh ConcurrentModificationException khi iterate đồng thời có thread
     * khác gọi register/cancel.
     *
     * @param auctionId ID phiên
     * @return list snapshot các AutoBidEntry đang hoạt động
     */
    public Collection<AutoBidEntry> getEntriesForAuction(String auctionId) {
        List<AutoBidEntry> result = new ArrayList<>();
        for (AutoBidEntry entry : registry.values()) {
            if (entry.getAuctionId().equals(auctionId)) {
                result.add(entry);
            }
        }
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String buildKey(String userId, String auctionId) {
        return userId + ":" + auctionId;
    }

    // ── Inner class: AutoBidEntry ─────────────────────────────────────────────

    /**
     * Dữ liệu của một auto-bid đang hoạt động.
     *
     * <p><b>Immutable</b> để thread-safe khi đọc từ nhiều thread đồng thời.
     * Khi cần update maxBid → tạo entry mới và put vào registry (replace-and-forget).
     */
    public static final class AutoBidEntry {
        private final String userId;
        private final String auctionId;
        private final long maxBid;
        private final LocalDateTime registeredAt;

        public AutoBidEntry(String userId, String auctionId,
                            long maxBid, LocalDateTime registeredAt) {
            this.userId = userId;
            this.auctionId = auctionId;
            this.maxBid = maxBid;
            this.registeredAt = registeredAt;
        }

        public String getUserId()              { return userId; }
        public String getAuctionId()           { return auctionId; }
        public long getMaxBid()                { return maxBid; }
        public LocalDateTime getRegisteredAt() { return registeredAt; }

        /**
         * Tính giá bid kế tiếp để vượt mức giá hiện tại của phiên.
         *
         * <p>Logic:
         * <pre>
         *   increment = BidIncrementCalculator.calculate(currentPrice)
         *   nextBid   = currentPrice + increment
         *   nếu nextBid > maxBid → trả về -1 (đã cạn, không bid nữa)
         * </pre>
         *
         * @param currentPrice giá hiện tại của phiên
         * @return giá cần đặt để vượt, hoặc -1 nếu maxBid không đủ
         */
        public long calculateNextBid(long currentPrice) {
            long increment = BidIncrementCalculator.calculate(currentPrice);
            long next = currentPrice + increment;
            return (next > maxBid) ? -1 : next;
        }

        @Override
        public String toString() {
            return String.format("AutoBidEntry{user=%s, auction=%s, maxBid=%d, at=%s}",
                    userId, auctionId, maxBid, registeredAt);
        }
    }
}

