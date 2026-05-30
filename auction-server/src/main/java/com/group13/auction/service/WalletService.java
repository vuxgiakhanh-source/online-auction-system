package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quản lý tài chính và cọc tập trung - Single Responsibility.
 *
 * <p>Mọi thao tác tiền tệ đều tạo {@link FinancialTransaction} để ghi log audit.
 *
 * <p>Rollback: nếu một bước trong luồng giao dịch thất bại, toàn bộ phải được rollback để tránh mất
 * tiền của người dùng.
 *
 * <p>FIX QODANA [Synchronization on method parameter]: Thay {@code synchronized(user)} bằng
 * per-user lock registry dùng {@link ConcurrentHashMap}. Lock lấy theo {@code userId} (String
 * identity), đảm bảo atomic "read-modify-write" mà không lock trên tham số truyền vào.
 */
public class WalletService implements IWalletService {

  private static final Logger log = LoggerFactory.getLogger(WalletService.class);

  private final SystemBank systemBank;
  private final List<FinancialTransaction> transactionLog;
  private final IRatingService ratingService;
  private final FinancialTransactionDAO financialTransactionDAO;
  private final UserDAO userDAO;

  /**
   * Per-user lock registry. Key = userId (String). Value = lock object dùng để synchronized.
   * computeIfAbsent đảm bảo mỗi userId chỉ có đúng 1 lock object.
   */
  private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

  public WalletService(
      FinancialTransactionDAO financialTransactionDAO,
      UserDAO userDAO,
      IRatingService ratingService) {
    this.systemBank = SystemBank.getInstance();
    this.transactionLog = new CopyOnWriteArrayList<>();
    this.ratingService = ratingService;
    this.financialTransactionDAO = financialTransactionDAO;
    this.userDAO = userDAO;
  }

  // ─── Private helper ───────────────────────────────────────────────────────

  /**
   * Lấy lock object theo userId từ registry. FIX: Thay synchronized(user/bidder/winner) — lock trên
   * tham số ngoài — bằng lock object gắn với identity của user (userId).
   */
  private Object lockFor(NormalUser user) {
    return userLocks.computeIfAbsent(user.getId(), id -> new Object());
  }

  /** Đồng bộ balance/lockedDeposit trên object RAM với giá trị thật trong DB. */
  private void reloadBalancesFromDatabase(NormalUser user) {
    NormalUser fromDb = userDAO.findUserCoreByUsername(user.getUsername());
    if (fromDb == null) {
      return;
    }
    user.restoreBalances(fromDb.getBalance(), fromDb.getLockedDeposit());
    AuctionManager.getInstance().refreshUser(fromDb);
  }

  /**
   * FIX STALE CACHE: Sau khi balance hoặc lockedDeposit thay đổi trên một user object KHÁC với
   * session cache (vd: PaymentService.refundDeposits() dùng fresh object từ DB), session cache của
   * BidHandler không biết về thay đổi này.
   *
   * <p>Hậu quả: BidHandler.lockDeposit(cachedUser, ...) kiểm tra getAvailableBalance() trên cache
   * stale → lockedDeposit cao hơn thực tế → INSUFFICIENT_DEPOSIT dù balance đủ.
   *
   * <p>Fix: sau mỗi thao tác thay đổi balance/lockedDeposit, đồng bộ ngược về session cache nếu
   * user đang online. Chỉ cập nhật balance/lockedDeposit, KHÔNG đụng joinedAuctionIds hay roles
   * (những field đó được quản lý riêng bởi BidService).
   */
  private void syncBalanceToSessionCache(String userId, long balance, long lockedDeposit) {
    ClientSession session = SessionManager.getInstance().getByUserId(userId);
    if (session == null) {
      return;
    }
    NormalUser cached = session.getCachedUser();
    if (cached == null) {
      return;
    }
    // restoreBalances() set cả balance lẫn lockedDeposit atomic
    cached.restoreBalances(balance, lockedDeposit);
    log.debug(
        "Session cache balance synced: userId={}, balance={}, lockedDeposit={}",
        userId,
        balance,
        lockedDeposit);
  }

  // ─── Deposit ──────────────────────────────────────────────────────────────

  /** Nạp tiền vào tài khoản NormalUser. */
  @Override
  public void deposit(NormalUser user, long amount) {
    if (!ratingService.isWalletOperationAllowed(user)) {
      throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
    }

    synchronized (lockFor(user)) {
      userDAO.addBalance(user.getId(), amount);
      reloadBalancesFromDatabase(user);
      log.info(
          "Deposit success: username={}, amount={}, newBalance={}",
          user.getUsername(),
          amount,
          user.getBalance());
      syncBalanceToSessionCache(user.getId(), user.getBalance(), user.getLockedDeposit());
    }
  }

  // ─── Withdraw ─────────────────────────────────────────────────────────────

  /**
   * Rút tiền từ tài khoản NormalUser. Chỉ rút được phần availableBalance (không rút vào tiền đang
   * khóa cọc).
   */
  @Override
  public void withdraw(NormalUser user, long amount) {
    if (!ratingService.isWalletOperationAllowed(user)) {
      throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0.");
    }

    synchronized (lockFor(user)) {
      if (user.getAvailableBalance() < amount) {
        throw new IllegalArgumentException(
            String.format(
                "Số dư khả dụng không đủ. Khả dụng: %d, Yêu cầu: %d",
                user.getAvailableBalance(), amount));
      }
      user.setBalance(user.getBalance() - amount);
      log.info(
          "Withdraw success: username={}, amount={}, newBalance={}",
          user.getUsername(),
          amount,
          user.getBalance());
      userDAO.updateBalances(user.getId(), user.getBalance(), user.getLockedDeposit());
      // FIX: đồng bộ balance mới về session cache
      syncBalanceToSessionCache(user.getId(), user.getBalance(), user.getLockedDeposit());
    }
  }

  // ─── Deposit (cọc) ────────────────────────────────────────────────────────

  /** Khóa cọc khi joinAuction thành công. */
  @Override
  public void lockDeposit(NormalUser bidder, long depositAmount, String auctionId) {
    synchronized (lockFor(bidder)) {
      if (bidder.getAvailableBalance() < depositAmount) {
        throw new AuctionBusinessException(AuctionBusinessException.Reason.INSUFFICIENT_DEPOSIT);
      }
      bidder.lockDeposit(depositAmount);
      userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

      FinancialTransaction tx =
          FinancialTransaction.create(
              bidder.getId(),
              "SYSTEM_LOCKED",
              depositAmount,
              TransactionType.DEPOSIT_LOCK,
              auctionId);
      transactionLog.add(tx);
      tx.printInfo();
      financialTransactionDAO.saveTransaction(tx);

      // BUG FIX: sync lockedDeposit mới về session cache.
      // lockDeposit() được gọi từ 2 nơi:
      // (1) BidService.joinAsNormalUser() — bidder là session.cachedUser → update in-place ✓
      // (2) PaymentService.acceptSecondChanceOffer() — runnerUp là fresh DB object,
      //     KHÔNG phải session.cachedUser → lockedDeposit trong cache không tăng
      //     → getAvailableBalance() tiếp theo trả giá trị cao hơn thực tế.
      syncBalanceToSessionCache(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());
    }
  }

  /** Hoàn cọc cho bidder không thắng khi phiên kết thúc. */
  @Override
  public void unlockDeposit(NormalUser bidder, long depositAmount, String auctionId) {
    synchronized (lockFor(bidder)) {
      bidder.unlockDeposit(depositAmount);
      userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

      FinancialTransaction tx =
          FinancialTransaction.create(
              "SYSTEM_LOCKED",
              bidder.getId(),
              depositAmount,
              TransactionType.DEPOSIT_UNLOCK,
              auctionId);
      transactionLog.add(tx);
      tx.printInfo();
      financialTransactionDAO.saveTransaction(tx);
      // FIX STALE CACHE: PaymentService.refundDeposits() gọi method này với fresh object
      // từ findBiddersByAuction(), không phải session cache object → session cache
      // của BidHandler giữ nguyên lockedDeposit cao → getAvailableBalance() trả về
      // giá trị thấp hơn thực tế → INSUFFICIENT_DEPOSIT dù balance thực đủ.
      syncBalanceToSessionCache(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());
    }
  }

  /** Tịch thu cọc của winner không thanh toán -> chuyển vào SystemBank. */
  @Override
  public void forfeitDeposit(NormalUser winner, long depositAmount, String auctionId) {
    synchronized (lockFor(winner)) {
      // Giải phóng phần locked trước
      winner.unlockDeposit(depositAmount);
      // FIX: trừ tiền khỏi balance — nếu không gọi dòng này thì lockedDeposit
      // giảm về 0 nhưng availableBalance tăng lại, user vẫn giữ đủ tiền dù đã bị tịch thu.
      winner.setBalance(Math.max(0, winner.getBalance() - depositAmount));
      userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

      systemBank.receiveForfeittedDeposit(depositAmount);

      FinancialTransaction tx =
          FinancialTransaction.create(
              winner.getId(),
              "SYSTEM_BANK",
              depositAmount,
              TransactionType.DEPOSIT_FORFEIT,
              auctionId);
      transactionLog.add(tx);
      tx.printInfo();
      log.info(
          "Deposit forfeited: username={}, depositAmount={}, auctionId={}",
          winner.getUsername(),
          depositAmount,
          auctionId);
      financialTransactionDAO.saveTransaction(tx);
      // FIX: sync về session cache
      syncBalanceToSessionCache(winner.getId(), winner.getBalance(), winner.getLockedDeposit());
    }
  }

  /**
   * Phạt một phần cọc khi bidder là current leader mà tự rời phiên. Tịch thu {@code penaltyAmount}
   * vào SystemBank, hoàn trả phần còn lại về balance. Toàn bộ thực hiện trong một lock.
   */
  @Override
  public void partialForfeitDeposit(
      NormalUser bidder, long depositAmount, long penaltyAmount, String auctionId) {
    long safeDeposit = Math.max(0, depositAmount);
    long safePenalty = Math.min(Math.max(0, penaltyAmount), safeDeposit);

    synchronized (lockFor(bidder)) {
      // Mở khóa toàn bộ cọc trước (giảm lockedDeposit)
      bidder.unlockDeposit(safeDeposit);

      // Trừ phần phạt trực tiếp khỏi balance → chuyển vào SystemBank
      if (safePenalty > 0) {
        bidder.setBalance(bidder.getBalance() - safePenalty);
        systemBank.receiveForfeittedDeposit(safePenalty);

        FinancialTransaction penaltyTx =
            FinancialTransaction.create(
                bidder.getId(),
                "SYSTEM_BANK",
                safePenalty,
                TransactionType.DEPOSIT_FORFEIT,
                auctionId);
        transactionLog.add(penaltyTx);
        financialTransactionDAO.saveTransaction(penaltyTx);
      }

      userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());
      syncBalanceToSessionCache(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

      long refund = safeDeposit - safePenalty;
      log.warn(
          "Partial deposit forfeited (bid-then-leave): userId={}, auctionId={}, "
              + "totalDeposit={}, penalty={}, refund={}",
          bidder.getId(),
          auctionId,
          safeDeposit,
          safePenalty,
          refund);
    }
  }

  // ─── Payment ──────────────────────────────────────────────────────────────

  /**
   * Chuyển tiền từ Winner -> SystemBank (FUNDS_HELD). Seller CHƯA nhận tiền - chỉ nhận qua
   * PaymentService.releaseToSeller.
   */
  public void executePaymentToBank(
      NormalUser winner, long finalPrice, long depositPaid, String auctionId) {
    synchronized (lockFor(winner)) {
      long remaining = finalPrice - depositPaid;

      if (winner.getAvailableBalance() < remaining) {
        throw new PaymentException(
            PaymentException.Reason.INSUFFICIENT_BALANCE,
            String.format("Cần %d, khả dụng: %d", remaining, winner.getAvailableBalance()));
      }

      long originalBalance = winner.getBalance();
      long originalLocked = winner.getLockedDeposit();

      List<FinancialTransaction> batchTx = new ArrayList<>();
      try {
        winner.setBalance(winner.getBalance() - remaining);
        batchTx.add(
            FinancialTransaction.create(
                winner.getId(),
                "SYSTEM_BANK",
                remaining,
                TransactionType.PAYMENT_FROM_WINNER,
                auctionId));

        winner.unlockDeposit(depositPaid);
        winner.setBalance(winner.getBalance() - depositPaid);
        batchTx.add(
            FinancialTransaction.create(
                "SYSTEM_LOCKED",
                winner.getId(),
                depositPaid,
                TransactionType.DEPOSIT_UNLOCK,
                auctionId));

        userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

        // FIX Bug SystemBank double-credit:
        // TX3 (ghi thêm finalPrice như PAYMENT_FROM_WINNER) đã bị xóa.
        // Lý do: TX1 đã ghi 'remaining = finalPrice - depositPaid' và TX2 ghi phần deposit.
        // Ghi thêm TX3 tạo phantom record, làm audit log hiển thị winner "trả" 2×finalPrice.
        //
        // FIX systemBank.receive():
        // systemBank.receive(depositPaid) đã được gọi trong
        // AuctionService.recordWinnerDepositHeldInBank()
        // khi phiên kết thúc (closeAuction). Do đó ở đây chỉ credit phần 'remaining' thực sự
        // mới đến từ ví winner. Nếu gọi receive(finalPrice) sẽ bị double-count depositPaid.
        transactionLog.addAll(batchTx);
        for (FinancialTransaction tx : batchTx) {
          tx.printInfo();
          financialTransactionDAO.saveTransaction(tx);
        }

        // Chỉ ghi nhận phần còn lại (remaining) vào SystemBank sau khi RAM + DB đã persist.
        // depositPaid đã được receive() trong recordWinnerDepositHeldInBank() tại closeAuction().
        systemBank.receive(remaining);

        // BUG FIX: syncBalance sau thanh toán thành công.
        // Tất cả method khác (deposit, withdraw, unlock, forfeit) đều gọi sync.
        // executePaymentToBank bị bỏ sót → winner.cachedUser giữ balance cũ
        // → getAvailableBalance() trả sai cho các request tiếp theo trong session.
        syncBalanceToSessionCache(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

        log.info(
            "Payment to bank success: username={}, finalPrice={}, auctionId={}",
            winner.getUsername(),
            finalPrice,
            auctionId);

      } catch (Exception e) {
        winner.restoreBalances(originalBalance, originalLocked);
        try {
          userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());
        } catch (Exception syncEx) {
          log.error(
              "Rollback DB sync failed: auctionId={}, error={}", auctionId, syncEx.getMessage());
        }
        log.error("Payment rolled back: auctionId={}, error={}", auctionId, e.getMessage());
        throw new PaymentException(
            PaymentException.Reason.WRONG_AMOUNT,
            "Giao dịch thất bại, đã rollback: " + e.getMessage());
      }
    }
  }

  /**
   * @return lịch sử giao dịch (chỉ đọc)
   */
  public List<FinancialTransaction> getTransactionLog() {
    return Collections.unmodifiableList(transactionLog);
  }
}
