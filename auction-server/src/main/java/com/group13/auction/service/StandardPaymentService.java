package com.group13.auction.service;

import com.group13.auction.exception.InvalidBidException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.user.Bidder;

/**
 * Xử lý thanh toán sau khi phiên đấu giá kết thúc.
 * Thanh toán tiêu chuẩn - trả toàn bộ ngay một lần.
 * 
 * <p>Là một trong nhiều implementation có thể có của {@link IPaymentService}.
 * (Nếu có time) thì thêm kiểu thanh toán mới (trả góp, escrow) implements IPaymentService
 *
 * <p>Nhận {@link IRatingService} và {@link IAuctionService} qua constructor
 * Tách khỏi BidService để tuân thủ SRP.
 * TODO: inject AuctionDAO, UserDAO để persist xuống DB.
 */
public class StandardPaymentService {

  private final IRatingService ratingService;
  private final IAuctionService auctionService;

  public StandardPaymentService(IRatingService ratingService,
      IAuctionService auctionService) {
    this.ratingService = ratingService;
    this.auctionService = auctionService;
  }

  /**
   * Xử lý thanh toán sau khi phiên FINISHED (trong 24h).
   * Trừ tiền Bidder, thưởng rating winner và seller, đánh dấu PAID.
   * TODO: auctionDAO.update(auction), userDAO.update(winner).
   *
   * @param auction phiên cần thanh toán
   * @throws IllegalStateException nếu không có winner
   * @throws InvalidBidException   nếu không đủ số dư
   */
  public void completePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    if (auctionWinner.isExpired()) {
      expirePayment(auction);
      return;
    }
    Bidder winner = auctionWinner.getWinner();
    double price = auctionWinner.getFinalPrice();
    if (winner.getBalance() < price) {
      throw new InvalidBidException(
          String.format("%s không đủ số dư (%.0f < %.0f).",
              winner.getUsername(), winner.getBalance(), price));
    }
    winner.setBalance(winner.getBalance() - price);
    auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    auctionService.markAsPaid(auction);
    ratingService.rewardBidder(winner);
    ratingService.rewardSeller(auction.getItem().getSeller());
    System.out.printf("[PAYMENT] %s thanh toán thành công %.0f%n",
        winner.getUsername(), price);
    // TODO: auctionDAO.update(auction), userDAO.update(winner)
  }

  /**
   * Xử lý hết hạn thanh toán (quá 24h).
   * Phạt rating nặng, có thể tự động ban.
   * TODO: userDAO.update(winner).
   *
   * @param auction phiên hết hạn thanh toán
   */
  public void expirePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    auctionWinner.setPaymentStatus(PaymentStatus.EXPIRED);
    ratingService.penalizeLatePayment(auctionWinner.getWinner());
    System.out.printf("[PAYMENT] %s không thanh toán đúng hạn.%n",
        auctionWinner.getWinner().getUsername());
    // TODO: userDAO.update(winner)
  }

  /** Lấy AuctionWinner — ném exception nếu không có. */
  private AuctionWinner requireWinner(Auction auction) {
    AuctionWinner w = auction.getWinner();
    if (w == null) {
      throw new IllegalStateException("Phiên này không có winner.");
    }
    return w;
  }
}