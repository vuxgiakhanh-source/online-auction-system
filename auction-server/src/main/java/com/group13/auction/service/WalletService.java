package com.group13.auction.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.service.iservice.IWalletService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý tài chính và cọc tập trung - Single Responsibility.
 *
 * <p>Mọi thao tác tiền tệ đều tạo {@link FinancialTransaction} để ghi log audit.
 *
 * <p>Rollback: nếu một bước trong luồng giao dịch thất bại, toàn bộ phải được
 * rollback để tránh mất tiền của người dùng.
 *
 * <p>FIX QODANA [Synchronization on method parameter]:
 * Thay {@code synchronized(user)} bằng per-user lock registry dùng
 * {@link ConcurrentHashMap}. Lock lấy theo {@code userId} (String identity),
 * đảm bảo atomic "read-modify-write" mà không lock trên tham số truyền vào.
 */
public class WalletService implements IWalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final SystemBank systemBank;
    private final List<FinancialTransaction> transactionLog;
    private final IRatingService ratingService;
    private final FinancialTransactionDAO financialTransactionDAO;
    private final UserDAO userDAO;

    /**
     * Per-user lock registry.
     * Key = userId (String). Value = lock object dùng để synchronized.
     * computeIfAbsent đảm bảo mỗi userId chỉ có đúng 1 lock object.
     */
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public WalletService(FinancialTransactionDAO financialTransactionDAO, UserDAO userDAO, IRatingService ratingService) {
        this.systemBank = SystemBank.getInstance();
        this.transactionLog = new CopyOnWriteArrayList<>();
        this.ratingService = ratingService;
        this.financialTransactionDAO = financialTransactionDAO;
        this.userDAO = userDAO;
    }

    // ─── Private helper ───────────────────────────────────────────────────────

    /**
     * Lấy lock object theo userId từ registry.
     * FIX: Thay synchronized(user/bidder/winner) — lock trên tham số ngoài —
     * bằng lock object gắn với identity của user (userId).
     */
    private Object lockFor(NormalUser user) {
        return userLocks.computeIfAbsent(user.getId(), id -> new Object());
    }

    // ─── Deposit ──────────────────────────────────────────────────────────────

    /**
     * Nạp tiền vào tài khoản NormalUser.
     */
    @Override
    public void deposit(NormalUser user, long amount) {
        if (!ratingService.isEligible(user)) {
            throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
        }

        synchronized (lockFor(user)) {
            user.setBalance(user.getBalance() + amount);
            log.info("Deposit success: username={}, amount={}, newBalance={}",
                    user.getUsername(), amount, user.getBalance());
            userDAO.addBalance(user.getId(), amount);
        }
    }

    // ─── Withdraw ─────────────────────────────────────────────────────────────

    /**
     * Rút tiền từ tài khoản NormalUser.
     * Chỉ rút được phần availableBalance (không rút vào tiền đang khóa cọc).
     */
    @Override
    public void withdraw(NormalUser user, long amount) {
        if (!ratingService.isEligible(user)) {
            throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0.");
        }

        synchronized (lockFor(user)) {
            if (user.getAvailableBalance() < amount) {
                throw new IllegalArgumentException(String.format(
                        "Số dư khả dụng không đủ. Khả dụng: %d, Yêu cầu: %d",
                        user.getAvailableBalance(), amount));
            }
            user.setBalance(user.getBalance() - amount);
            log.info("Withdraw success: username={}, amount={}, newBalance={}",
                    user.getUsername(), amount, user.getBalance());
            userDAO.updateBalances(user.getId(), user.getBalance(), user.getLockedDeposit());
        }
    }

    // ─── Deposit (cọc) ────────────────────────────────────────────────────────

    /**
     * Khóa cọc khi joinAuction thành công.
     */
    @Override
    public void lockDeposit(NormalUser bidder, long depositAmount, String auctionId) {
        synchronized (lockFor(bidder)) {
            if (bidder.getAvailableBalance() < depositAmount) {
                throw new AuctionBusinessException(AuctionBusinessException.Reason.INSUFFICIENT_DEPOSIT);
            }
            bidder.lockDeposit(depositAmount);
            userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

            FinancialTransaction tx = FinancialTransaction.create(
                    bidder.getId(), "SYSTEM_LOCKED", depositAmount,
                    TransactionType.DEPOSIT_LOCK, auctionId);
            transactionLog.add(tx);
            tx.printInfo();
            financialTransactionDAO.saveTransaction(tx);
        }
    }

    /**
     * Hoàn cọc cho bidder không thắng khi phiên kết thúc.
     */
    @Override
    public void unlockDeposit(NormalUser bidder, long depositAmount, String auctionId) {
        synchronized (lockFor(bidder)) {
            bidder.unlockDeposit(depositAmount);
            userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

            FinancialTransaction tx = FinancialTransaction.create(
                    "SYSTEM_LOCKED", bidder.getId(), depositAmount,
                    TransactionType.DEPOSIT_UNLOCK, auctionId);
            transactionLog.add(tx);
            tx.printInfo();
            financialTransactionDAO.saveTransaction(tx);
        }
    }

    /**
     * Tịch thu cọc của winner không thanh toán -> chuyển vào SystemBank.
     */
    @Override
    public void forfeitDeposit(NormalUser winner, long depositAmount, String auctionId) {
        synchronized (lockFor(winner)) {
            winner.unlockDeposit(depositAmount);
            userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

            systemBank.receiveForfeittedDeposit(depositAmount);

            FinancialTransaction tx = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", depositAmount,
                    TransactionType.DEPOSIT_FORFEIT, auctionId);
            transactionLog.add(tx);
            tx.printInfo();
            log.info("Deposit forfeited: username={}, depositAmount={}, auctionId={}",
                    winner.getUsername(), depositAmount, auctionId);
            financialTransactionDAO.saveTransaction(tx);
        }
    }

    // ─── Payment ──────────────────────────────────────────────────────────────

    /**
     * Chuyển tiền từ Winner -> SystemBank (FUNDS_HELD).
     * Seller CHƯA nhận tiền - chỉ nhận qua PaymentService.releaseToSeller.
     */
    public void executePaymentToBank(
            NormalUser winner, long finalPrice, long depositPaid, String auctionId) {
        synchronized (lockFor(winner)) {
            long remaining = finalPrice - depositPaid;

            if (winner.getAvailableBalance() < remaining) {
                throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                        String.format("Cần %d, khả dụng: %d", remaining, winner.getAvailableBalance()));
            }

            long originalBalance = winner.getBalance();
            long originalLocked  = winner.getLockedDeposit();

            List<FinancialTransaction> batchTx = new ArrayList<>();
            try {
                winner.setBalance(winner.getBalance() - remaining);
                batchTx.add(FinancialTransaction.create(
                        winner.getId(), "SYSTEM_BANK", remaining,
                        TransactionType.PAYMENT_FROM_WINNER, auctionId));

                winner.unlockDeposit(depositPaid);
                winner.setBalance(winner.getBalance() - depositPaid);
                batchTx.add(FinancialTransaction.create(
                        "SYSTEM_LOCKED", winner.getId(), depositPaid,
                        TransactionType.DEPOSIT_UNLOCK, auctionId));

                userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

                batchTx.add(FinancialTransaction.create(
                        winner.getId(), "SYSTEM_BANK", finalPrice,
                        TransactionType.PAYMENT_FROM_WINNER, auctionId));

                transactionLog.addAll(batchTx);
                for (FinancialTransaction tx : batchTx) {
                    tx.printInfo();
                    financialTransactionDAO.saveTransaction(tx);
                }

                // Chỉ ghi nhận vào SystemBank sau khi RAM + DB đã persist thành công
                systemBank.receive(finalPrice);

                log.info("Payment to bank success: username={}, finalPrice={}, auctionId={}",
                        winner.getUsername(), finalPrice, auctionId);

            } catch (Exception e) {
                winner.restoreBalances(originalBalance, originalLocked);
                try {
                    userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());
                } catch (Exception syncEx) {
                    log.error("Rollback DB sync failed: auctionId={}, error={}", auctionId, syncEx.getMessage());
                }
                log.error("Payment rolled back: auctionId={}, error={}", auctionId, e.getMessage());
                throw new PaymentException(PaymentException.Reason.WRONG_AMOUNT,
                        "Giao dịch thất bại, đã rollback: " + e.getMessage());
            }
        }
    }

    /** @return lịch sử giao dịch (chỉ đọc) */
    public List<FinancialTransaction> getTransactionLog() {
        return Collections.unmodifiableList(transactionLog);
    }
}