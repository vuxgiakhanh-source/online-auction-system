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
 * Unit test cho {@link AutoBidStrategy} và {@link BidIncrementCalculator}.
 *
 * <p>Không mock — Auction/Item/NormalUser là object thật.
 * Không DB, không network, không filesystem.
 */
@DisplayName("AutoBidStrategy — Strategy Pattern & Business Logic")
class AutoBidStrategyTest {

    // =========================================================================
    // BidIncrementCalculator — threshold tests (prerequisite)
    // =========================================================================

    @Nested
    @DisplayName("BidIncrementCalculator.calculate()")
    class BidIncrementCalculatorTest {

        // ── Tier LOW: currentPrice < 1_000_000 → increment = 50_000 ──────────

        @Test
        @DisplayName("currentPrice = 0 → increment = 50_000 (tier LOW)")
        void tierLow_priceZero_returns50k() {
            assertThat(BidIncrementCalculator.calculate(0L)).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("currentPrice = 1 → increment = 50_000 (tier LOW)")
        void tierLow_priceOne_returns50k() {
            assertThat(BidIncrementCalculator.calculate(1L)).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("currentPrice = 500_000 → increment = 50_000 (tier LOW, mid-range)")
        void tierLow_midRange_returns50k() {
            assertThat(BidIncrementCalculator.calculate(500_000L)).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("currentPrice = 999_999 → increment = 50_000 (tier LOW, upper boundary - 1)")
        void tierLow_upperBoundaryMinus1_returns50k() {
            assertThat(BidIncrementCalculator.calculate(999_999L)).isEqualTo(50_000L);
        }

        // ── Tier MID: 1_000_000 <= currentPrice <= 10_000_000 → 200_000 ──────

        @Test
        @DisplayName("currentPrice = 1_000_000 → increment = 200_000 (tier MID, exact lower boundary)")
        void tierMid_exactLowerBoundary_returns200k() {
            assertThat(BidIncrementCalculator.calculate(1_000_000L)).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("currentPrice = 5_000_000 → increment = 200_000 (tier MID, mid-range)")
        void tierMid_midRange_returns200k() {
            assertThat(BidIncrementCalculator.calculate(5_000_000L)).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("currentPrice = 10_000_000 → increment = 200_000 (tier MID, exact upper boundary)")
        void tierMid_exactUpperBoundary_returns200k() {
            assertThat(BidIncrementCalculator.calculate(10_000_000L)).isEqualTo(200_000L);
        }

        // ── Tier HIGH: currentPrice > 10_000_000 → increment = 500_000 ───────

        @Test
        @DisplayName("currentPrice = 10_000_001 → increment = 500_000 (tier HIGH, lower boundary + 1)")
        void tierHigh_lowerBoundaryPlus1_returns500k() {
            assertThat(BidIncrementCalculator.calculate(10_000_001L)).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("currentPrice = 50_000_000 → increment = 500_000 (tier HIGH, large value)")
        void tierHigh_largeValue_returns500k() {
            assertThat(BidIncrementCalculator.calculate(50_000_000L)).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("currentPrice = Long.MAX_VALUE / 2 → increment = 500_000 (tier HIGH, very large)")
        void tierHigh_veryLargeValue_returns500k() {
            assertThat(BidIncrementCalculator.calculate(Long.MAX_VALUE / 2)).isEqualTo(500_000L);
        }

        // ── Parameterized: quick coverage across all tiers ────────────────────

        @ParameterizedTest(name = "currentPrice={0} → increment={1}")
        @CsvSource({
                "0,         50000",
                "999999,    50000",
                "1000000,   200000",
                "10000000,  200000",
                "10000001,  500000",
                "100000000, 500000"
        })
        @DisplayName("calculate() — parameterized tier coverage")
        void calculate_parameterized(long currentPrice, long expectedIncrement) {
            assertThat(BidIncrementCalculator.calculate(currentPrice)).isEqualTo(expectedIncrement);
        }
    }

    // =========================================================================
    // AutoBidStrategy — Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("maxBid > 0 → khởi tạo thành công")
        void validMaxBid_positive_createsInstance() {
            // Act & Assert
            assertThatCode(() -> new AutoBidStrategy(1_000_000L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("maxBid = 1 (minimum positive) → khởi tạo thành công")
        void validMaxBid_minimumPositive_createsInstance() {
            assertThatCode(() -> new AutoBidStrategy(1L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("maxBid = Long.MAX_VALUE → khởi tạo thành công")
        void validMaxBid_maxLong_createsInstance() {
            assertThatCode(() -> new AutoBidStrategy(Long.MAX_VALUE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("maxBid = 0 → IllegalArgumentException")
        void invalidMaxBid_zero_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new AutoBidStrategy(0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxBid");
        }

        @Test
        @DisplayName("maxBid = -1 → IllegalArgumentException")
        void invalidMaxBid_minusOne_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new AutoBidStrategy(-1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("maxBid = Long.MIN_VALUE → IllegalArgumentException")
        void invalidMaxBid_minLong_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new AutoBidStrategy(Long.MIN_VALUE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "maxBid={0} → IllegalArgumentException")
        @ValueSource(longs = {0L, -1L, -100L, -1_000_000L})
        @DisplayName("maxBid <= 0 — parameterized → mọi giá trị đều ném exception")
        void invalidMaxBid_nonPositive_throwsException(long invalidMaxBid) {
            assertThatThrownBy(() -> new AutoBidStrategy(invalidMaxBid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // AutoBidStrategy — getMaxBid() & getMinIncrement()
    // =========================================================================

    @Nested
    @DisplayName("Accessors: getMaxBid() & getMinIncrement()")
    class AccessorsTest {

        @Test
        @DisplayName("getMaxBid() trả về đúng giá trị đã truyền vào constructor")
        void getMaxBid_returnsConstructorValue() {
            // Arrange
            long expectedMaxBid = 5_000_000L;
            AutoBidStrategy strategy = new AutoBidStrategy(expectedMaxBid);

            // Act & Assert
            assertThat(strategy.getMaxBid()).isEqualTo(expectedMaxBid);
        }

        @Test
        @DisplayName("getMinIncrement(currentPrice) — tier LOW → 50_000")
        void getMinIncrement_tierLow_returns50k() {
            // Arrange
            AutoBidStrategy strategy = new AutoBidStrategy(10_000_000L);

            // Act & Assert
            assertThat(strategy.getMinIncrement(500_000L)).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("getMinIncrement(currentPrice) — tier MID → 200_000")
        void getMinIncrement_tierMid_returns200k() {
            // Arrange
            AutoBidStrategy strategy = new AutoBidStrategy(10_000_000L);

            // Act & Assert
            assertThat(strategy.getMinIncrement(5_000_000L)).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("getMinIncrement(currentPrice) — tier HIGH → 500_000")
        void getMinIncrement_tierHigh_returns500k() {
            // Arrange
            AutoBidStrategy strategy = new AutoBidStrategy(100_000_000L);

            // Act & Assert
            assertThat(strategy.getMinIncrement(15_000_000L)).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("getMinIncrement() nhất quán với BidIncrementCalculator.calculate()")
        void getMinIncrement_consistentWithCalculator() {
            // Arrange
            AutoBidStrategy strategy = new AutoBidStrategy(100_000_000L);
            long currentPrice = 3_000_000L;

            // Act & Assert
            assertThat(strategy.getMinIncrement(currentPrice))
                    .isEqualTo(BidIncrementCalculator.calculate(currentPrice));
        }
    }

    // =========================================================================
    // AutoBidStrategy — describe()
    // =========================================================================

    @Nested
    @DisplayName("describe()")
    class DescribeTest {

        @Test
        @DisplayName("describe() không trả về null")
        void describe_notNull() {
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);
            assertThat(strategy.describe()).isNotNull();
        }

        @Test
        @DisplayName("describe() không trả về chuỗi rỗng")
        void describe_notEmpty() {
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);
            assertThat(strategy.describe()).isNotBlank();
        }

        @Test
        @DisplayName("describe() chứa giá trị maxBid")
        void describe_containsMaxBidValue() {
            // Arrange
            long maxBid = 3_500_000L;
            AutoBidStrategy strategy = new AutoBidStrategy(maxBid);

            // Act
            String description = strategy.describe();

            // Assert — mô tả phải include maxBid để người dùng biết ngưỡng tối đa
            assertThat(description).contains(String.valueOf(maxBid));
        }

        @Test
        @DisplayName("describe() trả về kết quả nhất quán khi gọi nhiều lần")
        void describe_consistent_acrossMultipleCalls() {
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            String first  = strategy.describe();
            String second = strategy.describe();
            String third  = strategy.describe();

            assertThat(first).isEqualTo(second).isEqualTo(third);
        }
    }

    // =========================================================================
    // AutoBidStrategy — isValidBid()
    // =========================================================================

    @Nested
    @DisplayName("isValidBid()")
    class IsValidBidTest {

        // ── Tier LOW: currentPrice < 1_000_000, increment = 50_000 ───────────

        @Test
        @DisplayName("isValidBid — amount hợp lệ (currentPrice + increment < amount <= maxBid)")
        void validBid_amountWithinRange_returnsTrue() {
            // Arrange: currentPrice=500_000 → increment=50_000, minValidAmount=550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act & Assert
            assertThat(strategy.isValidBid(auction, 600_000L)).isTrue();
        }

        @Test
        @DisplayName("isValidBid — amount đúng bằng currentPrice + increment → hợp lệ (boundary)")
        void validBid_amountExactlyMinimum_returnsTrue() {
            // Arrange: currentPrice=500_000 → increment=50_000 → min=550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act & Assert
            assertThat(strategy.isValidBid(auction, 550_000L)).isTrue();
        }

        @Test
        @DisplayName("isValidBid — amount đúng bằng maxBid → hợp lệ (boundary upper)")
        void validBid_amountExactlyMaxBid_returnsTrue() {
            // Arrange: currentPrice=500_000 → increment=50_000 → min=550_000, maxBid=1_000_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);

            // Act & Assert
            assertThat(strategy.isValidBid(auction, 1_000_000L)).isTrue();
        }

        @Test
        @DisplayName("isValidBid — amount vượt maxBid → không hợp lệ")
        void invalidBid_amountExceedsMaxBid_returnsFalse() {
            // Arrange: maxBid=1_000_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);

            // Act & Assert
            assertThat(strategy.isValidBid(auction, 1_000_001L)).isFalse();
        }

        @Test
        @DisplayName("isValidBid — amount nhỏ hơn currentPrice + increment → không hợp lệ")
        void invalidBid_amountBelowMinIncrement_returnsFalse() {
            // Arrange: currentPrice=500_000 → increment=50_000 → min=550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act & Assert — amount=549_999 < 550_000
            assertThat(strategy.isValidBid(auction, 549_999L)).isFalse();
        }

        @Test
        @DisplayName("isValidBid — amount đúng bằng currentPrice (không thêm increment) → không hợp lệ")
        void invalidBid_amountEqualCurrentPrice_returnsFalse() {
            // Arrange: phải vượt currentPrice ít nhất 1 increment
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act & Assert
            assertThat(strategy.isValidBid(auction, 500_000L)).isFalse();
        }

        @Test
        @DisplayName("isValidBid — amount = currentPrice + increment - 1 (boundary -1) → không hợp lệ")
        void invalidBid_amountOneBeforeMinimum_returnsFalse() {
            // Arrange: currentPrice=500_000, increment=50_000, min=550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            assertThat(strategy.isValidBid(auction, 549_999L)).isFalse();
        }

        @Test
        @DisplayName("isValidBid — amount = maxBid + 1 (boundary +1) → không hợp lệ")
        void invalidBid_amountOneAboveMaxBid_returnsFalse() {
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);

            assertThat(strategy.isValidBid(auction, 1_000_001L)).isFalse();
        }

        // ── Tier MID: currentPrice ∈ [1M, 10M], increment = 200_000 ──────────

        @Test
        @DisplayName("isValidBid — tier MID: amount = currentPrice + 200_000 → hợp lệ")
        void validBid_tierMid_exactMinimum_returnsTrue() {
            // Arrange: currentPrice=3_000_000 → increment=200_000 → min=3_200_000
            Auction auction = auctionWithPrice(3_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(10_000_000L);

            assertThat(strategy.isValidBid(auction, 3_200_000L)).isTrue();
        }

        @Test
        @DisplayName("isValidBid — tier MID: amount = currentPrice + 199_999 → không hợp lệ")
        void invalidBid_tierMid_oneBelowMinimum_returnsFalse() {
            // Arrange: increment=200_000, min=3_200_000
            Auction auction = auctionWithPrice(3_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(10_000_000L);

            assertThat(strategy.isValidBid(auction, 3_199_999L)).isFalse();
        }

        // ── Tier HIGH: currentPrice > 10M, increment = 500_000 ───────────────

        @Test
        @DisplayName("isValidBid — tier HIGH: amount = currentPrice + 500_000 → hợp lệ")
        void validBid_tierHigh_exactMinimum_returnsTrue() {
            // Arrange: currentPrice=15_000_000 → increment=500_000 → min=15_500_000
            Auction auction = auctionWithPrice(15_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(50_000_000L);

            assertThat(strategy.isValidBid(auction, 15_500_000L)).isTrue();
        }

        @Test
        @DisplayName("isValidBid — tier HIGH: amount = currentPrice + 499_999 → không hợp lệ")
        void invalidBid_tierHigh_oneBelowMinimum_returnsFalse() {
            Auction auction = auctionWithPrice(15_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(50_000_000L);

            assertThat(strategy.isValidBid(auction, 15_499_999L)).isFalse();
        }

        // ── Repeated validation consistency ───────────────────────────────────

        @Test
        @DisplayName("isValidBid — kết quả nhất quán khi gọi nhiều lần với cùng input (không có side effect)")
        void repeatedValidation_sameInput_consistentResult() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);
            long amount = 600_000L;

            // Act
            boolean first  = strategy.isValidBid(auction, amount);
            boolean second = strategy.isValidBid(auction, amount);
            boolean third  = strategy.isValidBid(auction, amount);

            // Assert
            assertThat(first).isTrue();
            assertThat(second).isEqualTo(first);
            assertThat(third).isEqualTo(first);
        }

        @Test
        @DisplayName("isValidBid — không thay đổi state auction (không mutate)")
        void isValidBid_doesNotMutateAuction() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            long priceBefore  = auction.getCurrentPrice();
            Auction.AuctionStatus statusBefore = auction.getStatus();
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act
            strategy.isValidBid(auction, 600_000L);
            strategy.isValidBid(auction, 400_000L); // invalid attempt

            // Assert — auction state không bị thay đổi
            assertThat(auction.getCurrentPrice()).isEqualTo(priceBefore);
            assertThat(auction.getStatus()).isEqualTo(statusBefore);
        }
    }

    // =========================================================================
    // AutoBidStrategy — calculateNextBid()
    // =========================================================================

    @Nested
    @DisplayName("calculateNextBid()")
    class CalculateNextBidTest {

        // ── Happy path ────────────────────────────────────────────────────────

        @Test
        @DisplayName("calculateNextBid — next = currentPrice + increment (tier LOW)")
        void happyPath_tierLow_returnsCurrentPlusTierLowIncrement() {
            // Arrange: currentPrice=500_000, increment=50_000, next=550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert
            assertThat(next).isEqualTo(550_000L);
        }

        @Test
        @DisplayName("calculateNextBid — next = currentPrice + increment (tier MID)")
        void happyPath_tierMid_returnsCurrentPlusTierMidIncrement() {
            // Arrange: currentPrice=5_000_000, increment=200_000, next=5_200_000
            Auction auction = auctionWithPrice(5_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(20_000_000L);

            assertThat(strategy.calculateNextBid(auction)).isEqualTo(5_200_000L);
        }

        @Test
        @DisplayName("calculateNextBid — next = currentPrice + increment (tier HIGH)")
        void happyPath_tierHigh_returnsCurrentPlusTierHighIncrement() {
            // Arrange: currentPrice=20_000_000, increment=500_000, next=20_500_000
            Auction auction = auctionWithPrice(20_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(100_000_000L);

            assertThat(strategy.calculateNextBid(auction)).isEqualTo(20_500_000L);
        }

        // ── currentPrice = 0 edge case ────────────────────────────────────────

        @Test
        @DisplayName("calculateNextBid — currentPrice = 0 → next = 0 + 50_000 = 50_000")
        void edgeCase_currentPriceZero_returnsIncrement() {
            // Arrange: price=0 → tier LOW → increment=50_000
            Auction auction = auctionWithPrice(0L);
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);

            assertThat(strategy.calculateNextBid(auction)).isEqualTo(50_000L);
        }

        // ── Boundary: next đúng bằng maxBid → hợp lệ, không trả -1 ──────────

        @Test
        @DisplayName("calculateNextBid — next đúng bằng maxBid → trả về next (không trả -1)")
        void boundary_nextExactlyMaxBid_returnsNext() {
            // Arrange: currentPrice=500_000, increment=50_000, next=550_000
            // maxBid=550_000 (đúng bằng next)
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(550_000L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert — next == maxBid → hợp lệ, không phải -1
            assertThat(next).isEqualTo(550_000L);
        }

        // ── Boundary: next vượt maxBid → trả -1 ──────────────────────────────

        @Test
        @DisplayName("calculateNextBid — next > maxBid → trả về -1 (không thể auto-bid)")
        void boundary_nextExceedsMaxBid_returnsMinusOne() {
            // Arrange: currentPrice=500_000, increment=50_000, next=550_000
            // maxBid=549_999 (nhỏ hơn next 1 đơn vị)
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(549_999L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert
            assertThat(next).isEqualTo(-1L);
        }

        @Test
        @DisplayName("calculateNextBid — maxBid thấp hơn currentPrice → trả -1 (không thể bid gì cả)")
        void boundary_maxBidBelowCurrentPrice_returnsMinusOne() {
            // Arrange: currentPrice=5_000_000, increment=200_000, next=5_200_000
            // maxBid=4_000_000 (thấp hơn cả currentPrice)
            Auction auction = auctionWithPrice(5_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(4_000_000L);

            assertThat(strategy.calculateNextBid(auction)).isEqualTo(-1L);
        }

        @Test
        @DisplayName("calculateNextBid — maxBid bằng currentPrice → trả -1 (cần thêm ít nhất 1 increment)")
        void boundary_maxBidEqualsCurrentPrice_returnsMinusOne() {
            // Arrange: currentPrice=3_000_000, increment=200_000, next=3_200_000
            // maxBid=3_000_000 → next(3_200_000) > maxBid(3_000_000) → -1
            Auction auction = auctionWithPrice(3_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(3_000_000L);

            assertThat(strategy.calculateNextBid(auction)).isEqualTo(-1L);
        }

        // ── Very large currentPrice ────────────────────────────────────────────

        @Test
        @DisplayName("calculateNextBid — currentPrice rất lớn (500M) → next = currentPrice + 500_000")
        void edgeCase_veryLargeCurrentPrice_computesCorrectly() {
            // Arrange: price=500_000_000 → tier HIGH → increment=500_000
            Auction auction = auctionWithPrice(500_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(600_000_000L);

            assertThat(strategy.calculateNextBid(auction)).isEqualTo(500_500_000L);
        }

        // ── Tier boundary transitions ─────────────────────────────────────────

        @Test
        @DisplayName("calculateNextBid — currentPrice tại biên LOW→MID (999_999) → increment=50_000")
        void tierBoundary_lowUpperEdge_usesLowIncrement() {
            Auction auction = auctionWithPrice(999_999L);
            AutoBidStrategy strategy = new AutoBidStrategy(5_000_000L);

            // 999_999 < 1_000_000 → tier LOW → increment=50_000 → next=1_049_999
            assertThat(strategy.calculateNextBid(auction)).isEqualTo(1_049_999L);
        }

        @Test
        @DisplayName("calculateNextBid — currentPrice tại biên LOW→MID (1_000_000) → increment=200_000")
        void tierBoundary_midLowerEdge_usesMidIncrement() {
            Auction auction = auctionWithPrice(1_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(5_000_000L);

            // 1_000_000 ∈ [1M, 10M] → tier MID → increment=200_000 → next=1_200_000
            assertThat(strategy.calculateNextBid(auction)).isEqualTo(1_200_000L);
        }

        @Test
        @DisplayName("calculateNextBid — currentPrice tại biên MID→HIGH (10_000_000) → increment=200_000")
        void tierBoundary_midUpperEdge_usesMidIncrement() {
            Auction auction = auctionWithPrice(10_000_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(50_000_000L);

            // 10_000_000 ∈ [1M, 10M] inclusive → tier MID → increment=200_000 → next=10_200_000
            assertThat(strategy.calculateNextBid(auction)).isEqualTo(10_200_000L);
        }

        @Test
        @DisplayName("calculateNextBid — currentPrice tại biên MID→HIGH (10_000_001) → increment=500_000")
        void tierBoundary_highLowerEdge_usesHighIncrement() {
            Auction auction = auctionWithPrice(10_000_001L);
            AutoBidStrategy strategy = new AutoBidStrategy(50_000_000L);

            // 10_000_001 > 10_000_000 → tier HIGH → increment=500_000 → next=10_500_001
            assertThat(strategy.calculateNextBid(auction)).isEqualTo(10_500_001L);
        }

        // ── Strategy không mutate auction ─────────────────────────────────────

        @Test
        @DisplayName("calculateNextBid — không mutate auction state sau khi gọi")
        void calculateNextBid_doesNotMutateAuction() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            Auction.AuctionStatus statusBefore = auction.getStatus();
            long priceBefore = auction.getCurrentPrice();
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act
            strategy.calculateNextBid(auction);

            // Assert
            assertThat(auction.getCurrentPrice()).isEqualTo(priceBefore);
            assertThat(auction.getStatus()).isEqualTo(statusBefore);
        }

        @Test
        @DisplayName("calculateNextBid — kết quả nhất quán khi gọi nhiều lần (deterministic)")
        void calculateNextBid_deterministicAcrossMultipleCalls() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act
            long first  = strategy.calculateNextBid(auction);
            long second = strategy.calculateNextBid(auction);
            long third  = strategy.calculateNextBid(auction);

            // Assert
            assertThat(first).isEqualTo(second).isEqualTo(third);
        }
    }

    // =========================================================================
    // Strategy Pattern — interface contract
    // =========================================================================

    @Nested
    @DisplayName("Strategy Pattern — BidStrategy interface contract")
    class StrategyPatternTest {

        @Test
        @DisplayName("AutoBidStrategy implements BidStrategy interface")
        void implementsBidStrategyInterface() {
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);
            assertThat(strategy).isInstanceOf(BidStrategy.class);
        }

        @Test
        @DisplayName("isValidBid() và calculateNextBid() nhất quán — nếu nextBid hợp lệ thì isValidBid(nextBid)=true")
        void consistency_nextBidIsAlwaysValidIfNotMinusOne() {
            // Arrange: next = currentPrice + increment ≤ maxBid → phải pass isValidBid
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(2_000_000L);

            // Act
            long nextBid = strategy.calculateNextBid(auction);

            // Assert — nextBid != -1 → isValidBid phải trả true
            assertThat(nextBid).isNotEqualTo(-1L);
            assertThat(strategy.isValidBid(auction, nextBid)).isTrue();
        }

        @Test
        @DisplayName("isValidBid() nhất quán với calculateNextBid() khi trả -1 — bid không hợp lệ")
        void consistency_whenNextBidIsMinusOne_isValidBidReturnsFalse() {
            // Arrange: next > maxBid → calculateNextBid=-1 → isValidBid(nextAmount) = false
            // currentPrice=500_000, increment=50_000, next=550_000, maxBid=549_999
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(549_999L);

            // Act
            long nextBid = strategy.calculateNextBid(auction);

            // Assert — -1 mean cannot bid; isValidBid with the calculated next is false
            assertThat(nextBid).isEqualTo(-1L);
            // 550_000 (raw next) > maxBid(549_999) → isValidBid false
            assertThat(strategy.isValidBid(auction, 550_000L)).isFalse();
        }

        @Test
        @DisplayName("Hai AutoBidStrategy với maxBid khác nhau hoạt động độc lập")
        void multipleInstances_independentState() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy lowMax  = new AutoBidStrategy(600_000L);  // chỉ 1 bước
            AutoBidStrategy highMax = new AutoBidStrategy(5_000_000L); // nhiều bước

            // Assert — phải độc lập với nhau
            assertThat(lowMax.getMaxBid()).isNotEqualTo(highMax.getMaxBid());
            assertThat(lowMax.isValidBid(auction, 600_000L)).isTrue();
            assertThat(highMax.isValidBid(auction, 600_000L)).isTrue();
            assertThat(lowMax.isValidBid(auction, 700_000L)).isFalse();   // vượt lowMax
            assertThat(highMax.isValidBid(auction, 700_000L)).isTrue();   // trong highMax
        }
    }

    // =========================================================================
    // Test helpers — không cần DB
    // =========================================================================

    /**
     * Tạo Auction RUNNING với currentPrice đã cho.
     * Dùng object thật: NormalUser, Art, Auction.
     */
    private static Auction auctionWithPrice(long currentPrice) {
        NormalUser seller = NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "seller01",
                User.hashPassword("password1"),
                "seller01@test.com",
                User.AccountStatus.ACTIVE,
                3.0, 10_000_000L, 0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false, false, null
        );

        Art item = Art.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "Tranh Test", "Mô tả tranh",
                currentPrice,   // startingPrice = currentPrice để auction.currentPrice khởi đầu đúng
                seller,
                "Nghệ sĩ Test", 2020, "Sơn dầu"
        );

        // Auction.create() → currentPrice = item.getStartingPrice() = currentPrice
        return Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                currentPrice * 2 == 0 ? 1L : currentPrice * 2  // reservePrice > 0
        );
    }
}