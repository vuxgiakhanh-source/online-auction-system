package com.group13.auction.unit.bank;

import com.group13.auction.bank.SystemBank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.group13.auction.unit.TestFixture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link SystemBank}.
 *
 * <p>Chiến lược:
 * <ul>
 *   <li>Mỗi test reset {@code totalBalance} về 0 qua reflection để tránh
 *       state rò rỉ giữa các test (Singleton cần được isolate).</li>
 *   <li>Không mock — SystemBank là pure calculation + AtomicLong state.</li>
 *   <li>Tất cả expected value được tính tay theo công thức Java
 *       {@code Math.round(price * rate)} (half-up rounding).</li>
 * </ul>
 *
 * <p>Thuế suất:
 * <pre>
 *   salePrice < 1_000_000          → 5%
 *   1_000_000 <= salePrice <= 10_000_000 → 3%
 *   salePrice > 10_000_000         → 2%
 * </pre>
 */
@DisplayName("SystemBank")
class SystemBankTest {

    private SystemBank bank;

    /**
     * Lấy singleton và reset totalBalance về 0 trước mỗi test.
     * Dùng reflection vì AtomicLong là private final.
     */
    @BeforeEach
    void setUp() throws Exception {
        bank = SystemBank.getInstance();
        TestFixture.resetSystemBankBalance();
    }

    // =========================================================================
    // Singleton contract
    // =========================================================================

    @Nested
    @DisplayName("Singleton contract")
    class SingletonTest {

        @Test
        @DisplayName("getInstance() luôn trả về cùng một instance")
        void getInstance_returnsSameInstance() {
            // Act
            SystemBank first  = SystemBank.getInstance();
            SystemBank second = SystemBank.getInstance();

            // Assert
            assertSame(first, second);
        }

        @Test
        @DisplayName("getInstance() không bao giờ trả về null")
        void getInstance_isNotNull() {
            // Assert
            assertNotNull(SystemBank.getInstance());
        }
    }

    // =========================================================================
    // calculateTax — tax tier routing
    // =========================================================================

    @Nested
    @DisplayName("calculateTax — tax tier routing")
    class CalculateTaxTierTest {

        @Test
        @DisplayName("giá trong tier thấp (< 1_000_000) áp dụng 5%")
        void calculateTax_lowTier_applies5Percent() {
            // Arrange
            long salePrice = 500_000L; // 500_000 * 0.05 = 25_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(25_000L, tax);
        }

        @Test
        @DisplayName("giá đúng tại ranh giới dưới của tier mid (= 1_000_000) áp dụng 3%")
        void calculateTax_exactLowerBoundaryOfMidTier_applies3Percent() {
            // Arrange
            long salePrice = 1_000_000L; // 1_000_000 * 0.03 = 30_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(30_000L, tax);
        }

        @Test
        @DisplayName("giá một đơn vị dưới ranh giới tier mid (999_999) vẫn áp dụng 5%")
        void calculateTax_oneBelowMidTierBoundary_applies5Percent() {
            // Arrange
            long salePrice = 999_999L; // 999_999 * 0.05 = 49_999.95 → Math.round = 50_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(50_000L, tax);
        }

        @Test
        @DisplayName("giá một đơn vị trên ranh giới dưới tier mid (1_000_001) áp dụng 3%")
        void calculateTax_oneAboveLowerMidTierBoundary_applies3Percent() {
            // Arrange
            long salePrice = 1_000_001L; // 1_000_001 * 0.03 = 30_000.03 → Math.round = 30_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(30_000L, tax);
        }

        @Test
        @DisplayName("giá trong tier mid (5_000_000) áp dụng 3%")
        void calculateTax_midTierValue_applies3Percent() {
            // Arrange
            long salePrice = 5_000_000L; // 5_000_000 * 0.03 = 150_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(150_000L, tax);
        }

        @Test
        @DisplayName("giá đúng tại ranh giới trên của tier mid (= 10_000_000) áp dụng 3%")
        void calculateTax_exactUpperBoundaryOfMidTier_applies3Percent() {
            // Arrange
            long salePrice = 10_000_000L; // 10_000_000 * 0.03 = 300_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(300_000L, tax);
        }

        @Test
        @DisplayName("giá một đơn vị trên ranh giới tier cao (10_000_001) áp dụng 2%")
        void calculateTax_oneAboveUpperMidTierBoundary_applies2Percent() {
            // Arrange
            long salePrice = 10_000_001L; // 10_000_001 * 0.02 = 200_000.02 → Math.round = 200_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(200_000L, tax);
        }

        @Test
        @DisplayName("giá trong tier cao (100_000_000) áp dụng 2%")
        void calculateTax_highTierValue_applies2Percent() {
            // Arrange
            long salePrice = 100_000_000L; // 100_000_000 * 0.02 = 2_000_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(2_000_000L, tax);
        }
    }

    // =========================================================================
    // calculateTax — zero và negative
    // =========================================================================

    @Nested
    @DisplayName("calculateTax — zero và negative input")
    class CalculateTaxEdgeInputTest {

        @Test
        @DisplayName("giá = 0 → thuế = 0")
        void calculateTax_zeroPrice_returnsZero() {
            // Arrange
            long salePrice = 0L;

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(0L, tax);
        }

        @Test
        @DisplayName("giá = 1 (tối thiểu dương) → thuế = 0 (1 * 0.05 = 0.05, round xuống)")
        void calculateTax_minPositivePrice_returnsZero() {
            // Arrange
            long salePrice = 1L; // 1 * 0.05 = 0.05 → Math.round = 0

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(0L, tax);
        }

        @Test
        @DisplayName("giá âm → thuế âm (behavior theo Java Math.round)")
        void calculateTax_negativePrice_returnsNegativeTax() {
            // Arrange
            long salePrice = -1_000_000L; // -1_000_000 * 0.05 = -50_000.0 → Math.round = -50_000
            // Lưu ý: giá âm rơi vào nhánh < PRICE_TIER_LOW, áp dụng rate 5%

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(-50_000L, tax);
        }

        @Test
        @DisplayName("giá âm nhỏ (-1) → thuế = 0 (−1 * 0.05 = −0.05 → Math.round = 0)")
        void calculateTax_negativeOnePrice_returnsZero() {
            // Arrange
            long salePrice = -1L; // -1 * 0.05 = -0.05 → Math.floor(-0.05 + 0.5) = Math.floor(0.45) = 0

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(0L, tax);
        }
    }

    // =========================================================================
    // calculateTax — rounding behavior
    // =========================================================================

    @Nested
    @DisplayName("calculateTax — rounding behavior (Math.round half-up)")
    class CalculateTaxRoundingTest {

        @Test
        @DisplayName("100 * 5% = 5.0 → làm tròn chính xác không có phần thập phân")
        void calculateTax_exactMultiple_noRounding() {
            // Arrange
            long salePrice = 100L; // 100 * 0.05 = 5.0 → 5

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(5L, tax);
        }

        @Test
        @DisplayName("101 * 5% = 5.05 → làm tròn xuống thành 5")
        void calculateTax_roundDown_whenFractionBelowHalf() {
            // Arrange
            long salePrice = 101L; // 101 * 0.05 = 5.05 → Math.round = 5

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(5L, tax);
        }

        @Test
        @DisplayName("110 * 5% = 5.5 → Math.round làm tròn lên thành 6 (half-up)")
        void calculateTax_roundUp_whenFractionIsExactlyHalf() {
            // Arrange
            long salePrice = 110L; // 110 * 0.05 = 5.5 → Math.round (half-up) = 6

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(6L, tax);
        }

        @Test
        @DisplayName("1_000_033 * 3% = 30_000.99 → làm tròn lên thành 30_001")
        void calculateTax_midTier_roundUpWhenFractionNearOne() {
            // Arrange
            long salePrice = 1_000_033L; // 1_000_033 * 0.03 = 30_000.99 → Math.round = 30_001

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(30_001L, tax);
        }

        @Test
        @DisplayName("1_000_034 * 3% = 30_001.02 → làm tròn xuống thành 30_001")
        void calculateTax_midTier_roundDownWhenFractionNearZero() {
            // Arrange
            long salePrice = 1_000_034L; // 1_000_034 * 0.03 = 30_001.02 → Math.round = 30_001

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(30_001L, tax);
        }

        @Test
        @DisplayName("9_999_999 * 3% = 299_999.97 → làm tròn lên thành 300_000")
        void calculateTax_oneBeforeUpperMidBoundary_roundsCorrectly() {
            // Arrange
            long salePrice = 9_999_999L; // 9_999_999 * 0.03 = 299_999.97 → Math.round = 300_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(300_000L, tax);
        }

        @Test
        @DisplayName("10_000_025 * 2% = 200_000.5 → Math.round làm tròn lên thành 200_001 (half-up)")
        void calculateTax_highTier_roundUpWhenFractionIsExactlyHalf() {
            // Arrange
            long salePrice = 10_000_025L; // 10_000_025 * 0.02 = 200_000.5 → Math.round = 200_001

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(200_001L, tax);
        }

        @Test
        @DisplayName("103 * 5% = 5.15 → làm tròn xuống thành 5")
        void calculateTax_roundDown_whenFractionBelowHalfV2() {
            // Arrange
            long salePrice = 103L; // 103 * 0.05 = 5.15 → Math.round = 5

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(5L, tax);
        }
    }

    // =========================================================================
    // calculateTax — large value
    // =========================================================================

    @Nested
    @DisplayName("calculateTax — large value")
    class CalculateTaxLargeValueTest {

        @Test
        @DisplayName("1 tỷ VNĐ (1_000_000_000) trong tier cao áp dụng 2%")
        void calculateTax_oneBillion_applies2Percent() {
            // Arrange
            long salePrice = 1_000_000_000L; // 1_000_000_000 * 0.02 = 20_000_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(20_000_000L, tax);
        }

        @Test
        @DisplayName("1 nghìn tỷ (1_000_000_000_000) trong tier cao áp dụng 2%")
        void calculateTax_oneTrillion_applies2Percent() {
            // Arrange
            long salePrice = 1_000_000_000_000L; // * 0.02 = 20_000_000_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert
            assertEquals(20_000_000_000L, tax);
        }

        @Test
        @DisplayName("tax + payout luôn bằng salePrice (không mất tiền)")
        void calculateTax_taxPlusPayout_equalsSalePrice() {
            // Arrange
            long salePrice = 7_654_321L;

            // Act
            long tax    = bank.calculateTax(salePrice);
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert — tax và payout khớp với salePrice (sai lệch tối đa 1 do rounding)
            long sum = tax + payout;
            assertTrue(Math.abs(salePrice - sum) <= 1L,
                    "tax + payout phải bằng hoặc chênh tối đa 1 so với salePrice do rounding");
        }
    }

    // =========================================================================
    // calculateSellerPayout — happy path
    // =========================================================================

    @Nested
    @DisplayName("calculateSellerPayout — happy path")
    class CalculateSellerPayoutTest {

        @Test
        @DisplayName("giá tier thấp: payout = salePrice − 5% tax")
        void calculateSellerPayout_lowTier_correctPayout() {
            // Arrange
            long salePrice = 500_000L; // tax = 25_000, payout = 475_000

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(475_000L, payout);
        }

        @Test
        @DisplayName("giá tại ranh giới dưới tier mid (1_000_000): payout = 970_000")
        void calculateSellerPayout_atLowerMidBoundary_correctPayout() {
            // Arrange
            long salePrice = 1_000_000L; // tax = 30_000, payout = 970_000

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(970_000L, payout);
        }

        @Test
        @DisplayName("giá một đơn vị dưới ranh giới tier mid (999_999): payout = 949_999")
        void calculateSellerPayout_oneBelowMidBoundary_correctPayout() {
            // Arrange
            long salePrice = 999_999L; // tax = 50_000, payout = 949_999

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(949_999L, payout);
        }

        @Test
        @DisplayName("giá tier mid (5_000_000): payout = 4_850_000")
        void calculateSellerPayout_midTier_correctPayout() {
            // Arrange
            long salePrice = 5_000_000L; // tax = 150_000, payout = 4_850_000

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(4_850_000L, payout);
        }

        @Test
        @DisplayName("giá tại ranh giới trên tier mid (10_000_000): payout = 9_700_000")
        void calculateSellerPayout_atUpperMidBoundary_correctPayout() {
            // Arrange
            long salePrice = 10_000_000L; // tax = 300_000, payout = 9_700_000

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(9_700_000L, payout);
        }

        @Test
        @DisplayName("giá một đơn vị trên tier cao (10_000_001): payout = 9_800_001")
        void calculateSellerPayout_oneAboveUpperMidBoundary_correctPayout() {
            // Arrange
            long salePrice = 10_000_001L; // tax = 200_000, payout = 9_800_001

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(9_800_001L, payout);
        }

        @Test
        @DisplayName("giá tier cao (100_000_000): payout = 98_000_000")
        void calculateSellerPayout_highTier_correctPayout() {
            // Arrange
            long salePrice = 100_000_000L; // tax = 2_000_000, payout = 98_000_000

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(98_000_000L, payout);
        }

        @Test
        @DisplayName("giá = 0 → payout = 0")
        void calculateSellerPayout_zeroPrice_returnsZero() {
            // Arrange
            long salePrice = 0L;

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(0L, payout);
        }

        @Test
        @DisplayName("payout luôn nhỏ hơn salePrice (thuế dương với giá dương)")
        void calculateSellerPayout_alwaysLessThanSalePrice_forPositivePrice() {
            // Arrange
            long salePrice = 1_000_000L;

            // Act
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertTrue(payout < salePrice,
                    "payout phải nhỏ hơn salePrice vì thuế luôn dương với giá dương");
        }
    }

    // =========================================================================
    // calculateTax và calculateSellerPayout — consistency
    // =========================================================================

    @Nested
    @DisplayName("calculateTax & calculateSellerPayout — consistency")
    class TaxPayoutConsistencyTest {

        @Test
        @DisplayName("calculateTax + calculateSellerPayout = salePrice (tier thấp)")
        void taxPlusPayout_equalsSalePrice_lowTier() {
            // Arrange
            long salePrice = 800_000L;

            // Act
            long tax    = bank.calculateTax(salePrice);
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(salePrice, tax + payout);
        }

        @Test
        @DisplayName("calculateTax + calculateSellerPayout = salePrice (tier mid)")
        void taxPlusPayout_equalsSalePrice_midTier() {
            // Arrange
            long salePrice = 3_000_000L;

            // Act
            long tax    = bank.calculateTax(salePrice);
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(salePrice, tax + payout);
        }

        @Test
        @DisplayName("calculateTax + calculateSellerPayout = salePrice (tier cao)")
        void taxPlusPayout_equalsSalePrice_highTier() {
            // Arrange
            long salePrice = 50_000_000L;

            // Act
            long tax    = bank.calculateTax(salePrice);
            long payout = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(salePrice, tax + payout);
        }

        @Test
        @DisplayName("calculateTax + calculateSellerPayout = salePrice tại ranh giới 1_000_000")
        void taxPlusPayout_equalsSalePrice_atLowerMidBoundary() {
            // Arrange
            long salePrice = 1_000_000L;

            // Act & Assert
            assertEquals(salePrice,
                    bank.calculateTax(salePrice) + bank.calculateSellerPayout(salePrice));
        }

        @Test
        @DisplayName("calculateTax + calculateSellerPayout = salePrice tại ranh giới 10_000_000")
        void taxPlusPayout_equalsSalePrice_atUpperMidBoundary() {
            // Arrange
            long salePrice = 10_000_000L;

            // Act & Assert
            assertEquals(salePrice,
                    bank.calculateTax(salePrice) + bank.calculateSellerPayout(salePrice));
        }

        @Test
        @DisplayName("calculateSellerPayout bằng salePrice − calculateTax trên nhiều giá trị")
        void sellerPayout_equalsPrice_minusTax_forVariousPrices() {
            // Arrange
            long[] prices = {1L, 100L, 500_000L, 1_000_000L, 9_999_999L,
                    10_000_000L, 10_000_001L, 100_000_000L};

            // Act & Assert
            for (long price : prices) {
                long expectedPayout = price - bank.calculateTax(price);
                assertEquals(expectedPayout, bank.calculateSellerPayout(price),
                        "Failed for price=" + price);
            }
        }
    }

    // =========================================================================
    // receive — bank operations
    // =========================================================================

    @Nested
    @DisplayName("receive — nhận tiền vào bank")
    class ReceiveTest {

        @Test
        @DisplayName("receive() cộng amount vào totalBalance")
        void receive_addsAmountToBalance() {
            // Arrange
            long amount = 500_000L;

            // Act
            bank.receive(amount);

            // Assert
            assertEquals(500_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("receive() nhiều lần cộng dồn vào totalBalance")
        void receive_multipleCalls_accumulatesBalance() {
            // Arrange & Act
            bank.receive(100_000L);
            bank.receive(200_000L);
            bank.receive(300_000L);

            // Assert
            assertEquals(600_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("receive(0) không thay đổi totalBalance")
        void receive_zeroAmount_balanceUnchanged() {
            // Arrange
            bank.receive(1_000_000L);
            long balanceBefore = bank.getTotalBalance();

            // Act
            bank.receive(0L);

            // Assert
            assertEquals(balanceBefore, bank.getTotalBalance());
        }

        @Test
        @DisplayName("receive() với số lớn không bị overflow")
        void receive_largeAmount_noOverflow() {
            // Arrange
            long large = 1_000_000_000_000L; // 1 nghìn tỷ

            // Act
            bank.receive(large);

            // Assert
            assertEquals(large, bank.getTotalBalance());
        }
    }

    // =========================================================================
    // payoutToSeller — bank operations
    // =========================================================================

    @Nested
    @DisplayName("payoutToSeller — chuyển tiền cho seller")
    class PayoutToSellerTest {

        @Test
        @DisplayName("payoutToSeller() trả về đúng số tiền seller nhận (payout, không phải tax)")
        void payoutToSeller_returnsCorrectPayoutAmount() {
            // Arrange
            long salePrice = 1_000_000L; // tax = 30_000, payout = 970_000

            // Act
            long returned = bank.payoutToSeller(salePrice);

            // Assert
            assertEquals(970_000L, returned);
        }

        @Test
        @DisplayName("payoutToSeller() giảm totalBalance đúng bằng payout (tax được giữ lại)")
        void payoutToSeller_decreasesBalanceByPayout_keepsTax() {
            // Arrange
            long salePrice = 1_000_000L;
            // tax = 30_000, payout = 970_000
            // Trước: bank nhận đủ salePrice, sau: balance giảm đi payout, chỉ còn tax
            bank.receive(salePrice); // balance = 1_000_000

            // Act
            bank.payoutToSeller(salePrice);

            // Assert: balance = 1_000_000 - 970_000 = 30_000 (chỉ còn thuế)
            assertEquals(30_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("payoutToSeller() với giá tier thấp trả về salePrice − 5% tax")
        void payoutToSeller_lowTier_returnsCorrectAmount() {
            // Arrange
            long salePrice = 500_000L; // tax = 25_000, payout = 475_000

            // Act
            long payout = bank.payoutToSeller(salePrice);

            // Assert
            assertEquals(475_000L, payout);
        }

        @Test
        @DisplayName("payoutToSeller() với giá tier cao trả về salePrice − 2% tax")
        void payoutToSeller_highTier_returnsCorrectAmount() {
            // Arrange
            long salePrice = 100_000_000L; // tax = 2_000_000, payout = 98_000_000

            // Act
            long payout = bank.payoutToSeller(salePrice);

            // Assert
            assertEquals(98_000_000L, payout);
        }

        @Test
        @DisplayName("payoutToSeller() nhất quán với calculateSellerPayout()")
        void payoutToSeller_consistentWithCalculateSellerPayout() {
            // Arrange
            long salePrice = 7_777_777L;

            // Act
            long fromPayoutMethod      = bank.payoutToSeller(salePrice);
            long fromCalculateMethod   = bank.calculateSellerPayout(salePrice);

            // Assert
            assertEquals(fromCalculateMethod, fromPayoutMethod);
        }
    }

    // =========================================================================
    // refundToWinner — hoàn tiền
    // =========================================================================

    @Nested
    @DisplayName("refundToWinner — hoàn tiền cho winner")
    class RefundToWinnerTest {

        @Test
        @DisplayName("refundToWinner() giảm totalBalance đúng bằng amount")
        void refundToWinner_decreasesBalanceByAmount() {
            // Arrange
            bank.receive(1_000_000L); // balance = 1_000_000

            // Act
            bank.refundToWinner(500_000L);

            // Assert
            assertEquals(500_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("refundToWinner() có thể làm balance âm")
        void refundToWinner_canMakeBalanceNegative() {
            // Arrange — balance = 0

            // Act
            bank.refundToWinner(100_000L);

            // Assert
            assertEquals(-100_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("refundToWinner(0) không thay đổi totalBalance")
        void refundToWinner_zeroAmount_balanceUnchanged() {
            // Arrange
            bank.receive(500_000L);

            // Act
            bank.refundToWinner(0L);

            // Assert
            assertEquals(500_000L, bank.getTotalBalance());
        }
    }

    // =========================================================================
    // receiveForfeittedDeposit — tịch thu cọc
    // =========================================================================

    @Nested
    @DisplayName("receiveForfeittedDeposit — tịch thu tiền cọc")
    class ReceiveForfeittedDepositTest {

        @Test
        @DisplayName("receiveForfeittedDeposit() cộng depositAmount vào totalBalance")
        void receiveForfeittedDeposit_addsToBalance() {
            // Arrange
            long deposit = 300_000L;

            // Act
            bank.receiveForfeittedDeposit(deposit);

            // Assert
            assertEquals(300_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("receiveForfeittedDeposit() nhiều lần cộng dồn")
        void receiveForfeittedDeposit_multipleCalls_accumulates() {
            // Arrange & Act
            bank.receiveForfeittedDeposit(100_000L);
            bank.receiveForfeittedDeposit(200_000L);

            // Assert
            assertEquals(300_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("receiveForfeittedDeposit(0) không thay đổi balance")
        void receiveForfeittedDeposit_zeroAmount_balanceUnchanged() {
            // Arrange
            bank.receive(500_000L);

            // Act
            bank.receiveForfeittedDeposit(0L);

            // Assert
            assertEquals(500_000L, bank.getTotalBalance());
        }
    }

    // =========================================================================
    // getTotalBalance — state tracking
    // =========================================================================

    @Nested
    @DisplayName("getTotalBalance — theo dõi trạng thái balance")
    class GetTotalBalanceTest {

        @Test
        @DisplayName("balance ban đầu = 0 sau khi reset")
        void getTotalBalance_afterReset_returnsZero() {
            // Assert
            assertEquals(0L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("balance phản ánh đúng sau chuỗi receive → payoutToSeller → refund")
        void getTotalBalance_afterMixedOperations_reflectsCorrectState() {
            // Arrange
            // Scenario: winner trả 1_000_000, payout cho seller, sau đó refund một phần
            long salePrice = 1_000_000L; // tax = 30_000

            // Act
            bank.receive(salePrice);               // balance = 1_000_000
            bank.payoutToSeller(salePrice);        // balance = 1_000_000 - 970_000 = 30_000 (thuế)
            bank.receiveForfeittedDeposit(50_000L); // balance = 80_000

            // Assert
            assertEquals(80_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("balance phản ánh đúng sau receive + forfeit")
        void getTotalBalance_afterReceiveAndForfeit_correct() {
            // Arrange & Act
            bank.receive(500_000L);
            bank.receiveForfeittedDeposit(100_000L);

            // Assert
            assertEquals(600_000L, bank.getTotalBalance());
        }
    }

    // =========================================================================
    // Boundary value analysis — all three tax tier boundaries
    // =========================================================================

    @Nested
    @DisplayName("Boundary value analysis — all tier boundaries")
    class BoundaryValueAnalysisTest {

        @Test
        @DisplayName("999_998: tier thấp 5% → tax = 49_999 (rounding down)")
        void boundary_999998_lowTier() {
            // Arrange
            long salePrice = 999_998L; // 999_998 * 0.05 = 49_999.9 → Math.round = 50_000

            // Act
            long tax = bank.calculateTax(salePrice);

            // Assert — 999_998 * 0.05 = 49999.9 → Math.round(49999.9) = 50000
            assertEquals(50_000L, tax);
        }

        @Test
        @DisplayName("999_999: tier thấp 5% → tax = 50_000")
        void boundary_999999_lowTier() {
            // Arrange & Act
            long tax = bank.calculateTax(999_999L);

            // Assert — 999_999 * 0.05 = 49_999.95 → Math.round = 50_000
            assertEquals(50_000L, tax);
        }

        @Test
        @DisplayName("1_000_000: tier mid 3% → tax = 30_000 (KHÔNG phải 50_000)")
        void boundary_1000000_midTier_notLowTier() {
            // Arrange & Act
            long tax = bank.calculateTax(1_000_000L);

            // Assert — rate chuyển sang 3%, không phải 5%
            assertEquals(30_000L, tax);
            assertNotEquals(50_000L, tax); // khẳng định không dùng nhầm rate 5%
        }

        @Test
        @DisplayName("9_999_999: tier mid 3% → tax = 300_000")
        void boundary_9999999_midTier() {
            // Arrange & Act
            long tax = bank.calculateTax(9_999_999L);

            // Assert — 9_999_999 * 0.03 = 299_999.97 → Math.round = 300_000
            assertEquals(300_000L, tax);
        }

        @Test
        @DisplayName("10_000_000: tier mid 3% → tax = 300_000 (KHÔNG phải 200_000)")
        void boundary_10000000_midTier_notHighTier() {
            // Arrange & Act
            long tax = bank.calculateTax(10_000_000L);

            // Assert — rate vẫn là 3%, không phải 2%
            assertEquals(300_000L, tax);
            assertNotEquals(200_000L, tax); // khẳng định không dùng nhầm rate 2%
        }

        @Test
        @DisplayName("10_000_001: tier cao 2% → tax = 200_000 (KHÔNG phải 300_000)")
        void boundary_10000001_highTier_notMidTier() {
            // Arrange & Act
            long tax = bank.calculateTax(10_000_001L);

            // Assert — rate chuyển sang 2%
            assertEquals(200_000L, tax);
            assertNotEquals(300_000L, tax); // khẳng định không dùng nhầm rate 3%
        }

        @Test
        @DisplayName("tax rate giảm đột ngột khi qua ranh giới 1_000_000 (off-by-one guard)")
        void boundary_offByOne_taxDropsAtMidTierEntry() {
            // Arrange
            long justBelow = 999_999L;  // 5% rate
            long atBoundary = 1_000_000L; // 3% rate

            // Act
            long taxBelow = bank.calculateTax(justBelow);   // 50_000
            long taxAt    = bank.calculateTax(atBoundary);  // 30_000

            // Assert — tax phải giảm khi vào tier mới
            assertTrue(taxBelow > taxAt,
                    "Tax tại 999_999 (5%) phải lớn hơn tax tại 1_000_000 (3%)");
        }

        @Test
        @DisplayName("tax rate giảm đột ngột khi qua ranh giới 10_000_000 (off-by-one guard)")
        void boundary_offByOne_taxDropsAtHighTierEntry() {
            // Arrange
            long atMidTop   = 10_000_000L; // 3% → 300_000
            long atHighBase = 10_000_001L; // 2% → 200_000

            // Act
            long taxMid  = bank.calculateTax(atMidTop);
            long taxHigh = bank.calculateTax(atHighBase);

            // Assert — tax phải giảm đột ngột khi vào tier cao
            assertTrue(taxMid > taxHigh,
                    "Tax tại 10_000_000 (3%) phải lớn hơn tax tại 10_000_001 (2%)");
        }
    }
}