package com.group13.auction.strategy;

/**
 * Tính bước giá tối thiểu (minIncrement) theo ngưỡng giá hiện tại.
 *
 * <p>Quy tắc bước giá:
 * <ul>
 *   <li>Giá từ 0 đến dưới 1.000.000: bước giá 50.000</li>
 *   <li>Giá từ 1.000.000 đến 10.000.000: bước giá 200.000</li>
 *   <li>Giá trên 10.000.000: bước giá 500.000</li>
 * </ul>
 *
 * <p>Utility class (không khởi tạo được) — dùng bởi tất cả BidStrategy.
 */
public final class BidIncrementCalculator {

    private static final long TIER_LOW = 1_000_000L;
    private static final long TIER_MID = 10_000_000L;
    private static final long INCREMENT_LOW = 50_000L;
    private static final long INCREMENT_MID = 200_000L;
    private static final long INCREMENT_HIGH = 500_000L;

    private BidIncrementCalculator() {
        // Utility class, không cho phép khởi tạo
    }

    /**
     * Tính bước giá tối thiểu dựa trên giá hiện tại của phiên.
     *
     * @param currentPrice giá hiện tại của phiên đấu giá
     * @return bước giá tối thiểu tương ứng
     */
    public static long calculate(long currentPrice) {
        if (currentPrice < TIER_LOW) {
            return INCREMENT_LOW;
        } else if (currentPrice <= TIER_MID) {
            return INCREMENT_MID;
        } else {
            return INCREMENT_HIGH;
        }
    }
}