package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
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
 *
 * <p>Thay vì để NormalUser tự quản lý mọi thứ, WalletService tập trung
 * vào logic trừ tiền trực tiếp để tránh việc "dùng một số tiền cọc cho nhiều nơi".
 *
 * <p>Mọi thao tác tiền tệ đều tạo {@link FinancialTransaction} để ghi log audit.
 *
 * <p>Rollback: nếu một bước trong luồng giao dịch thất bại, toàn bộ phải được
 * rollback để tránh mất tiền của người dùng.
 *
 * TODO: inject FinancialTransactionDAO để persist.
 */
public class WalletService {

    private final SystemBank systemBank;
    /** Lưu lịch sử giao dịch tài chính (in-memory, sau này persist qua DAO). */
    private final List<FinancialTransaction> transactionLog;

    public WalletService() {
        this.systemBank = SystemBank.getInstance();
        this.transactionLog = new ArrayList<>();
    }

    // ── Deposit (cọc) ──────────────────────────────────────────────────────────

    /**
     * Khóa cọc khi joinAuction thành công.
     * Trừ trực tiếp khỏi balance và tăng lockedDeposit.
     * Ngăn dùng cùng một số tiền cọc cho nhiều phiên vượt khả năng chi trả.
     *
     * @param bidder bidder tham gia
     * @param depositAmount số tiền cọc (30% giá khởi điểm)
     * @param auctionId id phiên
     * @throws AuctionBusinessException nếu số dư không đủ
     */
    public void lockDeposit(NormalUser bidder, double depositAmount, String auctionId) {
        if (bidder.getAvailableBalance() < depositAmount) {
            throw new AuctionBusinessException(AuctionBusinessException.Reason.INSUFFICIENT_DEPOSIT);
        }
        bidder.lockDeposit(depositAmount);
        // Ghi nhận giao dịch
        FinancialTransaction tx = FinancialTransaction.create(
                bidder.getId(), "SYSTEM_LOCKED", depositAmount,
                TransactionType.DEPOSIT_LOCK, auctionId);
        transactionLog.add(tx);
        tx.printInfo();
        // TODO: financialTransactionDAO.save(tx)
    }

    /**
     * Hoàn cọc cho bidder không thắng khi phiên kết thúc.
     *
     * @param bidder bidder được hoàn cọc
     * @param depositAmount số tiền cọc được hoàn
     * @param auctionId id phiên
     */
    public void unlockDeposit(NormalUser bidder, double depositAmount, String auctionId) {
        bidder.unlockDeposit(depositAmount);
        // Ghi nhận giao dịch
        FinancialTransaction tx = FinancialTransaction.create(
                "SYSTEM_LOCKED", bidder.getId(), depositAmount,
                TransactionType.DEPOSIT_UNLOCK, auctionId);
        transactionLog.add(tx);
        tx.printInfo();
        // TODO: financialTransactionDAO.save(tx)
    }

    /**
     * Tịch thu cọc của winner không thanh toán → chuyển vào SystemBank.
     * Theo luật đấu giá: winner không thanh toán mất cọc.
     *
     * @param winner winner vi phạm
     * @param depositAmount số tiền cọc bị tịch thu
     * @param auctionId id phiên
     */
    public void forfeitDeposit(NormalUser winner, double depositAmount, String auctionId) {
        // Xóa lockedDeposit của winner — tiền không trả về balance
        winner.unlockDeposit(depositAmount);
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
        // TODO: financialTransactionDAO.save(tx)
    }

    // ── Payment transaction ────────────────────────────────────────────────────

    /**
     * Thực hiện toàn bộ luồng giao dịch thanh toán trong một khối chặt chẽ.
     * Winner → SystemBank → Seller (sau thuế).
     *
     * <p>Nếu một bước lỗi → Rollback toàn bộ để tránh mất tiền.
     *
     * @param winner winner thanh toán
     * @param seller seller nhận tiền
     * @param finalPrice giá bán cuối cùng
     * @param depositPaid cọc winner đã khóa trước đó
     * @param auctionId id phiên
     * @throws PaymentException nếu winner không đủ số dư
     */
    public void executePaymentTransaction(NormalUser winner, NormalUser seller,
                                          double finalPrice, double depositPaid, String auctionId) {

        double remaining = finalPrice - depositPaid;

        // === BƯỚC 1: Kiểm tra số dư winner ===
        if (winner.getAvailableBalance() < remaining) {
            throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                    String.format("Cần %.0f, khả dụng: %.0f", remaining, winner.getAvailableBalance()));
        }

        // === BƯỚC 2: Rollback state để xử lý lỗi ===
        double originalWinnerBalance = winner.getBalance();
        double originalWinnerLocked = winner.getLockedDeposit();
        double originalSellerBalance = seller.getBalance();
        double originalBankBalance = systemBank.getTotalBalance();

        try {
            // === BƯỚC 3: Winner trả phần còn lại (balance trực tiếp) ===
            winner.setBalance(winner.getBalance() - remaining);
            FinancialTransaction txPayment = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", remaining,
                    TransactionType.PAYMENT_FROM_WINNER, auctionId);
            transactionLog.add(txPayment);
            txPayment.printInfo();

            // === BƯỚC 4: Giải phóng cọc của winner (cọc tính vào finalPrice) ===
            winner.unlockDeposit(depositPaid);
            FinancialTransaction txUnlock = FinancialTransaction.create(
                    "SYSTEM_LOCKED", winner.getId(), depositPaid,
                    TransactionType.DEPOSIT_UNLOCK, auctionId);
            transactionLog.add(txUnlock);
            txUnlock.printInfo();

            // === BƯỚC 5: SystemBank tiếp nhận toàn bộ finalPrice ===
            systemBank.receive(finalPrice);
            FinancialTransaction txTax = FinancialTransaction.create(
                    winner.getId(), "SYSTEM_BANK", systemBank.calculateTax(finalPrice),
                    TransactionType.TAX_COLLECTED, auctionId);
            transactionLog.add(txTax);
            txTax.printInfo();

            // === BƯỚC 6: SystemBank chuyển tiền cho seller (sau thuế) ===
            double payout = systemBank.payoutToSeller(finalPrice);
            seller.setBalance(seller.getBalance() + payout);
            FinancialTransaction txPayout = FinancialTransaction.create(
                    "SYSTEM_BANK", seller.getId(), payout,
                    TransactionType.PAYOUT_TO_SELLER, auctionId);
            transactionLog.add(txPayout);
            txPayout.printInfo();

            System.out.printf("[WALLET] Giao dịch thành công | Winner: %s | Seller: %s | Giá: %.0f | Payout: %.0f%n",
                    winner.getUsername(), seller.getUsername(), finalPrice, payout);

        } catch (Exception e) {
            // === ROLLBACK: khôi phục trạng thái ban đầu ===
            winner.setBalance(originalWinnerBalance);
            winner.lockDeposit(originalWinnerLocked - winner.getLockedDeposit());
            seller.setBalance(originalSellerBalance);
            // SystemBank rollback (xấp xỉ — trong thực tế cần DB transaction)
            System.err.printf("[WALLET] ROLLBACK giao dịch phiên %s | Lỗi: %s%n",
                    auctionId, e.getMessage());
            throw new PaymentException(PaymentException.Reason.WRONG_AMOUNT,
                    "Giao dịch thất bại, đã rollback: " + e.getMessage());
        }
        // TODO: financialTransactionDAO.save toàn bộ batch
    }

    /**
     * Hoàn tiền cho winner khi hàng lỗi (seller vi phạm chất lượng).
     * Hệ thống hoàn thuế + Seller hoàn phần tiền thực nhận = 100% trả lại cho Winner.
     *
     * @param winner winner nhận hoàn tiền
     * @param seller seller phải hoàn tiền
     * @param finalPrice giá bán ban đầu (để tính lại thuế và payout)
     * @param auctionId id phiên
     */
    public void executeRefundToWinner(NormalUser winner, NormalUser seller,
                                      double finalPrice, String auctionId) {

        double tax = systemBank.calculateTax(finalPrice);
        double sellerPayout = finalPrice - tax;

        // Seller hoàn phần thực nhận (trừ thuế)
        if (seller.getBalance() < sellerPayout) {
            System.err.printf("[WALLET] Seller %s không đủ tiền hoàn trả!%n", seller.getUsername());
            // Vẫn tiếp tục xử lý — seller sẽ bị ban
        }
        seller.setBalance(Math.max(0, seller.getBalance() - sellerPayout));

        // SystemBank hoàn phần thuế đã thu
        systemBank.refundToWinner(tax);

        // Cộng toàn bộ finalPrice cho winner
        winner.setBalance(winner.getBalance() + finalPrice);

        FinancialTransaction txRefund = FinancialTransaction.create(
                "SYSTEM_BANK", winner.getId(), finalPrice,
                TransactionType.REFUND_TO_WINNER, auctionId);
        transactionLog.add(txRefund);
        txRefund.printInfo();

        System.out.printf("[WALLET] Hoàn tiền 100%% (%.0f) cho winner %s.%n",
                finalPrice, winner.getUsername());
        // TODO: financialTransactionDAO.save(txRefund)
    }

    /**
     * Thực hiện giao dịch Second Chance Offer khi runner-up chấp nhận.
     * Logic tương tự winner ban đầu nhưng với offerPrice.
     *
     * @param runnerUp runner-up chấp nhận
     * @param seller seller nhận tiền
     * @param offerPrice giá mua theo second chance
     * @param depositPaid cọc runner-up đã khóa
     * @param auctionId id phiên
     * @throws PaymentException nếu runner-up không đủ số dư
     */
    public void executeSecondChancePayment(NormalUser runnerUp, NormalUser seller,
                                           double offerPrice, double depositPaid, String auctionId) {

        double remaining = offerPrice - depositPaid;

        if (runnerUp.getAvailableBalance() < remaining) {
            throw new PaymentException(PaymentException.Reason.INSUFFICIENT_BALANCE,
                    String.format("Runner-up cần %.0f, khả dụng: %.0f",
                            remaining, runnerUp.getAvailableBalance()));
        }

        double originalRunnerUpBalance = runnerUp.getBalance();
        double originalSellerBalance = seller.getBalance();

        try {
            // Runner-up trả phần còn lại
            runnerUp.setBalance(runnerUp.getBalance() - remaining);
            runnerUp.unlockDeposit(depositPaid);

            // SystemBank tiếp nhận và chuyển cho seller
            systemBank.receive(offerPrice);
            double payout = systemBank.payoutToSeller(offerPrice);
            seller.setBalance(seller.getBalance() + payout);

            FinancialTransaction tx = FinancialTransaction.create(
                    runnerUp.getId(), seller.getId(), offerPrice,
                    TransactionType.SECOND_CHANCE_PAYMENT, auctionId);
            transactionLog.add(tx);
            tx.printInfo();

            System.out.printf("[WALLET] Second Chance Payment thành công | Runner-up: %s | Giá: %.0f%n",
                    runnerUp.getUsername(), offerPrice);

        } catch (Exception e) {
            // Rollback
            runnerUp.setBalance(originalRunnerUpBalance);
            seller.setBalance(originalSellerBalance);
            System.err.printf("[WALLET] ROLLBACK Second Chance Payment phiên %s | Lỗi: %s%n",
                    auctionId, e.getMessage());
            throw new PaymentException(PaymentException.Reason.WRONG_AMOUNT,
                    "Second Chance giao dịch thất bại, đã rollback: " + e.getMessage());
        }
    }

    /** @return lịch sử giao dịch (read-only) */
    public List<FinancialTransaction> getTransactionLog() {
        return Collections.unmodifiableList(transactionLog);
    }
}