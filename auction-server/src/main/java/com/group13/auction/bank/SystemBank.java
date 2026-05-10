package com.group13.auction.bank;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Ngân hàng hệ thống — nơi lưu thuế và trung gian chuyển tiền.
 *
 * <p>Singleton. Mọi giao dịch tài chính đều đi qua SystemBank:
 * <ol>
 * <li>Winner -> SystemBank (trả tiền)</li>
 * <li>SystemBank -> Seller (sau khi trừ thuế)</li>
 * <li>Thuế ở lại SystemBank</li>
 * </ol>
 *
 * <p>Thuế suất theo giá bán:
 * <ul>
 * <li>Dưới 1000000: 5%</li>
 * <li>Từ 1000000 đến 10000000: 3%</li>
 * <li>Trên 10000000: 2%</li>
 * </ul>
 */
public class SystemBank {

    private static final SystemBank INSTANCE = new SystemBank();

    private static final long PRICE_TIER_LOW = 1_000_000L;
    private static final long PRICE_TIER_MID = 10_000_000L;
    private static final double TAX_RATE_LOW = 0.05;
    private static final double TAX_RATE_MID = 0.03;
    private static final double TAX_RATE_HIGH = 0.02;

    /** Số dư tổng trong ngân hàng hệ thống (tiền thuế tích lũy + cọc bị tịch thu). */
    private final AtomicLong totalBalance = new AtomicLong(0L);

    private SystemBank() {}

    public static SystemBank getInstance() { return INSTANCE; }

    // Tax calculation

    /**
     * Tính thuế theo giá bán.
     *
     * @param salePrice giá bán cuối cùng
     * @return số tiền thuế
     */
    public long calculateTax(long salePrice) {
        double rate;
        if (salePrice < PRICE_TIER_LOW) {
            rate = TAX_RATE_LOW;
        }
        else if (salePrice <= PRICE_TIER_MID) {
            rate = TAX_RATE_MID;
        }
        else {
            rate = TAX_RATE_HIGH;
        }
        return Math.round(salePrice * rate);
    }

    /**
     * Tính số tiền seller nhận sau khi trừ thuế.
     *
     * @param salePrice giá bán cuối cùng
     * @return tiền seller nhận
     */
    public long calculateSellerPayout(long salePrice) {
        return salePrice - calculateTax(salePrice);
    }

    // Bank operations

    /**
     * Tiếp nhận tiền từ winner (phần còn lại sau cọc).
     * Ghi nhận vào tổng balance của bank.
     *
     * @param amount số tiền nhận
     */
    public synchronized void receive(long amount) {
        long current = totalBalance.addAndGet(amount);
//        System.out.printf("[BANK] Tiếp nhận %d | Tổng quỹ: %d%n",
//                amount, current);
    }

    /**
     * Chuyển tiền cho seller sau khi trừ thuế.
     * Giảm balance của bank (phần thuế ở lại bank).
     *
     * @param salePrice giá bán cuối cùng
     * @return số tiền chuyển cho seller
     */
    public synchronized long payoutToSeller(long salePrice) {
        long tax = calculateTax(salePrice);
        long payout = salePrice - tax;
        long current = totalBalance.addAndGet(-payout);
//        System.out.printf("[BANK] Chuyển cho seller %d | Thuế giữ lại: %d | Tổng quỹ: %d%n",
//                payout, tax, current);
        return payout;
    }

    /**
     * Hoàn tiền cho winner (khi seller vi phạm chất lượng).
     *
     * @param amount số tiền hoàn trả
     */
    public synchronized void refundToWinner(long amount) {
        long current = totalBalance.addAndGet(-amount);
//        System.out.printf("[BANK] Hoàn tiền cho winner %d | Tổng quỹ: %d%n",
//                amount, current);
    }

    /**
     * Tiếp nhận tiền cọc bị tịch thu từ winner không thanh toán.
     * Cọc này được cộng thẳng vào balance của bank.
     *
     * @param depositAmount số tiền cọc bị tịch thu
     */
    public synchronized void receiveForfeittedDeposit(long depositAmount) {
        long current = totalBalance.addAndGet(+depositAmount);
//        System.out.printf("[BANK] Tịch thu cọc %d từ winner vi phạm | Tổng quỹ: %d%n",
//                depositAmount, current);
    }

    public long getTotalBalance() { return totalBalance.get(); }
}