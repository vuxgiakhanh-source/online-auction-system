package com.group13.auction.bank;

/**
 * "Ngân hàng" hệ thống — nơi lưu giữ thuế và trung gian chuyển tiền.
 *
 * <p>Singleton. Mọi giao dịch tài chính đều đi qua SystemBank:
 * <ol>
 *   <li>Winner → SystemBank (trả tiền)</li>
 *   <li>SystemBank → Seller (sau khi trừ thuế)</li>
 *   <li>Thuế ở lại SystemBank</li>
 * </ol>
 *
 * <p>Thuế suất theo giá bán:
 * <ul>
 *   <li>Dưới 1,000,000: 5%</li>
 *   <li>Từ 1,000,000 đến 10,000,000: 3%</li>
 *   <li>Trên 10,000,000: 2%</li>
 * </ul>
 */
public class SystemBank {

    private static final SystemBank INSTANCE = new SystemBank();

    private static final double PRICE_TIER_LOW    = 1_000_000.0;
    private static final double PRICE_TIER_MID    = 10_000_000.0;
    private static final double TAX_RATE_LOW      = 0.05;
    private static final double TAX_RATE_MID      = 0.03;
    private static final double TAX_RATE_HIGH     = 0.02;

    /** Số dư tổng trong ngân hàng hệ thống (tiền thuế tích lũy). */
    private double totalBalance;

    private SystemBank() {
        this.totalBalance = 0.0;
    }

    public static SystemBank getInstance() { return INSTANCE; }

    // ── Tax calculation ────────────────────────────────────────────────────

    /**
     * Tính thuế theo giá bán.
     *
     * @param salePrice giá bán cuối cùng
     * @return số tiền thuế
     */
    public double calculateTax(double salePrice) {
        double rate;
        if (salePrice < PRICE_TIER_LOW) {
            rate = TAX_RATE_LOW;
        } else if (salePrice <= PRICE_TIER_MID) {
            rate = TAX_RATE_MID;
        } else {
            rate = TAX_RATE_HIGH;
        }
        return salePrice * rate;
    }

    /**
     * Tính số tiền seller nhận sau khi trừ thuế.
     *
     * @param salePrice giá bán cuối cùng
     * @return tiền seller nhận
     */
    public double calculateSellerPayout(double salePrice) {
        return salePrice - calculateTax(salePrice);
    }

    // ── Bank operations ────────────────────────────────────────────────────

    /**
     * Tiếp nhận tiền từ winner (phần còn lại sau cọc).
     * Ghi nhận vào tổng balance của bank.
     *
     * @param amount số tiền nhận
     */
    public void receive(double amount) {
        this.totalBalance += amount;
        System.out.printf("[BANK] Tiếp nhận %.0f | Tổng quỹ: %.0f%n",
                amount, totalBalance);
    }

    /**
     * Chuyển tiền cho seller sau khi trừ thuế.
     * Giảm balance của bank (phần thuế ở lại bank).
     *
     * @param salePrice giá bán cuối cùng
     * @return số tiền chuyển cho seller
     */
    public double payoutToSeller(double salePrice) {
        double tax    = calculateTax(salePrice);
        double payout = salePrice - tax;
        // Thuế ở lại bank (totalBalance không giảm phần tax)
        this.totalBalance -= payout;
        System.out.printf("[BANK] Chuyển cho seller %.0f | Thuế giữ lại: %.0f | Tổng quỹ: %.0f%n",
                payout, tax, totalBalance);
        return payout;
    }

    /**
     * Hoàn tiền cho winner (khi seller vi phạm chất lượng).
     *
     * @param amount số tiền hoàn trả
     */
    public void refundToWinner(double amount) {
        this.totalBalance -= amount;
        System.out.printf("[BANK] Hoàn tiền cho winner %.0f | Tổng quỹ: %.0f%n",
                amount, totalBalance);
    }

    public double getTotalBalance() { return totalBalance; }
}
