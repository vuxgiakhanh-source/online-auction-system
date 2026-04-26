package com.group13.auction.service;

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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý tài chính và cọc tập trung - Single Responsibility.
 *
 * <p>Mọi thao tác tiền tệ đều tạo {@link FinancialTransaction} để ghi log audit.
 *
 * <p>Rollback: nếu một bước trong luồng giao dịch thất bại, toàn bộ phải được
 * rollback để tránh mất tiền của người dùng.
 *
 * Đã thực hiện TODO: inject FinancialTransactionDAO và UserDAO để persist.
 */
public class WalletService implements IWalletService {

    private final SystemBank systemBank;
    /** Lưu lịch sử giao dịch tài chính (FIX: Dùng CopyOnWriteArrayList cho thread-safety). */
    private final List<FinancialTransaction> transactionLog;
    private final IRatingService ratingService;

    // Tiêm DAO
    private final FinancialTransactionDAO financialTransactionDAO;
    private final UserDAO userDAO;

    /**
     * Cập nhật Constructor để nhận các DAO.
     */
    public WalletService(FinancialTransactionDAO financialTransactionDAO, UserDAO userDAO, IRatingService ratingService) {
        this.systemBank = SystemBank.getInstance();
        this.transactionLog = new CopyOnWriteArrayList<>();
        this.ratingService = ratingService;
        this.financialTransactionDAO = financialTransactionDAO;
        this.userDAO = userDAO;
    }

    // Deposit

    /**
     * Nạp tiền vào tài khoản NormalUser.
     *
     * @param user user cần nạp
     * @param amount số tiền nạp (phải > 0)
     * @throws IllegalStateException nếu tài khoản bị khóa hoặc rating quá thấp
     * @throws IllegalArgumentException nếu amount <= 0
     */
    @Override
    public void deposit(NormalUser user, long amount) {
        if (!ratingService.isEligible(user)) {
            throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
        }

        // FIX: Bọc synchronized để đảm bảo atomic "read-modify-write" cho DB và RAM
        synchronized (user) {
            user.setBalance(user.getBalance() + amount);
            System.out.printf(
                    "[ACCOUNT] %s nạp %d | Số dư mới: %d%n",
                    user.getUsername(), amount, user.getBalance());

            // Gọi DAO để cộng tiền dưới DB
            userDAO.addBalance(user.getId(), amount);
        }
    }

    // Rút tiền

    /**
     * Rút tiền từ tài khoản NormalUser.
     *
     * <p>Chỉ rút được phần {@code availableBalance} (không rút vào tiền đang khóa cọc).
     *
     * @param user user cần rút
     * @param amount số tiền rút (phải > 0)
     * @throws IllegalStateException nếu tài khoản không đủ điều kiện
     * @throws IllegalArgumentException nếu amount <= 0 hoặc vượt số dư khả dụng
     */
    @Override
    public void withdraw(NormalUser user, long amount) {
        if (!ratingService.isEligible(user)) {
            throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0.");
        }

        synchronized (user) {
            if (user.getAvailableBalance() < amount) {
                throw new IllegalArgumentException(
                        String.format(
                                "Số dư khả dụng không đủ. Khả dụng: %d, Yêu cầu: %d",
                                user.getAvailableBalance(), amount));
            }

            user.setBalance(user.getBalance() - amount);
            System.out.printf(
                    "[ACCOUNT] %s rút %d | Số dư mới: %d%n",
                    user.getUsername(), amount, user.getBalance());

            // Đã thực hiện TODO: persist số dư mới xuống DB
            userDAO.updateBalances(user.getId(), user.getBalance(), user.getLockedDeposit());
        }
    }


    // Deposit (cọc)

    /**
     * Khóa cọc khi joinAuction thành công.
     * Trừ trực tiếp khỏi balance và tăng lockedDeposit.
     * Ngăn dùng cùng một số tiền cọc cho nhiều phiên vượt khả năng chi trả.
     */
    @Override
    public void lockDeposit(NormalUser bidder, long depositAmount, String auctionId) {
        synchronized (bidder) {
            if (bidder.getAvailableBalance() < depositAmount) {
                throw new AuctionBusinessException(AuctionBusinessException.Reason.INSUFFICIENT_DEPOSIT);
            }

            // Cập nhật trên RAM
            bidder.lockDeposit(depositAmount);

            // Cập nhật xuống DB
            userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

            // Ghi nhận giao dịch
            FinancialTransaction tx = FinancialTransaction.create(
                    bidder.getId(), "SYSTEM_LOCKED", depositAmount,
                    TransactionType.DEPOSIT_LOCK, auctionId);
            transactionLog.add(tx);
            tx.printInfo();

            // Đã thực hiện TODO: Lưu lịch sử xuống DB
            financialTransactionDAO.saveTransaction(tx);
        }
    }

    /**
     * Hoàn cọc cho bidder không thắng khi phiên kết thúc.
     */
    @Override
    public void unlockDeposit(NormalUser bidder, long depositAmount, String auctionId) {
        synchronized (bidder) {
            bidder.unlockDeposit(depositAmount);
            userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

            // Ghi nhận giao dịch
            FinancialTransaction tx = FinancialTransaction.create(
                    "SYSTEM_LOCKED", bidder.getId(), depositAmount,
                    TransactionType.DEPOSIT_UNLOCK, auctionId);
            transactionLog.add(tx);
            tx.printInfo();
            // Đã thực hiện TODO: financialTransactionDao.save(tx)
            financialTransactionDAO.saveTransaction(tx);
        }
    }

    /**
     * Tịch thu cọc của winner không thanh toán -> chuyển vào SystemBank.
     * winner không thanh toán mất cọc.
     */
    @Override
    public void forfeitDeposit(NormalUser winner, long depositAmount, String auctionId) {
        synchronized (winner) {
            // Xóa lockedDeposit của winner, tiền không trả về balance
            winner.unlockDeposit(depositAmount);
            userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

            // Chuyển cọc vào SystemBank
            systemBank.receiveForfeittedDeposit(depositAmount);

            // Ghi nhận giao dịch
            FinancialTransaction tx = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", depositAmount,
                    TransactionType.DEPOSIT_FORFEIT, auctionId);
            transactionLog.add(tx);
            tx.printInfo();
            System.out.printf("[WALLET] Tịch thu cọc %d của %s — chuyển vào SystemBank.%n",
                    depositAmount, winner.getUsername());

            // Đã thực hiện TODO: financialTransactionDao.save(tx)
            financialTransactionDAO.saveTransaction(tx);
        }
    }

    // Payment transaction

    /**
     * Chuyển tiền từ Winner -> SystemBank (FUNDS_HELD).
     * Seller CHƯA nhận tiền - chỉ nhận qua {@link PaymentService#releaseToSeller}.
     *
     * <p>Logic:
     * <ol>
     *   <li>Trừ phần {@code remaining = finalPrice - depositPaid} từ balance winner.</li>
     *   <li>Giải phóng cọc (lockedDeposit) rồi trừ luôn khỏi balance - cọc chuyển vào bank.</li>
     *   <li>Bank ghi nhận toàn bộ {@code finalPrice}.</li>
     * </ol>
     *
     * @param winner người thắng
     * @param finalPrice giá cuối cùng
     * @param depositPaid số tiền cọc đã khóa trước
     * @param auctionId id phiên
     */
    public void executePaymentToBank(
            NormalUser winner, long finalPrice, long depositPaid, String auctionId) {
        synchronized (winner) {
            long remaining = finalPrice - depositPaid;

            if (winner.getAvailableBalance() < remaining) {
                throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                        String.format("Cần %d, khả dụng: %d", remaining, winner.getAvailableBalance()));
            }

            long originalBalance = winner.getBalance();
            long originalLocked = winner.getLockedDeposit();

            List<FinancialTransaction> batchTx = new ArrayList<>();
            try {
                // Trừ phần còn lại
                winner.setBalance(winner.getBalance() - remaining);
                batchTx.add(FinancialTransaction.create(
                        winner.getId(), "SYSTEM_BANK", remaining,
                        TransactionType.PAYMENT_FROM_WINNER, auctionId));

                // Giải phóng cọc và trừ khỏi balance
                winner.unlockDeposit(depositPaid);
                winner.setBalance(winner.getBalance() - depositPaid);
                batchTx.add(FinancialTransaction.create(
                        "SYSTEM_LOCKED", winner.getId(), depositPaid,
                        TransactionType.DEPOSIT_UNLOCK, auctionId));

                // TODO: [DB] userDAO.updateBalances(winner)
                userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

                // Bank giữ toàn bộ finalPrice
                systemBank.receive(finalPrice);
                batchTx.add(FinancialTransaction.create(
                        winner.getId(), "SYSTEM_BANK", finalPrice,
                        TransactionType.PAYMENT_FROM_WINNER, auctionId));

                System.out.printf(
                        "[WALLET] Winner %s chuyển %d vào SystemBank (FUNDS_HELD).%n",
                        winner.getUsername(), finalPrice);

                transactionLog.addAll(batchTx);
                for (FinancialTransaction tx : batchTx) {
                    tx.printInfo();
                    financialTransactionDAO.saveTransaction(tx);
                }

            } catch (Exception e) {
                winner.restoreBalances(originalBalance, originalLocked);
                try {
                    userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());
                } catch (Exception syncEx) {
                    System.err.printf("[WALLET] ROLLBACK DB thất bại phiên %s | Lỗi: %s%n",
                            auctionId, syncEx.getMessage());
                }
                System.err.printf("[WALLET] ROLLBACK phiên %s | Lỗi: %s%n", auctionId, e.getMessage());
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
