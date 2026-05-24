package com.group13.auction.strategy;

import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.BidService;
import com.group13.auction.manager.AuctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Engine xử lý Auto-Bid chain sau mỗi bid thành công.
 *
 * <h3>Cải tiến v3 — Phase-based dynamic increment:</h3>
 * <ul>
 *   <li>Phát hiện phase (EARLY/MID/LATE/VERY_HOT) từ thời gian còn lại
 *       và tần suất bid gần đây.</li>
 *   <li>Nhân increment theo phase: LATE → 1.5×, VERY_HOT → 2.0×.</li>
 *   <li>Priority-based: sort (maxBid DESC, registeredAt ASC).</li>
 * </ul>
 *
 * <h3>Thread-safety:</h3>
 * <p>process() được gọi NGOÀI lock của BidHandler. Mỗi bidService.placeBid()
 * bên trong tự acquire per-auction ReentrantLock — không deadlock vì ReentrantLock
 * là reentrant và gọi từ thread khác với thread đang giữ lock cũ.
 */
public class AutoBidProcessor {

    private static final Logger log = LoggerFactory.getLogger(AutoBidProcessor.class);

    private static final int MAX_CHAIN_DEPTH = 20;

    private final BidService     bidService;
    private final SessionManager sessionManager;
    private final AutoBidRegistry registry = AutoBidRegistry.getInstance();
    private       UserDAO        userDAO;

    /**
     * Static — chia sẻ giữa mọi instance.
     * Value là CopyOnWriteArrayList để đảm bảo thread-safe khi đọc (countRecentBids)
     * đồng thời với ghi (recordBidActivity) từ nhiều thread.
     */
    private static final ConcurrentHashMap<String, List<LocalDateTime>> recentBidTimes =
        new ConcurrentHashMap<>();

    /**
     * Thời điểm (nanoTime) của lần auto-bid cuối cùng trên mỗi phiên.
     * FIX race condition: dùng AtomicLong thay vì Long để getAndSet() atomic.
     * Trước đây dùng ConcurrentHashMap<String, Long> + put() — hai thread đồng thời
     * đọc last=null và cùng bỏ qua delay → auto-bid chain bắn quá nhanh dưới traffic cao.
     */
    private static final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>
        lastAutoBidNanoByAuction = new ConcurrentHashMap<>();

    /**
     * Per-user rate limiter cho auto-bid: mỗi user chỉ được phép 1 auto-bid / giây.
     * Key: userId, Value: nanoTime của lần auto-bid cuối cùng của user đó.
     * Dùng AtomicLong để getAndSet() atomic — tránh race condition tương tự lastAutoBidNanoByAuction.
     */
    private static final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>
        lastAutoBidNanoByUser = new ConcurrentHashMap<>();

    /** Khoảng cách tối thiểu giữa 2 auto-bid của cùng 1 user: 1 giây. */
    private static final long USER_AUTO_BID_MIN_NANOS = 1_000_000_000L; // 1s

    public AutoBidProcessor(BidService bidService, SessionManager sessionManager) {
        this.bidService     = bidService;
        this.sessionManager = sessionManager;
        this.userDAO        = new UserDAO();
    }

    /**
     * Kích hoạt chuỗi auto-bid sau khi một bid vừa thành công.
     *
     * @param auction           phiên vừa có bid mới
     * @param triggeredByUserId userId vừa bid (bỏ qua khi tìm counter)
     */
    public void process(Auction auction, String triggeredByUserId) {
        String auctionId = auction.getId();

        // Ghi nhận bid vừa xảy ra để phát hiện VERY_HOT
        recordBidActivity(auctionId);

        Collection<AutoBidRegistry.AutoBidEntry> allEntries = registry.getEntriesForAuction(auctionId);
        int maxIterations = Math.min(MAX_CHAIN_DEPTH,
            allEntries.isEmpty() ? 2 : allEntries.size() * 2 + 2);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Collection<AutoBidRegistry.AutoBidEntry> entries = registry.getEntriesForAuction(auctionId);

            NormalUser currentLeader = auction.getCurrentLeader();
            String     leaderId      = (currentLeader != null) ? currentLeader.getId() : null;

            AutoBidPhase phase = detectPhase(auction);

            // Tìm candidates: bị vượt, còn budget, ONLINE, sort (maxBid DESC, registeredAt ASC)
            // ANTI-SNIPE FIX: lọc offline ngay tại đây — không để winner là offline rồi break,
            // vì break sẽ giết chain của tất cả user khác (kể cả người đang online có autobid).
            List<AutoBidRegistry.AutoBidEntry> candidates = new ArrayList<>();
            for (AutoBidRegistry.AutoBidEntry entry : entries) {
                if (entry.getUserId().equals(leaderId)) continue;
                if (!sessionManager.isOnline(entry.getUserId())) {
                    log.debug("auto-bid candidate skipped (offline): userId={} auctionId={}",
                        entry.getUserId(), auctionId);
                    continue;
                }
                if (calcSmartBid(auction.getCurrentPrice(), entry.getMaxBid(), phase) > 0)
                    candidates.add(entry);
            }
            if (candidates.isEmpty()) break;

            candidates.sort(
                Comparator.comparingLong(AutoBidRegistry.AutoBidEntry::getMaxBid).reversed()
                    .thenComparing(AutoBidRegistry.AutoBidEntry::getRegisteredAt)
            );

            AutoBidRegistry.AutoBidEntry winner = candidates.get(0);
            long nextBid = calcSmartBid(auction.getCurrentPrice(), winner.getMaxBid(), phase);

            NormalUser autoBidder = findNormalUserById(winner.getUserId());
            if (autoBidder == null) {
                log.warn("auto-bid user not found, cancelling: userId={} auctionId={}",
                    winner.getUserId(), auctionId);
                registry.cancel(winner.getUserId(), auctionId);
                continue;
            }

            try {
                // Rate limiting (per-user): mỗi user chỉ được 1 auto-bid / giây
                if (!tryConsumeUserAutoBidToken(winner.getUserId())) {
                    log.debug("auto-bid skipped — user rate limit (1/s): userId={} auctionId={}",
                        winner.getUserId(), auctionId);
                    continue;
                }

                // Rate limiting: enforce minimum interval between auto-bids in same auction
                enforceAutoBidInterval(auctionId);

                AutoBidStrategy strategy = new AutoBidStrategy(winner.getMaxBid());
                bidService.placeBid(autoBidder, auction, nextBid, strategy);

                // Ghi nhận auto-bid này cho VERY_HOT detection ở vòng kế tiếp
                recordBidActivity(auctionId);

                sendAutoBidTriggeredNotify(winner, auction, nextBid);

                sessionManager.broadcastToAuctionAsync(auctionId,
                    Packet.of(PacketType.BID_UPDATE, DTOMapper.toBidUpdateDTO(auction, nextBid, 0L)));
                sessionManager.broadcastToAuctionAsync(auctionId,
                    Packet.of(PacketType.BID_CHART_POINT_UPDATE,
                        DTOMapper.toBidChartPoint(auctionId, nextBid,
                            autoBidder.getUsername(), true)));

                log.info("auto-bid triggered: userId={} username={} auctionId={} amount={} phase={} iteration={}",
                    autoBidder.getId(), autoBidder.getUsername(), auctionId,
                    nextBid, phase, iteration + 1);

            } catch (InvalidBidException e) {
                // Giá vừa bị người khác đẩy lên trong lúc process() chạy ngoài lock.
                // Đây là race condition tạm thời — KHÔNG cancel entry, vòng lặp kế tiếp
                // sẽ đọc lại currentPrice mới và tính lại nextBid đúng.
                log.debug("auto-bid stale price (race), retrying: userId={} auctionId={} reason={}",
                    winner.getUserId(), auctionId, e.getMessage());

            } catch (AuctionClosedException e) {
                // Phiên đã đóng — cancel entry và thoát vòng lặp hẳn.
                log.info("auto-bid stopped — auction closed: auctionId={}", auctionId);
                registry.cancel(winner.getUserId(), auctionId);
                break;

            } catch (Exception e) {
                // Lỗi không mong muốn (user bị ban, ví trống, ...) → cancel entry.
                log.warn("auto-bid failed, cancelling: userId={} auctionId={} reason={}",
                    winner.getUserId(), auctionId, e.getMessage());
                registry.cancel(winner.getUserId(), auctionId);
            }
        }

        notifyExhaustedBidders(auction);
    }

    /**
     * Dọn dẹp lịch sử bid của phiên khi phiên kết thúc.
     * Gọi từ {@code AuctionTimerService} cùng lúc với {@code AutoBidRegistry.clearAuction()}.
     */
    public static void clearAuctionActivity(String auctionId) {
        recentBidTimes.remove(auctionId);
        lastAutoBidNanoByAuction.remove(auctionId);
    }

    /**
     * Dọn dẹp per-user rate limit tracker khi user disconnect hoặc auction kết thúc.
     * Gọi tùy chọn — nếu không gọi, entry sẽ tồn tại trong memory nhưng không gây lỗi.
     *
     * @param userId ID của user cần xóa tracker
     */
    public static void clearUserActivity(String userId) {
        lastAutoBidNanoByUser.remove(userId);
    }

    /**
     * Đảm bảo khoảng cách tối thiểu giữa hai auto-bid liên tiếp trên cùng một phiên.
     * FIX: dùng AtomicLong.getAndSet() thay vì ConcurrentHashMap.put(Long).
     * Cũ: hai thread đồng thời gọi put() đều thấy last=null → cả hai skip delay.
     * Mới: getAndSet() là atomic — chỉ một thread thấy last=0 (first time), thread còn lại
     * thấy last=now của thread kia và phải chờ nếu chưa đủ interval.
     */
    private static void enforceAutoBidInterval(String auctionId) {
        long minNanos = 80L * 1_000_000L;
        long now = System.nanoTime();
        java.util.concurrent.atomic.AtomicLong tracker =
            lastAutoBidNanoByAuction.computeIfAbsent(
                auctionId, k -> new java.util.concurrent.atomic.AtomicLong(0L));
        long last = tracker.getAndSet(now);
        if (last == 0L) return; // lần đầu tiên, không cần delay

        long elapsed = now - last;
        if (elapsed < minNanos) {
            try {
                long sleepMs = (minNanos - elapsed) / 1_000_000L;
                if (sleepMs > 0) TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Per-user bid limiter: kiểm tra xem user có được phép auto-bid ngay bây giờ không.
     *
     * <p>Thuật toán: non-blocking token check dùng AtomicLong.compareAndSet().
     * Nếu user đã bid trong vòng {@code USER_AUTO_BID_MIN_NANOS} (1 giây) → từ chối (trả về false).
     * Nếu được phép → cập nhật timestamp và trả về true.
     *
     * <p>Không sleep — trả về false ngay để vòng lặp process() tiếp tục với candidate khác,
     * tránh block toàn bộ chain vì một user đang trong cooldown.
     *
     * @param userId ID của user muốn auto-bid
     * @return {@code true} nếu được phép, {@code false} nếu còn trong cooldown 1 giây
     */
    private static boolean tryConsumeUserAutoBidToken(String userId) {
        long now = System.nanoTime();
        java.util.concurrent.atomic.AtomicLong tracker =
            lastAutoBidNanoByUser.computeIfAbsent(
                userId, k -> new java.util.concurrent.atomic.AtomicLong(0L));

        while (true) {
            long last = tracker.get();
            if (last != 0L && (now - last) < USER_AUTO_BID_MIN_NANOS) {
                return false; // còn trong cooldown — từ chối ngay, không sleep
            }
            // CAS: chỉ update nếu giá trị chưa bị thread khác thay đổi
            if (tracker.compareAndSet(last, now)) {
                return true;
            }
            // CAS thất bại → thread khác vừa cập nhật → đọc lại now và thử lại
            now = System.nanoTime();
        }
    }

    // ── Smart bid calculation ─────────────────────────────────────────────────

    private long calcSmartBid(long currentPrice, long maxBid, AutoBidPhase phase) {
        long base      = BidIncrementCalculator.calculate(currentPrice);
        long smartNext = currentPrice + Math.round(base * phase.multiplier());

        if (smartNext <= maxBid) return smartNext;

        // Fallback: thử base increment trước khi bỏ cuộc
        long baseNext = currentPrice + base;
        return (baseNext <= maxBid) ? baseNext : -1;
    }

    // ── Phase detection ───────────────────────────────────────────────────────

    private AutoBidPhase detectPhase(Auction auction) {
        long totalSec     = ChronoUnit.SECONDS.between(auction.getStartTime(), auction.getEndTime());
        long remainingSec = ChronoUnit.SECONDS.between(LocalDateTime.now(), auction.getEndTime());
        int  recentBids   = countRecentBids(auction.getId());
        return AutoBidPhase.detect(totalSec, remainingSec, recentBids);
    }

    // ── Recent bid activity (VERY_HOT detection) ──────────────────────────────

    private void recordBidActivity(String auctionId) {
        LocalDateTime now = LocalDateTime.now();
        // computeIfAbsent + add trên CopyOnWriteArrayList — thread-safe
        recentBidTimes.computeIfAbsent(auctionId, id -> new CopyOnWriteArrayList<>()).add(now);

        // Giới hạn kích thước để tránh memory leak — chỉ giữ 50 entry gần nhất
        List<LocalDateTime> list = recentBidTimes.get(auctionId);
        if (list != null && list.size() > 50) {
            list.remove(0);
        }
    }

    private int countRecentBids(String auctionId) {
        List<LocalDateTime> list = recentBidTimes.get(auctionId);
        if (list == null) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(AutoBidPhase.HOT_WINDOW_SEC);
        int count = 0;
        // Iteration trên CopyOnWriteArrayList là thread-safe — không ConcurrentModificationException
        for (LocalDateTime ts : list) {
            if (!ts.isBefore(cutoff)) count++;
        }
        return count;
    }

    // ── Notify helpers ────────────────────────────────────────────────────────

    private void sendAutoBidTriggeredNotify(
        AutoBidRegistry.AutoBidEntry entry, Auction auction, long bidAmount) {
        BidDTOs.AutoBidTriggeredDTO dto = new BidDTOs.AutoBidTriggeredDTO();
        dto.setAuctionId(auction.getId());
        dto.setBidAmount(bidAmount);
        dto.setNewCurrentPrice(auction.getCurrentPrice());
        dto.setRemainingMaxBid(entry.getMaxBid() - bidAmount);
        dto.setNowLeading(auction.getCurrentLeader() != null
            && auction.getCurrentLeader().getId().equals(entry.getUserId()));
        dto.setTimestamp(LocalDateTime.now());
        sessionManager.sendToUser(entry.getUserId(),
            Packet.of(PacketType.AUTO_BID_TRIGGERED_NOTIFY, dto));
    }

    private void notifyExhaustedBidders(Auction auction) {
        String auctionId = auction.getId();
        AutoBidPhase phase = detectPhase(auction);
        NormalUser leader  = auction.getCurrentLeader();
        String leaderId    = (leader != null) ? leader.getId() : null;

        for (AutoBidRegistry.AutoBidEntry entry : registry.getEntriesForAuction(auctionId)) {
            if (entry.getUserId().equals(leaderId)) continue;
            if (calcSmartBid(auction.getCurrentPrice(), entry.getMaxBid(), phase) < 0) {
                // Bug 3 fix: chỉ cancel và notify khi user ONLINE.
                // Nếu offline: giữ entry trong registry — khi user online trở lại và có bid mới
                // thì process() sẽ thấy budget không đủ và cancel+notify lúc đó.
                if (!sessionManager.isOnline(entry.getUserId())) {
                    log.debug("auto-bid exhausted but user offline, keeping entry: userId={} auctionId={}",
                        entry.getUserId(), auctionId);
                    continue;
                }
                BidDTOs.AutoBidExhaustedDTO dto = new BidDTOs.AutoBidExhaustedDTO();
                dto.setAuctionId(auctionId);
                dto.setMaxBid(entry.getMaxBid());
                dto.setCurrentPrice(auction.getCurrentPrice());
                dto.setLeadingBidderUsername(leader != null ? leader.getUsername() : "Unknown");
                sessionManager.sendToUser(entry.getUserId(),
                    Packet.of(PacketType.AUTO_BID_EXHAUSTED_NOTIFY, dto));
                registry.cancel(entry.getUserId(), auctionId);
                log.info("auto-bid exhausted: userId={} auctionId={} maxBid={}",
                    entry.getUserId(), auctionId, entry.getMaxBid());
            }
        }
    }

    private NormalUser findNormalUserById(String userId) {
        for (User u : AuctionManager.getInstance().getAllUsers()) {
            if (u.getId().equals(userId) && u instanceof NormalUser) return (NormalUser) u;
        }
        NormalUser fromDb = userDAO.findNormalUserById(userId);
        if (fromDb != null) AuctionManager.getInstance().addToUserList(fromDb);
        return fromDb;
    }
}