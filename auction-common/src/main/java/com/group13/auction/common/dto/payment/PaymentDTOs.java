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
        private long amount;

        public DepositRequestDTO() {}
        public DepositRequestDTO(long amount) { this.amount = amount; }

        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
    }

    /** Payload của WITHDRAW. */
    public static class WithdrawRequestDTO {
        private long amount;

        public WithdrawRequestDTO() {}
        public WithdrawRequestDTO(long amount) { this.amount = amount; }

        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
    }

    /** Payload của DEPOSIT_SUCCESS, WITHDRAW_SUCCESS, GET_WALLET_BALANCE_SUCCESS. */
    public static class WalletBalanceResponseDTO {
        private long balance;
        private long lockedDeposit;
        private long availableBalance;

        public WalletBalanceResponseDTO() {}

        public WalletBalanceResponseDTO(long balance, long lockedDeposit, long availableBalance) {
            this.balance = balance;
            this.lockedDeposit = lockedDeposit;
            this.availableBalance = availableBalance;
        }

        public long getBalance() { return balance; }
        public void setBalance(long balance) { this.balance = balance; }
        public long getLockedDeposit() { return lockedDeposit; }
        public void setLockedDeposit(long lockedDeposit) { this.lockedDeposit = lockedDeposit; }
        public long getAvailableBalance() { return availableBalance; }
        public void setAvailableBalance(long availableBalance) { this.availableBalance = availableBalance; }
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
        private long finalPrice;
        private long depositDeducted;
        private long remainingToPay;
        private long newBalance;
        /** "COMPLETED" | "PENDING" (second chance đã accept, chờ thanh toán tiếp) */
        private String paymentStatus;
        private LocalDateTime paidAt;

        public PaymentResultDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getFinalPrice() { return finalPrice; }
        public void setFinalPrice(long finalPrice) { this.finalPrice = finalPrice; }
        public long getDepositDeducted() { return depositDeducted; }
        public void setDepositDeducted(long depositDeducted) { this.depositDeducted = depositDeducted; }
        public long getRemainingToPay() { return remainingToPay; }
        public void setRemainingToPay(long remainingToPay) { this.remainingToPay = remainingToPay; }
        public long getNewBalance() { return newBalance; }
        public void setNewBalance(long newBalance) { this.newBalance = newBalance; }
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    }

    /** Payload của PAYMENT_EXPIRED_NOTIFY. */
    public static class PaymentExpiredDTO {
        private String auctionId;
        private long depositForfeited;
        private double ratingPenalty;

        public PaymentExpiredDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getDepositForfeited() { return depositForfeited; }
        public void setDepositForfeited(long depositForfeited) { this.depositForfeited = depositForfeited; }
        public double getRatingPenalty() { return ratingPenalty; }
        public void setRatingPenalty(double ratingPenalty) { this.ratingPenalty = ratingPenalty; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Deposit Refund / Forfeit
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của DEPOSIT_REFUND_NOTIFY (gửi cho bidder thua). */
    public static class DepositRefundDTO {
        private String auctionId;
        private long refundAmount;
        private long newBalance;

        public DepositRefundDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getRefundAmount() { return refundAmount; }
        public void setRefundAmount(long refundAmount) { this.refundAmount = refundAmount; }
        public long getNewBalance() { return newBalance; }
        public void setNewBalance(long newBalance) { this.newBalance = newBalance; }
    }

    /** Payload của DEPOSIT_FORFEITED_NOTIFY (gửi cho winner không trả tiền). */
    public static class DepositForfeitedDTO {
        private String auctionId;
        private long forfeitedAmount;
        private long newBalance;

        public DepositForfeitedDTO() {}

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public long getForfeitedAmount() { return forfeitedAmount; }
        public void setForfeitedAmount(long forfeitedAmount) { this.forfeitedAmount = forfeitedAmount; }
        public long getNewBalance() { return newBalance; }
        public void setNewBalance(long newBalance) { this.newBalance = newBalance; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Second Chance Offer
    // ══════════════════════════════════════════════════════════════════════════

    /** Payload của SECOND_CHANCE_OFFER_NOTIFY. */
    public static class SecondChanceOfferDTO {
        private String offerId;
        private String auctionId;
        private String auctionItemName;
        private long offerPrice;
        private long depositRequired;
        private LocalDateTime deadline;

        public SecondChanceOfferDTO() {}

        public String getOfferId() { return offerId; }
        public void setOfferId(String offerId) { this.offerId = offerId; }
        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
        public String getAuctionItemName() { return auctionItemName; }
        public void setAuctionItemName(String auctionItemName) { this.auctionItemName = auctionItemName; }
        public long getOfferPrice() { return offerPrice; }
        public void setOfferPrice(long offerPrice) { this.offerPrice = offerPrice; }
        public long getDepositRequired() { return depositRequired; }
        public void setDepositRequired(long depositRequired) { this.depositRequired = depositRequired; }
        public LocalDateTime getDeadline() { return deadline; }
        public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    }
}