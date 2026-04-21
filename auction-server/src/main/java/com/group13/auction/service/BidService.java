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
import com.group13.auction.strategy.BidStrategy;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 * Nhận {@link IAuctionService} và {@link IRatingService} qua constructor (DIP).
 * Đã thực hiện TODO: inject BidTransactionDAO, AuctionDAO, UserDAO.
 */
public class BidService implements IBidService {

  private static final long ANTI_SNIPING_WINDOW_SECONDS = 30;
  private static final long ANTI_SNIPING_EXTENSION_SECONDS = 60;

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final WalletService walletService;

  // Thực hiện TODO: inject các DAO cần thiết
  private final BidTransactionDAO bidTransactionDAO;
  private final AuctionDAO auctionDAO;
  private final UserDAO userDAO;

  /**
   * Cập nhật constructor để nhận thêm các DAO.
   */
  public BidService(IAuctionService auctionService, IRatingService ratingService,
                    WalletService walletService, BidTransactionDAO bidTransactionDAO,
                    AuctionDAO auctionDAO, UserDAO userDAO) {
    this.auctionService = auctionService;
    this.ratingService = ratingService;
    this.walletService = walletService;
    this.bidTransactionDAO = bidTransactionDAO;
    this.auctionDAO = auctionDAO;
    this.userDAO = userDAO;
  }

  /**
   * Bidder tham gia phiên đấu giá.
   */
  @Override
  public void joinAuction(NormalUser bidder, Auction auction, AuctionObserver observer) {
    if (!ratingService.isEligible(bidder)) {
      throw buildIneligibleException(bidder);
    }

    if (bidder.hasRole(User.UserRole.SELLER)
            && bidder.getAllAuctionIds().contains(auction.getId())) {
      throw new AuctionBusinessException(
              AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
    }

    if (bidder.hasJoined(auction.getId())) {
      System.out.printf("[BID] %s đã tham gia phiên %s trước đó.%n",
              bidder.getUsername(), auction.getId());
      return;
    }

    // Trừ balance và lock deposit ngay lập tức (WalletService xử lý DB của phần này)
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;
    walletService.lockDeposit(bidder, depositAmount, auction.getId());

    bidder.addJoinedAuction(auction.getId());
    bidder.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction, observer);

    System.out.printf("[BID] %s tham gia phiên %s | Cọc khóa: %d%n",
            bidder.getUsername(), auction.getId(), depositAmount);

    // Thực hiện TODO: auctionDAO.update(auction), userDAO.update(bidder)
    // Lưu ý: Cần có hàm tương ứng trong UserDAO để lưu trạng thái Joined/Watchlist
    auctionDAO.updateViewerCount(auction.getId(), auction.getViewerCount());
    userDAO.saveUserAuctionActivity(bidder.getId(), auction.getId(), "JOINED");
  }

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   */
  @Override
  public void watchAuction(NormalUser bidder, Auction auction, AuctionObserver observer) {
    bidder.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction, observer);
    System.out.printf("[BID] %s theo dõi phiên %s.%n", bidder.getUsername(), auction.getId());

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
    if (!ratingService.isEligible(bidder)) {
      recordAndThrow(bidder, auction, amount, buildIneligibleException(bidder));
    }

    if (!auction.isAcceptingBids()) {
      throw new AuctionClosedException(auction.getStatus());
    }

    if (!bidder.hasJoined(auction.getId())) {
      recordAndThrow(bidder, auction, amount,
              new AuctionBusinessException(AuctionBusinessException.Reason.NOT_JOINED_AUCTION));
    }

    if (!strategy.isValidBid(auction, amount)) {
      recordAndThrow(bidder, auction, amount,
              new InvalidBidException(
                      String.format("Bid %d không hợp lệ. Giá hiện tại: %d. %s",
                              amount, auction.getCurrentPrice(), strategy.describe()),
                      amount, auction.getCurrentPrice()));
    }

    // Cập nhật trạng thái phiên
    auction.setCurrentPrice(amount);
    auction.setCurrentLeader(bidder);

    if (!auction.isReserveMet()) {
      BidTransaction tx = recordTransaction(bidder, auction, amount,
              BidResult.ACCEPTED_RESERVE_NOT_MET);
      auction.addBidTransactionId(tx.getId());
      auctionService.notify(auction,
              AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET, bidder, amount);
      System.out.printf("[BID] %s đặt giá %d — chưa đạt reserve price (%d).%n",
              bidder.getUsername(), amount,
              auction.getReserveStrategy().getReservePrice());
    } else {
      BidTransaction tx = recordTransaction(bidder, auction, amount, BidResult.ACCEPTED);
      auction.addBidTransactionId(tx.getId());
      auctionService.notify(auction,
              AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
      System.out.printf("[BID] %s đặt giá %d thành công!%n",
              bidder.getUsername(), amount);
    }

    // Anti-sniping: nếu có bid hợp lệ trong N giây cuối thì gia hạn phiên thêm M giây
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime currentEnd = auction.getEndTime();
    if (currentEnd != null) {
      long secondsLeft = Duration.between(now, currentEnd).getSeconds();
      if (secondsLeft >= 0 && secondsLeft <= ANTI_SNIPING_WINDOW_SECONDS) {
        auction.extendEndTime(Duration.ofSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
        auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
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

  private void recordAndThrow(NormalUser bidder, Auction auction,
                              long amount, RuntimeException ex) {
    recordTransaction(bidder, auction, amount, BidResult.REJECTED);
    throw ex;
  }

  private BidTransaction recordTransaction(NormalUser bidder, Auction auction,
                                           long amount, BidResult result) {
    BidTransaction tx = BidTransaction.create(bidder, auction.getId(), amount, result);
    bidder.addBidToHistory(tx);

    // Thực hiện TODO: bidTransactionDAO.save(tx)
    bidTransactionDAO.saveTransaction(tx);

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