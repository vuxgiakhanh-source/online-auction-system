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
 * PERFORMANCE FIXES (theo thứ tự ưu tiên):
 *
 * FIX #1 — Logging: hot path không có log.debug/info
 *   → Kết hợp với logback.xml BidService level=WARN + AsyncAppender
 *
 * FIX #2 — Rejected bid KHÔNG ghi DB
 *   → Cũ: mọi reject đều INSERT vào bid_transactions (bottleneck chính)
 *   → Mới: chỉ throw exception, không INSERT
 *
 * FIX #3 — Per-auction lock (không lock toàn method)
 *   → ConcurrentHashMap<auctionId, Object> — auction khác không block nhau
 *   → Validate nhanh (eligibility, isOpen, hasJoined) chạy NGOÀI lock
 *   → Chỉ lock: isValidBid + updateBid + recordTx (3 bước cần atomicity)
 *
 * FIX #4 — joinAsNormalUser() không cần lock auction
 *   → Guard hasJoined() đủ để tránh double-join
 *
 * FIX #5 — DB write ngoài lock
 *   → auctionDAO.updateHighestPrice() sau khi release lock
 *   → Thread kế tiếp bid được ngay, không chờ DB round-trip
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
   * FIX #3: Per-auction lock.
   * Mỗi auctionId có 1 Object lock riêng — auction khác nhau không block nhau.
   */
  private final ConcurrentHashMap<String, Object> auctionLocks = new ConcurrentHashMap<>();

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
    bidder.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction.getId(), observer);
    auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
    userDAO.saveUserAuctionActivity(bidder.getId(), auction.getId(), "WATCHING");
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

    // ── TRONG LOCK: critical section per-auction ──────────────────────────
    Object lock = auctionLocks.computeIfAbsent(auction.getId(), k -> new Object());
    BidTransaction tx;
    boolean reserveMet;

    synchronized (lock) {
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

      // Cập nhật state auction (atomic)
      auction.updateBid(amount, bidder);
      reserveMet = auction.isReserveMet();

      // FIX #2: chỉ ghi DB cho bid ACCEPTED (không bao giờ ghi REJECTED nữa)
      BidResult result = reserveMet ? BidResult.ACCEPTED : BidResult.ACCEPTED_RESERVE_NOT_MET;
      tx = recordTransaction(bidder, auction, amount, result);
      auction.addBidTransactionId(tx.getId());
    }
    // ── Hết critical section ──────────────────────────────────────────────

    // ── NGOÀI LOCK: DB + notify (không block bid tiếp theo) ──────────────
    // FIX #5: updateHighestPrice sau lock → thread khác không chờ DB round-trip
    auctionDAO.updateHighestPrice(auction.getId(), amount, bidder.getId());

    if (reserveMet) {
      auctionService.notify(auction, AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
    } else {
      auctionService.notify(auction, AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET, bidder, amount);
    }

    // Anti-sniping
    LocalDateTime currentEnd = auction.getEndTime();
    if (currentEnd != null) {
      long secondsLeft = Duration.between(LocalDateTime.now(), currentEnd).getSeconds();
      if (secondsLeft >= 0 && secondsLeft <= ANTI_SNIPING_WINDOW_SECONDS) {
        synchronized (lock) {
          auction.extendEndTime(Duration.ofSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
        }
        auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
        auctionService.notify(auction, AuctionEvent.AuctionEventType.AUCTION_EXTENDED, bidder, amount,
                String.format("Phiên được gia hạn thêm %ds (anti-sniping).", ANTI_SNIPING_EXTENSION_SECONDS));
      }
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
            && bidder.getAllAuctionIds().contains(auction.getId())) {
      log.warn("Join rejected — seller bid own auction: auctionId={}, bidderId={}",
              auction.getId(), bidder.getId());
      throw new AuctionBusinessException(AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
    }
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
    walletService.lockDeposit(bidder, depositAmount, auction.getId());
    registerJoin(bidder, auction, observer);
    log.warn("Bidder joined: auctionId={}, bidderId={}, deposit={}",
            auction.getId(), bidder.getId(), depositAmount);
  }

  private void joinAsAdmin(User admin, Auction auction, AuctionObserver observer) {
    registerJoin(admin, auction, observer);
  }

  private void registerJoin(User user, Auction auction, AuctionObserver observer) {
    user.addJoinedAuction(auction.getId());
    user.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction.getId(), observer);
    auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
    userDAO.saveUserAuctionActivity(user.getId(), auction.getId(), "JOINED");
  }

  /**
   * Tạo và lưu BidTransaction — chỉ gọi cho bid ACCEPTED.
   * FIX #2: method này không bao giờ được gọi cho BidResult.REJECTED.
   */
  private BidTransaction recordTransaction(NormalUser bidder, Auction auction,
                                           long amount, BidResult result) {
    BidTransaction tx = BidTransaction.create(bidder, auction.getId(), amount, result);
    bidder.addBidToHistory(tx);
    bidTransactionDAO.saveTransaction(tx);
    return tx;
  }

  private static AuthenticationException buildIneligibleException(NormalUser bidder) {
    switch (bidder.getAccountStatus()) {
      case BANNED:    return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_BANNED);
      case SUSPENDED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
      default:        return new AuthenticationException(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }
  }
}