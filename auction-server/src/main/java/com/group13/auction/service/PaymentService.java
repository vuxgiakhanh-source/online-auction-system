package com.group13.auction.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
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
import java.util.Objects;

/**
 * Xử lý thanh toán sau khi phiên đấu giá kết thúc.
 */
public class PaymentService implements IPaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;
  private final SystemBank systemBank = SystemBank.getInstance();

  private final AuctionWinnerDAO auctionWinnerDAO;
  private final SecondChanceOfferDAO secondChanceOfferDAO;
  private final BidTransactionDAO bidTransactionDAO;
  private final UserDAO userDAO;

  public PaymentService(
          IAuctionService auctionService,
          IRatingService ratingService,
          WalletService walletService,
          AuctionWinnerDAO auctionWinnerDAO,
          SecondChanceOfferDAO secondChanceOfferDAO,
          BidTransactionDAO bidTransactionDAO,
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

    walletService.executePaymentToBank(
            winner,
            auctionWinner.getFinalPrice(),
            auctionWinner.getDepositPaid(),
            auction.getId());

    auctionWinner.markFundsHeld();
    auctionService.markAsPaid(auction);

    ratingService.rewardBidder(winner);
    auctionService.notify(auction, AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
            winner, auctionWinner.getFinalPrice());

    log.info("[PAYMENT] Winner {} đã thanh toán {} — tiền giữ tại SystemBank (FUNDS_HELD).",
            winner.getUsername(), auctionWinner.getFinalPrice());

    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  public void confirmReceipt(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD) {
      throw new IllegalStateException(
              "Chỉ có thể xác nhận nhận hàng khi tiền đang FUNDS_HELD. "
                      + "Trạng thái hiện tại: " + auctionWinner.getPaymentStatus());
    }

    auctionWinner.confirmReceipt();
    log.info("[PAYMENT] Winner {} xác nhận nhận hàng — 3 ngày report bắt đầu đếm.",
            auctionWinner.getWinner().getUsername());

    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(),
            auctionWinner.getPaymentStatus().name());
  }

  public void releaseToSeller(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser seller = auction.getItem().getSeller();

    synchronized (auctionWinner) {
      // Idempotency guard: chỉ giải ngân khi đang FUNDS_HELD.
      // Thread thứ 2 gọi đồng thời sẽ thấy COMPLETED và bỏ qua — không sinh tiền ảo.
      if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD) {
        log.warn("[PAYMENT] releaseToSeller skipped — status already {}",
                auctionWinner.getPaymentStatus());
        return;
      }
      auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    }

    long payout = systemBank.payoutToSeller(auctionWinner.getFinalPrice());
    // FIX: addBalance() dùng AtomicLong.addAndGet — atomic, không cần synchronized(seller)
    // synchronized(seller) lock trên tham số ngoài → vi phạm Qodana + deadlock risk
    seller.addBalance(payout);

    userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());

    ratingService.rewardSeller(seller);

    log.info("[PAYMENT] Giải ngân {} cho Seller {} từ SystemBank.", payout, seller.getUsername());

    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(),
            auctionWinner.getPaymentStatus().name());
  }

  public void refundToWinnerFromBank(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser winner = auctionWinner.getWinner();

    synchronized (auctionWinner) {
      // Idempotency guard: chỉ hoàn tiền khi đang FUNDS_HELD.
      // Thread thứ 2 gọi đồng thời sẽ thấy COMPLETED và bỏ qua — không double-refund.
      if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD) {
        log.warn("[PAYMENT] refundToWinnerFromBank skipped — status already {}",
                auctionWinner.getPaymentStatus());
        return;
      }
      auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    }

    systemBank.refundToWinner(auctionWinner.getFinalPrice());
    // FIX: addBalance() atomic — không cần synchronized(winner)
    winner.addBalance(auctionWinner.getFinalPrice());

    userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

    log.info("[PAYMENT] SystemBank hoàn {} cho Winner {} (report thành công).",
            auctionWinner.getFinalPrice(), winner.getUsername());
  }

  @Override
  public void expirePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (!auctionWinner.isExpired()) {
      return;
    }

    NormalUser winner = auctionWinner.getWinner();
    walletService.forfeitDeposit(winner, auctionWinner.getDepositPaid(), auction.getId());
    ratingService.penalizeLatePayment(winner);
    SystemAdmin.getInstance().autoBanIfNeeded(winner);
    auctionWinner.setPaymentStatus(AuctionWinner.PaymentStatus.EXPIRED);

    if (auctionWinner.getIsSecondOffer()) {
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      log.info("[PAYMENT] Second-chance winner {} không thanh toán đúng hạn | hủy phiên.",
              winner.getUsername());
    } else {
      offerSecondChance(auction);
      log.info("[PAYMENT] Winner {} không thanh toán | Cọc tịch thu | Rating phạt.",
              winner.getUsername());
    }

    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  @Override
  public void expireSecondChanceOfferIfDue(Auction auction) {
    SecondChanceOffer offer = secondChanceOfferDAO.findPendingOfferByAuctionId(auction.getId());
    if (offer == null || !offer.isExpired()) {
      return;
    }
    finalizeSecondChanceOfferExpired(offer, auction);
  }

  @Override
  public void refundDeposits(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId()
            : null;

    log.info("[PAYMENT] Hoàn cọc cho tất cả bidder phiên {} (trừ winner {}).",
            auction.getId(), winnerId != null ? winnerId : "N/A");

    List<NormalUser> participants = bidTransactionDAO.findBiddersByAuction(auction.getId());
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;

    for (NormalUser bidder : participants) {
      // FIX QODANA [Unnecessary null check]: winnerId == null || !x.equals(winnerId)
      // thay bằng Objects.equals() — null-safe, ngắn gọn, không cần guard thủ công.
      if (!Objects.equals(bidder.getId(), winnerId)) {
        walletService.unlockDeposit(bidder, depositAmount, auction.getId());
      }
    }
  }

  public void acceptSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException("Second Chance Offer không còn ở PENDING: " + offer.getStatus());
    }
    if (offer.isExpired()) {
      finalizeSecondChanceOfferExpired(offer, auction);
      return;
    }

    NormalUser runnerUp = offer.getRunnerUp();

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

    log.info("[PAYMENT] Runner-up {} chấp nhận Second Chance Offer | Giá: {}",
            runnerUp.getUsername(), offer.getOfferPrice());

    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  public void declineSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException("Second Chance Offer không còn ở PENDING: " + offer.getStatus());
    }

    offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);
    auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);

    log.info("[PAYMENT] Runner-up {} từ chối Second Chance Offer — phiên {} bị hủy.",
            offer.getRunnerUp().getUsername(), auction.getId());

    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  private void offerSecondChance(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId() : null;

    log.info("[PAYMENT] Tìm runner-up cho phiên {} để tạo SecondChanceOffer...", auction.getId());

    BidTransaction runnerUpBid = bidTransactionDAO.findHighestValidBidExcept(auction.getId(), winnerId);

    if (runnerUpBid != null) {
      NormalUser runnerUp = runnerUpBid.getBidder();
      if (runnerUp != null) {
        long depositPaid = auction.getItem().getStartingPrice() * 3 / 10;
        createSecondChanceOffer(runnerUp, auction, runnerUpBid.getAmount(), depositPaid);
      }
    } else {
      log.info("[PAYMENT] Không tìm thấy runner-up hợp lệ. Hủy phiên.");
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
    }

    auctionService.notify(auction, AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED, null, 0L);
  }

  public SecondChanceOffer createSecondChanceOffer(NormalUser runnerUp,
                                                   Auction auction, long offerPrice, long depositPaid) {
    if (offerPrice < auction.getReservePrice()) {
      log.info("[PAYMENT] Runner-up bid {} chưa đạt reserve. Hủy phiên.", offerPrice);
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      return null;
    }

    SecondChanceOffer offer = SecondChanceOffer.create(
            runnerUp, auction.getId(), offerPrice, depositPaid);

    auctionService.notify(auction, AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED,
            runnerUp, offerPrice,
            String.format("Second Chance Offer: mua với giá %d trong 24h", offerPrice));

    log.info("[PAYMENT] Second Chance Offer tạo cho {} | Giá: {} | Hạn: {}",
            runnerUp.getUsername(), offerPrice, offer.getDeadline());

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

  private void finalizeSecondChanceOfferExpired(SecondChanceOffer offer, Auction auction) {
    offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);
    log.info("[PAYMENT] Second Chance Offer hết hạn — phiên {} bị hủy.", auction.getId());
    auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }
}