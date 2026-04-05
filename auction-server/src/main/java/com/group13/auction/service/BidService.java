package com.group13.auction.service;

import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.AuthenticationException.Reason;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.user.Bidder;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.strategy.BidStrategy;

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 * Nhận {@link IRatingService} và {@link IAuctionService} qua constructor (DIP)
 * Không new cứng bất kì service nào
 * Payment được tách sang {@link StandardPaymentService}.
 * TODO: inject BidTransactionDAO, AuctionDAO để persist xuống DB.
 */
public class BidService implements IBidService {

  private final IRatingService ratingService;
  private final IAuctionService auctionService;

  public BidService(IRatingService ratingService, IAuctionService auctionService) {
    this.ratingService = ratingService;
    this.auctionService = auctionService;
  }

  /**
   * Bidder tham gia phiên đấu giá.
   * Khi join: tự động vào watchList và addObserver.
   * Bidder có thể tham gia nhiều auction cùng lúc.
   *
   * @param bidder   bidder muốn tham gia
   * @param auction  phiên muốn tham gia
   * @param observer observer của bidder để nhận notify
   * @throws AuthenticationException nếu bidder không đủ điều kiện
   * @throws AuctionClosedException  nếu phiên không ở RUNNING
   */
  public void joinAuction(Bidder bidder, Auction auction,
      AuctionObserver observer) {
    if (!ratingService.isEligible(bidder)) {
      throw new AuthenticationException(Reason.INSUFFICIENT_RATING);
    }
    if (!auction.isAcceptingBids()) {
      throw new AuctionClosedException(auction.getStatus());
    }
    bidder.addJoinedAuction(auction.getId());
    bidder.addToWatchList(auction.getId());
    auctionService.addObserver(auction, observer);
    System.out.printf("[BID] %s tham gia phiên: %s%n",
        bidder.getUsername(), auction.getId());
  }

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   * Vẫn nhận đầy đủ notify.
   *
   * @param bidder   bidder muốn theo dõi
   * @param auction  phiên muốn theo dõi
   * @param observer observer để nhận notify
   */
  public void watchAuction(Bidder bidder, Auction auction,
      AuctionObserver observer) {
    bidder.addToWatchList(auction.getId());
    auctionService.addObserver(auction, observer);
    System.out.printf("[BID] %s theo dõi phiên: %s%n",
        bidder.getUsername(), auction.getId());
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   * Tự động lưu lịch sử dù thành công hay thất bại (DRY).
   * TODO: bidTransactionDAO.save(tx), auctionDAO.update(auction).
   *
   * @param bidder   người đặt giá
   * @param auction  phiên đấu giá
   * @param amount   số tiền đặt
   * @param strategy strategy kiểm tra tính hợp lệ
   * @throws AuthenticationException nếu chưa join hoặc không đủ điều kiện
   * @throws AuctionClosedException  nếu phiên không ở RUNNING
   * @throws InvalidBidException     nếu bid không hợp lệ theo strategy
   */
  public void placeBid(Bidder bidder, Auction auction,
      double amount, BidStrategy strategy) {
    if (!bidder.hasJoined(auction.getId())) {
      recordAndThrow(bidder, auction, amount,
          new AuthenticationException(Reason.NOT_JOINED_AUCTION));
    }
    if (!ratingService.isEligible(bidder)) {
      recordAndThrow(bidder, auction, amount, buildIneligibleException(bidder));
    }
    if (!auction.isAcceptingBids()) {
      recordAndThrow(bidder, auction, amount,
          new AuctionClosedException(auction.getStatus()));
    }
    if (!strategy.isValidBid(auction, amount)) {
      recordAndThrow(bidder, auction, amount,
          new InvalidBidException("Bid không hợp lệ: " + strategy.describe(),
              amount, auction.getCurrentPrice()));
    }
    // Hợp lệ — cập nhật auction
    auction.setCurrentPrice(amount);
    auction.setCurrentLeader(bidder);
    BidTransaction tx = recordTransaction(bidder, auction, amount, BidResult.ACCEPTED);
    auction.addBidTransactionId(tx.getId());
    recordTransaction(bidder, auction, amount, BidResult.ACCEPTED);
    auctionService.notify(auction, AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
    System.out.printf("[BID] %s đặt giá %.0f thành công!%n",
        bidder.getUsername(), amount);
    // TODO: auctionDAO.update(auction), bidTransactionDAO.save(tx)
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  /** Lưu lịch sử thất bại rồi ném exception (DRY). */
  private void recordAndThrow(Bidder bidder, Auction auction,
      double amount, RuntimeException ex) {
    recordTransaction(bidder, auction, amount, BidResult.REJECTED);
    throw ex;
  }

  /** Tạo và lưu BidTransaction vào lịch sử bidder. */
  private BidTransaction recordTransaction(Bidder bidder, Auction auction,
      double amount, BidResult result) {
    BidTransaction tx = BidTransaction.create(bidder, auction, amount, result);
    bidder.addBidToHistory(tx);
    // TODO: bidTransactionDAO.save(tx)
    return tx;
  }

  /** Thông báo observer khi có bid mới. */
  private void notifyBid(Auction auction, Bidder bidder, double amount) {
    AuctionEvent event = new AuctionEvent(
        AuctionEvent.AuctionEventType.BID_PLACED, auction, bidder, amount);
    for (AuctionObserver observer : auction.getObservers()) {
      observer.onBidPlaced(event);
    }
  }

  /** Tạo exception phù hợp theo trạng thái tài khoản (KISS). */
  private static AuthenticationException buildIneligibleException(Bidder bidder) {
    switch (bidder.getAccountStatus()) {
      case BANNED:    return new AuthenticationException(Reason.ACCOUNT_BANNED);
      case SUSPENDED: return new AuthenticationException(Reason.ACCOUNT_SUSPENDED);
      default:        return new AuthenticationException(Reason.INSUFFICIENT_RATING);
    }
  }
}