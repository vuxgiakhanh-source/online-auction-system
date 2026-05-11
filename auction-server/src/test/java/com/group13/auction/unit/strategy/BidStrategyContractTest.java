package com.group13.auction.unit.strategy;

import com.group13.auction.unit.TestFixture;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.strategy.AutoBidStrategy;
import com.group13.auction.strategy.BidStrategy;
import com.group13.auction.strategy.StandardBidStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link BidStrategy} interface contract.
 *
 * <p>Chiến lược kiểm tra:
 * <ul>
 *   <li><b>Contract integrity</b> – mọi implementation phải thỏa mãn
 *       contract của interface: {@code isValidBid()} và {@code describe()}.</li>
 *   <li><b>Liskov Substitution Principle (LSP)</b> – mọi implementation có thể
 *       thay thế nhau qua kiểu {@code BidStrategy} mà không phá vỡ hành vi
 *       đã được contract định nghĩa.</li>
 *   <li><b>Polymorphism correctness</b> – hành vi thực tế phụ thuộc vào
 *       implementation cụ thể, không phải kiểu khai báo.</li>
 *   <li><b>Interchangeability</b> – cùng auction + amount có thể cho kết quả
 *       khác nhau tùy strategy, đúng với ý đồ Strategy Pattern.</li>
 * </ul>
 *
 * <p>Không mock, không DB, không network.
 * Dùng object thật từ {@link TestFixture}.
 *
 * <p>Bước giá (BidIncrementCalculator):
 * <pre>
 *   currentPrice &lt; 1_000_000          → increment = 50_000
 *   1_000_000 &lt;= currentPrice &lt;= 10_000_000 → increment = 200_000
 *   currentPrice &gt; 10_000_000         → increment = 500_000
 * </pre>
 */
@DisplayName("BidStrategy — Interface Contract & Polymorphism")
class BidStrategyContractTest {

    // =========================================================================
    // Fixture helpers
    // =========================================================================

    /**
     * Tạo auction với currentPrice tùy chỉnh (RUNNING state).
     * Dùng {@link TestFixture#auctionWithStatus} để bypass factory validation.
     */
    private static Auction auctionWithPrice(long currentPrice) {
        NormalUser seller = TestFixture.normalSeller("sellerFixt1");
        // startingPrice <= currentPrice để hợp lệ (reserve = startingPrice * 2)
        long startingPrice = Math.max(1L, currentPrice / 2);
        return TestFixture.auctionWithStatus(
                seller, startingPrice, currentPrice, Auction.AuctionStatus.RUNNING);
    }

    /**
     * Cung cấp một instance của mỗi BidStrategy implementation.
     * Dùng cho parameterized test kiểm tra contract chung.
     *
     * <p>Mỗi strategy được khởi tạo với giá trị hợp lệ:
     * - StandardBidStrategy: không cần tham số.
     * - AutoBidStrategy: maxBid = 10_000_000 (đủ cao để không chặn bid trong tier thấp/mid).
     * - ReservePriceStrategy: reservePrice = 5_000_000.
     */
    static Stream<BidStrategy> allImplementations() {
        return Stream.of(
                new StandardBidStrategy(),
                new AutoBidStrategy(10_000_000L)
        );
    }

    /**
     * Cung cấp tên class của mỗi implementation để dùng trong display name.
     */
    static Stream<String> allImplementationNames() {
        return allImplementations()
                .map(s -> s.getClass().getSimpleName());
    }

    // =========================================================================
    // I. Contract: describe() — non-null, non-empty
    // =========================================================================

    @Nested
    @DisplayName("I. Contract: describe() — non-null và non-empty")
    class DescribeContractTest {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.group13.auction.unit.strategy.BidStrategyContractTest#allImplementations")
        @DisplayName("describe() không bao giờ trả về null")
        void describe_neverReturnsNull(BidStrategy strategy) {
            // Act
            String description = strategy.describe();

            // Assert
            assertNotNull(description,
                    strategy.getClass().getSimpleName() + ".describe() must not return null");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.group13.auction.unit.strategy.BidStrategyContractTest#allImplementations")
        @DisplayName("describe() không bao giờ trả về chuỗi rỗng")
        void describe_neverReturnsEmpty(BidStrategy strategy) {
            // Act
            String description = strategy.describe();

            // Assert
            assertFalse(description.isBlank(),
                    strategy.getClass().getSimpleName() + ".describe() must not return blank string");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.group13.auction.unit.strategy.BidStrategyContractTest#allImplementations")
        @DisplayName("describe() trả về cùng kết quả khi gọi nhiều lần (idempotent)")
        void describe_isIdempotent(BidStrategy strategy) {
            // Act
            String first  = strategy.describe();
            String second = strategy.describe();
            String third  = strategy.describe();

            // Assert
            assertEquals(first, second,
                    strategy.getClass().getSimpleName() + ".describe() must be idempotent");
            assertEquals(second, third,
                    strategy.getClass().getSimpleName() + ".describe() must be idempotent");
        }
    }

    // =========================================================================
    // II. Contract: isValidBid() — bid hợp lệ (happy path)
    // =========================================================================

    @Nested
    @DisplayName("II. Contract: isValidBid() — bid chính xác bằng currentPrice + increment phải hợp lệ")
    class IsValidBidMinimumValidBidTest {

        @Test
        @DisplayName("StandardBidStrategy: bid = currentPrice + increment (tier thấp) → hợp lệ")
        void standard_minimumValidBid_lowTier_returnsTrue() {
            // Arrange — currentPrice = 500_000, increment = 50_000
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 550_000L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("AutoBidStrategy: bid = currentPrice + increment, bid <= maxBid → hợp lệ")
        void auto_minimumValidBid_withinMaxBid_returnsTrue() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 1_000_000
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(1_000_000L);

            // Act
            boolean result = strategy.isValidBid(auction, 550_000L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("StandardBidStrategy: bid >> currentPrice + increment → hợp lệ")
        void standard_bidFarAboveMinimum_returnsTrue() {
            // Arrange — currentPrice = 500_000, minimum = 550_000, bid = 2_000_000
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 2_000_000L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("StandardBidStrategy: bid = currentPrice + increment (tier mid) → hợp lệ")
        void standard_minimumValidBid_midTier_returnsTrue() {
            // Arrange — currentPrice = 2_000_000, increment = 200_000
            Auction auction = auctionWithPrice(2_000_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 2_200_000L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("StandardBidStrategy: bid = currentPrice + increment (tier cao) → hợp lệ")
        void standard_minimumValidBid_highTier_returnsTrue() {
            // Arrange — currentPrice = 15_000_000, increment = 500_000
            Auction auction = auctionWithPrice(15_000_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 15_500_000L);

            // Assert
            assertTrue(result);
        }
    }

    // =========================================================================
    // III. Contract: isValidBid() — bid không hợp lệ (sad path)
    // =========================================================================

    @Nested
    @DisplayName("III. Contract: isValidBid() — bid dưới mức tối thiểu phải bị từ chối")
    class IsValidBidBelowMinimumTest {

        @Test
        @DisplayName("StandardBidStrategy: bid = currentPrice + increment - 1 → không hợp lệ")
        void standard_oneBelowMinimum_returnsFalse() {
            // Arrange — currentPrice = 500_000, increment = 50_000, min = 550_000
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 549_999L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("StandardBidStrategy: bid = currentPrice → không hợp lệ (thiếu increment)")
        void standard_bidEqualsCurrentPrice_returnsFalse() {
            // Arrange — currentPrice = 1_000_000, bid = 1_000_000 (thiếu increment 200_000)
            Auction auction = auctionWithPrice(1_000_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 1_000_000L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("StandardBidStrategy: bid = 0 → không hợp lệ")
        void standard_bidZero_returnsFalse() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 0L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("StandardBidStrategy: bid âm → không hợp lệ")
        void standard_negativeBid_returnsFalse() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, -1L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("AutoBidStrategy: bid đủ cao nhưng vượt maxBid → không hợp lệ")
        void auto_bidExceedsMaxBid_returnsFalse() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 600_000
            // bid = 700_000 > maxBid → bị từ chối dù đủ increment
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(600_000L);

            // Act
            boolean result = strategy.isValidBid(auction, 700_000L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("AutoBidStrategy: bid = maxBid + 1 → không hợp lệ")
        void auto_bidOneAboveMaxBid_returnsFalse() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 550_000
            // bid = 550_001 > maxBid
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(550_000L);

            // Act
            boolean result = strategy.isValidBid(auction, 550_001L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("AutoBidStrategy: bid dưới increment VÀ vượt maxBid → không hợp lệ")
        void auto_bidBelowIncrementAndExceedsMaxBid_returnsFalse() {
            // Arrange — currentPrice = 500_000, increment = 50_000
            // maxBid = 520_000: bid 520_000 < 550_000 (min) → không hợp lệ
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(520_000L);

            // Act
            boolean result = strategy.isValidBid(auction, 520_000L);

            // Assert
            assertFalse(result);
        }
    }

    // =========================================================================
    // IV. Contract: isValidBid() — biên (boundary)
    // =========================================================================

    @Nested
    @DisplayName("IV. Contract: isValidBid() — boundary values")
    class IsValidBidBoundaryTest {

        @Test
        @DisplayName("Standard: bid = currentPrice + increment (biên hợp lệ thấp nhất) → true")
        void standard_exactMinimumBid_isValid() {
            // Arrange — currentPrice = 999_999, increment = 50_000, min = 1_049_999
            Auction auction = auctionWithPrice(999_999L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 1_049_999L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Standard: bid = currentPrice + increment - 1 (biên không hợp lệ cao nhất) → false")
        void standard_oneBeforeMinimumBid_isInvalid() {
            // Arrange — currentPrice = 999_999, increment = 50_000, min = 1_049_999
            Auction auction = auctionWithPrice(999_999L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act
            boolean result = strategy.isValidBid(auction, 1_049_998L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Auto: bid = maxBid (biên hợp lệ cao nhất) và >= currentPrice + increment → true")
        void auto_bidExactlyAtMaxBid_andAboveMinimum_isValid() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 550_000
            // bid = 550_000 = maxBid = currentPrice + increment → hợp lệ
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(550_000L);

            // Act
            boolean result = strategy.isValidBid(auction, 550_000L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Auto: bid = maxBid - 1 nhưng < currentPrice + increment → không hợp lệ")
        void auto_bidBelowMinimumButUnderMaxBid_isInvalid() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 600_000
            // bid = 549_999 < 550_000 (min) → không hợp lệ
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(600_000L);

            // Act
            boolean result = strategy.isValidBid(auction, 549_999L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Standard: tier chuyển từ low sang mid — bid tại biên increment mới phải hợp lệ")
        void standard_tierBoundary_lowToMid_bidWithMidIncrement_isValid() {
            // Arrange — currentPrice = 1_000_000 (đúng biên tier mid), increment = 200_000
            Auction auction = auctionWithPrice(1_000_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act — bid = 1_200_000 = currentPrice + 200_000 (increment của tier mid)
            boolean result = strategy.isValidBid(auction, 1_200_000L);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Standard: tier chuyển từ low sang mid — bid với increment tier cũ (50_000) → không hợp lệ")
        void standard_tierBoundary_lowToMid_bidWithOldIncrement_isInvalid() {
            // Arrange — currentPrice = 1_000_000, increment = 200_000 (không phải 50_000)
            Auction auction = auctionWithPrice(1_000_000L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act — bid = 1_050_000 < 1_200_000 (min) → không hợp lệ
            boolean result = strategy.isValidBid(auction, 1_050_000L);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Standard: tier chuyển từ mid sang high — bid tại biên increment mới phải hợp lệ")
        void standard_tierBoundary_midToHigh_bidWithHighIncrement_isValid() {
            // Arrange — currentPrice = 10_000_001 (đúng biên tier cao), increment = 500_000
            Auction auction = auctionWithPrice(10_000_001L);
            BidStrategy strategy = new StandardBidStrategy();

            // Act — bid = 10_500_001 = currentPrice + 500_000
            boolean result = strategy.isValidBid(auction, 10_500_001L);

            // Assert
            assertTrue(result);
        }
    }

    // =========================================================================
    // V. LSP: tất cả implementation có thể dùng qua BidStrategy reference
    // =========================================================================

    @Nested
    @DisplayName("V. LSP — tất cả implementation hoạt động qua BidStrategy reference")
    class LiskovSubstitutionTest {

        @Test
        @DisplayName("StandardBidStrategy dùng được qua BidStrategy reference")
        void standardUsableViaInterfaceReference() {
            // Arrange
            BidStrategy strategy = new StandardBidStrategy();
            Auction auction = auctionWithPrice(500_000L);

            // Act & Assert — không throw, không NPE
            assertDoesNotThrow(() -> strategy.isValidBid(auction, 550_000L));
            assertDoesNotThrow(() -> strategy.describe());
        }

        @Test
        @DisplayName("AutoBidStrategy dùng được qua BidStrategy reference")
        void autoUsableViaInterfaceReference() {
            // Arrange
            BidStrategy strategy = new AutoBidStrategy(1_000_000L);
            Auction auction = auctionWithPrice(500_000L);

            // Act & Assert
            assertDoesNotThrow(() -> strategy.isValidBid(auction, 550_000L));
            assertDoesNotThrow(() -> strategy.describe());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.group13.auction.unit.strategy.BidStrategyContractTest#allImplementations")
        @DisplayName("isValidBid() trả về boolean (không throw) với bid hợp lệ")
        void isValidBid_withValidBid_returnsBooleanWithoutThrowing(BidStrategy strategy) {
            // Arrange — currentPrice 500_000, bid = 1_000_000 (đủ mọi increment)
            Auction auction = auctionWithPrice(500_000L);

            // Act & Assert — mọi implementation đều không throw với input hợp lệ
            assertDoesNotThrow(() -> strategy.isValidBid(auction, 1_000_000L));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.group13.auction.unit.strategy.BidStrategyContractTest#allImplementations")
        @DisplayName("isValidBid() trả về boolean (không throw) với bid không hợp lệ")
        void isValidBid_withInvalidBid_returnsBooleanWithoutThrowing(BidStrategy strategy) {
            // Arrange — currentPrice = 500_000, bid = 1 (quá thấp)
            Auction auction = auctionWithPrice(500_000L);

            // Act & Assert — mọi implementation từ chối bid thấp mà không throw
            assertDoesNotThrow(() -> strategy.isValidBid(auction, 1L));
        }
    }

    // =========================================================================
    // VI. Polymorphism: runtime dispatch dựa vào implementation
    // =========================================================================

    @Nested
    @DisplayName("VI. Polymorphism — runtime dispatch đúng implementation")
    class PolymorphismDispatchTest {

        @Test
        @DisplayName("AutoBidStrategy từ chối bid hợp lệ với Standard nhưng vượt maxBid")
        void auto_rejectsBidValidForStandard_whenExceedsMaxBid() {
            // Arrange — currentPrice = 500_000, increment = 50_000
            // bid = 600_000: hợp lệ với Standard (600_000 >= 550_000)
            //                nhưng không hợp lệ với Auto(maxBid=580_000) vì 600_000 > 580_000
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy standard = new StandardBidStrategy();
            BidStrategy auto    = new AutoBidStrategy(580_000L);

            // Act
            boolean standardResult = standard.isValidBid(auction, 600_000L);
            boolean autoResult     = auto.isValidBid(auction, 600_000L);

            // Assert — polymorphism: cùng call, kết quả khác nhau
            assertTrue(standardResult,  "Standard phải chấp nhận bid 600_000");
            assertFalse(autoResult,     "Auto phải từ chối bid 600_000 vượt maxBid=580_000");
        }

        @Test
        @DisplayName("Auto với maxBid đủ cao đồng thuận với Standard trên bid hợp lệ")
        void auto_withHighMaxBid_agreesWithStandardOnValidBid() {
            // Arrange — maxBid cao hơn nhiều so với bid → không bị chặn bởi maxBid
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy standard = new StandardBidStrategy();
            BidStrategy auto     = new AutoBidStrategy(100_000_000L);
            long bid = 550_000L;

            // Act
            boolean standardResult = standard.isValidBid(auction, bid);
            boolean autoResult     = auto.isValidBid(auction, bid);

            // Assert
            assertEquals(standardResult, autoResult,
                    "Auto với maxBid rất cao phải đồng thuận với Standard");
            assertTrue(standardResult);
        }

        @Test
        @DisplayName("cùng reference BidStrategy, gọi implementation khác nhau cho kết quả khác nhau")
        void sameReference_differentImplementation_differentBehavior() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            long bid = 560_000L; // hợp lệ với Standard (>= 550_000)

            List<BidStrategy> strategies = List.of(
                    new StandardBidStrategy(),
                    new AutoBidStrategy(555_000L)   // maxBid < bid → từ chối
            );

            // Act
            Boolean[] results = strategies.stream()
                    .map(s -> s.isValidBid(auction, bid))
                    .toArray(Boolean[]::new);

            // Assert — Standard và Reserve chấp nhận, Auto từ chối
            assertTrue(results[0],  "Standard phải chấp nhận bid 560_000");
            assertFalse(results[1], "Auto(maxBid=555_000) phải từ chối bid 560_000");
        }
    }

    // =========================================================================
    // VII. Interchangeability — Strategy Pattern swap behavior
    // =========================================================================

    @Nested
    @DisplayName("VII. Interchangeability — swap strategy không phá vỡ caller")
    class InterchangeabilityTest {

        /**
         * Helper: simulate caller nhận BidStrategy từ bên ngoài và gọi isValidBid.
         * Đây là đúng cách Strategy Pattern hoạt động — caller không biết implementation.
         */
        private boolean validateBid(BidStrategy strategy, Auction auction, long amount) {
            return strategy.isValidBid(auction, amount);
        }

        @Test
        @DisplayName("swap từ Standard sang Auto — caller hoạt động bình thường với cả hai")
        void swap_standardToAuto_callerWorksCorrectly() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            long validBid   = 600_000L; // hợp lệ với cả Standard và Auto(maxBid=1_000_000)

            BidStrategy standard = new StandardBidStrategy();
            BidStrategy auto     = new AutoBidStrategy(1_000_000L);

            // Act — swap strategy, caller giống hệt nhau
            boolean withStandard = validateBid(standard, auction, validBid);
            boolean withAuto     = validateBid(auto, auction, validBid);

            // Assert — cả hai đều chấp nhận bid hợp lệ
            assertTrue(withStandard, "Standard phải chấp nhận valid bid");
            assertTrue(withAuto,     "Auto phải chấp nhận valid bid khi maxBid đủ");
        }

        @Test
        @DisplayName("strategy describe() vẫn gọi được sau khi swap reference")
        void swappedStrategy_describeStillCallable() {
            // Arrange
            BidStrategy strategy = new StandardBidStrategy();

            // Act — swap sang implementation khác
            strategy = new AutoBidStrategy(5_000_000L);
            String description = strategy.describe();

            // Assert — describe vẫn hoạt động sau swap
            assertNotNull(description);
            assertFalse(description.isBlank());
        }

        @Test
        @DisplayName("collection của nhiều strategy — gọi isValidBid qua interface đều hoạt động")
        void collection_ofMixedStrategies_allCallableViaInterface() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            long bid = 550_000L; // minimum valid bid

            List<BidStrategy> strategies = List.of(
                    new StandardBidStrategy(),
                    new AutoBidStrategy(1_000_000L)
            );

            // Act & Assert — không có exception, tất cả trả về boolean
            for (BidStrategy strategy : strategies) {
                assertDoesNotThrow(
                        () -> strategy.isValidBid(auction, bid),
                        strategy.getClass().getSimpleName() + ".isValidBid() must not throw"
                );
            }
        }
    }

    // =========================================================================
    // VIII. Constructor contract — invalid input
    // =========================================================================

    @Nested
    @DisplayName("VIII. Constructor contract — invalid input")
    class ConstructorContractTest {

        @Test
        @DisplayName("AutoBidStrategy: maxBid = 0 → ném IllegalArgumentException")
        void auto_maxBidZero_throwsIllegalArgument() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> new AutoBidStrategy(0L));
        }

        @Test
        @DisplayName("AutoBidStrategy: maxBid âm → ném IllegalArgumentException")
        void auto_negativeMaxBid_throwsIllegalArgument() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> new AutoBidStrategy(-1L));
        }

        @Test
        @DisplayName("AutoBidStrategy: maxBid = Long.MIN_VALUE → ném IllegalArgumentException")
        void auto_longMinMaxBid_throwsIllegalArgument() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> new AutoBidStrategy(Long.MIN_VALUE));
        }

        @Test
        @DisplayName("AutoBidStrategy: maxBid = 1 (minimum dương) → tạo thành công")
        void auto_maxBidOne_createsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(() -> new AutoBidStrategy(1L));
        }

        @Test
        @DisplayName("StandardBidStrategy: không cần tham số → tạo thành công")
        void standard_noArgConstructor_createsSuccessfully() {
            // Act & Assert
            assertDoesNotThrow(StandardBidStrategy::new);
        }
    }

    // =========================================================================
    // IX. isValidBid() — consistency (idempotent)
    // =========================================================================

    @Nested
    @DisplayName("IX. isValidBid() — consistency: cùng input cho cùng output")
    class IsValidBidConsistencyTest {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.group13.auction.unit.strategy.BidStrategyContractTest#allImplementations")
        @DisplayName("isValidBid() idempotent: gọi 3 lần cùng input cho cùng kết quả")
        void isValidBid_sameInput_sameOutput_repeatedly(BidStrategy strategy) {
            // Arrange — bid = 1_000_000, price = 500_000 (hợp lệ với tất cả strategies được cấp)
            Auction auction = auctionWithPrice(500_000L);
            long bid = 1_000_000L;

            // Act
            boolean first  = strategy.isValidBid(auction, bid);
            boolean second = strategy.isValidBid(auction, bid);
            boolean third  = strategy.isValidBid(auction, bid);

            // Assert — không có side effect ảnh hưởng kết quả
            assertEquals(first, second,
                    strategy.getClass().getSimpleName() + ".isValidBid() must be consistent");
            assertEquals(second, third,
                    strategy.getClass().getSimpleName() + ".isValidBid() must be consistent");
        }

        @Test
        @DisplayName("StandardBidStrategy: kết quả không thay đổi sau nhiều lần gọi với invalid bid")
        void standard_consistentlyRejectsInvalidBid() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new StandardBidStrategy();
            long invalidBid = 100L;

            // Act
            boolean r1 = strategy.isValidBid(auction, invalidBid);
            boolean r2 = strategy.isValidBid(auction, invalidBid);

            // Assert
            assertFalse(r1);
            assertFalse(r2);
        }

        @Test
        @DisplayName("AutoBidStrategy: kết quả không thay đổi sau nhiều lần gọi với valid bid")
        void auto_consistentlyAcceptsValidBid() {
            // Arrange — currentPrice = 500_000, bid = 600_000 <= maxBid = 1_000_000
            Auction auction = auctionWithPrice(500_000L);
            BidStrategy strategy = new AutoBidStrategy(1_000_000L);
            long validBid = 600_000L;

            // Act
            boolean r1 = strategy.isValidBid(auction, validBid);
            boolean r2 = strategy.isValidBid(auction, validBid);
            boolean r3 = strategy.isValidBid(auction, validBid);

            // Assert
            assertTrue(r1);
            assertTrue(r2);
            assertTrue(r3);
        }
    }

    // =========================================================================
    // X. AutoBidStrategy.calculateNextBid() — specific behavior
    // =========================================================================

    @Nested
    @DisplayName("X. AutoBidStrategy.calculateNextBid() — specific contract")
    class AutoBidCalculateNextBidTest {

        @Test
        @DisplayName("calculateNextBid() trả về currentPrice + increment khi maxBid đủ")
        void calculateNextBid_whenMaxBidSufficient_returnsNextBid() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 1_000_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert
            assertEquals(550_000L, next);
        }

        @Test
        @DisplayName("calculateNextBid() trả về -1 khi maxBid không đủ để vượt increment")
        void calculateNextBid_whenMaxBidInsufficient_returnsMinusOne() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 540_000 < 550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(540_000L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert
            assertEquals(-1L, next);
        }

        @Test
        @DisplayName("calculateNextBid() trả về -1 khi maxBid = currentPrice + increment - 1")
        void calculateNextBid_whenMaxBidOneBeforeMinimum_returnsMinusOne() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 549_999
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(549_999L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert
            assertEquals(-1L, next);
        }

        @Test
        @DisplayName("calculateNextBid() trả về next bid khi maxBid = currentPrice + increment (biên)")
        void calculateNextBid_whenMaxBidExactlyAtMinimum_returnsNextBid() {
            // Arrange — currentPrice = 500_000, increment = 50_000, maxBid = 550_000
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(550_000L);

            // Act
            long next = strategy.calculateNextBid(auction);

            // Assert
            assertEquals(550_000L, next);
        }

        @Test
        @DisplayName("calculateNextBid() nhất quán với isValidBid() — next bid phải pass validation")
        void calculateNextBid_resultPassesIsValidBidValidation() {
            // Arrange
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(1_000_000L);

            // Act
            long nextBid = strategy.calculateNextBid(auction);

            // Assert — nextBid phải pass validation của chính strategy đó
            assertTrue(nextBid > 0, "nextBid phải > 0 khi maxBid đủ");
            assertTrue(strategy.isValidBid(auction, nextBid),
                    "nextBid được tính bởi calculateNextBid phải pass isValidBid");
        }

        @Test
        @DisplayName("calculateNextBid() = -1 nhất quán: isValidBid(next) cũng false khi maxBid không đủ")
        void calculateNextBid_minusOne_isValidBid_withNextPriceAlsoFails() {
            // Arrange — maxBid = 540_000 không đủ để bid (min = 550_000)
            Auction auction = auctionWithPrice(500_000L);
            AutoBidStrategy strategy = new AutoBidStrategy(540_000L);

            // Act
            long nextBid = strategy.calculateNextBid(auction);

            // Assert
            assertEquals(-1L, nextBid);
            // Confirm: isValidBid với giá bằng maxBid cũng sẽ false vì < min
            assertFalse(strategy.isValidBid(auction, 540_000L),
                    "Khi maxBid không đủ, isValidBid với maxBid cũng phải false");
        }
    }
}