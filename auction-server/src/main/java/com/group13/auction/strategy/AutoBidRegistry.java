package com.group13.auction.strategy;

import com.group13.auction.dao.AutoBidDAO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton registry lưu toàn bộ auto-bid đang hoạt động.
 *
 * <h3>Cải tiến v2:</h3>
 *
 * <ul>
 *   <li>Persist AutoBid xuống DB qua {@link AutoBidDAO} — không mất khi restart.
 *   <li>Logging chuẩn SLF4J.
 *   <li>AutoBidEntry vẫn immutable — thread-safe khi đọc song song.
 * </ul>
 */
public class AutoBidRegistry {

  private static final Logger log = LoggerFactory.getLogger(AutoBidRegistry.class);

  private static final AutoBidRegistry INSTANCE = new AutoBidRegistry();

  /** Persist DB ngoài luồng register — tránh block 32+ thread concurrent trên HikariCP. */
  private static final ExecutorService PERSIST_EXECUTOR =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "autobid-persist-" + index.incrementAndGet());
              t.setDaemon(true);
              return t;
            }
          });

  /** Map lưu toàn bộ auto-bid. Key = "{userId}:{auctionId}" */
  private final ConcurrentHashMap<String, AutoBidEntry> registry = new ConcurrentHashMap<>();

  /* package-private for test injection */ AutoBidDAO autoBidDAO;

  private AutoBidRegistry() {
    this.autoBidDAO = new AutoBidDAO();
  }

  public static AutoBidRegistry getInstance() {
    return INSTANCE;
  }

  // ── CRUD ──────────────────────────────────────────────────────────────────

  /** Đăng ký hoặc cập nhật auto-bid. Persist xuống DB để không mất khi server restart. */
  public void register(String userId, String auctionId, long maxBid) {
    String key = buildKey(userId, auctionId);
    LocalDateTime now = LocalDateTime.now();
    AutoBidEntry entry =
        registry.compute(
            key,
            (k, existing) -> {
              LocalDateTime registeredAt = (existing != null) ? existing.getRegisteredAt() : now;
              return new AutoBidEntry(userId, auctionId, maxBid, registeredAt);
            });

    // Persist xuống DB bất đồng bộ — register() chỉ cập nhật RAM (hot path).
    if (autoBidDAO != null) {
      LocalDateTime registeredAt = entry.getRegisteredAt();
      PERSIST_EXECUTOR.submit(
          () -> {
            try {
              autoBidDAO.upsert(userId, auctionId, maxBid, registeredAt);
            } catch (Exception e) {
              log.warn("auto-bid DB upsert failed (non-critical): {}", e.getMessage());
            }
          });
    }

    log.info("auto-bid registered: userId={} auctionId={} maxBid={}", userId, auctionId, maxBid);
  }

  /**
   * Hủy auto-bid.
   *
   * @return true nếu có entry và đã xóa
   */
  public boolean cancel(String userId, String auctionId) {
    String key = buildKey(userId, auctionId);
    boolean removed = registry.remove(key) != null;
    if (removed) {
      if (autoBidDAO != null) {
        try {
          autoBidDAO.delete(userId, auctionId);
        } catch (Exception e) {
          log.warn("auto-bid DB delete failed: {}", e.getMessage());
        }
      }
      log.info("auto-bid cancelled: userId={} auctionId={}", userId, auctionId);
    }
    return removed;
  }

  /** Xóa toàn bộ auto-bid của một phiên khi phiên kết thúc. */
  public void clearAuction(String auctionId) {
    int before = registry.size();
    registry.entrySet().removeIf(e -> e.getValue().getAuctionId().equals(auctionId));
    int removed = before - registry.size();
    if (removed > 0) {
      if (autoBidDAO != null) {
        try {
          autoBidDAO.deleteByAuction(auctionId);
        } catch (Exception e) {
          log.warn("auto-bid DB deleteByAuction failed: {}", e.getMessage());
        }
      }
      log.info("auto-bid cleared: auctionId={} count={}", auctionId, removed);
    }
  }

  public AutoBidEntry get(String userId, String auctionId) {
    return registry.get(buildKey(userId, auctionId));
  }

  public boolean hasActiveBid(String userId, String auctionId) {
    return registry.containsKey(buildKey(userId, auctionId));
  }

  /** Trả về snapshot tất cả auto-bid của một phiên. Thread-safe: ArrayList mới, không live view. */
  public Collection<AutoBidEntry> getEntriesForAuction(String auctionId) {
    List<AutoBidEntry> result = new ArrayList<>();
    for (AutoBidEntry entry : registry.values()) {
      if (entry.getAuctionId().equals(auctionId)) {
        result.add(entry);
      }
    }
    return result;
  }

  /**
   * Load lại auto-bid từ DB khi server restart. Gọi từ server bootstrap sau khi
   * loadDataFromDatabase(). Bug 4 fix: chỉ load auto-bid của phiên còn OPEN hoặc RUNNING.
   */
  public void loadFromDatabase() {
    List<AutoBidDAO.AutoBidRow> rows = autoBidDAO.findAll();
    int count = 0;
    for (AutoBidDAO.AutoBidRow row : rows) {
      // Bỏ qua nếu auction không còn tồn tại hoặc đã kết thúc
      com.group13.auction.model.auction.Auction auction =
          com.group13.auction.manager.AuctionManager.getInstance().findAuctionById(row.auctionId);
      if (auction == null) {
        continue;
      }
      com.group13.auction.model.auction.Auction.AuctionStatus status = auction.getStatus();
      if (status != com.group13.auction.model.auction.Auction.AuctionStatus.OPEN
          && status != com.group13.auction.model.auction.Auction.AuctionStatus.RUNNING) {
        // Xóa luôn entry rác khỏi DB
        try {
          autoBidDAO.delete(row.userId, row.auctionId);
        } catch (Exception ignored) {
        }
        continue;
      }
      String key = buildKey(row.userId, row.auctionId);
      AutoBidEntry entry =
          new AutoBidEntry(row.userId, row.auctionId, row.maxBid, row.registeredAt);
      registry.putIfAbsent(key, entry);
      count++;
    }
    if (count > 0) {
      log.info("auto-bid registry loaded from DB: count={}", count);
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private static String buildKey(String userId, String auctionId) {
    return userId + ":" + auctionId;
  }

  // ── Inner class: AutoBidEntry ─────────────────────────────────────────────

  /** Immutable auto-bid entry. Thread-safe khi đọc song song. */
  public static final class AutoBidEntry {
    private final String userId;
    private final String auctionId;
    private final long maxBid;
    private final LocalDateTime registeredAt;

    public AutoBidEntry(String userId, String auctionId, long maxBid, LocalDateTime registeredAt) {
      this.userId = userId;
      this.auctionId = auctionId;
      this.maxBid = maxBid;
      this.registeredAt = registeredAt;
    }

    public String getUserId() {
      return userId;
    }

    public String getAuctionId() {
      return auctionId;
    }

    public long getMaxBid() {
      return maxBid;
    }

    public LocalDateTime getRegisteredAt() {
      return registeredAt;
    }

    /**
     * Tính giá bid kế tiếp.
     *
     * @return giá bid tiếp theo, hoặc -1 nếu maxBid không đủ
     */
    public long calculateNextBid(long currentPrice) {
      long increment = BidIncrementCalculator.calculate(currentPrice);
      long next = currentPrice + increment;
      return (next > maxBid) ? -1 : next;
    }

    @Override
    public String toString() {
      return String.format(
          "AutoBidEntry{user=%s, auction=%s, maxBid=%d, at=%s}",
          userId, auctionId, maxBid, registeredAt);
    }
  }
}
