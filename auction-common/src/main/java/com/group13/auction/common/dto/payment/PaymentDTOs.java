package com.group13.auction.common.dto.payment;

import java.time.LocalDateTime;

/** Namespace class chứa toàn bộ DTO liên quan đến Payment, Wallet, Second Chance. */
public final class PaymentDTOs {

    private PaymentDTOs() {}

    // ══════════════════════════════════════════════════════════════════════════
    // Wallet / Deposit
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của DEPOSIT. */
    public static class DepositRequestDTO {
        private double amount;

        public DepositRequestDTO() {}
        public DepositRequestDTO(double amount) { this.amount = amount; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
    }

    /** Payload của WITHDRAW. */
    public static class WithdrawRequestDTO {
        private double amount;

        public WithdrawRequestDTO() {}
        public WithdrawRequestDTO(double amount) { this.amount = amount; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
    }

    /** Payload của DEPOSIT_SUCCESS, WITHDRAW_SUCCESS, GET_WALLET_BALANCE_SUCCESS. */
    public static class WalletBalanceResponseDTO {
        private double balance;
        private double lockedDeposit;
        private double availableBalance;

        public WalletBalanceResponseDTO() {}

        public WalletBalanceResponseDTO(double balance, double lockedDeposit, double availableBalance) {
            this.balance = balance;
            this.lockedDeposit = lockedDeposit;
            this.availableBalance = availableBalance;
        }

        public double getBalance() { return balance; }
        public void setBalance(double balance) { this.balance = balance; }
        public double getLockedDeposit() { return lockedDeposit; }
        public void setLockedDeposit(double lockedDeposit) { this.lockedDeposit = lockedDeposit; }
        public double getAvailableBalance() { return availableBalance; }
        public void setAvailableBalance(double availableBalance) { this.availableBalance = availableBalance; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Payment (sau khi phiên kết thúc)
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của PAYMENT_REQUEST. */
    public static class PaymentRequestDTO {
        private String auctionId;

        public PaymentRequestDTO() {}
        public PaymentRequestDTO(String auctionId) { this.auctionId = auctionId; }

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
    }

    /** Payload của PAYMENT_SUCCESS và PAYMENT_COMPLETED_NOTIFY. */
    public static class PaymentResultDTO {
        private String auctionId;
        private double finalPrice;
        private double depositDeducted;
        private double remainingToPay;
        private double newBalance;
        /** "COMPLETED" | "PENDING" (second chance đã accept, chờ thanh toán tiếp) */
        private String paymentStatus;
        private LocalDateTime paidAt;

        public PaymentResultDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public double getFinalPrice() { return finalPrice; }
        public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }
        public double getDepositDeducted() { return depositDeducted; }
        public void setDepositDeducted(double depositDeducted) { this.depositDeducted = depositDeducted; }
        public double getRemainingToPay() { return remainingToPay; }
        public void setRemainingToPay(double remainingToPay) { this.remainingToPay = remainingToPay; }
        public double getNewBalance() { return newBalance; }
        public void setNewBalance(double newBalance) { this.newBalance = newBalance; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    }

    /** Payload của PAYMENT_EXPIRED_NOTIFY. */
    public static class PaymentExpiredDTO {
        private String auctionId;
        private double depositForfeited;
        private double ratingPenalty;

        public PaymentExpiredDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public double getDepositForfeited() { return depositForfeited; }
        public void setDepositForfeited(double depositForfeited) { this.depositForfeited = depositForfeited; }
        public double getRatingPenalty() { return ratingPenalty; }
        public void setRatingPenalty(double ratingPenalty) { this.ratingPenalty = ratingPenalty; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Deposit Refund / Forfeit
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của DEPOSIT_REFUND_NOTIFY (gửi cho bidder thua). */
    public static class DepositRefundDTO {
        private String auctionId;
        private double refundAmount;
        private double newBalance;

        public DepositRefundDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public double getRefundAmount() { return refundAmount; }
        public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }
        public double getNewBalance() { return newBalance; }
        public void setNewBalance(double newBalance) { this.newBalance = newBalance; }
    }

    /** Payload của DEPOSIT_FORFEITED_NOTIFY (gửi cho winner không trả tiền). */
    public static class DepositForfeitedDTO {
        private String auctionId;
        private double forfeitedAmount;
        private double newBalance;

        public DepositForfeitedDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public double getForfeitedAmount() { return forfeitedAmount; }
        public void setForfeitedAmount(double forfeitedAmount) { this.forfeitedAmount = forfeitedAmount; }
        public double getNewBalance() { return newBalance; }
        public void setNewBalance(double newBalance) { this.newBalance = newBalance; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Second Chance Offer
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của SECOND_CHANCE_OFFER_NOTIFY. */
    public static class SecondChanceOfferDTO {
        private String offerId;
        private String auctionId;
        private String auctionItemName;
        private double offerPrice;
        private double depositRequired;
        private LocalDateTime deadline;

        public SecondChanceOfferDTO() {}

        public String getOfferId() { return offerId; }
        public void setOfferId(String offerId) { this.offerId = offerId; }
        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getAuctionItemName() { return auctionItemName; }
        public void setAuctionItemName(String auctionItemName) { this.auctionItemName = auctionItemName; }
        public double getOfferPrice() { return offerPrice; }
        public void setOfferPrice(double offerPrice) { this.offerPrice = offerPrice; }
        public double getDepositRequired() { return depositRequired; }
        public void setDepositRequired(double depositRequired) { this.depositRequired = depositRequired; }
        public LocalDateTime getDeadline() { return deadline; }
        public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    }
}