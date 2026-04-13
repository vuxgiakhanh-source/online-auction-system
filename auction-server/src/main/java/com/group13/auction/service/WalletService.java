package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.exception.AuctionBusinessException;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.bid.FinancialTransaction;
import com.group13.auction.model.bid.FinancialTransaction.TransactionType;
import com.group13.auction.model.user.NormalUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quản lý tài chính và cọc tập trung — Single Responsibility.
 * Đã thực hiện TODO: inject FinancialTransactionDAO và UserDAO để persist.
 */
public class WalletService {

    private final SystemBank systemBank;
    private final List<FinancialTransaction> transactionLog;

    // Tiêm DAO
    private final FinancialTransactionDAO financialTransactionDAO;
    private final UserDAO userDAO;

    /**
     * Cập nhật Constructor để nhận các DAO.
     */
    public WalletService(FinancialTransactionDAO financialTransactionDAO, UserDAO userDAO) {
        this.systemBank = SystemBank.getInstance();
        this.transactionLog = new ArrayList<>();
        this.financialTransactionDAO = financialTransactionDAO;
        this.userDAO = userDAO;
    }

    // ── Deposit (cọc) ──────────────────────────────────────────────────────────

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

        // Lưu lịch sử xuống DB
        financialTransactionDAO.saveTransaction(tx);
    }

    public void unlockDeposit(NormalUser bidder, double depositAmount, String auctionId) {
        bidder.unlockDeposit(depositAmount);
        userDAO.updateBalances(bidder.getId(), bidder.getBalance(), bidder.getLockedDeposit());

        FinancialTransaction tx = FinancialTransaction.create(
                "SYSTEM_LOCKED", bidder.getId(), depositAmount,
                TransactionType.DEPOSIT_UNLOCK, auctionId);
        transactionLog.add(tx);
        tx.printInfo();

        financialTransactionDAO.saveTransaction(tx);
    }

    public void forfeitDeposit(NormalUser winner, double depositAmount, String auctionId) {
        winner.unlockDeposit(depositAmount);
        userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

        systemBank.receiveForfeittedDeposit(depositAmount);

        FinancialTransaction tx = FinancialTransaction.create(
                winner.getId(), "SYSTEM_BANK", depositAmount,
                TransactionType.DEPOSIT_FORFEIT, auctionId);
        transactionLog.add(tx);
        tx.printInfo();
        System.out.printf("[WALLET] Tịch thu cọc %.0f của %s — chuyển vào SystemBank.%n",
                depositAmount, winner.getUsername());

        financialTransactionDAO.saveTransaction(tx);
    }

    // ── Payment transaction ────────────────────────────────────────────────────

    public void executePaymentTransaction(NormalUser winner, NormalUser seller,
                                          double finalPrice, double depositPaid, String auctionId) {

        double remaining = finalPrice - depositPaid;

        if (winner.getAvailableBalance() < remaining) {
            throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                    String.format("Cần %.0f, khả dụng: %.0f", remaining, winner.getAvailableBalance()));
        }

        double originalWinnerBalance = winner.getBalance();
        double originalWinnerLocked = winner.getLockedDeposit();
        double originalSellerBalance = seller.getBalance();

        // Danh sách lưu trữ các transaction trong phiên giao dịch này để lưu DB hàng loạt
        List<FinancialTransaction> batchTx = new ArrayList<>();

        try {
            // Bước 3: Trừ tiền Winner
            winner.setBalance(winner.getBalance() - remaining);
            FinancialTransaction txPayment = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", remaining,
                    TransactionType.PAYMENT_FROM_WINNER, auctionId);
            batchTx.add(txPayment);

            // Bước 4: Mở khóa cọc
            winner.unlockDeposit(depositPaid);
            FinancialTransaction txUnlock = FinancialTransaction.create(
                    "SYSTEM_LOCKED", winner.getId(), depositPaid,
                    TransactionType.DEPOSIT_UNLOCK, auctionId);
            batchTx.add(txUnlock);

            // Cập nhật DB cho Winner (Bao gồm trừ balance và unlock cọc)
            userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

            // Bước 5 & 6: Tính toán và trả tiền cho Seller
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
                financialTransactionDAO.saveTransaction(tx);
            }

        } catch (Exception e) {
            winner.setBalance(originalWinnerBalance);
            winner.lockDeposit(originalWinnerLocked - winner.getLockedDeposit());
            seller.setBalance(originalSellerBalance);

            System.err.printf("[WALLET] ROLLBACK giao dịch phiên %s | Lỗi: %s%n",
                    auctionId, e.getMessage());
            throw new PaymentException(PaymentException.Reason.WRONG_AMOUNT,
                    "Giao dịch thất bại, đã rollback: " + e.getMessage());
        }
    }

    public void executeRefundToWinner(NormalUser winner, NormalUser seller,
                                      double finalPrice, String auctionId) {

        double tax = systemBank.calculateTax(finalPrice);
        double sellerPayout = finalPrice - tax;

        if (seller.getBalance() < sellerPayout) {
            System.err.printf("[WALLET] Seller %s không đủ tiền hoàn trả!%n", seller.getUsername());
        }

        seller.setBalance(Math.max(0, seller.getBalance() - sellerPayout));
        userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());

        systemBank.refundToWinner(tax);

        winner.setBalance(winner.getBalance() + finalPrice);
        userDAO.updateBalances(winner.getId(), winner.getBalance(), winner.getLockedDeposit());

        FinancialTransaction txRefund = FinancialTransaction.create(
                "SYSTEM_BANK", winner.getId(), finalPrice,
                TransactionType.REFUND_TO_WINNER, auctionId);
        transactionLog.add(txRefund);
        txRefund.printInfo();

        System.out.printf("[WALLET] Hoàn tiền 100%% (%.0f) cho winner %s.%n",
                finalPrice, winner.getUsername());

        financialTransactionDAO.saveTransaction(txRefund);
    }

    public void executeSecondChancePayment(NormalUser runnerUp, NormalUser seller,
                                           double offerPrice, double depositPaid, String auctionId) {

        double remaining = offerPrice - depositPaid;

        if (runnerUp.getAvailableBalance() < remaining) {
            throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                    String.format("Runner-up cần %.0f, khả dụng: %.0f",
                            remaining, runnerUp.getAvailableBalance()));
        }

        double originalRunnerUpBalance = runnerUp.getBalance();
        double originalRunnerUpLocked = runnerUp.getLockedDeposit();
        double originalSellerBalance = seller.getBalance();

        try {
            runnerUp.setBalance(runnerUp.getBalance() - remaining);
            runnerUp.unlockDeposit(depositPaid);
            userDAO.updateBalances(runnerUp.getId(), runnerUp.getBalance(), runnerUp.getLockedDeposit());

            systemBank.receive(offerPrice);
            double payout = systemBank.payoutToSeller(offerPrice);

            seller.setBalance(seller.getBalance() + payout);
            userDAO.updateBalances(seller.getId(), seller.getBalance(), seller.getLockedDeposit());

            FinancialTransaction tx = FinancialTransaction.create(
                    runnerUp.getId(), seller.getId(), offerPrice,
                    TransactionType.SECOND_CHANCE_PAYMENT, auctionId);
            transactionLog.add(tx);
            tx.printInfo();
            financialTransactionDAO.saveTransaction(tx);

            System.out.printf("[WALLET] Second Chance Payment thành công | Runner-up: %s | Giá: %.0f%n",
                    runnerUp.getUsername(), offerPrice);

        } catch (Exception e) {
            runnerUp.setBalance(originalRunnerUpBalance);
            runnerUp.lockDeposit(originalRunnerUpLocked - runnerUp.getLockedDeposit());
            seller.setBalance(originalSellerBalance);

            System.err.printf("[WALLET] ROLLBACK Second Chance Payment phiên %s | Lỗi: %s%n",
                    auctionId, e.getMessage());
            throw new PaymentException(PaymentException.Reason.WRONG_AMOUNT,
                    "Second Chance giao dịch thất bại, đã rollback: " + e.getMessage());
        }
    }

    public List<FinancialTransaction> getTransactionLog() {
        return Collections.unmodifiableList(transactionLog);
    }
}