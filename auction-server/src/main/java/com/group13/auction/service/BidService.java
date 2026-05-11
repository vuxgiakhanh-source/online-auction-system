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

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 * Nhận {@link IAuctionService} và {@link IRatingService} qua constructor (DIP).
 * Đã thực hiện TODO: inject BidTransactionDAO, AuctionDAO, UserDAO.
 */
public class BidService implements IBidService {
  private static final Logger log = LoggerFactory.getLogger(BidService.class);

  private static final long ANTI_SNIPING_WINDOW_SECONDS = 30;
  private static final long ANTI_SNIPING_EXTENSION_SECONDS = 60;

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;

  // Thực hiện TODO: inject các DAO cần thiết
  private final BidTransactionDAO bidTransactionDAO;
  private final AuctionDAO auctionDAO;
  private final UserDAO userDAO;

  /**
   * Cập nhật constructor để nhận thêm các DAO.
   */
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

  /**
   * Bidder tham gia phiên đấu giá.
   * Admin không đóng cọc - chỉ join để theo dõi, không đặt giá
   */
  @Override
  public void joinAuction(User user, Auction auction, AuctionObserver observer) {
    if (user.hasJoined(auction.getId())) {
      log.info("User already joined auction: userId={}, username={}, auctionId={}",
              user.getId(), user.getUsername(), auction.getId());
      return;
    }

    if (user instanceof NormalUser) {
      joinAsNormalUser((NormalUser) user, auction, observer);
    } else {
      // Admin role — join để theo dõi, không cọc, không validate rating
      joinAsAdmin(user, auction, observer);
    }
  }

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   */
  @Override
  public void watchAuction(User bidder, Auction auction, AuctionObserver observer) {
    bidder.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction.getId(), observer);
    log.info("User watching auction: userId={}, username={}, auctionId={}",
            bidder.getId(), bidder.getUsername(), auction.getId());

    // Lưu trạng thái vào DB
    auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
    userDAO.saveUserAuctionActivity(bidder.getId(), auction.getId(), "WATCHING");
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   */
  @Override
  public void placeBid(NormalUser bidder, Auction auction,
                       long amount, BidStrategy strategy) {
    log.debug("Placing bid: auctionId={}, bidderId={}, username={}, amount={}, strategy={}",
            auction.getId(), bidder.getId(), bidder.getUsername(), amount,
            strategy.getClass().getSimpleName());
    if (!ratingService.isEligible(bidder)) {
      log.warn("Bid rejected because bidder is not eligible: auctionId={}, bidderId={}, username={}, amount={}, status={}",
              auction.getId(), bidder.getId(), bidder.getUsername(), amount, bidder.getAccountStatus());
      throw recordAndThrow(bidder, auction, amount, buildIneligibleException(bidder));
    }

    if (!auction.isAcceptingBids()) {
      log.warn("Bid rejected because auction is not accepting bids: auctionId={}, bidderId={}, amount={}, status={}",
              auction.getId(), bidder.getId(), amount, auction.getStatus());
      throw new AuctionClosedException(auction.getStatus());
    }

    if (!bidder.hasJoined(auction.getId())) {
      log.warn("Bid rejected because bidder has not joined auction: auctionId={}, bidderId={}, amount={}",
              auction.getId(), bidder.getId(), amount);
      throw recordAndThrow(bidder, auction, amount,
              new AuctionBusinessException(AuctionBusinessException.Reason.NOT_JOINED_AUCTION));
    }

    if (!strategy.isValidBid(auction, amount)) {
      log.warn("Bid rejected by strategy validation: auctionId={}, bidderId={}, amount={}, currentPrice={}, strategy={}",
              auction.getId(), bidder.getId(), amount, auction.getCurrentPrice(),
              strategy.getClass().getSimpleName());
      throw recordAndThrow(bidder, auction, amount,
              new InvalidBidException(
                      String.format("Bid %d không hợp lệ. Giá hiện tại: %d. %s",
                              amount, auction.getCurrentPrice(), strategy.describe()),
                      amount, auction.getCurrentPrice()));
    }

    // Cập nhật trạng thái phiên
    auction.updateBid(amount, bidder);

    if (!auction.isReserveMet()) {
      BidTransaction tx = recordTransaction(bidder, auction, amount,
              BidResult.ACCEPTED_RESERVE_NOT_MET);
      auction.addBidTransactionId(tx.getId());
      auctionService.notify(auction,
              AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET, bidder, amount);
      log.info("Bid accepted but reserve not met: auctionId={}, bidderId={}, username={}, amount={}, reservePrice={}",
              auction.getId(), bidder.getId(), bidder.getUsername(), amount, auction.getReservePrice());
    } else {
      BidTransaction tx = recordTransaction(bidder, auction, amount, BidResult.ACCEPTED);
      auction.addBidTransactionId(tx.getId());
      auctionService.notify(auction,
              AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
      log.info("Bid placed: auctionId={}, bidderId={}, username={}, amount={}, currentPrice={}",
              auction.getId(), bidder.getId(), bidder.getUsername(), amount, auction.getCurrentPrice());
    }

    // Anti-sniping: nếu có bid hợp lệ trong N giây cuối thì gia hạn phiên thêm M giây
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime currentEnd = auction.getEndTime();
    if (currentEnd != null) {
      long secondsLeft = Duration.between(now, currentEnd).getSeconds();
      if (secondsLeft >= 0 && secondsLeft <= ANTI_SNIPING_WINDOW_SECONDS) {
        auction.extendEndTime(Duration.ofSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
        auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
        log.info("Auction extended by anti-sniping: auctionId={}, bidderId={}, amount={}, oldEndTime={}, newEndTime={}",
                auction.getId(), bidder.getId(), amount, currentEnd, auction.getEndTime());
        auctionService.notify(
                auction,
                AuctionEvent.AuctionEventType.AUCTION_EXTENDED,
                bidder,
                amount,
                String.format("Phiên được gia hạn thêm %ds (anti-sniping).", ANTI_SNIPING_EXTENSION_SECONDS));
      }
    }

    // Thực hiện TODO: auctionDAO.update(auction)
    auctionDAO.updateHighestPrice(auction.getId(), amount, bidder.getId());
  }

  // Private helpers

  /**
   * Logic join cho NormalUser: validate rating, chặn seller tự bid,
   * đóng cọc 30% giá khởi điểm.
   */
  private void joinAsNormalUser(NormalUser bidder, Auction auction, AuctionObserver observer) {
    if (!ratingService.isEligible(bidder)) {
      log.warn("Join rejected because bidder is not eligible: auctionId={}, bidderId={}, username={}, status={}",
              auction.getId(), bidder.getId(), bidder.getUsername(), bidder.getAccountStatus());
      throw buildIneligibleException(bidder);
    }

    if (bidder.hasRole(User.UserRole.SELLER)
            && bidder.getAllAuctionIds().contains(auction.getId())) {
      log.warn("Join rejected because seller cannot bid own auction: auctionId={}, bidderId={}, username={}",
              auction.getId(), bidder.getId(), bidder.getUsername());
      throw new AuctionBusinessException(
              AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
    }

    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
    walletService.lockDeposit(bidder, depositAmount, auction.getId());

    registerJoin(bidder, auction, observer);
    log.info("Bidder joined auction: auctionId={}, bidderId={}, username={}, lockedDeposit={}",
            auction.getId(), bidder.getId(), bidder.getUsername(), depositAmount);
  }

  /**
   * Logic join cho Admin: không cọc, không validate rating.
   */
  private void joinAsAdmin(User admin, Auction auction, AuctionObserver observer) {
    registerJoin(admin, auction, observer);
    log.info("Admin joined auction without deposit: auctionId={}, adminId={}, username={}",
            auction.getId(), admin.getId(), admin.getUsername());
  }

  /**
   * Các bước join chung cho mọi loại user: đánh dấu joined, thêm watchList,
   * tăng viewerCount, đăng ký observer, persist DB.
   */
  private void registerJoin(User user, Auction auction, AuctionObserver observer) {
    user.addJoinedAuction(auction.getId());
    user.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction.getId(), observer);

    // TODO: [DB] auctionDAO.updateViewerCount / userDAO.saveUserAuctionActivity
    auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
    userDAO.saveUserAuctionActivity(user.getId(), auction.getId(), "JOINED");
    log.debug("Join registered: auctionId={}, userId={}, viewerCount={}",
            auction.getId(), user.getId(), auction.getViewerCount());
  }

  private RuntimeException recordAndThrow(NormalUser bidder, Auction auction,
                              long amount, RuntimeException ex) {
    recordTransaction(bidder, auction, amount, BidResult.REJECTED);
    log.warn("Rejected bid recorded: auctionId={}, bidderId={}, amount={}, reason={}",
            auction.getId(), bidder.getId(), amount, ex.getMessage());
    return ex;
  }

  private BidTransaction recordTransaction(NormalUser bidder, Auction auction,
                                           long amount, BidResult result) {
    BidTransaction tx = BidTransaction.create(bidder, auction.getId(), amount, result);
    bidder.addBidToHistory(tx);

    // Thực hiện TODO: bidTransactionDAO.save(tx)
    bidTransactionDAO.saveTransaction(tx);
    log.debug("Bid transaction recorded: txId={}, auctionId={}, bidderId={}, amount={}, result={}",
            tx.getId(), auction.getId(), bidder.getId(), amount, result);

    return tx;
  }

  private static AuthenticationException buildIneligibleException(NormalUser bidder) {
    switch (bidder.getAccountStatus()) {
      case BANNED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_BANNED);
      case SUSPENDED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
      default: return new AuthenticationException(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }
  }
}
