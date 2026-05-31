package com.group13.auction.strategy;

import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.BidService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Xử lý chuỗi auto-bid chạy nền; mỗi phiên một luồng riêng, submit() trả về ngay. */
public class AutoBidProcessor {

  private static final Logger log = LoggerFactory.getLogger(AutoBidProcessor.class);

  // Cấu hình

  /** Giới hạn tuyệt đối vòng lặp chain — chống infinite loop. */
  private static final int MAX_CHAIN_ITERATIONS = 100;

  /** Số lần retry khi gặp InvalidBidException (stale price). */
  private static final int MAX_RETRIES_PER_STEP = 5;

  /**
   * Khoảng thời gian tối thiểu (ms) giữa 2 auto-bid liên tiếp trong 1 phiên. Không dùng sleep —
   * kiểm tra timestamp thụ động (non-blocking). 50ms = 20 auto-bid/giây/phiên.
   */
  private static final long AUTO_BID_MIN_INTERVAL_MS = 50L;

  /** TTL của UserCache entry (ms). 5 phút là đủ cho 1 phiên đấu giá. */
  private static final long USER_CACHE_TTL_MS = 5 * 60 * 1_000L;

  /** Khớp {@link BidService} ANTI_SNIPING_EXTENSION_SECONDS — dùng trong broadcast. */
  private static final int ANTI_SNIPING_EXTENSION_SECONDS = 60;

  // Dependencies

  private final BidService bidService;
  private final SessionManager sessionManager;
  private final AutoBidRegistry registry = AutoBidRegistry.getInstance();
  private final UserDAO userDAO;

  // Per-auction async infrastructure

  /**
   * Map auctionId → SingleThreadExecutor.
   *
   * <p>SingleThread đảm bảo:
   *
   * <ul>
   *   <li>Chỉ 1 chain chạy per auction tại 1 thời điểm (không race condition giữa các bước).
   *   <li>Các auction khác nhau chạy song song hoàn toàn (mỗi cái trên thread riêng).
   * </ul>
   */
  private static final ConcurrentHashMap<String, ExecutorService> auctionExecutors =
      new ConcurrentHashMap<>();

  /**
   * Cờ "có task đang pending/chạy không" cho mỗi auction. Dùng trong cơ chế task-coalescing để
   * tránh xếp chồng chain.
   *
   * <p>Trạng thái: false = idle, true = running/pending.
   */
  private static final ConcurrentHashMap<String, AtomicBoolean> chainRunning =
      new ConcurrentHashMap<>();

  /**
   * Cờ "cần chạy lại chain sau khi chain hiện tại kết thúc không". Set true khi có trigger mới đến
   * trong lúc chain đang chạy.
   */
  private static final ConcurrentHashMap<String, AtomicBoolean> chainNeedsRecheck =
      new ConcurrentHashMap<>();

  // Shared state (static — tồn tại suốt vòng đời server)

  /** Recent bid tracker per auction — dùng để detect VERY_HOT phase. */
  private static final ConcurrentHashMap<String, BidActivityRing> bidActivityRings =
      new ConcurrentHashMap<>();

  /**
   * Timestamp của auto-bid cuối cùng per auction. Dùng để kiểm tra throttle (thay thế
   * Thread.sleep).
   */
  private static final ConcurrentHashMap<String, AtomicLong> lastAutoBidMs =
      new ConcurrentHashMap<>();

  /** User cache chia sẻ toàn server — tránh DB lookup lặp lại. */
  private static final UserCache userCache = new UserCache(USER_CACHE_TTL_MS);

  // Constructor

  /** Tạo bộ xử lý Auto-Bid với các dependency cần thiết cho đặt giá và broadcast. */
  public AutoBidProcessor(BidService bidService, SessionManager sessionManager) {
    this.bidService = bidService;
    this.sessionManager = sessionManager;
    this.userDAO = new UserDAO();
  }

  // =========================================================================
  // Public API
  // =========================================================================

  /**
   * Đăng ký trigger auto-bid — NON-BLOCKING với production caller, trả về ngay lập tức.
   *
   * <p><b>Cơ chế task-coalescing:</b>
   *
   * <ol>
   *   <li>Nếu không có chain nào đang chạy → set running=true, submit task mới.
   *   <li>Nếu đã có chain chạy → set needsRecheck=true. Chain tự loop lại sau khi xong.
   * </ol>
   *
   * <p>Kết quả: dù có 100 bid/giây đến, mỗi auction chỉ có tối đa 1 task queued + 1 "rerun signal",
   * không bao giờ stack thêm. Zero wasted task submissions.
   *
   * <p><b>Threading:</b> Caller thread (BidHandler) trả về ngay — chain chạy trên executor riêng.
   * Ngoại lệ duy nhất: khi gọi từ autobid- worker thread (recheck recursive) sẽ block tới 5s để
   * tránh deadlock.
   *
   * @param auction phiên vừa có bid mới
   * @param triggeredByUserId userId vừa bid (cho context logging)
   */
  public void submit(Auction auction, String triggeredByUserId) {
    final Auction liveAuction = resolveLiveAuction(auction);
    String auctionId = liveAuction.getId();

    AtomicBoolean running = chainRunning.computeIfAbsent(auctionId, k -> new AtomicBoolean(false));
    AtomicBoolean recheck =
        chainNeedsRecheck.computeIfAbsent(auctionId, k -> new AtomicBoolean(false));

    recordBidActivity(auctionId);

    if (running.compareAndSet(false, true)) {
      // Chúng ta "win" quyền submit task — không ai khác đang chạy
      Future<?> chainFuture =
          getOrCreateExecutor(auctionId)
              .submit(
                  () -> {
                    try {
                      // Vòng lặp ngoài: tiếp tục nếu có recheck được đánh dấu trong lúc chain chạy
                      do {
                        recheck.set(
                            false); // Reset trước khi chạy — trigger mới trong lúc chạy sẽ set lại
                        runChain(liveAuction, triggeredByUserId);
                      } while (recheck.getAndSet(false));
                      // getAndSet(false): đọc giá trị hiện tại VÀ reset về false atomically.
                      // Nếu true → có trigger mới đến trong lúc chain chạy → loop lại 1 lần nữa.
                      // Nếu false → không có trigger mới → thoát.

                    } catch (Exception e) {
                      log.error("[AutoBid] Chain crashed unexpectedly: auctionId={}", auctionId, e);
                    } finally {
                      running.set(false);
                      // Sau khi set running=false, kiểm tra lần cuối xem có recheck pending không
                      // (edge case: trigger đến giữa getAndSet và running.set(false))
                      if (recheck.getAndSet(false)) {
                        submit(liveAuction, triggeredByUserId + "_recheck");
                      }
                    }
                  });
      // submit() là NON-BLOCKING hoàn toàn — chain chạy trên executor riêng.
      // Tests dùng awaitAutoBidChain() để chờ chain xong thay vì block ở đây.
      // KHÔNG dùng chainFuture.get(): nếu finally block gọi lại submit() từ autobid- thread
      // → thread đó block chờ chính executor SingleThread của mình → deadlock.
      log.debug(
          "[AutoBid] Chain task submitted: auctionId={} trigger={}", auctionId, triggeredByUserId);
    } else {
      // Chain đang chạy → đánh dấu recheck để chain tự loop lại sau khi xong
      recheck.set(true);
      log.debug(
          "[AutoBid] Chain already running, marked recheck: auctionId={} trigger={}",
          auctionId,
          triggeredByUserId);
    }
  }

  /**
   * Dọn dẹp toàn bộ state của phiên khi phiên kết thúc. Gọi từ {@code AuctionTimerService} (đã có
   * sẵn trong code hiện tại).
   *
   * <p>Shutdown executor gracefully: chờ task hiện tại kết thúc (tối đa 2s), sau đó force-kill nếu
   * cần. Không leak thread.
   */
  public static void clearAuctionActivity(String auctionId) {
    shutdownAuction(auctionId);
  }

  /** Shutdown executor của phiên — tách riêng để dễ test và gọi từ nhiều nơi. */
  public static void shutdownAuction(String auctionId) {
    // Dừng executor
    ExecutorService executor = auctionExecutors.remove(auctionId);
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
          executor.shutdownNow();
          log.warn("[AutoBid] Executor force-shutdown after timeout: auctionId={}", auctionId);
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // Xóa state
    chainRunning.remove(auctionId);
    chainNeedsRecheck.remove(auctionId);
    bidActivityRings.remove(auctionId);
    lastAutoBidMs.remove(auctionId);

    log.debug("[AutoBid] Auction state cleared: auctionId={}", auctionId);
  }

  // =========================================================================
  // Chain logic (chạy trên executor thread — không phải caller thread)
  // =========================================================================

  /**
   * Chuỗi auto-bid chính. Chạy hoàn toàn trên thread của executor, KHÔNG block thread của
   * BidHandler.
   *
   * <p>Dừng khi:
   *
   * <ul>
   *   <li>Không còn candidate nào đủ budget / đang online.
   *   <li>Phiên đã đóng.
   *   <li>Đạt giới hạn an toàn {@link #MAX_CHAIN_ITERATIONS}.
   *   <li>3 bước liên tiếp không có tiến triển (no-progress guard).
   * </ul>
   */
  private void runChain(Auction auction, String triggeredByUserId) {
    String auctionId = auction.getId();
    auction = resolveLiveAuction(auction);
    log.info("[AutoBid] Chain started: auctionId={} trigger={}", auctionId, triggeredByUserId);

    int iteration = 0;
    int noProgressCount = 0;

    while (iteration < MAX_CHAIN_ITERATIONS) {
      iteration++;

      Collection<AutoBidRegistry.AutoBidEntry> entries = registry.getEntriesForAuction(auctionId);
      if (entries.isEmpty()) {
        log.info("[AutoBid] Chain stopped — no entries: auctionId={}", auctionId);
        break;
      }

      String leaderId = currentLeaderId(auction);
      AutoBidPhase phase = detectPhase(auction);

      List<AutoBidRegistry.AutoBidEntry> candidates =
          buildCandidates(entries, leaderId, auction, phase);
      if (candidates.isEmpty()) {
        log.info(
            "[AutoBid] Chain stopped — no candidates: auctionId={} leader={} step={}",
            auctionId,
            leaderId,
            iteration);
        break;
      }

      // Sort: maxBid DESC (ưu tiên người trả nhiều nhất),
      //       registeredAt ASC (tie-break: đăng ký sớm hơn thắng)
      candidates.sort(
          Comparator.comparingLong(AutoBidRegistry.AutoBidEntry::getMaxBid)
              .reversed()
              .thenComparing(AutoBidRegistry.AutoBidEntry::getRegisteredAt));

      AutoBidRegistry.AutoBidEntry winner = candidates.get(0);

      // Throttle: kiểm tra non-blocking — không sleep, chỉ skip nếu quá nhanh
      if (!checkAndUpdateThrottle(auctionId)) {
        log.debug("[AutoBid] Throttled, yielding: auctionId={} step={}", auctionId, iteration);
        // Ngủ ngắn 1 lần để tránh busy-wait khi throttle
        sleepQuietly(AUTO_BID_MIN_INTERVAL_MS);
      }

      NormalUser autoBidder = resolveUser(winner.getUserId(), auctionId);
      if (autoBidder == null) {
        // Không cancel registry khi resolveUser thất bại (có thể do DB tạm chậm,
        // user chưa được load vào RAM, hoặc joinedAuctionIds chưa sync).
        // Cancel vĩnh viễn khiến chain dừng sớm dù maxBid rất cao — đây là nguyên nhân
        // chính khiến autobid chỉ chạy 1 bước khi 2 user có maxBid bằng nhau.
        // Thay vì cancel → chỉ bỏ qua iteration này, cho chain cơ hội tự phục hồi.
        log.warn(
            "[AutoBid] User not resolved (skipping, NOT cancelling): userId={} auctionId={}",
            winner.getUserId(),
            auctionId);
        noProgressCount++;
        if (noProgressCount >= 3) {
          log.warn(
              "[AutoBid] Chain stopped — 3 consecutive resolve failures: auctionId={}",
              auctionId);
          break;
        }
        continue;
      }

      boolean bidded = attemptBid(autoBidder, winner, auction, phase, iteration);

      if (bidded) {
        noProgressCount = 0;
        recordBidActivity(auctionId);
      } else {
        noProgressCount++;
        if (noProgressCount >= 3) {
          log.warn("[AutoBid] Chain stopped — 3 no-progress steps: auctionId={}", auctionId);
          break;
        }
      }
    }

    if (iteration >= MAX_CHAIN_ITERATIONS) {
      log.warn(
          "[AutoBid] Chain stopped — safety limit {} reached: auctionId={}",
          MAX_CHAIN_ITERATIONS,
          auctionId);

      // Nếu chain dừng do safety limit nhưng vẫn còn candidates hợp lệ
      // (2 user cùng maxBid rất cao), không có gì trigger submit() lại vì
      // không có bid thủ công nào từ bên ngoài.
      // Giải pháp: dùng chainNeedsRecheck flag — cơ chế recheck đã có sẵn trong submit().
      // Sau khi runChain() return, finally block của submit() sẽ thấy recheck=true
      // và tự gọi submit() lại — không cần thread mới hay schedule.
      final Auction auctionSnapshot = auction; // effectively final for lambda
      AutoBidPhase phaseCheck = detectPhase(auctionSnapshot);
      String leaderIdCheck = currentLeaderId(auctionSnapshot);
      boolean stillHasCandidates =
          registry.getEntriesForAuction(auctionId).stream()
              .anyMatch(
                  e ->
                      !e.getUserId().equals(leaderIdCheck)
                          && calcSmartBid(auctionSnapshot.getCurrentPrice(), e.getMaxBid(), phaseCheck) > 0);

      if (stillHasCandidates) {
        log.info(
            "[AutoBid] Marking recheck after safety limit — candidates still exist: auctionId={}",
            auctionId);
        // Set recheck=true — cơ chế trong submit() sẽ tự chạy lại chain sau khi runChain() return.
        // An toàn: không có deadlock, không cần thread mới.
        chainNeedsRecheck
            .computeIfAbsent(auctionId, k -> new java.util.concurrent.atomic.AtomicBoolean(false))
            .set(true);
      }
    }

    notifyExhaustedBidders(auction);
    log.info("[AutoBid] Chain finished: auctionId={} steps={}", auctionId, iteration);
  }

  // =========================================================================
  // Bid attempt (với retry)
  // =========================================================================

  /**
   * Thực hiện 1 bước bid cho winner với retry khi stale price.
   *
   * @return true nếu bid thành công, false nếu không thể bid
   */
  private boolean attemptBid(
      NormalUser autoBidder,
      AutoBidRegistry.AutoBidEntry winner,
      Auction auction,
      AutoBidPhase phase,
      int step) {
    String auctionId = auction.getId();

    for (int retry = 0; retry <= MAX_RETRIES_PER_STEP; retry++) {
      long nextBid = calcSmartBid(auction.getCurrentPrice(), winner.getMaxBid(), phase);
      if (nextBid < 0) {
        log.debug(
            "[AutoBid] Budget exhausted: userId={} auctionId={} price={} maxBid={}",
            winner.getUserId(),
            auctionId,
            auction.getCurrentPrice(),
            winner.getMaxBid());
        return false;
      }

      try {
        LocalDateTime endTimeBefore = auction.getEndTime();
        long previousPrice = auction.getCurrentPrice();
        AutoBidStrategy strategy = new AutoBidStrategy(winner.getMaxBid());
        bidService.placeBid(autoBidder, auction, nextBid, strategy);

        broadcastAutoBidSideEffects(
            auction, auctionId, nextBid, previousPrice, endTimeBefore, autoBidder);

        log.info(
            "[AutoBid] Bid placed: userId={} username={} auctionId={} amount={} phase={} step={}"
                + " retry={}",
            autoBidder.getId(),
            autoBidder.getUsername(),
            auctionId,
            nextBid,
            phase,
            step,
            retry);
        return true;

      } catch (InvalidBidException e) {
        if (retry >= MAX_RETRIES_PER_STEP) {
          log.warn(
              "[AutoBid] Stale price max retries: userId={} auctionId={} reason={}",
              winner.getUserId(),
              auctionId,
              e.getMessage());
          return false;
        }
        log.debug(
            "[AutoBid] Stale price retry {}/{}: userId={} auctionId={}",
            retry + 1,
            MAX_RETRIES_PER_STEP,
            winner.getUserId(),
            auctionId);
        // Backoff tuyến tính ngắn: 5ms, 10ms, 15ms, ... — không phải Thread.sleep dài
        sleepQuietly(5L * (retry + 1));

      } catch (AuctionClosedException e) {
        log.info("[AutoBid] Auction closed mid-bid: auctionId={}", auctionId);
        registry.cancel(winner.getUserId(), auctionId);
        return false;

      } catch (Exception e) {
        log.warn(
            "[AutoBid] Bid failed, cancelling: userId={} auctionId={} err={}",
            winner.getUserId(),
            auctionId,
            e.getMessage());
        registry.cancel(winner.getUserId(), auctionId);
        return false;
      }
    }
    return false;
  }

  /** Broadcast BID_UPDATE / anti-sniping giống {@link com.group13.auction.network.server.handler.BidHandler}. */
  private void broadcastAutoBidSideEffects(
      Auction auction,
      String auctionId,
      long confirmedAmount,
      long previousPrice,
      LocalDateTime endTimeBefore,
      NormalUser autoBidder) {
    LocalDateTime endTimeAfter = auction.getEndTime();
    boolean extended =
        endTimeBefore != null
            && endTimeAfter != null
            && !endTimeAfter.equals(endTimeBefore);

    BidDTOs.BidUpdateDTO update = DTOMapper.toBidUpdateDTO(auction, confirmedAmount, previousPrice);
    if (extended) {
      update.setNewEndTime(endTimeAfter);
      AuctionDTOs.AuctionExtendedDTO extDto = new AuctionDTOs.AuctionExtendedDTO();
      extDto.setAuctionId(auctionId);
      extDto.setNewEndTime(endTimeAfter);
      extDto.setExtendedBySeconds(ANTI_SNIPING_EXTENSION_SECONDS);
      sessionManager.broadcastToAuctionAsync(
          auctionId, Packet.of(PacketType.AUCTION_EXTENDED_NOTIFY, extDto));
    }
    PacketType broadcastType =
        auction.isReserveMet() ? PacketType.BID_UPDATE : PacketType.BID_RESERVE_NOT_MET_UPDATE;
    sessionManager.broadcastToAuctionAsync(auctionId, Packet.of(broadcastType, update));
    sessionManager.broadcastToAuctionAsync(
        auctionId,
        Packet.of(
            PacketType.BID_CHART_POINT_UPDATE,
            DTOMapper.toBidChartPoint(
                auctionId, confirmedAmount, autoBidder.getUsername(), false)));
  }

  // =========================================================================
  // Candidate selection
  // =========================================================================

  private List<AutoBidRegistry.AutoBidEntry> buildCandidates(
      Collection<AutoBidRegistry.AutoBidEntry> entries,
      String leaderId,
      Auction auction,
      AutoBidPhase phase) {

    List<AutoBidRegistry.AutoBidEntry> result = new ArrayList<>();
    long price = auction.getCurrentPrice();

    for (AutoBidRegistry.AutoBidEntry e : entries) {
      if (e.getUserId().equals(leaderId)) {
        continue;
      }
      if (calcSmartBid(price, e.getMaxBid(), phase) > 0) {
        result.add(e);
      }
    }
    return result;
  }

  // =========================================================================
  // Smart bid calculation
  // =========================================================================

  /**
   * Tính giá bid thông minh theo phase.
   *
   * <ul>
   *   <li>EARLY/MID → base increment (tiết kiệm).
   *   <li>LATE → 1.5× increment (aggressive).
   *   <li>VERY_HOT → 2.0× increment hoặc dùng hết maxBid nếu cần.
   * </ul>
   *
   * @return giá bid tiếp theo, hoặc -1 nếu không đủ budget
   */
  private long calcSmartBid(long currentPrice, long maxBid, AutoBidPhase phase) {
    long base = BidIncrementCalculator.calculate(currentPrice);
    long smartNext = currentPrice + Math.round(base * phase.multiplier());

    if (smartNext <= maxBid) {
      return smartNext;
    }

    // Fallback 1: base increment
    long baseNext = currentPrice + base;
    if (baseNext <= maxBid) {
      return baseNext;
    }

    // Fallback 2 (chỉ LATE/VERY_HOT): dùng toàn bộ maxBid — tận dụng hết budget.
    // maxBid phải >= currentPrice + base (đáp ứng minimum increment),
    // nếu không isValidBid() sẽ từ chối → retry 5 lần vô ích → return false → chain dừng sớm.
    if ((phase == AutoBidPhase.LATE || phase == AutoBidPhase.VERY_HOT) && maxBid >= currentPrice + base) {
      return maxBid;
    }

    return -1;
  }

  // =========================================================================
  // Phase detection
  // =========================================================================

  private AutoBidPhase detectPhase(Auction auction) {
    long totalSec = ChronoUnit.SECONDS.between(auction.getStartTime(), auction.getEndTime());
    long remainingSec = ChronoUnit.SECONDS.between(LocalDateTime.now(), auction.getEndTime());
    int recentBids = countRecentBids(auction.getId());
    return AutoBidPhase.detect(totalSec, remainingSec, recentBids);
  }

  // =========================================================================
  // Throttle — non-blocking timestamp check
  // =========================================================================

  /**
   * Kiểm tra throttle bằng so sánh timestamp — KHÔNG sleep. Trả về true nếu đã qua đủ thời gian,
   * false nếu cần đợi.
   *
   * <p>Không dùng AtomicLong.getAndSet() nữa — thay bằng compareAndSet loop để tránh race condition
   * khi 2 thread cùng pass throttle. (Trên SingleThreadExecutor, điều này không xảy ra, nhưng
   * defensive code tốt hơn.)
   */
  private static boolean checkAndUpdateThrottle(String auctionId) {
    long now = System.currentTimeMillis();
    AtomicLong tracker = lastAutoBidMs.computeIfAbsent(auctionId, k -> new AtomicLong(0L));

    while (true) {
      long last = tracker.get();
      if (last != 0L && (now - last) < AUTO_BID_MIN_INTERVAL_MS) {
        return false; // quá sớm
      }
      if (tracker.compareAndSet(last, now)) {
        return true; // CAS thành công — đã cập nhật timestamp
      }
      // CAS thất bại → thread khác vừa cập nhật → đọc lại
      now = System.currentTimeMillis();
    }
  }

  // =========================================================================
  // Recent bid activity tracking — BidActivityRing
  // =========================================================================

  private void recordBidActivity(String auctionId) {
    bidActivityRings.computeIfAbsent(auctionId, k -> new BidActivityRing(50)).record();
  }

  private int countRecentBids(String auctionId) {
    BidActivityRing ring = bidActivityRings.get(auctionId);
    if (ring == null) {
      return 0;
    }
    long since = System.currentTimeMillis() - (AutoBidPhase.HOT_WINDOW_SEC * 1_000L);
    return ring.countSince(since);
  }

  // =========================================================================
  // Notifications
  // =========================================================================

  private void notifyExhaustedBidders(Auction auction) {
    String auctionId = auction.getId();
    AutoBidPhase phase = detectPhase(auction);
    String leaderId = currentLeaderId(auction);

    // Lấy username của leader để đưa vào thông báo (có thể null nếu chưa có ai bid)
    NormalUser leader = auction.getCurrentLeader();
    String leaderUsername = (leader != null) ? leader.getUsername() : null;

    for (AutoBidRegistry.AutoBidEntry entry : registry.getEntriesForAuction(auctionId)) {
      if (entry.getUserId().equals(leaderId)) {
        continue;
      }
      if (calcSmartBid(auction.getCurrentPrice(), entry.getMaxBid(), phase) >= 0) {
        continue;
      }

      String leaderName =
          leaderUsername != null && !leaderUsername.isBlank() ? leaderUsername : "Chưa có";
      ServerBroadcastNotifier.getInstance()
          .notifyAutoBidExhausted(
              entry.getUserId(),
              auction,
              entry.getMaxBid(),
              auction.getCurrentPrice(),
              leaderName);

      registry.cancel(entry.getUserId(), auctionId);
      log.info(
          "[AutoBid] Exhausted & cancelled: userId={} auctionId={} maxBid={}",
          entry.getUserId(),
          auctionId,
          entry.getMaxBid());
    }
  }

  // =========================================================================
  // Utility
  // =========================================================================

  private String currentLeaderId(Auction auction) {
    NormalUser leader = auction.getCurrentLeader();
    return leader != null ? leader.getId() : null;
  }

  /**
   * Tra cứu NormalUser cho chuỗi auto-bid — phải có {@code joinedAuctionIds} đúng.
   *
   * <p>FIX: Đăng ký auto-bid dùng {@code session.getCachedUser()} (đã join in-place), nhưng chain
   * trước đây ưu tiên {@link AuctionManager#getAllUsers()} — thường là bản user lúc login chưa có
   * JOINED trong RAM → {@link BidService#placeBid} ném NOT_JOINED → chain dừng dù maxBid rất cao.
   */
  private NormalUser resolveUser(String userId, String auctionId) {
    NormalUser cached = userCache.get(userId);
    if (cached != null && cached.hasJoined(auctionId)) {
      return cached;
    }

    for (User u : AuctionManager.getInstance().getAllUsers()) {
      if (u.getId().equals(userId) && u instanceof NormalUser inMemory) {
        if (!inMemory.hasJoined(auctionId)) {
          java.util.Set<String> joinedIds = userDAO.findJoinedAuctionIdsByUserId(userId);
          if (joinedIds != null && !joinedIds.isEmpty()) {
            inMemory.setJoinedAuctionIds(joinedIds);
          }
        }
        if (inMemory.hasJoined(auctionId)) {
          userCache.put(userId, inMemory);
          return inMemory;
        }
        break;
      }
    }

    NormalUser fromDb = userDAO.findNormalUserById(userId);
    if (fromDb != null && fromDb.hasJoined(auctionId)) {
      AuctionManager.getInstance().refreshUser(fromDb);
      userCache.put(userId, fromDb);
      return fromDb;
    }

    log.warn("[AutoBid] User not joined auction: userId={} auctionId={}", userId, auctionId);
    return null;
  }

  /** Luôn dùng bản Auction trong {@link AuctionManager} nếu có — tránh bid trên object tách rời. */
  private static Auction resolveLiveAuction(Auction auction) {
    if (auction == null) {
      return null;
    }
    Auction live = AuctionManager.getInstance().findAuctionById(auction.getId());
    return live != null ? live : auction;
  }

  private ExecutorService getOrCreateExecutor(String auctionId) {
    return auctionExecutors.computeIfAbsent(
        auctionId,
        id ->
            Executors.newSingleThreadExecutor(
                r -> {
                  Thread t = new Thread(r, "autobid-" + id.substring(0, Math.min(8, id.length())));
                  t.setDaemon(true); // không ngăn JVM shutdown
                  return t;
                }));
  }

  private static void sleepQuietly(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // =========================================================================
  // Inner class: BidActivityRing
  // =========================================================================

  /**
   * Ring buffer cho recent bid timestamps — thay thế CopyOnWriteArrayList + remove(0).
   *
   * <h3>Tại sao tốt hơn?</h3>
   *
   * <ul>
   *   <li>CopyOnWriteArrayList.remove(0): O(n) — copy toàn bộ array mỗi lần xóa đầu.
   *   <li>{@link ConcurrentLinkedDeque}: lock-free, addLast/pollFirst O(1).
   *   <li>Không cần synchronized — ConcurrentLinkedDeque thread-safe built-in.
   * </ul>
   *
   * <p><b>Thread-safety:</b> addLast và pollFirst là lock-free trên ConcurrentLinkedDeque.
   * countSince() iterates snapshot — an toàn với concurrent add.
   */
  private static final class BidActivityRing {

    private final ConcurrentLinkedDeque<Long> ring; // epoch millisecond timestamps
    private final int maxSize;

    BidActivityRing(int maxSize) {
      this.ring = new ConcurrentLinkedDeque<>();
      this.maxSize = maxSize;
    }

    /** Ghi nhận 1 bid xảy ra tại thời điểm hiện tại. */
    void record() {
      ring.addLast(System.currentTimeMillis());
      // Trim đầu nếu vượt maxSize — O(1) amortized
      while (ring.size() > maxSize) {
        ring.pollFirst();
      }
    }

    /**
     * Đếm số bid xảy ra từ {@code sinceMillis} đến nay. Scan từ cuối về đầu — dừng sớm khi gặp
     * timestamp cũ hơn ngưỡng.
     *
     * <p><b>Lưu ý:</b> Iterator của ConcurrentLinkedDeque iterates từ đầu → cuối. Chúng ta đếm từ
     * cuối (mới nhất) để break sớm khi gặp entry cũ. Dùng descendingIterator() để hiệu quả hơn.
     */
    int countSince(long sinceMillis) {
      int count = 0;
      // descendingIterator: từ cuối (mới nhất) về đầu (cũ nhất)
      Iterator<Long> it = ring.descendingIterator();
      while (it.hasNext()) {
        long ts = it.next();
        if (ts < sinceMillis) {
          break; // phần còn lại đều cũ hơn → dừng sớm
        }
        count++;
      }
      return count;
    }
  }

  // =========================================================================
  // Inner class: UserCache
  // =========================================================================

  /**
   * TTL-based user cache — thay thế scan O(n) qua AuctionManager.getAllUsers().
   *
   * <h3>Thiết kế:</h3>
   *
   * <ul>
   *   <li>ConcurrentHashMap: O(1) get/put, không lock global.
   *   <li>TTL per entry: evict tự nhiên khi get() thấy entry hết hạn.
   *   <li>Lazy eviction: không cần background cleanup thread — entries cũ bị xóa khi được access
   *       lần tiếp theo.
   *   <li>Không dùng Caffeine để tránh thêm dependency — đủ dùng cho use case này.
   * </ul>
   *
   * <p><b>Thread-safety:</b> ConcurrentHashMap đảm bảo get/put thread-safe. Entry bất biến sau khi
   * tạo — không cần thêm sync.
   *
   * <p><b>Khi nào cần Caffeine?</b> Khi cần: eviction policy phức tạp (LRU/LFU), async loading,
   * stats/metrics, hoặc cache size rất lớn (>10k entries). Với ~30-100 users per auction,
   * ConcurrentHashMap là đủ.
   */
  private static final class UserCache {

    private final long ttlMs;

    private static final class Entry {
      final NormalUser user;
      final long expiresAt;

      Entry(NormalUser user, long ttlMs) {
        this.user = user;
        this.expiresAt = System.currentTimeMillis() + ttlMs;
      }

      boolean isAlive() {
        return System.currentTimeMillis() < expiresAt;
      }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    UserCache(long ttlMs) {
      this.ttlMs = ttlMs;
    }

    /** Trả về user nếu cache còn sống, null nếu không có hoặc đã hết hạn. */
    NormalUser get(String userId) {
      Entry e = map.get(userId);
      if (e == null) {
        return null;
      }
      if (!e.isAlive()) {
        map.remove(userId, e); // xóa entry hết hạn
        return null;
      }
      return e.user;
    }

    /** Lưu user vào cache với TTL. */
    void put(String userId, NormalUser user) {
      map.put(userId, new Entry(user, ttlMs));
    }

    /** Xóa 1 user khỏi cache (ví dụ: sau khi user bị ban). */
    void invalidate(String userId) {
      map.remove(userId);
    }
  }
}