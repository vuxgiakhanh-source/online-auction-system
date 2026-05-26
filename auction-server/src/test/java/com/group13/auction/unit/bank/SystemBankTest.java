package com.group13.auction.unit.bank;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.unit.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link SystemBank} — thuế theo tier và thao tác số dư.
 */
@DisplayName("SystemBank")
class SystemBankTest {

    private SystemBank bank;

    @BeforeEach
    void setUp() throws Exception {
        bank = SystemBank.getInstance();
        TestFixture.resetSystemBankBalance();
    }

    @Test
    @DisplayName("getInstance() — singleton")
    void getInstance_sameInstance() {
        assertSame(SystemBank.getInstance(), bank);
    }

    @ParameterizedTest(name = "salePrice={0} → tax={1}")
    @CsvSource({
            "500000, 25000",
            "999999, 50000",
            "1000000, 30000",
            "5000000, 150000",
            "10000000, 300000",
            "10000001, 200000",
            "100000000, 2000000"
    })
    @DisplayName("calculateTax() — tier 5% / 3% / 2%")
    void calculateTax_tiers(long salePrice, long expectedTax) {
        assertEquals(expectedTax, bank.calculateTax(salePrice));
    }

    @Test
    @DisplayName("calculateTax(0) = 0")
    void calculateTax_zero() {
        assertEquals(0L, bank.calculateTax(0L));
    }

    @Test
    @DisplayName("tax + payout = salePrice")
    void taxPlusPayout_equalsSalePrice() {
        for (long price : new long[] {500_000L, 1_000_000L, 10_000_001L}) {
            long tax = bank.calculateTax(price);
            long payout = bank.calculateSellerPayout(price);
            assertEquals(price, tax + payout, "price=" + price);
        }
    }

    @Nested
    @DisplayName("Bank operations")
    class BankOperations {

        @Test
        @DisplayName("receive() cộng vào totalBalance")
        void receive_increasesBalance() {
            bank.receive(100_000L);
            assertEquals(100_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("payoutToSeller() trả payout và giữ thuế trong bank")
        void payoutToSeller_keepsTaxInBank() {
            bank.receive(1_000_000L);
            long payout = bank.payoutToSeller(500_000L);
            assertEquals(475_000L, payout);
            assertEquals(525_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("refundToWinner() giảm balance")
        void refundToWinner_decreasesBalance() {
            bank.receive(200_000L);
            bank.refundToWinner(50_000L);
            assertEquals(150_000L, bank.getTotalBalance());
        }

        @Test
        @DisplayName("receiveForfeittedDeposit() cộng cọc bị tịch thu")
        void receiveForfeittedDeposit_addsDeposit() {
            bank.receiveForfeittedDeposit(30_000L);
            assertEquals(30_000L, bank.getTotalBalance());
        }
    }
}
