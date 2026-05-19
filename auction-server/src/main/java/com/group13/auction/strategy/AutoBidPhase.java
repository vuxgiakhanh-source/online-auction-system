package com.group13.auction.strategy;

/**
 * Phase của phiên đấu giá, dùng để nhân increment khi auto-bid.
 *
 * <pre>
 * EARLY    (> 30% còn lại)  → 1.0× — tiết kiệm
 * MID      (10–30%)          → 1.0×
 * LATE     (< 10% / < 10ph) → 1.5× — bắt đầu aggressive
 * VERY_HOT (≥ 3 bid/10s)    → 2.0× — phiên đang nóng
 * </pre>
 */
public enum AutoBidPhase {

    EARLY(1.0), MID(1.0), LATE(1.5), VERY_HOT(2.0);

    private final double multiplier;

    AutoBidPhase(double multiplier) { this.multiplier = multiplier; }

    /** Multiplier nhân vào baseIncrement khi tính smart bid. */
    public double multiplier() { return multiplier; }

    // ── Detection constants ───────────────────────────────────────────────────

    /** Số bid tối thiểu trong {@link #HOT_WINDOW_SEC} giây để coi là VERY_HOT. */
    public static final int HOT_BID_THRESHOLD = 3;
    public static final int HOT_WINDOW_SEC    = 10;

    private static final double EARLY_RATIO  = 0.30;
    private static final double MID_RATIO    = 0.10;
    private static final long   LATE_MINUTES = 10L;

    /**
     * Phát hiện phase từ thời gian còn lại và số bid gần đây.
     *
     * @param totalSec     tổng thời lượng phiên (giây)
     * @param remainingSec thời gian còn lại (giây)
     * @param recentBids   số bid trong {@link #HOT_WINDOW_SEC} giây gần nhất
     */
    public static AutoBidPhase detect(long totalSec, long remainingSec, int recentBids) {
        if (recentBids >= HOT_BID_THRESHOLD) return VERY_HOT;
        if (totalSec <= 0 || remainingSec <= 0) return LATE;
        double ratio = (double) remainingSec / totalSec;
        if (remainingSec <= LATE_MINUTES * 60 || ratio < MID_RATIO) return LATE;
        if (ratio < EARLY_RATIO) return MID;
        return EARLY;
    }
}