package com.group13.auction.service;

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
import com.group13.auction.service.serviceInterface.IAuctionService;
import com.group13.auction.service.serviceInterface.IBidService;
import com.group13.auction.service.serviceInterface.IRatingService;
import com.group13.auction.strategy.BidStrategy;

/**
 * Xử lý nghiệp vụ đặt giá: join, watch, placeBid.
 * Nhận {@link IAuctionService} và {@link IRatingService} qua constructor — không new cứng (DIP).
 * TODO: inject BidTransactionDAO.
 */
public class BidService implements IBidService {

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final WalletService walletService;

  /**
   * @param auctionService để notify sau khi bid
   * @param ratingService để kiểm tra eligibility
   * @param walletService để quản lý cọc
   */
  public BidService(IAuctionService auctionService, IRatingService ratingService,
                    WalletService walletService) {
    this.auctionService = auctionService;
    this.ratingService = ratingService;
    this.walletService = walletService;
  }

  /**
   * Bidder tham gia phiên đấu giá.
   * Ngay khi joinAuction thành công: trừ balance và cộng vào lockedDeposit.
   * Khi phiên kết thúc, nếu không thắng thì mới hoàn cọc lại.
   *
   * <p>Chặn Seller tự đấu giá món hàng của chính mình ngay từ bước này.
   *
   * @param bidder bidder muốn tham gia
   * @param auction phiên muốn tham gia
   * @param observer observer của bidder để nhận notify
   * @throws AuctionBusinessException nếu không đủ cọc hoặc seller tự bid
   * @throws AuthenticationException nếu tài khoản không đủ điều kiện
   */
  @Override
  public void joinAuction(NormalUser bidder, Auction auction, AuctionObserver observer) {
    // Guard: check trạng thái tài khoản và rating (cũng check lại ở mỗi placeBid)
    if (!ratingService.isEligible(bidder)) {
      throw buildIneligibleException(bidder);
    }

    // Guard: Seller không được tự đấu giá món hàng của chính mình
    if (bidder.hasRole(User.UserRole.SELLER)
            && bidder.getAllAuctionIds().contains(auction.getId())) {
      throw new AuctionBusinessException(
              AuctionBusinessException.Reason.SELLER_CANNOT_BID_OWN_ITEM);
    }

    // Guard: đã join rồi thì không join lại
    if (bidder.hasJoined(auction.getId())) {
      System.out.printf("[BID] %s đã tham gia phiên %s trước đó.%n",
              bidder.getUsername(), auction.getId());
      return;
    }

    // Trừ balance và lock deposit ngay lập tức
    double depositAmount = auction.getItem().getStartingPrice() * 0.3;
    walletService.lockDeposit(bidder, depositAmount, auction.getId());

    bidder.addJoinedAuction(auction.getId());
    bidder.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction, observer);

    System.out.printf("[BID] %s tham gia phiên %s | Cọc khóa: %.0f%n",
            bidder.getUsername(), auction.getId(), depositAmount);
    // TODO: auctionDAO.update(auction), userDAO.update(bidder)
  }

  /**
   * Theo dõi phiên mà không tham gia đặt bid.
   *
   * @param bidder bidder muốn theo dõi
   * @param auction phiên muốn theo dõi
   * @param observer observer để nhận notify
   */
  @Override
  public void watchAuction(NormalUser bidder, Auction auction, AuctionObserver observer) {
    bidder.addToWatchList(auction.getId());
    auction.incrementViewerCount();
    auctionService.addObserver(auction, observer);
    System.out.printf("[BID] %s theo dõi phiên %s.%n", bidder.getUsername(), auction.getId());
  }

  /**
   * Đặt giá cho một phiên đấu giá.
   *
   * <p>Luôn check status == ACTIVE và rating >= threshold ở mỗi lần placeBid,
   * không chỉ ở bước joinAuction — ngăn người dùng "lách" qua cửa kiểm tra.
   *
   * @param bidder người đặt giá
   * @param auction phiên đấu giá
   * @param amount số tiền đặt
   * @param strategy strategy kiểm tra tính hợp lệ
   * @throws AuctionClosedException nếu phiên không RUNNING
   * @throws AuctionBusinessException nếu chưa join phiên
   * @throws AuthenticationException nếu tài khoản không đủ điều kiện
   * @throws InvalidBidException nếu số tiền không hợp lệ
   */
  @Override
  public void placeBid(NormalUser bidder, Auction auction,
                       double amount, BidStrategy strategy) {
    // FIX: luôn check status và rating tại mỗi lần placeBid — không chỉ ở joinAuction
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
                      String.format("Bid %.0f không hợp lệ. Giá hiện tại: %.0f. %s",
                              amount, auction.getCurrentPrice(), strategy.describe()),
                      amount, auction.getCurrentPrice()));
    }

    // Cập nhật trạng thái phiên
    NormalUser previousLeader = auction.getCurrentLeader();
    auction.setCurrentPrice(amount);
    auction.setCurrentLeader(bidder);

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

  // ── Private helpers ────────────────────────────────────────────────────────

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
      case BANNED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_BANNED);
      case SUSPENDED: return new AuthenticationException(AuthenticationException.Reason.ACCOUNT_SUSPENDED);
      default: return new AuthenticationException(AuthenticationException.Reason.INSUFFICIENT_RATING);
    }
  }
}