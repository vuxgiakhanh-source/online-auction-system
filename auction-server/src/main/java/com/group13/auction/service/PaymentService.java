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

/**
 * Xử lý thanh toán sau khi phiên đấu giá kết thúc.
 * Đã thực hiện TODO: inject các DAO cần thiết.
 */
public class PaymentService implements IPaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;
  private final SystemBank systemBank = SystemBank.getInstance();

  // Tiêm các DAO tương ứng
  private final AuctionWinnerDAO auctionWinnerDAO;
  private final SecondChanceOfferDAO secondChanceOfferDAO;
  private final BidTransactionDAO bidTransactionDAO;
  private final UserDAO userDAO;

  /**
   * Cập nhật constructor để nhận các DAO.
   */
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

    // Chuyển tiền từ Winner -> SystemBank (không chuyển cho Seller ngay)
    walletService.executePaymentToBank(
            winner,
            auctionWinner.getFinalPrice(),
            auctionWinner.getDepositPaid(),
            auction.getId());


    // Đánh dấu FUNDS_HELD và kích hoạt 7 ngày "nhận hàng"
    auctionWinner.markFundsHeld();
    auctionService.markAsPaid(auction);

    ratingService.rewardBidder(winner);
    auctionService.notify(auction, AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
            winner, auctionWinner.getFinalPrice());

    log.info("[PAYMENT] Winner {} đã thanh toán {} — tiền giữ tại SystemBank (FUNDS_HELD).", winner.getUsername(), auctionWinner.getFinalPrice());

    // Thực hiện TODO: Cập nhật trạng thái thanh toán xuống DB
    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  /**
   * Winner bấm "Nhận hàng" -> kích hoạt 3 ngày cho phép gửi report.
   * Sau 3 ngày không report -> {@link #releaseToSeller} được Scheduler gọi.
   *
   * @param auction phiên đấu giá
   */
  public void confirmReceipt(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD) {
      throw new IllegalStateException(
              "Chỉ có thể xác nhận nhận hàng khi tiền đang FUNDS_HELD. "
                      + "Trạng thái hiện tại: " + auctionWinner.getPaymentStatus());
    }

    auctionWinner.confirmReceipt();
    log.info("[PAYMENT] Winner {} xác nhận nhận hàng — 3 ngày report bắt đầu đếm.", auctionWinner.getWinner().getUsername());

    // TODO: [DB] auctionWinnerDAO.updateReportDeadline(auctionWinner.getId(), reportDeadline)
    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(),
            auctionWinner.getPaymentStatus().name());
  }

  /**
   * Giải ngân cho Seller từ SystemBank.
   *
   * <p>Gọi khi:
   * <ul>
   *   <li>Hết hạn xác nhận nhận hàng (7 ngày sau FUNDS_HELD), hoặc</li>
   *   <li>Hết hạn report (3 ngày sau confirmReceipt), hoặc</li>
   *   <li>Admin từ chối QualityReport.</li>
   * </ul>
   *
   * @param auction phiên đấu giá
   */
  public void releaseToSeller(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser seller = auction.getItem().getSeller();

    long payout = systemBank.payoutToSeller(auctionWinner.getFinalPrice());
    seller.setBalance(seller.getBalance() + payout);

    // TODO: [DB] userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit())
    userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());

    ratingService.rewardSeller(seller);

    auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);

    log.info("[PAYMENT] Giải ngân {} cho Seller {} từ SystemBank.", payout, seller.getUsername());

    // TODO: [DB] auctionWinnerDAO.updatePaymentStatus(...)
    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(),
            auctionWinner.getPaymentStatus().name());
  }

  /**
   * Report thành công -> SystemBank hoàn toàn bộ tiền lại cho Winner.
   * Chỉ {@link QualityReportService} gọi sau khi Admin approve report.
   *
   * @param auction phiên đấu giá
   */
  public void refundToWinnerFromBank(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser winner = auctionWinner.getWinner();

    // SystemBank hoàn toàn bộ finalPrice về winner
    systemBank.refundToWinner(auctionWinner.getFinalPrice());
    winner.setBalance(winner.getBalance() + auctionWinner.getFinalPrice());

    // TODO: [DB] userDAO.updateBalances(winner.getId(), ...)
    userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

    log.info("[PAYMENT] SystemBank hoàn {} cho Winner {} (report thành công).", auctionWinner.getFinalPrice(), winner.getUsername());
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

    log.info("[PAYMENT] Winner {} không thanh toán | Cọc tịch thu | Rating phạt.",
            winner.getUsername());

    // Thực hiện TODO: Cập nhật DB
    auctionWinnerDAO.updatePaymentStatus(auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  @Override
  public void refundDeposits(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId()
            : null;

    log.info("[PAYMENT] Hoàn cọc cho tất cả bidder phiên {} (trừ winner {}).",
            auction.getId(), winnerId != null ? winnerId : "N/A");


    // Thực hiện TODO: query DB lấy danh sách bidder đã tham gia (những người có bid ACCEPTED)
    List<NormalUser> participants = bidTransactionDAO.findBiddersByAuction(auction.getId());
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;

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
      log.info("[PAYMENT] Second Chance Offer hết hạn — phiên {} bị hủy.", auction.getId());
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);

      // Cập nhật trạng thái hết hạn
      secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
      return;
    }

    NormalUser runnerUp = offer.getRunnerUp();

    // Khởi tạo Winner mới có thông tin của Runner-up
    AuctionWinner newWinner = AuctionWinner.create(
            runnerUp,
            auction.getId(),
            offer.getOfferPrice(),
            offer.getDepositPaid(),
            true
    );
    // TODO: DAO lưu thông tin người Winner mới

    auction.setWinner(newWinner);
    // TODO: notificationDao.save() - báo cho seller là runner-up đã accept
    walletService.lockDeposit(runnerUp, offer.getDepositPaid(), auction.getId());

    offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

    log.info("[PAYMENT] Runner-up {} chấp nhận Second Chance Offer | Giá: {}",
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

    log.info("[PAYMENT] Runner-up {} từ chối Second Chance Offer — phiên {} bị hủy.",
            offer.getRunnerUp().getUsername(), auction.getId());

    // TODO: notificationDao.save() - báo cho seller
    // Thực hiện TODO: Cập nhật DB
    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  private void offerSecondChance(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId() : null;

    log.info("[PAYMENT] Tìm runner-up cho phiên {} để tạo SecondChanceOffer...", auction.getId());

    // Thực hiện TODO: Query DAO lấy Bid cao nhất (trừ winner)
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
    // TODO: notificationDao.save() - báo cho runner-up
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