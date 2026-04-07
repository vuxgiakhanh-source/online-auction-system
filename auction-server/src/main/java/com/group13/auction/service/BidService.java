package com.group13.auction.service;

import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.AuctionBusinessException.Reason;
import com.group13.auction.exception.AuctionClosedException;
import com.group13.auction.exception.AuthenticationException;
import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.strategy.BidStrategy;

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 * Nhận {@link IRatingService} và {@link IAuctionService} qua constructor (DIP).
 * Không new cứng bất kì service nào.
 * Payment được tách sang {@link StandardPaymentService}.
 * TODO: inject BidTransactionDAO, AuctionDAO để persist xuống DB.
 */
public class BidService implements IBidService {

  /** Tỷ lệ cọc tối thiểu — 30% giá khởi điểm. */
  private static final double DEPOSIT_RATIO = 0.30;

  private final IRatingService  ratingService;
  private final IAuctionService auctionService;

  public BidService(IRatingService ratingService, IAuctionService auctionService) {
    this.ratingService  = ratingService;
    this.auctionService = auctionService;
  }

  /**
   * Bidder tham gia phiên đấu giá.
   * Khi join: tự động vào watchList và addObserver.
   * Bidder có thể tham gia nhiều auction cùng lúc.
   * Yêu cầu: rating >= 2.0 + số dư khả dụng >= 30% giá khởi điểm.
   * Khoản cọc bị khóa cho đến khi phiên đóng.
   *
   * @param bidder   bidder muốn tham gia
   * @param auction  phiên muốn tham gia
   * @param observer observer của bidder để nhận notify
   * @throws AuthenticationException nếu bidder không đủ điều kiện
   * @throws AuctionClosedException  nếu phiên không ở RUNNING
   */
  @Override
  public void joinAuction(NormalUser bidder, Auction auction, AuctionObserver observer) {

    // FIX 1: Chặn Seller tự đấu giá hàng của mình
    NormalUser seller = (NormalUser) auction.getItem().getSeller();
    if (bidder.getId().equals(seller.getId())) {
      throw new InvalidBidException(
              "Seller không được tham gia đấu giá món hàng của chính mình.");
    }

    if (!ratingService.isEligible(bidder)) {
      throw buildIneligibleException(bidder);
    }
    if (!auction.isAcceptingBids()) {
      throw new AuctionClosedException(auction.getStatus());
    }


    // Kiểm tra và khóa cọc
    double required = auction.getItem().getStartingPrice() * DEPOSIT_RATIO;
    if (bidder.getBalance() < required) {   // so sánh với balance thực, không phải available
      throw new AuctionBusinessException(Reason.INSUFFICIENT_DEPOSIT);
    }

    bidder.lockDeposit(required);

    bidder.addJoinedAuction(auction.getId());
    bidder.addToWatchList(auction.getId());
    auctionService.addObserver(auction, observer);
    System.out.printf("[BID] %s tham gia phiên: %s (cọc khóa: %.0f)%n",
            bidder.getUsername(), auction.getId(), required);
  }

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   * Vẫn nhận đầy đủ notify. Không cần cọc.
   *
   * @param bidder   bidder muốn theo dõi
   * @param auction  phiên muốn theo dõi
   * @param observer observer để nhận notify
   */
  @Override
  public void watchAuction(NormalUser bidder, Auction auction,
                           AuctionObserver observer) {
    bidder.addToWatchList(auction.getId());
    auctionService.addObserver(auction, observer);
    System.out.printf("[BID] %s theo dõi phiên: %s%n",
            bidder.getUsername(), auction.getId());
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   * Tự động lưu lịch sử dù thành công hay thất bại (DRY).
   * Nếu bid hợp lệ nhưng chưa đạt reserve price → ACCEPTED_RESERVE_NOT_MET.
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
  @Override
  public void placeBid(NormalUser bidder, Auction auction,
                       double amount, BidStrategy strategy) {
    if (!bidder.hasJoined(auction.getId())) {
      recordAndThrow(bidder, auction, amount,
              new AuctionBusinessException(Reason.NOT_JOINED_AUCTION));
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

    // Hợp lệ về số tiền — cập nhật auction
    auction.setCurrentPrice(amount);
    auction.setCurrentLeader(bidder);

    // Kiểm tra reserve price
    if (!auction.isReserveMet()) {
      BidTransaction tx = recordTransaction(bidder, auction, amount,
              BidResult.ACCEPTED_RESERVE_NOT_MET);
      auction.addBidTransactionId(tx.getId());
      auctionService.notify(auction,
              AuctionEvent.AuctionEventType.BID_RESERVE_NOT_MET, bidder, amount);
      System.out.printf("[BID] %s đặt giá %.0f — chưa đạt reserve price (%.0f).%n",
              bidder.getUsername(), amount,
              auction.getReserveStrategy().getReservePrice());
    } else {
      BidTransaction tx = recordTransaction(bidder, auction, amount, BidResult.ACCEPTED);
      auction.addBidTransactionId(tx.getId());
      auctionService.notify(auction,
              AuctionEvent.AuctionEventType.BID_PLACED, bidder, amount);
      System.out.printf("[BID] %s đặt giá %.0f thành công!%n",
              bidder.getUsername(), amount);
    }
    // TODO: auctionDAO.update(auction), bidTransactionDAO.save(tx)
  }

  // ── Private helpers ────────────────────────────────────────────────────

  /** Lưu lịch sử thất bại rồi ném exception (DRY). */
  private void recordAndThrow(NormalUser bidder, Auction auction,
                              double amount, RuntimeException ex) {
    recordTransaction(bidder, auction, amount, BidResult.REJECTED);
    throw ex;
  }

  /** Tạo và lưu BidTransaction vào lịch sử bidder. */
  private BidTransaction recordTransaction(NormalUser bidder, Auction auction,
                                           double amount, BidResult result) {
    BidTransaction tx = BidTransaction.create(bidder, auction, amount, result);
    bidder.addBidToHistory(tx);
    // TODO: bidTransactionDAO.save(tx)
    return tx;
  }

  /** Tạo exception phù hợp theo trạng thái tài khoản (KISS). */
  private static AuthenticationException buildIneligibleException(NormalUser bidder) {
    switch (bidder.getAccountStatus()) {
      case BANNED:    return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_BANNED);
      case SUSPENDED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
      default:        return new AuthenticationException(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }
  }
}