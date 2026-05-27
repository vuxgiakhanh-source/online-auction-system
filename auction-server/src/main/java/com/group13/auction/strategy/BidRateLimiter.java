package com.group13.auction.strategy;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-user rate limiter cho việc đặt giá.
 *
 * <h3>Mục đích:</h3>
 *
 * <p>Khi hệ thống có 5000 người dùng đồng thời, một user duy nhất có thể gửi hàng trăm bid/giây
 * (flood attack hoặc bug client), gây nghẽn lock và DB. Rate limiter này chặn request quá nhanh ở
 * tầng sớm nhất có thể.
 *
 * <h3>Thuật toán: Token Bucket đơn giản hóa (Fixed Window)</h3>
 *
 * <ul>
 *   <li>Mỗi user có một bucket với {@code MAX_BIDS_PER_WINDOW} token.
 *   <li>Mỗi lần bid tiêu tốn 1 token.
 *   <li>Bucket reset mỗi {@code WINDOW_MS} milliseconds.
 *   <li>Thread-safe: dùng AtomicLong cho count và lastReset.
 * </ul>
 *
 * <h3>Giá trị mặc định:</h3>
 *
 * <ul>
 *   <li>5 bid / 1 giây / user — đủ cho normal usage và đủ hạn chế flood.
 *   <li>ConcurrentHashMap — không global lock khi check rate.
 *   <li>Tự cleanup sau khi user idle {@code IDLE_CLEANUP_MS} (15 phút).
 * </ul>
 */
public final class BidRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(BidRateLimiter.class);

  // ── Config ────────────────────────────────────────────────────────────────
  /** Số bid tối đa trong 1 window. */
  private static final int MAX_BIDS_PER_WINDOW = 5;

  /** Độ dài window (ms). */
  private static final long WINDOW_MS = 1_000L;

  /** Xóa bucket sau khi idle (ms) — 15 phút. */
  private static final long IDLE_CLEANUP_MS = 15 * 60 * 1_000L;

  // Singleton
  private static final BidRateLimiter INSTANCE = new BidRateLimiter();

  public static BidRateLimiter getInstance() {
    return INSTANCE;
  }

  // ── State ─────────────────────────────────────────────────────────────────

  /** Map userId → BidBucket. */
  private final ConcurrentHashMap<String, BidBucket> buckets = new ConcurrentHashMap<>();

  private BidRateLimiter() {}

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Kiểm tra và tiêu thụ 1 token cho user.
   *
   * @param userId ID của user đang bid
   * @return {@code true} nếu được phép, {@code false} nếu vượt rate limit
   */
  public boolean tryConsume(String userId) {
    BidBucket bucket = buckets.computeIfAbsent(userId, id -> new BidBucket());
    boolean allowed = bucket.tryConsume();
    if (!allowed) {
      log.warn(
          "Rate limit exceeded: userId={} (max {}/{}ms)", userId, MAX_BIDS_PER_WINDOW, WINDOW_MS);
    }
    return allowed;
  }

  /** Xóa bucket của user (gọi khi user disconnect hoặc auction kết thúc). */
  public void remove(String userId) {
    buckets.remove(userId);
  }

  /** Cleanup các bucket đã idle quá lâu. Gọi định kỳ từ AuctionTimerService (mỗi 5 phút là đủ). */
  public void cleanupIdle() {
    long now = System.currentTimeMillis();
    int removed = 0;
    var iter = buckets.entrySet().iterator();
    while (iter.hasNext()) {
      var entry = iter.next();
      if (now - entry.getValue().getLastAccessMs() > IDLE_CLEANUP_MS) {
        iter.remove();
        removed++;
      }
    }
    if (removed > 0) {
      log.debug("BidRateLimiter cleanup: removed {} idle buckets", removed);
    }
  }

  /** Chỉ dùng cho test. */
  public void clearAll() {
    buckets.clear();
  }

  public int size() {
    return buckets.size();
  }

  // ── Inner: BidBucket ──────────────────────────────────────────────────────

  private static final class BidBucket {
    private long count = 0;
    private long windowStart = System.currentTimeMillis();

    /** volatile vì cleanupIdle() đọc ngoài synchronized block. */
    private volatile long lastAccessMs = System.currentTimeMillis();

    /**
     * Cố gắng tiêu thụ 1 token. Thread-safe: synchronized trên instance — đủ vì mỗi BidBucket chỉ
     * phục vụ 1 userId. (comment cũ "CAS loop" là sai — thực tế dùng synchronized từ đầu)
     */
    boolean tryConsume() {
      synchronized (this) {
        long now = System.currentTimeMillis();
        lastAccessMs = now;
        if (now - windowStart >= WINDOW_MS) {
          windowStart = now;
          count = 0;
        }
        count++;
        return count <= MAX_BIDS_PER_WINDOW;
      }
    }

    long getLastAccessMs() {
      return lastAccessMs;
    }
  }
}
