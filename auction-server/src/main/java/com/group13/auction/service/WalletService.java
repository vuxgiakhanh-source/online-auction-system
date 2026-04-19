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

/**
 * Quản lý tài chính và cọc tập trung — Single Responsibility.
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
    /** Lưu lịch sử giao dịch tài chính (in-memory, sau này persist qua DAO). */
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
        this.transactionLog = new ArrayList<>();
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
    public void deposit(NormalUser user, double amount) {
        if (!ratingService.isEligible(user)) {
            throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0.");
        }
        user.setBalance(user.getBalance() + amount);
        System.out.printf(
                "[ACCOUNT] %s nạp %.0f | Số dư mới: %.0f%n",
                user.getUsername(), amount, user.getBalance());

        // Gọi DAO để cộng tiền dưới DB
        userDAO.addBalance(user.getId(), amount);
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
    public void withdraw(NormalUser user, double amount) {
        if (!ratingService.isEligible(user)) {
            throw new IllegalStateException("Tài khoản không đủ điều kiện thực hiện giao dịch.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0.");
        }
        if (user.getAvailableBalance() < amount) {
            throw new IllegalArgumentException(
                    String.format(
                            "Số dư khả dụng không đủ. Khả dụng: %.0f, Yêu cầu: %.0f",
                            user.getAvailableBalance(), amount));
        }

        user.setBalance(user.getBalance() - amount);
        System.out.printf(
                "[ACCOUNT] %s rút %.0f | Số dư mới: %.0f%n",
                user.getUsername(), amount, user.getBalance());

        // Đã thực hiện TODO: persist số dư mới xuống DB
        userDAO.updateBalances(user.getId(), user.getBalance(), user.getLockedDeposit());
    }


    // Deposit (cọc)

    /**
     * Khóa cọc khi joinAuction thành công.
     * Trừ trực tiếp khỏi balance và tăng lockedDeposit.
     * Ngăn dùng cùng một số tiền cọc cho nhiều phiên vượt khả năng chi trả.
     */
    @Override
    public void lockDeposit(NormalUser bidder, double depositAmount, String auctionId) {
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

    /**
     * Hoàn cọc cho bidder không thắng khi phiên kết thúc.
     */
    @Override
    public void unlockDeposit(NormalUser bidder, double depositAmount, String auctionId) {
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

    /**
     * Tịch thu cọc của winner không thanh toán → chuyển vào SystemBank.
     * Theo luật đấu giá: winner không thanh toán mất cọc.
     */
    @Override
    public void forfeitDeposit(NormalUser winner, double depositAmount, String auctionId) {
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
        System.out.printf("[WALLET] Tịch thu cọc %.0f của %s — chuyển vào SystemBank.%n",
                depositAmount, winner.getUsername());

        // Đã thực hiện TODO: financialTransactionDao.save(tx)
        financialTransactionDAO.saveTransaction(tx);
    }

    // Payment transaction

    /**
     * Thực hiện toàn bộ luồng giao dịch thanh toán trong một khối chặt chẽ.
     * Winner -> SystemBank -> Seller (sau thuế).
     */
    @Override
    public void executePaymentTransaction(NormalUser winner, NormalUser seller,
                                          double finalPrice, double depositPaid, String auctionId) {

        double remaining = finalPrice - depositPaid;

        // b1: Kiểm tra số dư winner
        if (winner.getAvailableBalance() < remaining) {
            throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                    String.format("Cần %.0f, khả dụng: %.0f", remaining, winner.getAvailableBalance()));
        }

        // b2: Rollback state để xử lí lỗi
        double originalWinnerBalance = winner.getBalance();
        double originalWinnerLocked = winner.getLockedDeposit();
        double originalSellerBalance = seller.getBalance();

        // Danh sách lưu trữ các transaction trong phiên giao dịch này để lưu DB hàng loạt
        List<FinancialTransaction> batchTx = new ArrayList<>();

        try {
            // b3: Trừ tiền Winner
            winner.setBalance(winner.getBalance() - remaining);
            FinancialTransaction txPayment = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", remaining,
                    TransactionType.PAYMENT_FROM_WINNER, auctionId);
            batchTx.add(txPayment);

            // b4: Mở khóa cọc
            winner.unlockDeposit(depositPaid);
            FinancialTransaction txUnlock = FinancialTransaction.create(
                    "SYSTEM_LOCKED", winner.getId(), depositPaid,
                    TransactionType.DEPOSIT_UNLOCK, auctionId);
            batchTx.add(txUnlock);

            // Cập nhật DB cho Winner (Bao gồm trừ balance và unlock cọc)
            userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

            // b5: Tính toán và trả tiền cho Seller
            systemBank.receive(finalPrice);
            FinancialTransaction txTax = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", systemBank.calculateTax(finalPrice),
                    TransactionType.TAX_COLLECTED, auctionId);
            batchTx.add(txTax);

            double payout = systemBank.payoutToSeller(finalPrice);
            seller.setBalance(seller.getBalance() + payout);

            FinancialTransaction txPayout = FinancialTransaction.create(
                    "SYSTEM_BANK", seller.getId(), payout,
                    TransactionType.PAYOUT_TO_SELLER, auctionId);
            batchTx.add(txPayout);

            // Cập nhật DB cho Seller (Cộng balance)
            userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());

            System.out.printf("[WALLET] Giao dịch thành công | Winner: %s | Seller: %s | Giá: %.0f | Payout: %.0f%n",
                    winner.getUsername(), seller.getUsername(), finalPrice, payout);

            // Lưu toàn bộ batch lịch sử xuống DB
            transactionLog.addAll(batchTx);
            for (FinancialTransaction tx : batchTx) {
                tx.printInfo();
                // Đã thực hiện TODO: finanacialTransactionDAO.save toàn bộ batch
                financialTransactionDAO.saveTransaction(tx);
            }

        } catch (Exception e) {
            // Rollback: khôi phục trạng thái ban đầu
            winner.restoreBalances(originalWinnerBalance, originalWinnerLocked);
            seller.restoreBalances(originalSellerBalance, seller.getLockedDeposit());
            // TODO: Sync lại DB về trạng thái gốc

            System.err.printf("[WALLET] ROLLBACK giao dịch phiên %s | Lỗi: %s%n",
                    auctionId, e.getMessage());
            throw new PaymentException(PaymentException.Reason.WRONG_AMOUNT,
                    "Giao dịch thất bại, đã rollback: " + e.getMessage());
        }
    }

    /**
     * Hoàn tiền cho winner khi hàng lỗi (seller vi phạm chất lượng).
     * Hệ thống hoàn thuế + Seller hoàn phần tiền nhận = 100% trả lại cho Winner.
     */
    @Override
    public void executeRefundToWinner(NormalUser winner, NormalUser seller,
                                      double finalPrice, String auctionId) {

        double tax = systemBank.calculateTax(finalPrice);
        double sellerPayout = finalPrice - tax;

        // Seller hoàn phần thực nhận (trừ thuế)
        if (seller.getBalance() < sellerPayout) {
            System.err.printf("[WALLET] Seller %s không đủ tiền hoàn trả!%n", seller.getUsername());
        }

        seller.setBalance(Math.max(0, seller.getBalance() - sellerPayout));
        userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());

        // SystemBank hoàn phần thuế đã thu
        systemBank.refundToWinner(tax);

        // Cộng toàn bộ finalPrice cho winner
        winner.setBalance(winner.getBalance() + finalPrice);
        userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

        FinancialTransaction txRefund = FinancialTransaction.create(
                "SYSTEM_BANK", winner.getId(), finalPrice,
                TransactionType.REFUND_TO_WINNER, auctionId);
        transactionLog.add(txRefund);
        txRefund.printInfo();

        System.out.printf("[WALLET] Hoàn tiền 100%% (%.0f) cho winner %s.%n",
                finalPrice, winner.getUsername());

        // Đã thực hiện TODO: financialTransactionDAO.save(txRefund)
        financialTransactionDAO.saveTransaction(txRefund);
    }


    /** @return lịch sử giao dịch (chỉ đọc) */
    public List<FinancialTransaction> getTransactionLog() {
        return Collections.unmodifiableList(transactionLog);
    }
}