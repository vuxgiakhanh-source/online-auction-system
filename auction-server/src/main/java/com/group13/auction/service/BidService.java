package com.group13.auction.service;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IBidService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.BidStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PERFORMANCE & CORRECTNESS FIXES:
 *
 * FIX #1 — Logging: hot path không có log.debug/info
 *   → logback.xml BidService level=WARN + AsyncAppender
 *
 * FIX #2 — Rejected bid KHÔNG ghi DB
 *   → Cũ: mọi reject đều INSERT vào bid_transactions
 *   → Mới: chỉ throw exception, không INSERT
 *
 * FIX #3 — Per-auction lock (ReentrantLock từ AuctionLockRegistry)
 *   → Validate nhanh (eligibility, isOpen, hasJoined) chạy NGOÀI lock
 *   → Trong lock: isValidBid + updateBid + tạo TX object
 *
 * FIX #4 — joinAsNormalUser() dùng tryMarkJoined() atomic gate
 *
 * FIX #5 — DB writes NGOÀI lock để giảm lock hold time
 *   → bidTransactionDAO.saveTransaction(tx): TX object tạo trong lock,
 *      lưu DB ngoài lock (TX có UUID rồi, không cần lock để persist)
 *   → auctionDAO.updateHighestPrice(): dùng conditional SQL
 *      (WHERE current_price < ?) để tránh stale-write race condition
 *
 * FIX #6 — Race condition stale-write (BUG CHÍNH trong load test):
 *   TRƯỚC:  Thread A unlock → Thread B unlock+writeDB(giá cao) →
 *           Thread A writeDB(giá cũ) → OVERWRITE giá cao! ✗
 *   SAU:    WHERE current_price < ? → Thread A's stale write = no-op ✓
 *   → Giá DB luôn = giá CAO NHẤT đã được chấp nhận trong RAM
 * ═══════════════════════════════════════════════════════════════════
 */
public class BidService implements IBidService {

  private static final Logger log = LoggerFactory.getLogger(BidService.class);

  private static final long ANTI_SNIPING_WINDOW_SECONDS = 30;
  private static final long ANTI_SNIPING_EXTENSION_SECONDS = 60;

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;
  private final BidTransactionDAO bidTransactionDAO;
  private final AuctionDAO auctionDAO;
  private final UserDAO userDAO;

  /**
   * FIX: Dùng AuctionLockRegistry thay vì map riêng —
   * (1) cùng lock với BidHandler nên ReentrantLock reentrant tránh deadlock,
   * (2) lockRegistry.release() trong AuctionTimerService dọn sạch entry → không leak.
   */
  private final AuctionLockRegistry lockRegistry = AuctionLockRegistry.getInstance();

  public BidService(
      IAuctionService auctionService,
      IRatingService ratingService,
      IWalletService walletService,
      BidTransactionDAO bidTransactionDAO,
      AuctionDAO auctionDAO,
      UserDAO userDAO) {
    this.auctionService = auctionService;
    this.ratingService = ratingService;
    this.walletService = walletService;
    this.bidTransactionDAO = bidTransactionDAO;
    this.auctionDAO = auctionDAO;
    this.userDAO = userDAO;
  }

  // =========================================================================
  // Public API
  // =========================================================================

  /** FIX #4 (rev2): tryMarkJoined() là atomic gate — ConcurrentHashMap.add() trả về false nếu đã tồn tại.
   * Tránh race window giữa hasJoined() check và addJoinedAuction() call. */
  @Override
  public void joinAuction(User user, Auction auction, AuctionObserver observer) {
    // Atomic check-and-mark: chỉ 1 thread được phép tiếp tục join
    if (!user.tryMarkJoined(auction.getId())) {
      log.warn("User already joined: userId={}, auctionId={}", user.getId(), auction.getId());
      return;
    }
    // Đã mark joined — nếu join thất bại thì phải unmark để không block join lại sau
    try {
      if (user instanceof NormalUser) {
        joinAsNormalUser((NormalUser) user, auction, observer);
      } else {
        joinAsAdmin(user, auction, observer);
      }
    } catch (RuntimeException e) {
      // Rollback mark nếu join thất bại (ineligible, insufficient deposit, v.v.)
      user.removeJoinedAuction(auction.getId());
      throw e;
    }
  }

  @Override
  public void watchAuction(User bidder, Auction auction, AuctionObserver observer) {
    java.util.concurrent.locks.ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      // FIX viewCount +2: addToWatchList() đã idempotent (check contains trước khi add),
      // nhưng incrementViewerCount() không có guard — gọi 2 lần (vd: join rồi watch,
      // hoặc click 2 lần) sẽ cộng +2. Check watchList TRƯỚC khi increment để đảm bảo
      // mỗi user chỉ đóng góp đúng 1 vào viewerCount.
      boolean alreadyWatching = bidder.getWatchListAuctionIds().contains(auction.getId());
      bidder.addToWatchList(auction.getId());
      auctionService.addObserver(auction.getId(), observer);
      if (!alreadyWatching) {
        auction.incrementViewerCount();
        auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
      }
      // FIX: không ghi WATCHING nếu user đã JOINED.
      // Bảng user_auction_activity có PK (user_id, auction_id) — chỉ 1 dòng mỗi cặp.
      // Nếu ghi WATCHING đè lên JOINED, findJoinedAuctionIdsByUserId() sẽ miss auction này
      // → placeBid() báo NOT_JOINED_AUCTION dù user đã join thành công.
      // Lớp bảo vệ thứ 2 (lớp 1 là SQL IF trong saveUserAuctionActivity).
      if (!bidder.hasJoined(auction.getId())) {
        userDAO.saveUserAuctionActivity(bidder.getId(), auction.getId(), "WATCHING");
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Đặt giá — flow 3 vùng tối ưu throughput:
   *
   * [NGOÀI LOCK] validate nhanh in-memory → throw ngay nếu sai, không tốn lock
   * [TRONG LOCK ] isValidBid + updateBid + recordTx   (atomic, ngắn nhất có thể)
   * [NGOÀI LOCK] DB write + notify + anti-sniping     (không block bid tiếp theo)
   */
  @Override
  public void placeBid(NormalUser bidder, Auction auction,
                       long amount, BidStrategy strategy) {

    // ── NGOÀI LOCK: validate nhanh ────────────────────────────────────────
    if (!ratingService.isEligible(bidder)) {
      // FIX #1 + #2: chỉ WARN, không ghi DB
      log.warn("Bid rejected — ineligible: auctionId={}, bidderId={}, status={}",
          auction.getId(), bidder.getId(), bidder.getAccountStatus());
      throw buildIneligibleException(bidder);
    }

    if (!auction.isAcceptingBids()) {
      log.warn("Bid rejected — auction closed: auctionId={}, bidderId={}",
          auction.getId(), bidder.getId());
      throw new AuctionClosedException(auction.getStatus());
    }

    if (!bidder.hasJoined(auction.getId())) {
      log.warn("Bid rejected — not joined: auctionId={}, bidderId={}",
          auction.getId(), bidder.getId());
      throw new AuctionBusinessException(AuctionBusinessException.Reason.NOT_JOINED_AUCTION);
    }

    // Phát hiện bid thao túng: bid > 3× giá hiện tại → tịch thu cọc ngay
    // Mục đích: chặn pump-and-dump (đẩy giá ảo rồi bỏ chạy).
    long currentPrice = auction.getCurrentPrice();
    if (currentPrice > 0 && amount > currentPrice * 3) {
      long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
      try {
        walletService.forfeitDeposit(bidder, depositAmount, auction.getId());
        log.warn("Deposit forfeited — manipulative bid detected: bidderId={}, auctionId={}, " +
            "currentPrice={}, bidAmount={}", bidder.getId(), auction.getId(), currentPrice, amount);
      } catch (RuntimeException e) {
        log.error("Failed to forfeit deposit for manipulative bid: bidderId={}, auctionId={}",
            bidder.getId(), auction.getId(), e);
      }
      // Vẫn cho bid tiếp tục — cọc đã bị phạt là đủ deterrent
    }

    // ── TRONG LOCK: critical section per-auction ──────────────────────────
    java.util.concurrent.locks.ReentrantLock lock = lockRegistry.getLock(auction.getId());
    BidTransaction tx;
    boolean reserveMet;
    boolean extendedForAntiSniping = false;

    lock.lock();
    try {
      // Re-check sau khi acquire lock (auction có thể vừa đóng)
      if (!auction.isAcceptingBids()) {
        log.warn("Bid rejected — auction closed (in lock): auctionId={}, bidderId={}",
            auction.getId(), bidder.getId());
        throw new AuctionClosedException(auction.getStatus());
      }

      // FIX #3: validate strategy trong lock — tránh race condition trên currentPrice
      // FIX #2: nếu invalid thì THROW THẲNG, không ghi DB
      if (!strategy.isValidBid(auction, amount)) {
        // StandardBidStrategy đã tự log.warn bên trong — không log lại ở đây
        throw new InvalidBidException(
            String.format("Bid %d không hợp lệ. Giá hiện tại: %d. %s",
                amount, auction.getCurrentPrice(), strategy.describe()),
            amount, auction.getCurrentPrice());
      }

      // Cập nhật state auction trong RAM (atomic, trong lock)
      auction.updateBid(amount, bidder);
      reserveMet = auction.isReserveMet();

      // Anti-sniping: đọc endTime và extend trong cùng critical section (tránh TOCTOU).
      LocalDateTime currentEnd = auction.getEndTime();
      if (currentEnd != null) {
        long secondsLeft = Duration.between(LocalDateTime.now(), currentEnd).getSeconds();
        if (secondsLeft >= 0 && secondsLeft <= ANTI_SNIPING_WINDOW_SECONDS) {
          auction.extendEndTime(Duration.ofSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
          extendedForAntiSniping = true;
        }
      }

      // FIX PERF: Tạo BidTransaction object TRONG lock để capture đúng state tại thời điểm bid.
      // Nhưng KHÔNG gọi bidTransactionDAO.saveTransaction() trong lock → tránh giữ lock
      // trong khi đợi DB round-trip (giảm lock hold time từ ~5ms xuống ~0.1ms).
      BidResult result = reserveMet ? BidResult.ACCEPTED : BidResult.ACCEPTED_RESERVE_NOT_MET;
      tx = BidTransaction.create(bidder, auction.getId(), amount, result);
      bidder.addBidToHistory(tx);
      auction.addBidTransactionId(tx.getId());
    } finally { lock.unlock(); }
    // ── Hết critical section ──────────────────────────────────────────────

    // ── NGOÀI LOCK: DB writes song song (không block bid tiếp theo) ───────
    // FIX RACE CONDITION: updateHighestPrice dùng conditional SQL (WHERE current_price < ?)
    // → ngay cả khi thread khác đã ghi giá cao hơn trước, query này sẽ là no-op.
    // FIX PERF: saveTransaction() cũng chạy ngoài lock → giảm lock contention.
    if (!bidTransactionDAO.saveTransactionAndUpdatePrice(
        tx, auction.getId(), amount, bidder.getId())) {
      log.error("Bid persist failed after RAM update: auctionId={}, bidderId={}, amount={}",
          auction.getId(), bidder.getId(), amount);
    }

    if (reserveMet) {
      auctionService.notify(auction, AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
    } else {
      auctionService.notify(auction, AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET, bidder, amount);
    }

    if (extendedForAntiSniping) {
      auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
      auctionService.notify(auction, AuctionEvent.AuctionEventType.AUCTION_EXTENDED, bidder, amount,
          String.format("Phiên được gia hạn thêm %ds (anti-sniping).", ANTI_SNIPING_EXTENSION_SECONDS));
    }
  }

  // =========================================================================
  // Private helpers
  // =========================================================================

  private void joinAsNormalUser(NormalUser bidder, Auction auction, AuctionObserver observer) {
    if (!ratingService.isEligible(bidder)) {
      log.warn("Join rejected — ineligible: auctionId={}, bidderId={}",
          auction.getId(), bidder.getId());
      throw buildIneligibleException(bidder);
    }
    if (bidder.hasRole(User.UserRole.SELLER)
        && auction.getItem().getSeller() != null
        && auction.getItem().getSeller().getId().equals(bidder.getId())) {
      log.warn("Join rejected — seller bid own auction: auctionId={}, bidderId={}",
          auction.getId(), bidder.getId());
      throw new AuctionBusinessException(AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
    }
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
    walletService.lockDeposit(bidder, depositAmount, auction.getId());
    try {
      registerJoin(bidder, auction, observer);
    } catch (RuntimeException e) {
      // FIX: rollback deposit nếu registerJoin thất bại (DB error, lock timeout, v.v.)
      // Trước đây: lockDeposit() xong → registerJoin() ném → joinAuction() catch chỉ xóa
      // joinedAuctionIds nhưng KHÔNG unlock deposit → user bị lock tiền mà không join được
      // → retry lần sau: double-lock → INSUFFICIENT_DEPOSIT dù balance đủ.
      log.warn("registerJoin failed, rolling back deposit: auctionId={}, bidderId={}, deposit={}",
          auction.getId(), bidder.getId(), depositAmount, e);
      walletService.unlockDeposit(bidder, depositAmount, auction.getId());
      throw e;
    }
    log.info("Bidder joined: auctionId={}, bidderId={}, deposit={}",
        auction.getId(), bidder.getId(), depositAmount);
  }

  private void joinAsAdmin(User admin, Auction auction, AuctionObserver observer) {
    registerJoin(admin, auction, observer);
  }

  private void registerJoin(User user, Auction auction, AuctionObserver observer) {
    java.util.concurrent.locks.ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      user.addJoinedAuction(auction.getId());
      // BUG FIX: chỉ incrementViewerCount nếu user chưa watch auction này.
      // Không có guard → user join/leave/rejoin cộng dồn auction.viewerCount liên tục
      // → DB viewerCount phình to mãi. watchAuction() đã có guard alreadyWatching nhưng
      // registerJoin() thì không. Display dùng getActiveViewerCount() nên UI không bị ảnh hưởng,
      // nhưng DB value ngày càng sai lệch.
      boolean alreadyWatching = user.getWatchListAuctionIds().contains(auction.getId());
      user.addToWatchList(auction.getId());
      auctionService.addObserver(auction.getId(), observer);
      if (!alreadyWatching) {
        auction.incrementViewerCount();
        int viewerCount = auction.getViewerCount();
        auctionDAO.updateViewerCount(auction.getId(), viewerCount);
      }
      userDAO.saveUserAuctionActivity(user.getId(), auction.getId(), "JOINED");
    } finally {
      lock.unlock();
    }
  }

  // recordTransaction() đã bị xóa:
  // TX object được tạo trực tiếp trong placeBid() bên trong lock.
  // bidTransactionDAO.saveTransaction() được gọi ngoài lock để giảm lock hold time.

  /**
   * Rời phiên: xử lý cọc (có phạt nếu đang là current leader), xóa join state khỏi in-memory VÀ DB.
   *
   * <p>Nếu user đang là current leader khi rời → mất 50% cọc (phạt bid cao rồi out).
   * Nếu chưa bid hoặc không phải leader → hoàn toàn bộ cọc.
   * Nếu auction == null (phiên đã xóa), bỏ qua wallet operation — không ném exception.
   */
  @Override
  public void leaveAuction(User user, Auction auction) {
    String auctionId = auction != null ? auction.getId() : null;

    if (user instanceof NormalUser && auction != null) {
      NormalUser bidder = (NormalUser) user;
      long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;

      // FIX RACE CONDITION: đọc currentLeader trong per-auction lock.
      // Nếu đọc ngoài lock: thread khác có thể place bid và thay leader
      // ngay giữa lúc đọc và quyết định forfeit → forfeit nhầm người không
      // còn dẫn đầu, hoặc bỏ sót người đang dẫn đầu.
      // placeBid() cũng acquire cùng lock này khi gọi auction.updateBid() →
      // đảm bảo snapshot currentLeader nhất quán với trạng thái đấu giá.
      java.util.concurrent.locks.ReentrantLock auctionLock = lockRegistry.getLock(auction.getId());
      auctionLock.lock();
      final boolean isCurrentLeader;
      try {
        NormalUser currentLeader = auction.getCurrentLeader();
        isCurrentLeader = currentLeader != null
            && currentLeader.getId().equals(bidder.getId());
      } finally {
        auctionLock.unlock();
        // Wallet operation (forfeit/unlock) xảy ra SAU khi release lock auction
        // để tránh giữ lock auction trong khi chờ DB — không thay đổi quyết định
        // vì snapshot isCurrentLeader đã được chốt trong lock.
      }

      try {
        if (isCurrentLeader) {
          // Leader tự rời phiên → tịch thu toàn bộ cọc
          walletService.forfeitDeposit(bidder, depositAmount, auction.getId());
          log.warn("Full deposit forfeited — leader left auction: userId={}, auctionId={}, deposit={}",
              bidder.getId(), auction.getId(), depositAmount);
        } else {
          // Không phải leader → hoàn toàn bộ cọc
          walletService.unlockDeposit(bidder, depositAmount, auction.getId());
          log.info("Deposit unlocked on leave (not leader): userId={}, auctionId={}, deposit={}",
              bidder.getId(), auction.getId(), depositAmount);
        }
      } catch (RuntimeException e) {
        log.error("Wallet operation failed on leave (continuing): userId={}, auctionId={}, reason={}",
            user.getId(), auction.getId(), e.getMessage());
      }
    }

    if (auctionId != null) {
      user.removeJoinedAuction(auctionId);
      userDAO.removeJoinedActivity(user.getId(), auctionId);
    }
    log.info("User left auction: userId={}, auctionId={}", user.getId(), auctionId);
  }

  private static AuthenticationException buildIneligibleException(NormalUser bidder) {
    switch (bidder.getAccountStatus()) {
      case BANNED:    return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_BANNED);
      case SUSPENDED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
      default:        return new AuthenticationException(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }
  }
}