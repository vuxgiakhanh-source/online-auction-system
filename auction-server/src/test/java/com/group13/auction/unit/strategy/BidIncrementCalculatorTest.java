package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.BidIncrementCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test cho {@link BidIncrementCalculator}.
 *
 * <p>Chiến lược kiểm tra:
 * <ul>
 *   <li>Equivalence Partitioning: 3 tier [0, 1M), [1M, 10M], (10M, ∞)</li>
 *   <li>Boundary Value Analysis: các ngưỡng chuyển tier (0, 999_999, 1_000_000, 10_000_000, 10_000_001)</li>
 *   <li>Edge case: giá âm, giá = Long.MAX_VALUE</li>
 *   <li>Idempotency: cùng input → cùng output mọi lần</li>
 *   <li>No side effect: gọi nhiều lần không làm thay đổi kết quả lần sau</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network — tuân thủ FIRST hoàn toàn.
 */
@DisplayName("BidIncrementCalculator")
class BidIncrementCalculatorTest {

    // -------------------------------------------------------------------------
    // Constants mirror source để dễ đọc assertion
    // -------------------------------------------------------------------------
    private static final long INCREMENT_LOW  =  50_000L;
    private static final long INCREMENT_MID  = 200_000L;
    private static final long INCREMENT_HIGH = 500_000L;

    private static final long TIER_LOW = 1_000_000L;
    private static final long TIER_MID = 10_000_000L;

    // =========================================================================
    // Tier 1: currentPrice < 1.000.000  →  increment = 50.000
    // =========================================================================

    @Nested
    @DisplayName("Tier thấp (currentPrice < 1.000.000) → increment = 50.000")
    class TierLow {

        @Test
        @DisplayName("currentPrice = 0 → 50.000")
        void priceZero_returns50000() {
            // Arrange
            long currentPrice = 0L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_LOW, increment);
        }

        @Test
        @DisplayName("currentPrice = 1 (nhỏ nhất dương) → 50.000")
        void priceOne_returns50000() {
            // Arrange
            long currentPrice = 1L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_LOW, increment);
        }

        @Test
        @DisplayName("currentPrice = 500.000 (giữa tier thấp) → 50.000")
        void priceMidTierLow_returns50000() {
            // Arrange
            long currentPrice = 500_000L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_LOW, increment);
        }

        @Test
        @DisplayName("currentPrice = 999.999 (ngay dưới ngưỡng TIER_LOW) → 50.000")
        void priceJustBelowTierLowBoundary_returns50000() {
            // Arrange
            long currentPrice = TIER_LOW - 1; // 999_999

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_LOW, increment);
        }
    }

    // =========================================================================
    // Tier 2: 1.000.000 ≤ currentPrice ≤ 10.000.000  →  increment = 200.000
    // =========================================================================

    @Nested
    @DisplayName("Tier trung (1.000.000 ≤ currentPrice ≤ 10.000.000) → increment = 200.000")
    class TierMid {

        @Test
        @DisplayName("currentPrice = 1.000.000 (đúng ngưỡng TIER_LOW) → 200.000")
        void priceAtTierLowBoundary_returns200000() {
            // Arrange
            long currentPrice = TIER_LOW; // 1_000_000

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_MID, increment);
        }

        @Test
        @DisplayName("currentPrice = 1.000.001 (vừa qua ngưỡng TIER_LOW) → 200.000")
        void priceJustAboveTierLowBoundary_returns200000() {
            // Arrange
            long currentPrice = TIER_LOW + 1; // 1_000_001

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_MID, increment);
        }

        @Test
        @DisplayName("currentPrice = 5.000.000 (giữa tier trung) → 200.000")
        void priceMidTierMid_returns200000() {
            // Arrange
            long currentPrice = 5_000_000L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_MID, increment);
        }

        @Test
        @DisplayName("currentPrice = 9.999.999 (ngay dưới ngưỡng TIER_MID) → 200.000")
        void priceJustBelowTierMidBoundary_returns200000() {
            // Arrange
            long currentPrice = TIER_MID - 1; // 9_999_999

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_MID, increment);
        }

        @Test
        @DisplayName("currentPrice = 10.000.000 (đúng ngưỡng TIER_MID — inclusive) → 200.000")
        void priceAtTierMidBoundary_returns200000() {
            // Arrange
            long currentPrice = TIER_MID; // 10_000_000

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_MID, increment,
                    "Ngưỡng TIER_MID = 10.000.000 phải thuộc tier MID (inclusive)");
        }
    }

    // =========================================================================
    // Tier 3: currentPrice > 10.000.000  →  increment = 500.000
    // =========================================================================

    @Nested
    @DisplayName("Tier cao (currentPrice > 10.000.000) → increment = 500.000")
    class TierHigh {

        @Test
        @DisplayName("currentPrice = 10.000.001 (vừa qua ngưỡng TIER_MID) → 500.000")
        void priceJustAboveTierMidBoundary_returns500000() {
            // Arrange
            long currentPrice = TIER_MID + 1; // 10_000_001

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_HIGH, increment);
        }

        @Test
        @DisplayName("currentPrice = 50.000.000 (giữa tier cao) → 500.000")
        void priceMidTierHigh_returns500000() {
            // Arrange
            long currentPrice = 50_000_000L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_HIGH, increment);
        }

        @Test
        @DisplayName("currentPrice = Long.MAX_VALUE (giá cực lớn) → 500.000")
        void priceMaxLong_returns500000() {
            // Arrange
            long currentPrice = Long.MAX_VALUE;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_HIGH, increment);
        }

        @Test
        @DisplayName("currentPrice = 1.000.000.000 (1 tỷ) → 500.000")
        void priceOneBillion_returns500000() {
            // Arrange
            long currentPrice = 1_000_000_000L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_HIGH, increment);
        }
    }

    // =========================================================================
    // Edge case: currentPrice âm
    // =========================================================================

    @Nested
    @DisplayName("Edge case: currentPrice âm")
    class NegativePrice {

        @Test
        @DisplayName("currentPrice = -1 → 50.000 (vẫn xử lý như tier thấp vì < TIER_LOW)")
        void priceNegativeOne_returns50000() {
            // Arrange
            long currentPrice = -1L;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_LOW, increment,
                    "Giá âm < TIER_LOW nên phải trả về INCREMENT_LOW theo logic hiện tại");
        }

        @Test
        @DisplayName("currentPrice = Long.MIN_VALUE → 50.000 (giá âm cực nhỏ)")
        void priceMinLong_returns50000() {
            // Arrange
            long currentPrice = Long.MIN_VALUE;

            // Act
            long increment = BidIncrementCalculator.calculate(currentPrice);

            // Assert
            assertEquals(INCREMENT_LOW, increment);
        }
    }

    // =========================================================================
    // Boundary transition: kiểm tra chuyển tier chính xác
    // =========================================================================

    @Nested
    @DisplayName("Boundary transition: xác nhận ranh giới tier rõ ràng")
    class BoundaryTransition {

        @Test
        @DisplayName("999.999 → LOW, 1.000.000 → MID: transition tại TIER_LOW đúng")
        void transitionAtTierLow_isCorrect() {
            // Arrange
            long belowBoundary = TIER_LOW - 1;
            long atBoundary    = TIER_LOW;

            // Act & Assert
            assertEquals(INCREMENT_LOW, BidIncrementCalculator.calculate(belowBoundary),
                    "999.999 phải thuộc tier LOW");
            assertEquals(INCREMENT_MID, BidIncrementCalculator.calculate(atBoundary),
                    "1.000.000 phải thuộc tier MID");
        }

        @Test
        @DisplayName("10.000.000 → MID, 10.000.001 → HIGH: transition tại TIER_MID đúng")
        void transitionAtTierMid_isCorrect() {
            // Arrange
            long atBoundary    = TIER_MID;
            long aboveBoundary = TIER_MID + 1;

            // Act & Assert
            assertEquals(INCREMENT_MID, BidIncrementCalculator.calculate(atBoundary),
                    "10.000.000 phải thuộc tier MID (inclusive)");
            assertEquals(INCREMENT_HIGH, BidIncrementCalculator.calculate(aboveBoundary),
                    "10.000.001 phải thuộc tier HIGH");
        }
    }

    // =========================================================================
    // Idempotency & no side effect
    // =========================================================================

    @Nested
    @DisplayName("Idempotency và không có side effect")
    class IdempotencyAndSideEffect {

        @ParameterizedTest(name = "currentPrice = {0}")
        @ValueSource(longs = {0L, 999_999L, 1_000_000L, 5_000_000L, 10_000_000L, 10_000_001L, 100_000_000L})
        @DisplayName("Cùng input → cùng output mọi lần (pure function)")
        void sameInput_alwaysReturnsSameOutput(long price) {
            // Act
            long first  = BidIncrementCalculator.calculate(price);
            long second = BidIncrementCalculator.calculate(price);
            long third  = BidIncrementCalculator.calculate(price);

            // Assert
            assertEquals(first, second, "Lần 1 và lần 2 phải bằng nhau với price=" + price);
            assertEquals(second, third, "Lần 2 và lần 3 phải bằng nhau với price=" + price);
        }

        @Test
        @DisplayName("Gọi tier cao trước, rồi gọi tier thấp → kết quả tier thấp vẫn đúng (no state)")
        void callingHighTierThenLowTier_noStateCorruption() {
            // Arrange - gọi tier cao trước
            BidIncrementCalculator.calculate(50_000_000L);

            // Act - sau đó gọi tier thấp
            long increment = BidIncrementCalculator.calculate(500_000L);

            // Assert
            assertEquals(INCREMENT_LOW, increment,
                    "Static utility không được giữ state giữa các lần gọi");
        }

        @Test
        @DisplayName("Gọi xen kẽ nhiều tier → từng kết quả vẫn đúng (no shared mutable state)")
        void interleavedTierCalls_eachResultCorrect() {
            // Act & Assert
            assertEquals(INCREMENT_LOW,  BidIncrementCalculator.calculate(0L));
            assertEquals(INCREMENT_HIGH, BidIncrementCalculator.calculate(50_000_000L));
            assertEquals(INCREMENT_MID,  BidIncrementCalculator.calculate(5_000_000L));
            assertEquals(INCREMENT_LOW,  BidIncrementCalculator.calculate(999_999L));
            assertEquals(INCREMENT_HIGH, BidIncrementCalculator.calculate(10_000_001L));
            assertEquals(INCREMENT_MID,  BidIncrementCalculator.calculate(1_000_000L));
        }
    }

    // =========================================================================
    // Business rule correctness: kiểm tra giá trị trả về đúng magnitude
    // =========================================================================

    @Nested
    @DisplayName("Business rule: increment phải đúng giá trị tuyệt đối")
    class BusinessRuleCorrectness {

        @Test
        @DisplayName("INCREMENT_LOW = 50.000 VND")
        void incrementLow_is50000() {
            long increment = BidIncrementCalculator.calculate(0L);
            assertEquals(50_000L, increment);
        }

        @Test
        @DisplayName("INCREMENT_MID = 200.000 VND")
        void incrementMid_is200000() {
            long increment = BidIncrementCalculator.calculate(1_000_000L);
            assertEquals(200_000L, increment);
        }

        @Test
        @DisplayName("INCREMENT_HIGH = 500.000 VND")
        void incrementHigh_is500000() {
            long increment = BidIncrementCalculator.calculate(10_000_001L);
            assertEquals(500_000L, increment);
        }

        @Test
        @DisplayName("increment không bao giờ âm hoặc bằng 0")
        void increment_isAlwaysPositive() {
            long[] samplePrices = {Long.MIN_VALUE, -1L, 0L, 500_000L, 1_000_000L, 10_000_000L, Long.MAX_VALUE};
            for (long price : samplePrices) {
                long increment = BidIncrementCalculator.calculate(price);
                assertEquals(true, increment > 0,
                        "increment phải > 0 với mọi currentPrice, failed at price=" + price);
            }
        }
    }
}