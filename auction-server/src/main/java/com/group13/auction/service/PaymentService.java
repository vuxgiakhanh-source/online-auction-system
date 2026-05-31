package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.BidTransactionDAO;
import com.group13.auction.dao.DatabaseConnection;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.Auction.AuctionStatus;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.network.server.ServerBroadcastNotifier;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IPaymentService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import java.sql.Connection;
import java.sql.SQLException;
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
  private final FinancialTransactionDAO financialTransactionDAO = new FinancialTransactionDAO();

  /** Lock theo winner/phiên; dọn qua cleanupAuctionLocks khi phiên kết thúc hoặc hủy. */
  private final ConcurrentHashMap<String, Object> winnerLocks = new ConcurrentHashMap<>();

  /** Serialize completePayment / expirePayment / second-chance trên cùng một phiên. */
  private final ConcurrentHashMap<String, Object> auctionPaymentLocks = new ConcurrentHashMap<>();

  private Object winnerLockFor(AuctionWinner w) {
    return winnerLocks.computeIfAbsent(w.getId(), id -> new Object());
  }

  private Object auctionPaymentLock(Auction auction) {
    return auctionPaymentLocks.computeIfAbsent(auction.getId(), id -> new Object());
  }

  /** Khởi tạo PaymentService với AuctionDAO mặc định. */
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

  /** Winner xác nhận đã nhận hàng để bắt đầu cửa sổ report 3 ngày. */
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

  /** Giải ngân khoản tiền đang giữ tại hệ thống cho seller. */
  public void releaseToSeller(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser seller = auction.getItem().getSeller();
    long payout;

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
      payout =
          systemBank.isDbPersistenceEnabled()
              ? releaseToSellerPersisted(auction, auctionWinner, seller)
              : releaseToSellerInMemory(auction, auctionWinner, seller);
    }
    ratingService.rewardSeller(seller);

    log.info("[PAYMENT] Giải ngân {} cho Seller {} từ SystemBank.", payout, seller.getUsername());

    // Phiên đã xong: dọn lock trong map (gọi nhiều lần cũng được).
    cleanupAuctionLocks(auction.getId(), auctionWinner.getId());
  }

  /** Hoàn tiền đang giữ tại hệ thống về lại winner khi đủ điều kiện. */
  public void refundToWinnerFromBank(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    NormalUser winner = auctionWinner.getWinner();
    long refundAmount = auctionWinner.getFinalPrice();

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
      if (systemBank.isDbPersistenceEnabled()) {
        refundToWinnerPersisted(auction, auctionWinner, winner, refundAmount);
      } else {
        refundToWinnerInMemory(auction, auctionWinner, winner, refundAmount);
      }
    }
    log.info(
        "[PAYMENT] SystemBank hoàn {} cho Winner {} (report thành công).",
        refundAmount,
        winner.getUsername());

    // Winner đã xử lý xong, bỏ lock của winner đó.
    cleanupAuctionLocks(auction.getId(), auctionWinner.getId());
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

      boolean terminalCancel = false;
      if (auctionWinner.getIsSecondOffer()) {
        auctionService.cancelAuction(
            auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
        terminalCancel = true;
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

      // Chỉ dọn lock khi phiên đã CANCELED (nhánh second-chance
      // mới phát sinh không terminal — vẫn cần lock cho accept/decline/expire offer sau này.
      if (terminalCancel) {
        cleanupAuctionLocks(auction.getId(), auctionWinner.getId());
      }
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
      // Hết hạn offer → hủy phiên → dọn lock.
      // winnerId null vì runner-up không persist auctionWinner row.
      cleanupAuctionLocks(auction.getId(), null);
    }
  }

  @Override
  public void refundDeposits(Auction auction) {
    String winnerId = auction.getWinner() != null ? auction.getWinner().getWinner().getId() : null;

    // Hoàn cọc theo tập người đang JOINED (đã khóa cọc), không dựa vào lịch sử bid.
    // Trước đây dùng findBiddersByAuction() khiến user join nhưng chưa có bid hợp lệ bị bỏ sót.
    List<NormalUser> participants = new java.util.ArrayList<>();
    for (String userId : userDAO.findJoinedUserIdsByAuctionId(auction.getId())) {
      NormalUser user = userDAO.findNormalUserById(userId);
      if (user != null) {
        participants.add(user);
      }
    }
    long depositAmount = auction.getItem().getStartingPrice() * 3 / 10;

    log.info(
        "[PAYMENT] Bắt đầu hoàn cọc: auctionId={}, status={}, tổng joined={}, winner={},"
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

  /** Chấp nhận second chance offer và gán runner-up thành winner mới của phiên. */
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
    if (!auctionWinnerDAO.saveOrReplaceWinner(newWinner)) {
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

  /** Từ chối second chance offer và hủy phiên do không còn winner hợp lệ. */
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

  /** Tạo second chance offer cho runner-up khi winner cũ hết hạn thanh toán. */
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

  /** Runs seller release as one DB transaction. */
  private long releaseToSellerPersisted(
      Auction auction, AuctionWinner auctionWinner, NormalUser seller) {
    long previousBankBalance = systemBank.getTotalBalance();

    synchronized (systemBank) {
      try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
          long finalPrice = auctionWinner.getFinalPrice();
          long payout = systemBank.payoutToSeller(finalPrice, conn);
          saveSystemBankAudit(
              conn,
              "SYSTEM_BANK",
              seller.getId(),
              payout,
              TransactionType.PAYOUT_TO_SELLER,
              auction.getId());

          long tax = Math.max(0L, finalPrice - payout);
          if (tax > 0) {
            saveSystemBankAudit(
                conn,
                "SYSTEM_BANK",
                "SYSTEM_BANK",
                tax,
                TransactionType.TAX_COLLECTED,
                auction.getId());
          }

          requireUpdated(
              userDAO.addBalance(conn, seller.getId(), payout), "seller balance payout");
          requireUpdated(
              auctionWinnerDAO.updatePaymentStatus(
                  conn, auctionWinner.getId(), PaymentStatus.COMPLETED.name()),
              "auction winner payment status");

          conn.commit();
          seller.addBalance(payout);
          auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
          return payout;
        } catch (SQLException | RuntimeException e) {
          rollbackQuietly(conn);
          systemBank.restoreTotalBalance(previousBankBalance);
          throw new IllegalStateException("Khong the giai ngan cho seller", e);
        } finally {
          restoreAutoCommit(conn, previousAutoCommit);
        }
      } catch (SQLException e) {
        systemBank.restoreTotalBalance(previousBankBalance);
        throw new IllegalStateException("Khong the mo transaction giai ngan", e);
      }
    }
  }

  private long releaseToSellerInMemory(
      Auction auction, AuctionWinner auctionWinner, NormalUser seller) {
    auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    long finalPrice = auctionWinner.getFinalPrice();
    long payout = systemBank.payoutToSeller(finalPrice);
    recordSystemBankAudit(
        "SYSTEM_BANK", seller.getId(), payout, TransactionType.PAYOUT_TO_SELLER, auction.getId());
    long tax = Math.max(0L, finalPrice - payout);
    if (tax > 0) {
      recordSystemBankAudit(
          "SYSTEM_BANK", "SYSTEM_BANK", tax, TransactionType.TAX_COLLECTED, auction.getId());
    }
    seller.addBalance(payout);
    userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());
    auctionWinnerDAO.updatePaymentStatus(
        auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
    return payout;
  }

  private void refundToWinnerPersisted(
      Auction auction, AuctionWinner auctionWinner, NormalUser winner, long refundAmount) {
    long previousBankBalance = systemBank.getTotalBalance();

    synchronized (systemBank) {
      try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
          systemBank.refundToWinner(refundAmount, conn);
          saveSystemBankAudit(
              conn,
              "SYSTEM_BANK",
              winner.getId(),
              refundAmount,
              TransactionType.REFUND_TO_WINNER,
              auction.getId());
          requireUpdated(
              userDAO.addBalance(conn, winner.getId(), refundAmount), "winner refund balance");
          requireUpdated(
              auctionWinnerDAO.updatePaymentStatus(
                  conn, auctionWinner.getId(), PaymentStatus.COMPLETED.name()),
              "auction winner payment status");

          conn.commit();
          winner.addBalance(refundAmount);
          auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
        } catch (SQLException | RuntimeException e) {
          rollbackQuietly(conn);
          systemBank.restoreTotalBalance(previousBankBalance);
          throw new IllegalStateException("Khong the hoan tien cho winner", e);
        } finally {
          restoreAutoCommit(conn, previousAutoCommit);
        }
      } catch (SQLException e) {
        systemBank.restoreTotalBalance(previousBankBalance);
        throw new IllegalStateException("Khong the mo transaction hoan tien", e);
      }
    }
  }

  private void refundToWinnerInMemory(
      Auction auction, AuctionWinner auctionWinner, NormalUser winner, long refundAmount) {
    auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    systemBank.refundToWinner(refundAmount);
    recordSystemBankAudit(
        "SYSTEM_BANK",
        winner.getId(),
        refundAmount,
        TransactionType.REFUND_TO_WINNER,
        auction.getId());
    winner.addBalance(refundAmount);
    userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());
    auctionWinnerDAO.updatePaymentStatus(
        auctionWinner.getId(), auctionWinner.getPaymentStatus().name());
  }

  private void saveSystemBankAudit(
      Connection conn,
      String senderId,
      String receiverId,
      long amount,
      TransactionType type,
      String auctionId)
      throws SQLException {
    FinancialTransaction tx =
        FinancialTransaction.create(senderId, receiverId, amount, type, auctionId);
    requireUpdated(financialTransactionDAO.saveTransaction(conn, tx), "system bank audit " + type);
  }

  private void requireUpdated(boolean updated, String operation) throws SQLException {
    if (!updated) {
      throw new SQLException("No row updated for " + operation);
    }
  }

  private void rollbackQuietly(Connection conn) {
    try {
      conn.rollback();
    } catch (SQLException rollbackEx) {
      log.error("Payment transaction rollback failed", rollbackEx);
    }
  }

  private void restoreAutoCommit(Connection conn, boolean autoCommit) {
    try {
      conn.setAutoCommit(autoCommit);
    } catch (SQLException autoCommitEx) {
      log.warn("Failed to restore autoCommit after payment transaction", autoCommitEx);
    }
  }

  /** Returns the auction winner or throws a PaymentException with a client-safe reason. */
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

  private void recordSystemBankAudit(
      String senderId, String receiverId, long amount, TransactionType type, String auctionId) {
    FinancialTransaction tx =
        FinancialTransaction.create(senderId, receiverId, amount, type, auctionId);
    if (!financialTransactionDAO.saveTransaction(tx)) {
      if (systemBank.isDbPersistenceEnabled()) {
        throw new IllegalStateException(
            "Khong luu duoc audit SystemBank: type="
                + type
                + ", amount="
                + amount
                + ", auctionId="
                + auctionId);
      }
      log.warn(
          "[PAYMENT] Không lưu được audit SystemBank: type={}, amount={}, auctionId={}",
          type,
          amount,
          auctionId);
    }
  }

  private void finalizeSecondChanceOfferExpired(SecondChanceOffer offer, Auction auction) {
    if (offer == null || offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      return;
    }
    if (!offer.isExpired()) {
      return;
    }
    // Caller đã truyền offer đúng (đã load từ DB nếu cần), không query lại tránh null sai.

    offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);
    secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());

    log.info("[PAYMENT] Second Chance Offer hết hạn — phiên {} bị hủy.", auction.getId());
    ServerBroadcastNotifier.getInstance().notifySecondChanceExpired(auction, offer);
    auctionService.cancelAuction(
        auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
  }

  /**
   * Dọn lock khi phiên kết thúc hoặc hủy. Gọi nhiều lần vẫn an toàn.
   *
   * @param auctionId mã phiên
   * @param winnerId mã winner, hoặc null nếu không có winner
   */
  public void cleanupAuctionLocks(String auctionId, String winnerId) {
    if (auctionId != null) {
      auctionPaymentLocks.remove(auctionId);
    }
    if (winnerId != null) {
      winnerLocks.remove(winnerId);
    }
  }
}
