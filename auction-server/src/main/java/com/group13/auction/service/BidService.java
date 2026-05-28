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
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IBidService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import com.group13.auction.strategy.AuctionLockRegistry;
import com.group13.auction.strategy.AutoBidRegistry;
import com.group13.auction.strategy.BidStrategy;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 *
 * <p>═══════════════════════════════════════════════════════════════════ PERFORMANCE & CORRECTNESS
 * FIXES:
 *
 * <p>FIX #1 — Logging: hot path không có log.debug/info → logback.xml BidService level=WARN +
 * AsyncAppender
 *
 * <p>FIX #2 — Rejected bid KHÔNG ghi DB → Cũ: mọi reject đều INSERT vào bid_transactions → Mới: chỉ
 * throw exception, không INSERT
 *
 * <p>FIX #3 — Per-auction lock (ReentrantLock từ AuctionLockRegistry) → Validate nhanh
 * (eligibility, isOpen, hasJoined) chạy NGOÀI lock → Trong lock: isValidBid + updateBid + tạo TX
 * object
 *
 * <p>FIX #4 — joinAsNormalUser() dùng tryMarkJoined() atomic gate
 *
 * <p>FIX #5 — DB writes NGOÀI lock để giảm lock hold time → bidTransactionDAO.saveTransaction(tx):
 * TX object tạo trong lock, lưu DB ngoài lock (TX có UUID rồi, không cần lock để persist) →
 * auctionDAO.updateHighestPrice(): dùng conditional SQL (WHERE current_price < ?) để tránh
 * stale-write race condition
 *
 * <p>FIX #6 — Race condition stale-write (BUG CHÍNH trong load test): TRƯỚC: Thread A unlock →
 * Thread B unlock+writeDB(giá cao) → Thread A writeDB(giá cũ) → OVERWRITE giá cao! ✗ SAU: WHERE
 * current_price < ? → Thread A's stale write = no-op ✓ → Giá DB luôn = giá CAO NHẤT đã được chấp
 * nhận trong RAM
 *
 * <p>FIX #7 — Ghost bid khi non-leader rời phiên: BUG: cancelBidsByBidder() CHỈ gọi khi
 * isCurrentLeader=true. → Non-leader rời phiên mà bids vẫn ACCEPTED trong DB. → Khi leader sau đó
 * rời, findHighestValidBidExcept() trả về bid của người đã rời → set họ làm leader mới dù đã out!
 * FIX: cancelBidsByBidder() gọi LUÔN LUÔN cho mọi người rời phiên, không phân biệt leader hay
 * không. Chỉ rollback leader khi cần.
 * ═══════════════════════════════════════════════════════════════════
 */
public class BidService implements IBidService {

  private static final Logger log = LoggerFactory.getLogger(BidService.class);

  private static final long ANTI_SNIPING_WINDOW_SECONDS = 30;
  private static final long ANTI_SNIPING_EXTENSION_SECONDS = 60;

  /**
   * Kết quả trả về từ {@link #leaveAuction(User, Auction)}. Chứa đủ thông tin để BidHandler build
   * response mà không cần tính lại độc lập (tránh race condition do tính 2 lần ngoài lock).
   */
  public static class LeaveResult {
    public final boolean leaderChanged;
    public final boolean depositForfeited;
    public final long forfeitedAmount;
    public final boolean ratingPenalized;
    public final long newAvailableBalance;

    /** Giá trước khi leader rời phiên — dùng để tính priceChange trong broadcast. */
    public final long previousPrice;

    /** true nếu leader rời trong cửa sổ anti-sniping và phiên được gia hạn. */
    public final boolean extendedForAntiSniping;

    LeaveResult(
        boolean leaderChanged,
        boolean depositForfeited,
        long forfeitedAmount,
        boolean ratingPenalized,
        long newAvailableBalance,
        boolean extendedForAntiSniping,
        long previousPrice) {
      this.leaderChanged = leaderChanged;
      this.depositForfeited = depositForfeited;
      this.forfeitedAmount = forfeitedAmount;
      this.ratingPenalized = ratingPenalized;
      this.newAvailableBalance = newAvailableBalance;
      this.extendedForAntiSniping = extendedForAntiSniping;
      this.previousPrice = previousPrice;
    }

    /** Shorthand khi user không phải NormalUser hoặc auction null. */
    static LeaveResult noOp() {
      return new LeaveResult(false, false, 0L, false, 0L, false, 0L);
    }
  }

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;
  private final BidTransactionDAO bidTransactionDAO;
  private final AuctionDAO auctionDAO;
  private final UserDAO userDAO;

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

  @Override
  public void joinAuction(User user, Auction auction, AuctionObserver observer) {
    if (!user.tryMarkJoined(auction.getId())) {
      log.warn("User already joined: userId={}, auctionId={}", user.getId(), auction.getId());
      return;
    }
    try {
      if (user instanceof NormalUser) {
        joinAsNormalUser((NormalUser) user, auction, observer);
      } else {
        joinAsAdmin(user, auction, observer);
      }
    } catch (RuntimeException e) {
      user.removeJoinedAuction(auction.getId());
      throw e;
    }
  }

  @Override
  public void watchAuction(User bidder, Auction auction, AuctionObserver observer) {
    java.util.concurrent.locks.ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      boolean alreadyWatching = bidder.getWatchListAuctionIds().contains(auction.getId());
      bidder.addToWatchList(auction.getId());
      auctionService.addObserver(auction.getId(), observer);
      if (!alreadyWatching) {
        auction.incrementViewerCount();
        auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
      }
      if (!bidder.hasJoined(auction.getId())) {
        userDAO.saveUserAuctionActivity(bidder.getId(), auction.getId(), "WATCHING");
      }
    } finally {
      lock.unlock();
    }
  }

  /** Đặt giá — flow 3 vùng tối ưu throughput. */
  @Override
  public void placeBid(NormalUser bidder, Auction auction, long amount, BidStrategy strategy) {

    // ── NGOÀI LOCK: validate nhanh ─────────────────────────────────────────
    if (!ratingService.isEligible(bidder)) {
      log.warn(
          "Bid rejected — ineligible: auctionId={}, bidderId={}, status={}",
          auction.getId(),
          bidder.getId(),
          bidder.getAccountStatus());
      throw buildIneligibleException(bidder);
    }

    if (!auction.isAcceptingBids()) {
      log.warn(
          "Bid rejected — auction closed: auctionId={}, bidderId={}",
          auction.getId(),
          bidder.getId());
      throw new AuctionClosedException(auction.getStatus());
    }

    if (!bidder.hasJoined(auction.getId())) {
      log.warn(
          "Bid rejected — not joined: auctionId={}, bidderId={}", auction.getId(), bidder.getId());
      throw new AuctionBusinessException(AuctionBusinessException.Reason.NOT_JOINED_AUCTION);
    }

    // Phát hiện bid thao túng: bid > 3× giá hiện tại → tịch thu cọc ngay
    long currentPrice = auction.getCurrentPrice();
    if (currentPrice > 0 && amount > currentPrice * 3) {
      long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
      try {
        walletService.forfeitDeposit(bidder, depositAmount, auction.getId());
        log.warn(
            "Deposit forfeited — manipulative bid detected: bidderId={}, auctionId={}, "
                + "currentPrice={}, bidAmount={}",
            bidder.getId(),
            auction.getId(),
            currentPrice,
            amount);
      } catch (RuntimeException e) {
        log.error(
            "Failed to forfeit deposit for manipulative bid: bidderId={}, auctionId={}",
            bidder.getId(),
            auction.getId(),
            e);
      }
    }

    // ── TRONG LOCK: critical section per-auction ───────────────────────────
    java.util.concurrent.locks.ReentrantLock lock = lockRegistry.getLock(auction.getId());
    BidTransaction tx;
    boolean reserveMet;
    boolean extendedForAntiSniping = false;

    NormalUser previousLeader = auction.getCurrentLeader();
    long previousPrice = auction.getCurrentPrice();

    lock.lock();
    try {
      if (!auction.isAcceptingBids()) {
        log.warn(
            "Bid rejected — auction closed (in lock): auctionId={}, bidderId={}",
            auction.getId(),
            bidder.getId());
        throw new AuctionClosedException(auction.getStatus());
      }

      if (!strategy.isValidBid(auction, amount)) {
        throw new InvalidBidException(
            String.format(
                "Bid %d không hợp lệ. Giá hiện tại: %d. %s",
                amount, auction.getCurrentPrice(), strategy.describe()),
            amount,
            auction.getCurrentPrice());
      }

      auction.updateBid(amount, bidder);
      reserveMet = auction.isReserveMet();

      if (applyAntiSnipingExtension(auction)) {
        extendedForAntiSniping = true;
      }

      BidResult result = reserveMet ? BidResult.ACCEPTED : BidResult.ACCEPTED_RESERVE_NOT_MET;
      tx = BidTransaction.create(bidder, auction.getId(), amount, result);
      bidder.addBidToHistory(tx);
      auction.addBidTransactionId(tx.getId());
    } finally {
      lock.unlock();
    }

    // ── NGOÀI LOCK: DB writes ──────────────────────────────────────────────
    if (!bidTransactionDAO.saveTransactionAndUpdatePrice(
        tx, auction.getId(), amount, bidder.getId())) {
      log.error(
          "Bid persist failed after RAM update: auctionId={}, bidderId={}, amount={}",
          auction.getId(),
          bidder.getId(),
          amount);
    }

    if (reserveMet) {
      auctionService.notify(auction, AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
    } else {
      auctionService.notify(
          auction, AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET, bidder, amount);
    }

    if (previousLeader != null && !previousLeader.getId().equals(bidder.getId())) {
      ServerBroadcastNotifier.getInstance()
          .notifyOutbid(previousLeader, auction, bidder, amount, previousPrice);
    }

    if (extendedForAntiSniping) {
      auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
      auctionService.notify(
          auction,
          AuctionEvent.AuctionEventType.AUCTION_EXTENDED,
          bidder,
          amount,
          String.format(
              "Phiên được gia hạn thêm %ds (anti-sniping).", ANTI_SNIPING_EXTENSION_SECONDS));
    }
  }

  // =========================================================================
  // Private helpers
  // =========================================================================

  private void joinAsNormalUser(NormalUser bidder, Auction auction, AuctionObserver observer) {
    if (!ratingService.isEligible(bidder)) {
      log.warn(
          "Join rejected — ineligible: auctionId={}, bidderId={}", auction.getId(), bidder.getId());
      throw buildIneligibleException(bidder);
    }
    if (bidder.hasRole(User.UserRole.SELLER)
        && auction.getItem().getSeller() != null
        && auction.getItem().getSeller().getId().equals(bidder.getId())) {
      log.warn(
          "Join rejected — seller bid own auction: auctionId={}, bidderId={}",
          auction.getId(),
          bidder.getId());
      throw new AuctionBusinessException(
          AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
    }
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
    walletService.lockDeposit(bidder, depositAmount, auction.getId());
    try {
      registerJoin(bidder, auction, observer);
    } catch (RuntimeException e) {
      log.warn(
          "registerJoin failed, rolling back deposit: auctionId={}, bidderId={}, deposit={}",
          auction.getId(),
          bidder.getId(),
          depositAmount,
          e);
      walletService.unlockDeposit(bidder, depositAmount, auction.getId());
      throw e;
    }
    log.info(
        "Bidder joined: auctionId={}, bidderId={}, deposit={}",
        auction.getId(),
        bidder.getId(),
        depositAmount);
  }

  private void joinAsAdmin(User admin, Auction auction, AuctionObserver observer) {
    registerJoin(admin, auction, observer);
  }

  private void registerJoin(User user, Auction auction, AuctionObserver observer) {
    java.util.concurrent.locks.ReentrantLock lock = lockRegistry.getLock(auction.getId());
    lock.lock();
    try {
      user.clearLeftAuction(auction.getId());
      user.addJoinedAuction(auction.getId());
      boolean alreadyWatching = user.getWatchListAuctionIds().contains(auction.getId());
      user.addToWatchList(auction.getId());
      auctionService.addObserver(auction.getId(), observer);
      if (!alreadyWatching) {
        auction.incrementViewerCount();
        auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
      }
      userDAO.saveUserAuctionActivity(user.getId(), auction.getId(), "JOINED");
    } finally {
      lock.unlock();
    }
  }

  /**
   * Rời phiên: xử lý cọc và rating theo điều kiện vi phạm, xóa join state.
   *
   * <p>Phạt toàn bộ cọc + trừ 1 rating nếu:
   *
   * <ul>
   *   <li>User đang là current leader khi rời phiên.
   *   <li>Phiên đã qua 2/3 tổng thời gian.
   * </ul>
   *
   * Nếu không: hoàn toàn bộ cọc, không phạt rating.
   *
   * <p><b>Anti-sniping:</b> Nếu người dẫn đầu rời phiên khi còn ≤ 30 giây (phiên RUNNING), gia hạn
   * thêm 60 giây — cùng quy tắc với {@link #placeBid}.
   *
   * <p><b>FIX race condition isPastTwoThirdsTime:</b> {@code isPastTwoThirds} và {@code
   * isCurrentLeader} được tính TRONG lock auction, sau đó trả về qua {@link LeaveResult} để
   * BidHandler dùng trực tiếp — không tính lại lần nữa bên ngoài (tránh kết quả khác nhau do
   * anti-snipe extend đúng lúc).
   *
   * @return {@link LeaveResult} mang đủ thông tin penalty để build response
   */
  @Override
  public LeaveResult leaveAuction(User user, Auction auction) {
    String auctionId = auction != null ? auction.getId() : null;
    boolean leaderChanged = false;
    boolean depositForfeited = false;
    long forfeitedAmount = 0L;
    boolean ratingPenalized = false;
    long previousPrice = 0L;
    boolean extendedForAntiSniping = false;
    NormalUser leavingLeader = null;

    if (user instanceof NormalUser && auction != null) {
      NormalUser bidder = (NormalUser) user;
      long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;

      java.util.concurrent.locks.ReentrantLock auctionLock = lockRegistry.getLock(auction.getId());
      auctionLock.lock();
      // FIX: tính isCurrentLeader và isPastTwoThirds TRONG lock để giá trị nhất quán.
      // Trước đây BidHandler tính lại ngoài lock → nếu anti-snipe extend đúng lúc rời,
      // BidHandler thấy thời gian khác với BidService → penalty info không khớp.
      final boolean isCurrentLeader;
      final boolean isPastTwoThirds;
      final boolean shouldPenalize;
      try {
        NormalUser currentLeader = auction.getCurrentLeader();
        isCurrentLeader = currentLeader != null && currentLeader.getId().equals(bidder.getId());
        isPastTwoThirds = isPastTwoThirdsTime(auction);
        shouldPenalize = isCurrentLeader || isPastTwoThirds;

        // FIX #7: cancel bids của bidder LUÔN LUÔN, không chỉ khi là leader.
        int cancelledRows = bidTransactionDAO.cancelBidsByBidder(auctionId, bidder.getId());
        if (cancelledRows > 0) {
          log.info(
              "Bids cancelled on leave (FIX ghost-bid): auctionId={}, bidderId={}, rows={}",
              auctionId,
              bidder.getId(),
              cancelledRows);
        }

        if (isCurrentLeader) {
          previousPrice = auction.getCurrentPrice(); // capture trước khi reset
          BidTransaction nextBid =
              bidTransactionDAO.findHighestValidBidExcept(auctionId, bidder.getId());

          if (nextBid != null && nextBid.getBidder() != null) {
            long nextPrice = nextBid.getAmount();
            NormalUser nextUser = nextBid.getBidder();
            auction.resetLeader(nextPrice, nextUser);
            bidTransactionDAO.updateLeaderAfterLeave(auctionId, nextUser.getId(), nextPrice);
            log.info(
                "Leader rolled back to next bidder: auctionId={}, newLeader={}, newPrice={}",
                auctionId,
                nextUser.getUsername(),
                nextPrice);
          } else {
            // FIX: khi không còn ai bid, giá về startingPrice chứ KHÔNG về 0.
            // Nếu về 0 → AutoBidProcessor tính nextBid từ 0 → autobid bắn với giá rất thấp.
            long fallbackPrice = auction.getItem().getStartingPrice();
            auction.resetLeader(fallbackPrice, null);
            bidTransactionDAO.updateLeaderAfterLeave(auctionId, null, fallbackPrice);
            log.info(
                "Leader rolled back to empty (no other bids), price reset to startingPrice:"
                    + " auctionId={}, startingPrice={}",
                auctionId,
                fallbackPrice);
          }
          leaderChanged = true;
          if (applyAntiSnipingExtension(auction)) {
            extendedForAntiSniping = true;
            leavingLeader = bidder;
            log.info(
                "Anti-sniping on leader leave: auctionId={}, leaderId={}, newEndTime={}",
                auctionId,
                bidder.getId(),
                auction.getEndTime());
          }
        }

        // Capture penalty info trước khi unlock — giá trị này nhất quán với state trong lock
        if (shouldPenalize) {
          depositForfeited = true;
          forfeitedAmount = depositAmount;
          ratingPenalized = true;
        }
      } finally {
        auctionLock.unlock();
      }

      try {
        if (shouldPenalize) {
          walletService.forfeitDeposit(bidder, depositAmount, auction.getId());
          log.warn(
              "Full deposit forfeited on leave: userId={}, auctionId={}, deposit={}, reason={}",
              bidder.getId(),
              auction.getId(),
              depositAmount,
              isCurrentLeader ? "IS_LEADER" : "PAST_TWO_THIRDS");

          try {
            ratingService.penalizeEarlyLeave(bidder);
          } catch (RuntimeException e) {
            log.error(
                "Rating penalty failed on leave (continuing): userId={}, auctionId={}, reason={}",
                bidder.getId(),
                auction.getId(),
                e.getMessage());
          }
        } else {
          walletService.unlockDeposit(bidder, depositAmount, auction.getId());
          log.info(
              "Deposit unlocked on leave (no penalty): userId={}, auctionId={}, deposit={}",
              bidder.getId(),
              auction.getId(),
              depositAmount);
        }
      } catch (RuntimeException e) {
        log.error(
            "Wallet operation failed on leave (continuing): userId={}, auctionId={}, reason={}",
            user.getId(),
            auction.getId(),
            e.getMessage());
      }
    }

    if (auctionId != null) {
      user.removeJoinedAuction(auctionId);
      user.removeFromWatchList(auctionId);
      user.addLeftAuction(auctionId);
      userDAO.markUserLeftAuction(user.getId(), auctionId);
      auctionService.removeObserversForUser(auctionId, user.getId());
      ServerBroadcastNotifier.getInstance().clearAutoBidExhaustedFlag(user.getId(), auctionId);
      // FIX: cancel autobid của người rời phiên — tránh entry zombie trong registry
      // khiến process() tính candidate sai (người đã rời không được bid nữa)
      boolean autoBidCancelled = AutoBidRegistry.getInstance().cancel(user.getId(), auctionId);
      if (autoBidCancelled) {
        log.info("Auto-bid cancelled on leave: userId={}, auctionId={}", user.getId(), auctionId);
      }
    }
    log.info(
        "User left auction: userId={}, auctionId={}, leaderChanged={}",
        user.getId(),
        auctionId,
        leaderChanged);

    if (extendedForAntiSniping && leavingLeader != null) {
      auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
      auctionService.notify(
          auction,
          AuctionEvent.AuctionEventType.AUCTION_EXTENDED,
          leavingLeader,
          auction.getCurrentPrice(),
          String.format(
              "Phiên được gia hạn thêm %ds (anti-sniping — người dẫn đầu rời phiên).",
              ANTI_SNIPING_EXTENSION_SECONDS));
    }

    long newBalance = (user instanceof NormalUser) ? ((NormalUser) user).getAvailableBalance() : 0L;
    return new LeaveResult(
        leaderChanged,
        depositForfeited,
        forfeitedAmount,
        ratingPenalized,
        newBalance,
        extendedForAntiSniping,
        previousPrice);
  }

  /**
   * Gia hạn phiên nếu còn trong cửa sổ anti-sniping (dùng chung cho bid và leader rời phiên).
   *
   * @return true nếu đã gia hạn
   */
  private boolean applyAntiSnipingExtension(Auction auction) {
    if (!auction.isAcceptingBids()) {
      return false;
    }
    LocalDateTime currentEnd = auction.getEndTime();
    if (currentEnd == null) {
      return false;
    }
    long secondsLeft = Duration.between(LocalDateTime.now(), currentEnd).getSeconds();
    if (secondsLeft >= 0 && secondsLeft <= ANTI_SNIPING_WINDOW_SECONDS) {
      auction.extendEndTime(Duration.ofSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
      return true;
    }
    return false;
  }

  /** Kiểm tra phiên đã qua 2/3 tổng thời gian chưa. */
  private boolean isPastTwoThirdsTime(Auction auction) {
    java.time.LocalDateTime startTime = auction.getStartTime();
    java.time.LocalDateTime endTime = auction.getEndTime();
    if (startTime == null || endTime == null) {
      return false;
    }

    long totalSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
    if (totalSeconds <= 0) {
      return false;
    }

    long twoThirdsSeconds = totalSeconds * 2 / 3;
    java.time.LocalDateTime twoThirdsPoint = startTime.plusSeconds(twoThirdsSeconds);
    return java.time.LocalDateTime.now().isAfter(twoThirdsPoint);
  }

  private static AuthenticationException buildIneligibleException(NormalUser bidder) {
    switch (bidder.getAccountStatus()) {
      case BANNED:
        return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_BANNED);
      case SUSPENDED:
        return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
      default:
        return new AuthenticationException(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }
  }
}
