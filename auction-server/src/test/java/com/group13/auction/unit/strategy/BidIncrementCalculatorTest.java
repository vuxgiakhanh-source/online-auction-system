package com.group13.auction.unit.strategy;

import com.group13.auction.strategy.BidIncrementCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test cho {@link BidIncrementCalculator} — tier giá và ranh giới.
 */
@DisplayName("BidIncrementCalculator")
class BidIncrementCalculatorTest {

    private static final long INCREMENT_LOW = 50_000L;
    private static final long INCREMENT_MID = 200_000L;
    private static final long INCREMENT_HIGH = 500_000L;

    @ParameterizedTest(name = "price={0} → increment={1}")
    @CsvSource({
            "0, 50000",
            "999999, 50000",
            "1000000, 200000",
            "5000000, 200000",
            "10000000, 200000",
            "10000001, 500000",
            "1000000000, 500000",
            "9223372036854775807, 500000"
    })
    @DisplayName("calculate() — các tier và ranh giới chính")
    void calculate_tierBoundaries(long price, long expectedIncrement) {
        assertEquals(expectedIncrement, BidIncrementCalculator.calculate(price));
    }

    @Test
    @DisplayName("calculate() — giá âm vẫn thuộc tier thấp")
    void calculate_negativePrice_usesLowTier() {
        assertEquals(INCREMENT_LOW, BidIncrementCalculator.calculate(-1L));
    }

    @Test
    @DisplayName("chuyển tier tại 1_000_000 và 10_000_001")
    void calculate_tierTransitions() {
        assertEquals(INCREMENT_LOW, BidIncrementCalculator.calculate(999_999L));
        assertEquals(INCREMENT_MID, BidIncrementCalculator.calculate(1_000_000L));
        assertEquals(INCREMENT_MID, BidIncrementCalculator.calculate(10_000_000L));
        assertEquals(INCREMENT_HIGH, BidIncrementCalculator.calculate(10_000_001L));
    }

    @Test
    @DisplayName("increment luôn dương")
    void calculate_alwaysPositive() {
        for (long price : new long[] {Long.MIN_VALUE, -1L, 0L, 1_000_000L, Long.MAX_VALUE}) {
            assertTrue(BidIncrementCalculator.calculate(price) > 0, "price=" + price);
        }
    }

    @ParameterizedTest(name = "price={0}")
    @CsvSource({"0", "999999", "1000000", "10000001", "50000000"})
    @DisplayName("pure function — cùng input cho cùng output")
    void calculate_idempotent(long price) {
        long first = BidIncrementCalculator.calculate(price);
        assertEquals(first, BidIncrementCalculator.calculate(price));
    }
}
