package com.group13.auction.service;

import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;

import java.util.List;

/**
 * Xử lý thanh toán sau khi phiên đấu giá kết thúc.
 * Đã thực hiện TODO: inject các DAO cần thiết.
 */
public class PaymentService implements IPaymentService {

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;

  // Tiêm các DAO tương ứng
  private final AuctionWinnerDAO auctionWinnerDAO;
  private final SecondChanceOfferDAO secondChanceOfferDAO;
  private final BidTransactionDAO bidTransactionDAO;
  private final UserDAO userDAO;

  /**
   * Cập nhật constructor để nhận các DAO.
   */
  public PaymentService(IAuctionService auctionService, IRatingService ratingService,
                        WalletService walletService, AuctionWinnerDAO auctionWinnerDAO,
                        SecondChanceOfferDAO secondChanceOfferDAO, BidTransactionDAO bidTransactionDAO,
                        UserDAO userDAO) {
    this.auctionService = auctionService;
    this.ratingService = ratingService;
    this.walletService = walletService;
    this.auctionWinnerDAO = auctionWinnerDAO;
    this.secondChanceOfferDAO = secondChanceOfferDAO;
    this.bidTransactionDAO = bidTransactionDAO;
    this.userDAO = userDAO;
  }

  @Override
  public void completePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (auctionWinner.isExpired()) {
      throw new PaymentException(PaymentException.Reason.PAYMENT_EXPIRED,
              "Đã quá hạn 24h thanh toán.");
    }

    NormalUser winner = auctionWinner.getWinner();
    NormalUser seller = auction.getItem().getSeller();

    walletService.executePaymentTransaction(
            winner, seller,
            auctionWinner.getFinalPrice(),
            auctionWinner.getDepositPaid(),
            auction.getId());

    auctionWinner.setPaymentStatus(AuctionWinner.PaymentStatus.COMPLETED);
    auctionService.markAsPaid(auction);

    // Thực hiện TODO: Cập nhật trạng thái thanh toán xuống DB
    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  /** Chú ý: method ni được gọi ở Scheduler check isExpired */
  @Override
  public void expirePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (auctionWinner.getIsSecondOffer()) {
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      return;
    }

    if (!auctionWinner.isExpired()) {
      return;
    }

    NormalUser winner = auctionWinner.getWinner();

    walletService.forfeitDeposit(winner, auctionWinner.getDepositPaid(), auction.getId());
    ratingService.penalizeLatePayment(winner);
    SystemAdmin.getInstance().autoBanIfNeeded(winner);

    auctionWinner.setPaymentStatus(AuctionWinner.PaymentStatus.EXPIRED);
    offerSecondChance(auction);

    System.out.printf("[PAYMENT] Winner %s không thanh toán | Cọc tịch thu | Rating phạt.%n",
            winner.getUsername());

    // Thực hiện TODO: Cập nhật DB
    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  @Override
  public void refundDeposits(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId()
            : null;

    System.out.printf("[PAYMENT] Hoàn cọc cho tất cả bidder phiên %s (trừ winner %s).%n",
            auction.getId(), winnerId != null ? winnerId : "N/A");

    // Thực hiện TODO: query DB lấy danh sách bidder đã tham gia (những người có bid ACCEPTED)
    List<NormalUser> participants = bidTransactionDAO.findBiddersByAuction(auction.getId());
    double depositAmount = auction.getItem().getStartingPrice() * 0.3;

    for (NormalUser bidder : participants) {
      if (winnerId == null || !bidder.getId().equals(winnerId)) {
        walletService.unlockDeposit(bidder, depositAmount, auction.getId());
      }
    }
  }

  public void acceptSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException("Second Chance Offer không còn ở PENDING: " + offer.getStatus());
    }
    if (offer.isExpired()) {
      offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);
      System.out.printf("[PAYMENT] Second Chance Offer hết hạn — phiên %s bị hủy.%n", auction.getId());
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);

      // Cập nhật trạng thái hết hạn
      secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
      return;
    }

    NormalUser runnerUp = offer.getRunnerUp();
    NormalUser seller = auction.getItem().getSeller();

    // Khởi tạo Winner mới có thông tin của Runner-up
    AuctionWinner newWinner = AuctionWinner.create(
            runnerUp,
            auction.getId(),
            offer.getOfferPrice(),
            offer.getDepositPaid(),
            true
    );

    auction.setWinner(newWinner);
    walletService.lockDeposit(runnerUp, offer.getDepositPaid(), auction.getId());

    offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

    System.out.printf("[PAYMENT] Runner-up %s chấp nhận Second Chance Offer | Giá: %.0f%n",
            runnerUp.getUsername(), offer.getOfferPrice());

    // Thực hiện TODO: Cập nhật DB
    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  public void declineSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException("Second Chance Offer không còn ở PENDING: " + offer.getStatus());
    }

    offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);
    auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);

    System.out.printf("[PAYMENT] Runner-up %s từ chối Second Chance Offer — phiên %s bị hủy.%n",
            offer.getRunnerUp().getUsername(), auction.getId());

    // Thực hiện TODO: Cập nhật DB
    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  private void offerSecondChance(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId() : null;

    System.out.printf("[PAYMENT] Tìm runner-up cho phiên %s để tạo SecondChanceOffer...%n", auction.getId());

    // Thực hiện TODO: Query DAO lấy Bid cao nhất (trừ winner)
    BidTransaction runnerUpBid = bidTransactionDAO.findHighestValidBidExcept(auction.getId(), winnerId);

    if (runnerUpBid != null) {
      NormalUser runnerUp = runnerUpBid.getBidder();

      if (runnerUp != null) {
        double depositPaid = auction.getItem().getStartingPrice() * 0.3;
        createSecondChanceOffer(runnerUp, auction, runnerUpBid.getAmount(), depositPaid);
      }
    } else {
      System.out.println("[PAYMENT] Không tìm thấy runner-up hợp lệ. Hủy phiên.");
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
    }
  }

  public SecondChanceOffer createSecondChanceOffer(NormalUser runnerUp,
                                                   Auction auction, double offerPrice, double depositPaid) {
    if (offerPrice < auction.getReserveStrategy().getReservePrice()) {
      System.out.printf("[PAYMENT] Runner-up bid %.0f chưa đạt reserve. Hủy phiên.%n", offerPrice);
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      return null;
    }

    SecondChanceOffer offer = SecondChanceOffer.create(
            runnerUp, auction.getId(), offerPrice, depositPaid);

    auctionService.notify(auction, AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED,
            runnerUp, offerPrice,
            String.format("Second Chance Offer: mua với giá %.0f trong 24h", offerPrice));

    System.out.printf("[PAYMENT] Second Chance Offer tạo cho %s | Giá: %.0f | Hạn: %s%n",
            runnerUp.getUsername(), offerPrice, offer.getDeadline());

    // Thực hiện TODO: Lưu DB
    secondChanceOfferDAO.saveOffer(offer);

    return offer;
  }

  private AuctionWinner requireWinner(Auction auction) {
    AuctionWinner w = auction.getWinner();
    if (w == null) {
      throw new IllegalStateException("Phiên này không có winner.");
    }
    return w;
  }
}