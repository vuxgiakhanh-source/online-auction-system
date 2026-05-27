package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.Auction.AuctionStatus;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Xử lý thanh toán sau khi phiên đấu giá kết thúc. */
public class PaymentService implements IPaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final IWalletService walletService;
  private final SystemBank systemBank = SystemBank.getInstance();

  private final AuctionDAO auctionDAO;
  private final AuctionWinnerDAO auctionWinnerDAO;
  private final SecondChanceOfferDAO secondChanceOfferDAO;
  private final BidTransactionDAO bidTransactionDAO;
  private final UserDAO userDAO;

  /**
   * Per-winner idempotency lock, keyed by AuctionWinner.getId(). Tránh synchronized trên method
   * parameter (Qodana violation).
   */
  private final ConcurrentHashMap<String, Object> winnerLocks = new ConcurrentHashMap<>();

  /** Serialize completePayment / expirePayment / second-chance trên cùng một phiên. */
  private final ConcurrentHashMap<String, Object> auctionPaymentLocks = new ConcurrentHashMap<>();

  private Object winnerLockFor(AuctionWinner w) {
    return winnerLocks.computeIfAbsent(w.getId(), id -> new Object());
  }

  private Object auctionPaymentLock(Auction auction) {
    return auctionPaymentLocks.computeIfAbsent(auction.getId(), id -> new Object());
  }

  public PaymentService(
      IAuctionService auctionService,
      IRatingService ratingService,
      WalletService walletService,
      AuctionWinnerDAO auctionWinnerDAO,
      SecondChanceOfferDAO secondChanceOfferDAO,
      BidTransactionDAO bidTransactionDAO,
      UserDAO userDAO) {
    this(
        auctionService,
        ratingService,
        walletService,
        new AuctionDAO(),
        auctionWinnerDAO,
        secondChanceOfferDAO,
        bidTransactionDAO,
        userDAO);
  }

  /** Constructor đầy đủ — dùng trong test hoặc khi cần inject AuctionDAO tùy chỉnh. */
  public PaymentService(
      IAuctionService auctionService,
      IRatingService ratingService,
      WalletService walletService,
      AuctionDAO auctionDAO,
      AuctionWinnerDAO auctionWinnerDAO,
      SecondChanceOfferDAO secondChanceOfferDAO,
      BidTransactionDAO bidTransactionDAO,
      UserDAO userDAO) {
    this.auctionService = auctionService;
    this.ratingService = ratingService;
    this.walletService = walletService;
    this.auctionDAO = auctionDAO;
    this.auctionWinnerDAO = auctionWinnerDAO;
    this.secondChanceOfferDAO = secondChanceOfferDAO;
    this.bidTransactionDAO = bidTransactionDAO;
    this.userDAO = userDAO;
  }

  @Override
  public void completePayment(Auction auction) {
    synchronized (auctionPaymentLock(auction)) {
      if (auction.getStatus() == AuctionStatus.PAID) {
        throw new PaymentException(
            PaymentException.Reason.WRONG_AMOUNT, "Phiên đã được thanh toán.");
      }
      AuctionWinner auctionWinner = requireWinner(auction);

      if (auctionWinner.getPaymentStatus() == PaymentStatus.EXPIRED) {
        throw new PaymentException(
            PaymentException.Reason.PAYMENT_EXPIRED, "Thanh toán đã hết hạn.");
      }
      if (auctionWinner.getPaymentStatus() == PaymentStatus.FUNDS_HELD
          || auctionWinner.getPaymentStatus() == PaymentStatus.COMPLETED) {
        throw new PaymentException(
            PaymentException.Reason.WRONG_AMOUNT, "Thanh toán đã được xử lý.");
      }

      if (auctionWinner.isExpired()) {
        throw new PaymentException(
            PaymentException.Reason.PAYMENT_EXPIRED, "Đã quá hạn 24h thanh toán.");
      }

      NormalUser winner = auctionWinner.getWinner();

      walletService.executePaymentToBank(
          winner, auctionWinner.getFinalPrice(), auctionWinner.getDepositPaid(), auction.getId());

      // FIX: markFundsHeld() chỉ được gọi MỘT LẦN trong markAsPaid() (qua auctionService).
      // Trước đây gọi markFundsHeld() ở đây rồi markAsPaid() lại gọi lần nữa
      // → confirmReceiptDeadline bị reset sang thời điểm muộn hơn → auto-release trễ 7 ngày.
      // markAsPaid() bên dưới đã gọi markFundsHeld() + persist DB (sau khi fix TODO).
      auctionService.markAsPaid(auction);

      ratingService.rewardBidder(winner);
      auctionService.notify(
          auction,
          AuctionEvent.AuctionEventType.PAYMENT_COMPLETED,
          winner,
          auctionWinner.getFinalPrice());

      log.info(
          "[PAYMENT] Winner {} đã thanh toán {} — tiền giữ tại SystemBank (FUNDS_HELD).",
          winner.getUsername(),
          auctionWinner.getFinalPrice());

      PaymentDTOs.PaymentResultDTO result = new PaymentDTOs.PaymentResultDTO();
      result.setAuctionId(auction.getId());
      result.setFinalPrice(auctionWinner.getFinalPrice());
      result.setPaymentStatus("FUNDS_HELD");
      result.setPaidAt(java.time.LocalDateTime.now());
      ServerBroadcastNotifier.getInstance().notifyPaymentSuccess(auction, result);
      // NOTE: updateFundsHeld() đã được gọi trong markAsPaid() — không cần gọi lại ở đây.
    }
  }

  public void confirmItemReceived(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD) {
      throw new IllegalStateException(
          "Chỉ có thể xác nhận nhận hàng khi tiền đang FUNDS_HELD. "
              + "Trạng thái hiện tại: "
              + auctionWinner.getPaymentStatus());
    }

    auctionWinner.confirmReceipt(); // set ITEM_RECEIVED + reportDeadline = now+3days
    log.info(
        "[PAYMENT] Winner {} xác nhận nhận hàng — 3 ngày report bắt đầu đếm.",
        auctionWinner.getWinner().getUsername());

    // Persist cả status mới lẫn reportDeadline
    auctionWinnerDAO.updatePaymentStatus(
        auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
    auctionWinnerDAO.updateReportDeadline(auctionWinner.getId(), auctionWinner.getReportDeadline());

    ServerBroadcastNotifier.getInstance().notifyItemReceived(auction);
  }

  public void releaseToSeller(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser seller = auction.getItem().getSeller();

    synchronized (winnerLockFor(auctionWinner)) {
      // Idempotency guard: giải ngân khi FUNDS_HELD (winner chưa confirm) hoặc ITEM_RECEIVED (đã
      // confirm, hết report window).
      // FUNDS_HELD: timer auto-release sau 7 ngày không confirm.
      // ITEM_RECEIVED: timer auto-release sau 3 ngày report window.
      if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD
          && auctionWinner.getPaymentStatus() != PaymentStatus.ITEM_RECEIVED) {
        log.warn(
            "[PAYMENT] releaseToSeller skipped — status already {}",
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

    auctionWinnerDAO.updatePaymentStatus(
        auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  public void refundToWinnerFromBank(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser winner = auctionWinner.getWinner();

    synchronized (winnerLockFor(auctionWinner)) {
      // Idempotency guard: hoàn tiền khi FUNDS_HELD hoặc ITEM_RECEIVED.
      // ITEM_RECEIVED: winner đã confirm nhận hàng nhưng admin approve quality report → vẫn phải
      // hoàn.
      if (auctionWinner.getPaymentStatus() != PaymentStatus.FUNDS_HELD
          && auctionWinner.getPaymentStatus() != PaymentStatus.ITEM_RECEIVED) {
        log.warn(
            "[PAYMENT] refundToWinnerFromBank skipped — status already {}",
            auctionWinner.getPaymentStatus());
        return;
      }
      auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    }

    systemBank.refundToWinner(auctionWinner.getFinalPrice());
    // FIX: addBalance() atomic — không cần synchronized(winner)
    winner.addBalance(auctionWinner.getFinalPrice());

    userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

    log.info(
        "[PAYMENT] SystemBank hoàn {} cho Winner {} (report thành công).",
        auctionWinner.getFinalPrice(),
        winner.getUsername());
  }

  @Override
  public void expirePayment(Auction auction) {
    synchronized (auctionPaymentLock(auction)) {
      if (auction.getStatus() == AuctionStatus.CANCELED) {
        return;
      }
      if (auction.getStatus() == AuctionStatus.PAID) {
        return;
      }
      // Guard: winner có thể null nếu auction vừa FINISHED nhưng setWinner chưa commit.
      // Trong race condition này, timer sẽ quét lại ở lần sau — không nên crash.
      if (auction.getWinner() == null) {
        log.warn(
            "[PAYMENT] expirePayment: auctionId={} status={} — winner is null, skipping.",
            auction.getId(),
            auction.getStatus());
        return;
      }
      AuctionWinner auctionWinner = requireWinner(auction);

      if (auctionWinner.getPaymentStatus() == PaymentStatus.FUNDS_HELD
          || auctionWinner.getPaymentStatus() == PaymentStatus.COMPLETED) {
        return;
      }
      if (auctionWinner.getPaymentStatus() == PaymentStatus.EXPIRED) {
        return;
      }

      if (!auctionWinner.isExpired()) {
        return;
      }

      NormalUser winner = auctionWinner.getWinner();
      walletService.forfeitDeposit(winner, auctionWinner.getDepositPaid(), auction.getId());
      ratingService.penalizeLatePayment(winner);
      SystemAdmin.getInstance().autoBanIfNeeded(winner);
      auctionWinner.setPaymentStatus(PaymentStatus.EXPIRED);

      if (auctionWinner.getIsSecondOffer()) {
        auctionService.cancelAuction(
            auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
        log.info(
            "[PAYMENT] Second-chance winner {} không thanh toán đúng hạn | hủy phiên.",
            winner.getUsername());
      } else {
        if (secondChanceOfferDAO.findPendingOfferByAuctionId(auction.getId()) == null) {
          offerSecondChance(auction);
        } else {
          log.info(
              "[PAYMENT] Second Chance Offer PENDING đã tồn tại — bỏ qua tạo lại: auctionId={}",
              auction.getId());
        }
        log.info(
            "[PAYMENT] Winner {} không thanh toán | Cọc tịch thu | Rating phạt.",
            winner.getUsername());
      }

      auctionWinnerDAO.updatePaymentStatus(
          auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
      ServerBroadcastNotifier.getInstance().notifyPaymentFailed(auction);
    }
  }

  @Override
  public void expireSecondChanceOfferIfDue(Auction auction) {
    synchronized (auctionPaymentLock(auction)) {
      if (auction.getStatus() == AuctionStatus.CANCELED) {
        return;
      }
      SecondChanceOffer offer = secondChanceOfferDAO.findPendingOfferByAuctionId(auction.getId());
      if (offer == null || !offer.isExpired()) {
        return;
      }
      finalizeSecondChanceOfferExpired(offer, auction);
    }
  }

  @Override
  public void refundDeposits(Auction auction) {
    String winnerId = auction.getWinner() != null ? auction.getWinner().getWinner().getId() : null;

    List<NormalUser> participants = bidTransactionDAO.findBiddersByAuction(auction.getId());
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;

    log.info(
        "[PAYMENT] Bắt đầu hoàn cọc: auctionId={}, status={}, tổng bidder={}, winner={},"
            + " depositPerBidder={}",
        auction.getId(),
        auction.getStatus(),
        participants.size(),
        winnerId != null ? winnerId : "N/A",
        depositAmount);

    int refunded = 0;
    int skipped = 0;
    for (NormalUser bidder : participants) {
      // FIX QODANA [Unnecessary null check]: winnerId == null || !x.equals(winnerId)
      // thay bằng Objects.equals() — null-safe, ngắn gọn, không cần guard thủ công.
      if (!Objects.equals(bidder.getId(), winnerId)) {
        walletService.unlockDeposit(bidder, depositAmount, auction.getId());
        log.info(
            "[PAYMENT] Hoàn cọc: userId={}, username={}, amount={}, auctionId={}",
            bidder.getId(),
            bidder.getUsername(),
            depositAmount,
            auction.getId());
        refunded++;
      } else {
        log.info(
            "[PAYMENT] Bỏ qua winner: userId={}, username={}, auctionId={}",
            bidder.getId(),
            bidder.getUsername(),
            auction.getId());
        skipped++;
      }
    }

    log.info(
        "[PAYMENT] Hoàn cọc xong: auctionId={}, đã hoàn={}, bỏ qua (winner)={}",
        auction.getId(),
        refunded,
        skipped);
  }

  public void acceptSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException(
          "Second Chance Offer không còn ở PENDING: " + offer.getStatus());
    }
    if (offer.isExpired()) {
      finalizeSecondChanceOfferExpired(offer, auction);
      return;
    }

    NormalUser runnerUp = offer.getRunnerUp();

    AuctionWinner newWinner =
        AuctionWinner.create(
            runnerUp, auction.getId(), offer.getOfferPrice(), offer.getDepositPaid(), true);

    auction.setWinner(newWinner);
    walletService.lockDeposit(runnerUp, offer.getDepositPaid(), auction.getId());

    offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);

    // FIX: persist new winner vào DB ngay lập tức.
    // Trước đây không có dòng này → sau restart getWinner() = null → PaymentHandler crash.
    // Đây là trường hợp song song với closeAuction() (winner thường) — phải saveWinner().
    if (!auctionWinnerDAO.saveWinner(newWinner)) {
      log.error(
          "[PAYMENT] Không thể lưu second-chance AuctionWinner vào DB: auctionId={} runnerId={}",
          auction.getId(),
          runnerUp.getId());
    }

    // FIX: đồng bộ trạng thái auction lên DB (trạng thái có thể đã FINISHED từ lần đóng trước).
    auctionDAO.updateAuctionStatus(auction.getId(), auction.getStatus().name());

    // FIX Bug B: broadcast realtime tới tất cả client đang xem phiên để cập nhật UI winner mới.
    // Trước đây không có broadcast → client hiển thị trạng thái cũ (không biết có winner mới).
    ServerBroadcastNotifier.getInstance().notifySecondChanceAccepted(auction);

    log.info(
        "[PAYMENT] Runner-up {} chấp nhận Second Chance Offer | Giá: {}",
        runnerUp.getUsername(),
        offer.getOfferPrice());

    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  public void declineSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException(
          "Second Chance Offer không còn ở PENDING: " + offer.getStatus());
    }

    offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);
    ServerBroadcastNotifier.getInstance().notifySecondChanceDeclined(auction, offer);
    auctionService.cancelAuction(
        auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);

    log.info(
        "[PAYMENT] Runner-up {} từ chối Second Chance Offer — phiên {} bị hủy.",
        offer.getRunnerUp().getUsername(),
        auction.getId());

    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
  }

  private void offerSecondChance(Auction auction) {
    String winnerId = auction.getWinner() != null ? auction.getWinner().getWinner().getId() : null;

    log.info("[PAYMENT] Tìm runner-up cho phiên {} để tạo SecondChanceOffer...", auction.getId());

    BidTransaction runnerUpBid =
        bidTransactionDAO.findHighestValidBidExcept(auction.getId(), winnerId);

    if (runnerUpBid != null) {
      NormalUser runnerUp = runnerUpBid.getBidder();
      if (runnerUp != null) {
        long depositPaid = auction.getItem().getStartingPrice() * 3 / 10;
        createSecondChanceOffer(runnerUp, auction, runnerUpBid.getAmount(), depositPaid);
      }
    } else {
      log.info("[PAYMENT] Không tìm thấy runner-up hợp lệ. Hủy phiên.");
      auctionService.cancelAuction(
          auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
    }
  }

  public SecondChanceOffer createSecondChanceOffer(
      NormalUser runnerUp, Auction auction, long offerPrice, long depositPaid) {
    if (offerPrice < auction.getReservePrice()) {
      log.info("[PAYMENT] Runner-up bid {} chưa đạt reserve. Hủy phiên.", offerPrice);
      auctionService.cancelAuction(
          auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      return null;
    }

    SecondChanceOffer existing = secondChanceOfferDAO.findPendingOfferByAuctionId(auction.getId());
    if (existing != null) {
      log.info(
          "[PAYMENT] Second Chance Offer PENDING đã có — không tạo/thông báo lại: auctionId={}",
          auction.getId());
      return existing;
    }

    SecondChanceOffer offer =
        SecondChanceOffer.create(runnerUp, auction.getId(), offerPrice, depositPaid);

    log.info(
        "[PAYMENT] Second Chance Offer tạo cho {} | Giá: {} | Hạn: {}",
        runnerUp.getUsername(),
        offerPrice,
        offer.getDeadline());

    if (!secondChanceOfferDAO.saveOffer(offer)) {
      log.error("[PAYMENT] Không lưu được Second Chance Offer: auctionId={}", auction.getId());
      return null;
    }

    // Inbox + realtime chỉ cho seller và runner-up (loại SecondChanceOffer), không gửi toàn bộ
    // JOINED.
    ServerBroadcastNotifier.getInstance().notifySecondChanceOffered(auction, runnerUp, offer);

    // Audit log hệ thống (không tạo inbox cho người tham gia khác).
    auctionService.notify(
        auction,
        AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED,
        runnerUp,
        offerPrice,
        String.format("Second Chance Offer: mua với giá %d trong 24h", offerPrice));

    return offer;
  }

  /**
   * Lấy AuctionWinner, throw rõ ràng nếu null.
   *
   * <p>Dùng PaymentException thay IllegalStateException để PaymentHandler bắt đúng catch block và
   * trả về error code có nghĩa cho client. IllegalStateException chỉ được bắt bởi generic {@code
   * catch (Exception e)} → log ở level ERROR không phân biệt được "lỗi nghiệp vụ" vs "lỗi hệ
   * thống".
   *
   * <p>Race condition: AuctionTimerService có thể gọi closeAuction() mà chưa setWinner() xong trước
   * khi PaymentHandler gọi vào đây — lock theo auctionId ở cả hai phía giảm thiểu rủi ro, nhưng
   * log.error ở đây là tuyến phòng thủ cuối.
   */
  private AuctionWinner requireWinner(Auction auction) {
    AuctionWinner w = auction.getWinner();
    if (w == null) {
      log.error(
          "[PAYMENT] requireWinner: auctionId={} status={} — winner is null. Possible race"
              + " condition between AuctionTimerService.closeAuction() and payment trigger.",
          auction.getId(),
          auction.getStatus());
      throw new PaymentException(
          PaymentException.Reason.WINNER_NOT_FOUND,
          "Phiên này chưa có người thắng cuộc (winner=null). Vui lòng thử lại sau.");
    }
    return w;
  }

  private void finalizeSecondChanceOfferExpired(SecondChanceOffer offer, Auction auction) {
    if (offer == null || offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      return;
    }
    SecondChanceOffer pending = secondChanceOfferDAO.findPendingOfferByAuctionId(auction.getId());
    if (pending == null || !pending.isExpired()) {
      return;
    }
    offer = pending;

    offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);
    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());

    log.info("[PAYMENT] Second Chance Offer hết hạn — phiên {} bị hủy.", auction.getId());
    ServerBroadcastNotifier.getInstance().notifySecondChanceExpired(auction, offer);
    auctionService.cancelAuction(
        auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
  }
}
