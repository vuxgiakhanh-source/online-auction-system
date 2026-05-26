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
            "-1, 50000",
            "0, 50000",
            "999999, 50000",
            "1000000, 200000",
            "10000000, 200000",
            "10000001, 500000",
            "9223372036854775807, 500000"
    })
    @DisplayName("calculate() — tier, ranh giới, giá âm")
    void calculate_tierBoundaries(long price, long expectedIncrement) {
        assertEquals(expectedIncrement, BidIncrementCalculator.calculate(price));
        assertTrue(BidIncrementCalculator.calculate(price) > 0);
        assertEquals(expectedIncrement, BidIncrementCalculator.calculate(price));
    }
}
