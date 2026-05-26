package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.AutoBidPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho {@link AutoBidPhase#detect(long, long, int)} và multiplier.
 */
@DisplayName("AutoBidPhase")
class AutoBidPhaseTest {

    @ParameterizedTest(name = "totalSec={0} remainingSec={1} recentBids={2} → {3}")
    @CsvSource({
            "3600, 2000,  0, EARLY",
            "3600,  720,  0, MID",
            "3600,  300,  0, LATE",
            "3600,  540,  0, LATE",
            "3600, 2000,  3, VERY_HOT",
            "3600,   60,  5, VERY_HOT",
            "3600,    0,  0, LATE",
            "   0,  100,  0, LATE"
    })
    @DisplayName("detect() — phase theo thời gian và recentBids")
    void detect_parameterized(long totalSec, long remainingSec, int recentBids, AutoBidPhase expected) {
        assertThat(AutoBidPhase.detect(totalSec, remainingSec, recentBids)).isEqualTo(expected);
    }

    @Test
    @DisplayName("multiplier tăng dần EARLY ≤ MID < LATE < VERY_HOT")
    void multipliers_increaseWithUrgency() {
        assertThat(AutoBidPhase.EARLY.multiplier()).isEqualTo(1.0);
        assertThat(AutoBidPhase.MID.multiplier()).isEqualTo(1.0);
        assertThat(AutoBidPhase.LATE.multiplier()).isEqualTo(1.5);
        assertThat(AutoBidPhase.VERY_HOT.multiplier()).isEqualTo(2.0);
        assertThat(AutoBidPhase.LATE.multiplier()).isGreaterThan(AutoBidPhase.EARLY.multiplier());
        assertThat(AutoBidPhase.VERY_HOT.multiplier()).isGreaterThan(AutoBidPhase.LATE.multiplier());
    }
}
