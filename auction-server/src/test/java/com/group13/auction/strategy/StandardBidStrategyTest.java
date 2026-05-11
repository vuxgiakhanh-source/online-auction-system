package com.group13.auction.strategy;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit test cho {@link StandardBidStrategy} và {@link BidIncrementCalculator}.
 *
 * <p>Phủ: bid validation, dynamic increment theo tier, boundary testing,
 * strategy pattern behavior, state immutability, determinism.
 *
 * <p>Không mock — tất cả object đều là thật.
 * Không truy cập DB, filesystem, network.
 */
@DisplayName("StandardBidStrategy — Bid Validation & Dynamic Increment")
class StandardBidStrategyTest {

    // ── Constants mirrors từ BidIncrementCalculator ───────────────────────────

    private static final long TIER_LOW_BOUNDARY  = 1_000_000L;   // < 1tr  → 50k
    private static final long TIER_MID_BOUNDARY  = 10_000_000L;  // 1-10tr → 200k
    // > 10tr → 500k

    private static final long INCREMENT_LOW  = 50_000L;
    private static final long INCREMENT_MID  = 200_000L;
    private static final long INCREMENT_HIGH = 500_000L;

    // ── SUT ───────────────────────────────────────────────────────────────────

    private StandardBidStrategy sut;

    @BeforeEach
    void setUp() {
        sut = new StandardBidStrategy();
    }

    // =========================================================================
    // BidIncrementCalculator — tier correctness
    // =========================================================================

    @Nested
    @DisplayName("BidIncrementCalculator — tier thresholds")
    class BidIncrementCalculatorTiers {

        @Test
        @DisplayName("currentPrice = 0 → increment = 50_000 (tier LOW)")
        void tierLow_priceZero() {
            assertThat(sut.getMinIncrement(0L)).isEqualTo(INCREMENT_LOW);
        }

        @Test
        @DisplayName("currentPrice = 1 → increment = 50_000 (tier LOW)")
        void tierLow_priceOne() {
            assertThat(sut.getMinIncrement(1L)).isEqualTo(INCREMENT_LOW);
        }

        @Test
        @DisplayName("currentPrice = 500_000 → increment = 50_000 (tier LOW, mid-range)")
        void tierLow_midRange() {
            assertThat(sut.getMinIncrement(500_000L)).isEqualTo(INCREMENT_LOW);
        }

        @Test
        @DisplayName("currentPrice = 999_999 → increment = 50_000 (tier LOW, just below boundary)")
        void tierLow_justBelowBoundary() {
            assertThat(sut.getMinIncrement(TIER_LOW_BOUNDARY - 1)).isEqualTo(INCREMENT_LOW);
        }

        @Test
        @DisplayName("currentPrice = 1_000_000 → increment = 200_000 (tier MID, exactly at boundary)")
        void tierMid_exactlyAtLowerBoundary() {
            assertThat(sut.getMinIncrement(TIER_LOW_BOUNDARY)).isEqualTo(INCREMENT_MID);
        }

        @Test
        @DisplayName("currentPrice = 1_000_001 → increment = 200_000 (tier MID, just above lower boundary)")
        void tierMid_justAboveLowerBoundary() {
            assertThat(sut.getMinIncrement(TIER_LOW_BOUNDARY + 1)).isEqualTo(INCREMENT_MID);
        }

        @Test
        @DisplayName("currentPrice = 5_000_000 → increment = 200_000 (tier MID, mid-range)")
        void tierMid_midRange() {
            assertThat(sut.getMinIncrement(5_000_000L)).isEqualTo(INCREMENT_MID);
        }

        @Test
        @DisplayName("currentPrice = 10_000_000 → increment = 200_000 (tier MID, exactly at upper boundary)")
        void tierMid_exactlyAtUpperBoundary() {
            assertThat(sut.getMinIncrement(TIER_MID_BOUNDARY)).isEqualTo(INCREMENT_MID);
        }

        @Test
        @DisplayName("currentPrice = 10_000_001 → increment = 500_000 (tier HIGH, just above upper boundary)")
        void tierHigh_justAboveUpperBoundary() {
            assertThat(sut.getMinIncrement(TIER_MID_BOUNDARY + 1)).isEqualTo(INCREMENT_HIGH);
        }

        @Test
        @DisplayName("currentPrice = 50_000_000 → increment = 500_000 (tier HIGH, large value)")
        void tierHigh_largeValue() {
            assertThat(sut.getMinIncrement(50_000_000L)).isEqualTo(INCREMENT_HIGH);
        }

        @Test
        @DisplayName("currentPrice = Long.MAX_VALUE → increment = 500_000 (tier HIGH, extreme)")
        void tierHigh_extremeMaxValue() {
            assertThat(sut.getMinIncrement(Long.MAX_VALUE)).isEqualTo(INCREMENT_HIGH);
        }

        @ParameterizedTest(name = "currentPrice={0} → expected increment={1}")
        @CsvSource({
                "0,          50000",
                "999999,     50000",
                "1000000,    200000",
                "10000000,   200000",
                "10000001,   500000",
                "100000000,  500000"
        })
        @DisplayName("getMinIncrement — parameterized tier coverage")
        void getMinIncrement_parameterized(long currentPrice, long expectedIncrement) {
            assertThat(sut.getMinIncrement(currentPrice)).isEqualTo(expectedIncrement);
        }
    }

    // =========================================================================
    // isValidBid — tier LOW (currentPrice < 1_000_000, increment = 50_000)
    // =========================================================================

    @Nested
    @DisplayName("isValidBid() — Tier LOW (currentPrice < 1_000_000, increment = 50_000)")
    class IsValidBid_TierLow {

        @Test
        @DisplayName("valid — amount đúng bằng currentPrice + 50_000")
        void valid_exactlyMinimum() {
            Auction auction = auctionWithPrice(500_000L);

            boolean result = sut.isValidBid(auction, 500_000L + INCREMENT_LOW);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("valid — amount lớn hơn currentPrice + 50_000")
        void valid_aboveMinimum() {
            Auction auction = auctionWithPrice(500_000L);

            boolean result = sut.isValidBid(auction, 500_000L + INCREMENT_LOW + 1);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("valid — amount vượt xa ngưỡng minimum")
        void valid_farAboveMinimum() {
            Auction auction = auctionWithPrice(200_000L);

            boolean result = sut.isValidBid(auction, 1_000_000L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("invalid — amount đúng bằng currentPrice (thiếu increment)")
        void invalid_equalCurrentPrice() {
            Auction auction = auctionWithPrice(500_000L);

            boolean result = sut.isValidBid(auction, 500_000L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("invalid — amount = currentPrice + 49_999 (thiếu 1 đồng so với increment)")
        void invalid_oneBeforeMinimum() {
            Auction auction = auctionWithPrice(500_000L);

            boolean result = sut.isValidBid(auction, 500_000L + INCREMENT_LOW - 1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("invalid — amount nhỏ hơn currentPrice")
        void invalid_belowCurrentPrice() {
            Auction auction = auctionWithPrice(500_000L);

            boolean result = sut.isValidBid(auction, 100_000L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("invalid — amount = 0 khi currentPrice > 0")
        void invalid_amountZero() {
            Auction auction = auctionWithPrice(500_000L);

            boolean result = sut.isValidBid(auction, 0L);

            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // isValidBid — tier MID (1_000_000 ≤ currentPrice ≤ 10_000_000, increment = 200_000)
    // =========================================================================

    @Nested
    @DisplayName("isValidBid() — Tier MID (1M ≤ currentPrice ≤ 10M, increment = 200_000)")
    class IsValidBid_TierMid {

        @Test
        @DisplayName("valid — amount đúng bằng currentPrice + 200_000")
        void valid_exactlyMinimum() {
            Auction auction = auctionWithPrice(5_000_000L);

            boolean result = sut.isValidBid(auction, 5_000_000L + INCREMENT_MID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("valid — amount lớn hơn currentPrice + 200_000")
        void valid_aboveMinimum() {
            Auction auction = auctionWithPrice(5_000_000L);

            boolean result = sut.isValidBid(auction, 5_000_000L + INCREMENT_MID + 100_000L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("invalid — amount = currentPrice + 199_999 (thiếu 1 đồng)")
        void invalid_oneBeforeMinimum() {
            Auction auction = auctionWithPrice(5_000_000L);

            boolean result = sut.isValidBid(auction, 5_000_000L + INCREMENT_MID - 1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("invalid — amount = currentPrice (không tăng giá)")
        void invalid_equalCurrentPrice() {
            Auction auction = auctionWithPrice(5_000_000L);

            boolean result = sut.isValidBid(auction, 5_000_000L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("boundary — currentPrice = 1_000_000 (đầu tier MID) → increment phải là 200_000")
        void boundary_lowerEdgeOfTierMid() {
            Auction auction = auctionWithPrice(TIER_LOW_BOUNDARY);

            // amount đúng bằng minimum
            assertThat(sut.isValidBid(auction, TIER_LOW_BOUNDARY + INCREMENT_MID)).isTrue();
            // amount thiếu 1 đồng
            assertThat(sut.isValidBid(auction, TIER_LOW_BOUNDARY + INCREMENT_MID - 1)).isFalse();
        }

        @Test
        @DisplayName("boundary — currentPrice = 10_000_000 (đầu tier MID cuối) → increment phải là 200_000")
        void boundary_upperEdgeOfTierMid() {
            Auction auction = auctionWithPrice(TIER_MID_BOUNDARY);

            assertThat(sut.isValidBid(auction, TIER_MID_BOUNDARY + INCREMENT_MID)).isTrue();
            assertThat(sut.isValidBid(auction, TIER_MID_BOUNDARY + INCREMENT_MID - 1)).isFalse();
        }
    }

    // =========================================================================
    // isValidBid — tier HIGH (currentPrice > 10_000_000, increment = 500_000)
    // =========================================================================

    @Nested
    @DisplayName("isValidBid() — Tier HIGH (currentPrice > 10M, increment = 500_000)")
    class IsValidBid_TierHigh {

        @Test
        @DisplayName("valid — amount đúng bằng currentPrice + 500_000")
        void valid_exactlyMinimum() {
            Auction auction = auctionWithPrice(20_000_000L);

            boolean result = sut.isValidBid(auction, 20_000_000L + INCREMENT_HIGH);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("valid — amount vượt xa ngưỡng minimum")
        void valid_farAboveMinimum() {
            Auction auction = auctionWithPrice(20_000_000L);

            boolean result = sut.isValidBid(auction, 25_000_000L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("invalid — amount = currentPrice + 499_999 (thiếu 1 đồng)")
        void invalid_oneBeforeMinimum() {
            Auction auction = auctionWithPrice(20_000_000L);

            boolean result = sut.isValidBid(auction, 20_000_000L + INCREMENT_HIGH - 1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("boundary — currentPrice = 10_000_001 (đầu tier HIGH) → increment phải là 500_000")
        void boundary_lowerEdgeOfTierHigh() {
            long price = TIER_MID_BOUNDARY + 1;
            Auction auction = auctionWithPrice(price);

            assertThat(sut.isValidBid(auction, price + INCREMENT_HIGH)).isTrue();
            assertThat(sut.isValidBid(auction, price + INCREMENT_HIGH - 1)).isFalse();
        }

        @Test
        @DisplayName("valid — currentPrice rất lớn (100_000_000) → vẫn dùng increment 500_000")
        void valid_veryLargeCurrentPrice() {
            long price = 100_000_000L;
            Auction auction = auctionWithPrice(price);

            assertThat(sut.isValidBid(auction, price + INCREMENT_HIGH)).isTrue();
            assertThat(sut.isValidBid(auction, price + INCREMENT_HIGH - 1)).isFalse();
        }
    }

    // =========================================================================
    // isValidBid — edge cases
    // =========================================================================

    @Nested
    @DisplayName("isValidBid() — Edge Cases")
    class IsValidBid_EdgeCases {

        @Test
        @DisplayName("currentPrice = 0 → increment = 50_000; amount = 50_000 hợp lệ")
        void currentPriceZero_validBid() {
            Auction auction = auctionWithPrice(0L);

            boolean result = sut.isValidBid(auction, INCREMENT_LOW);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("currentPrice = 0 → amount = 49_999 không hợp lệ")
        void currentPriceZero_invalidBid() {
            Auction auction = auctionWithPrice(0L);

            boolean result = sut.isValidBid(auction, INCREMENT_LOW - 1);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("currentPrice = 0 → amount = 0 không hợp lệ (phải >= 50_000)")
        void currentPriceZero_amountZeroInvalid() {
            Auction auction = auctionWithPrice(0L);

            boolean result = sut.isValidBid(auction, 0L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("tier transition — currentPrice = 999_999 (LOW) vs 1_000_000 (MID): increment khác nhau")
        void tierTransition_lowToMid_differentIncrements() {
            Auction auctionLow = auctionWithPrice(999_999L);
            Auction auctionMid = auctionWithPrice(1_000_000L);

            // Amount thỏa tier LOW nhưng không thỏa tier MID
            long amount = 999_999L + INCREMENT_LOW; // = 1_049_999

            assertThat(sut.isValidBid(auctionLow, amount)).isTrue();
            // 1_000_000 + 200_000 = 1_200_000 > 1_049_999 → false
            assertThat(sut.isValidBid(auctionMid, amount)).isFalse();
        }

        @Test
        @DisplayName("tier transition — currentPrice = 10_000_000 (MID) vs 10_000_001 (HIGH): increment khác nhau")
        void tierTransition_midToHigh_differentIncrements() {
            Auction auctionMid  = auctionWithPrice(10_000_000L);
            Auction auctionHigh = auctionWithPrice(10_000_001L);

            // Amount thỏa tier MID nhưng không thỏa tier HIGH
            long amount = 10_000_000L + INCREMENT_MID; // = 10_200_000

            assertThat(sut.isValidBid(auctionMid, amount)).isTrue();
            // 10_000_001 + 500_000 = 10_500_001 > 10_200_000 → false
            assertThat(sut.isValidBid(auctionHigh, amount)).isFalse();
        }
    }

    // =========================================================================
    // Strategy Pattern behavior
    // =========================================================================

    @Nested
    @DisplayName("Strategy Pattern Behavior")
    class StrategyPatternBehavior {

        @Test
        @DisplayName("describe() trả về chuỗi không null")
        void describe_notNull() {
            assertThat(sut.describe()).isNotNull();
        }

        @Test
        @DisplayName("describe() trả về chuỗi không rỗng")
        void describe_notEmpty() {
            assertThat(sut.describe()).isNotBlank();
        }

        @Test
        @DisplayName("describe() chứa thông tin các ngưỡng (50k, 200k, 500k)")
        void describe_containsIncrementInfo() {
            String desc = sut.describe();
            assertThat(desc).containsAnyOf("50k", "50.000", "50000");
            assertThat(desc).containsAnyOf("200k", "200.000", "200000");
            assertThat(desc).containsAnyOf("500k", "500.000", "500000");
        }

        @Test
        @DisplayName("isValidBid() không mutate currentPrice của auction")
        void isValidBid_doesNotMutateAuctionCurrentPrice() {
            // Arrange
            Auction auction = auctionWithPrice(5_000_000L);
            long priceBefore = auction.getCurrentPrice();

            // Act
            sut.isValidBid(auction, 6_000_000L);  // valid bid
            sut.isValidBid(auction, 1_000L);       // invalid bid

            // Assert — auction state không bị thay đổi
            assertThat(auction.getCurrentPrice()).isEqualTo(priceBefore);
        }

        @Test
        @DisplayName("isValidBid() không mutate status của auction")
        void isValidBid_doesNotMutateAuctionStatus() {
            // Arrange
            Auction auction = auctionWithPrice(5_000_000L);
            Auction.AuctionStatus statusBefore = auction.getStatus();

            // Act
            sut.isValidBid(auction, 6_000_000L);
            sut.isValidBid(auction, 1_000L);

            // Assert
            assertThat(auction.getStatus()).isEqualTo(statusBefore);
        }

        @Test
        @DisplayName("isValidBid() deterministic — cùng input luôn trả cùng kết quả")
        void isValidBid_deterministic_sameInputSameOutput() {
            Auction auction = auctionWithPrice(5_000_000L);
            long amount = 5_000_000L + INCREMENT_MID;

            boolean first  = sut.isValidBid(auction, amount);
            boolean second = sut.isValidBid(auction, amount);
            boolean third  = sut.isValidBid(auction, amount);

            assertThat(first).isEqualTo(second).isEqualTo(third).isTrue();
        }

        @Test
        @DisplayName("isValidBid() deterministic — invalid case cũng nhất quán")
        void isValidBid_deterministic_invalidCaseConsistent() {
            Auction auction = auctionWithPrice(5_000_000L);
            long amount = 5_000_000L + INCREMENT_MID - 1;

            boolean first  = sut.isValidBid(auction, amount);
            boolean second = sut.isValidBid(auction, amount);

            assertThat(first).isEqualTo(second).isFalse();
        }

        @Test
        @DisplayName("getMinIncrement() nhất quán với isValidBid() — exact boundary đều đúng")
        void getMinIncrement_consistentWithIsValidBid() {
            long[] prices = { 0L, 500_000L, 999_999L, 1_000_000L, 5_000_000L,
                    10_000_000L, 10_000_001L, 50_000_000L };

            for (long price : prices) {
                Auction auction = auctionWithPrice(price);
                long increment  = sut.getMinIncrement(price);

                // amount = currentPrice + minIncrement phải hợp lệ
                assertThat(sut.isValidBid(auction, price + increment))
                        .as("price=%d: amount=price+increment phải hợp lệ", price)
                        .isTrue();

                // amount = currentPrice + minIncrement - 1 phải không hợp lệ
                assertThat(sut.isValidBid(auction, price + increment - 1))
                        .as("price=%d: amount=price+increment-1 phải không hợp lệ", price)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("nhiều StandardBidStrategy instances cho kết quả giống nhau (stateless)")
        void multipleInstances_sameResult_stateless() {
            StandardBidStrategy strategy1 = new StandardBidStrategy();
            StandardBidStrategy strategy2 = new StandardBidStrategy();
            Auction auction = auctionWithPrice(5_000_000L);
            long amount = 5_200_000L;

            assertThat(strategy1.isValidBid(auction, amount))
                    .isEqualTo(strategy2.isValidBid(auction, amount));
        }
    }

    // =========================================================================
    // Parameterized — boundary sweep
    // =========================================================================

    @Nested
    @DisplayName("Boundary sweep — parameterized")
    class BoundarySweep {

        @ParameterizedTest(name = "currentPrice={0}, amount={1} → valid={2}")
        @CsvSource({
                // Tier LOW boundary
                "0,         50000,  true",
                "0,         49999,  false",
                "999999,    1049999,true",
                "999999,    1049998,false",

                // Tier MID lower boundary
                "1000000,   1200000,true",
                "1000000,   1199999,false",

                // Tier MID upper boundary
                "10000000,  10200000,true",
                "10000000,  10199999,false",

                // Tier HIGH lower boundary
                "10000001,  10500001,true",
                "10000001,  10500000,false",

                // Large price
                "100000000, 100500000,true",
                "100000000, 100499999,false"
        })
        @DisplayName("isValidBid() — boundary sweep across all tiers")
        void isValidBid_boundarySweep(long currentPrice, long amount, boolean expectedValid) {
            Auction auction = auctionWithPrice(currentPrice);

            boolean result = sut.isValidBid(auction, amount);

            assertThat(result)
                    .as("currentPrice=%d, amount=%d → expected valid=%b", currentPrice, amount, expectedValid)
                    .isEqualTo(expectedValid);
        }

        @ParameterizedTest(name = "currentPrice={0} → increment={1}")
        @CsvSource({
                "0,          50000",
                "1,          50000",
                "500000,     50000",
                "999999,     50000",
                "1000000,    200000",
                "1000001,    200000",
                "5000000,    200000",
                "10000000,   200000",
                "10000001,   500000",
                "50000000,   500000",
                "999999999,  500000"
        })
        @DisplayName("getMinIncrement() — tier classification correctness")
        void getMinIncrement_allTiers(long currentPrice, long expectedIncrement) {
            assertThat(sut.getMinIncrement(currentPrice)).isEqualTo(expectedIncrement);
        }
    }

    // =========================================================================
    // Helpers — không cần DB
    // =========================================================================

    /**
     * Tạo Auction OPEN với currentPrice được set tới giá chỉ định.
     * startingPrice = currentPrice để auction.getCurrentPrice() = currentPrice.
     */
    private static Auction auctionWithPrice(long currentPrice) {
        NormalUser seller = makeSeller();
        Art item = Art.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "Item Test", "Mô tả", currentPrice, seller,
                "Nghệ sĩ", 2020, "Sơn dầu"
        );
        // Auction mới: currentPrice khởi tạo = item.startingPrice
        return Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                currentPrice * 2 + 1   // reservePrice không ảnh hưởng validation strategy
        );
    }

    private static NormalUser makeSeller() {
        return NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "seller01", User.hashPassword("password1"),
                "seller01@test.com",
                User.AccountStatus.ACTIVE,
                3.0, 10_000_000L, 0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false, false, null
        );
    }
}