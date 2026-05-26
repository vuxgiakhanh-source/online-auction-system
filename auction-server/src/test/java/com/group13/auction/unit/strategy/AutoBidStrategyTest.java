package com.group13.auction.unit.strategy;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit test cho {@link AutoBidStrategy} — maxBid, isValidBid, calculateNextBid.
 */
@DisplayName("AutoBidStrategy")
class AutoBidStrategyTest {

    private static Auction auctionAt(long currentPrice) {
        NormalUser seller = TestFixture.normalSeller("autoSeller1");
        return TestFixture.auctionWithStatus(
                seller, Math.max(1L, currentPrice / 2), currentPrice, Auction.AuctionStatus.RUNNING);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        void positiveMaxBid_ok() {
            assertThat(new AutoBidStrategy(1_000_000L).getMaxBid()).isEqualTo(1_000_000L);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L})
        void nonPositiveMaxBid_throws(long invalid) {
            assertThatThrownBy(() -> new AutoBidStrategy(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isValidBid")
    class IsValidBidTest {

        @Test
        void validAmountWithinMaxBid() {
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);
            assertThat(strategy.isValidBid(auctionAt(500_000L), 600_000L)).isTrue();
        }

        @Test
        void belowMinimumIncrement_invalid() {
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);
            assertThat(strategy.isValidBid(auctionAt(500_000L), 500_000L)).isFalse();
        }

        @Test
        void aboveMaxBid_invalid() {
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);
            assertThat(strategy.isValidBid(auctionAt(500_000L), 1_000_001L)).isFalse();
        }
    }

    @Nested
    @DisplayName("calculateNextBid")
    class CalculateNextBidTest {

        @Test
        void returnsCurrentPricePlusIncrement() {
            AutoBidStrategy strategy = new AutoBidStrategy(10_000_000L);
            assertThat(strategy.calculateNextBid(auctionAt(500_000L))).isEqualTo(550_000L);
        }

        @Test
        void exceedsMaxBid_returnsMinusOne() {
            AutoBidStrategy strategy = new AutoBidStrategy(500_000L);
            assertThat(strategy.calculateNextBid(auctionAt(500_000L))).isEqualTo(-1L);
        }
    }

    @Test
    @DisplayName("describe() chứa maxBid")
    void describe_containsMaxBid() {
        assertThat(new AutoBidStrategy(3_500_000L).describe()).contains("3500000");
    }
}
