package com.group13.auction.unit.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.group13.auction.strategy.AutoBidPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests cho {@link AutoBidPhase#detect(long, long, int)}. */
@DisplayName("AutoBidPhase.detect()")
class AutoBidPhaseTest {

  // totalSec = 3600 (1 giờ) dùng làm baseline cho các test thời gian

  @Nested
  @DisplayName("VERY_HOT — ưu tiên phát hiện trước mọi phase khác")
  class VeryHotTests {

    @Test
    @DisplayName("recentBids >= 3 → VERY_HOT dù còn nhiều thời gian")
    void manyBids_earlyTime_returnsVeryHot() {
      assertThat(AutoBidPhase.detect(3600, 2000, 3)).isEqualTo(AutoBidPhase.VERY_HOT);
    }

    @Test
    @DisplayName("recentBids >= 3 → VERY_HOT dù còn ít thời gian (ưu tiên hơn LATE)")
    void manyBids_lateTime_returnsVeryHotNotLate() {
      assertThat(AutoBidPhase.detect(3600, 60, 5)).isEqualTo(AutoBidPhase.VERY_HOT);
    }

    @Test
    @DisplayName("recentBids = 2 (dưới ngưỡng) → không phải VERY_HOT")
    void belowThreshold_notVeryHot() {
      assertThat(AutoBidPhase.detect(3600, 2000, 2)).isNotEqualTo(AutoBidPhase.VERY_HOT);
    }
  }

  @Nested
  @DisplayName("EARLY — còn > 30% thời gian")
  class EarlyTests {

    @Test
    @DisplayName("còn 55% → EARLY")
    void above30Percent_returnsEarly() {
      assertThat(AutoBidPhase.detect(3600, 2000, 0)).isEqualTo(AutoBidPhase.EARLY);
    }

    @Test
    @DisplayName("còn đúng 31% → EARLY (trên ngưỡng)")
    void justAbove30Percent_returnsEarly() {
      long remaining = (long) (3600 * 0.31);
      assertThat(AutoBidPhase.detect(3600, remaining, 0)).isEqualTo(AutoBidPhase.EARLY);
    }
  }

  @Nested
  @DisplayName("MID — còn 10–30% thời gian")
  class MidTests {

    @Test
    @DisplayName("còn 20% (720s = 12 phút) → MID")
    void between10and30Percent_returnsMid() {
      // 720s / 3600s = 20%, 720s > 600s (10 phút) → MID
      long remaining = 720L;
      assertThat(AutoBidPhase.detect(3600, remaining, 0)).isEqualTo(AutoBidPhase.MID);
    }

    @Test
    @DisplayName("còn 29% (ngay dưới EARLY) → MID")
    void justBelow30Percent_returnsMid() {
      // 29% × 3600 = 1044s = 17.4 phút > 10 phút → MID
      long remaining = (long) (3600 * 0.29);
      assertThat(AutoBidPhase.detect(3600, remaining, 0)).isEqualTo(AutoBidPhase.MID);
    }
  }

  @Nested
  @DisplayName("LATE — còn < 10% hoặc < 10 phút")
  class LateTests {

    @Test
    @DisplayName("còn 8% → LATE (dưới 10%)")
    void below10Percent_returnsLate() {
      long remaining = (long) (3600 * 0.08);
      assertThat(AutoBidPhase.detect(3600, remaining, 0)).isEqualTo(AutoBidPhase.LATE);
    }

    @Test
    @DisplayName("còn 500 giây (< 10 phút) dù ratio vẫn > 10% → LATE")
    void lessThan10Minutes_returnsLate() {
      // 500s / 3600s = 13.8% > 10%, nhưng 500s < 600s (10 phút) → LATE
      assertThat(AutoBidPhase.detect(3600, 500, 0)).isEqualTo(AutoBidPhase.LATE);
    }

    @Test
    @DisplayName("remainingSec = 0 → LATE")
    void zeroRemaining_returnsLate() {
      assertThat(AutoBidPhase.detect(3600, 0, 0)).isEqualTo(AutoBidPhase.LATE);
    }

    @Test
    @DisplayName("remainingSec âm (phiên quá hạn) → LATE")
    void negativeRemaining_returnsLate() {
      assertThat(AutoBidPhase.detect(3600, -1, 0)).isEqualTo(AutoBidPhase.LATE);
    }

    @Test
    @DisplayName("totalSec = 0 → LATE")
    void zeroTotal_returnsLate() {
      assertThat(AutoBidPhase.detect(0, 100, 0)).isEqualTo(AutoBidPhase.LATE);
    }
  }

  @Nested
  @DisplayName("Multiplier — giá trị đúng theo từng phase")
  class MultiplierTests {

    @Test
    @DisplayName("EARLY multiplier = 1.0")
    void early_multiplier() {
      assertThat(AutoBidPhase.EARLY.multiplier()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("MID multiplier = 1.0")
    void mid_multiplier() {
      assertThat(AutoBidPhase.MID.multiplier()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("LATE multiplier = 1.5")
    void late_multiplier() {
      assertThat(AutoBidPhase.LATE.multiplier()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("VERY_HOT multiplier = 2.0")
    void veryHot_multiplier() {
      assertThat(AutoBidPhase.VERY_HOT.multiplier()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("LATE multiplier > EARLY multiplier (cuối phiên aggressive hơn)")
    void late_greaterThan_early() {
      assertThat(AutoBidPhase.LATE.multiplier()).isGreaterThan(AutoBidPhase.EARLY.multiplier());
    }

    @Test
    @DisplayName("VERY_HOT multiplier > LATE multiplier")
    void veryHot_greaterThan_late() {
      assertThat(AutoBidPhase.VERY_HOT.multiplier()).isGreaterThan(AutoBidPhase.LATE.multiplier());
    }
  }

  @ParameterizedTest(name = "totalSec={0} remainingSec={1} recentBids={2} → {3}")
  @CsvSource({
    "3600, 2000,  0, EARLY",
    "3600,  720,  0, MID", // 720s = 12 phút, 20% → MID (540s = 9 phút < 10 phút = LATE)
    "3600,  300,  0, LATE",
    "3600,  540,  0, LATE", // 540s = 9 phút < 10 phút tuyệt đối → LATE
    "3600, 2000,  3, VERY_HOT",
    "3600,   60,  5, VERY_HOT",
    "3600,    0,  0, LATE",
    "   0,  100,  0, LATE"
  })
  @DisplayName("detect() — parameterized coverage")
  void detect_parameterized(
      long totalSec, long remainingSec, int recentBids, AutoBidPhase expected) {
    assertThat(AutoBidPhase.detect(totalSec, remainingSec, recentBids)).isEqualTo(expected);
  }
}
