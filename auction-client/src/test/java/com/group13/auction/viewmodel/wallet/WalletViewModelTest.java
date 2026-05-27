package com.group13.auction.viewmodel.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link WalletViewModel}. */
class WalletViewModelTest {

  @Test
  void gettersShouldReturnWalletAmountsAndDisplayTexts() {
    WalletViewModel viewModel =
        new WalletViewModel(
            10_000_000L, 2_000_000L, 8_000_000L, "10.000.000 ₫", "2.000.000 ₫", "8.000.000 ₫");

    assertEquals(10_000_000L, viewModel.balance());
    assertEquals(2_000_000L, viewModel.lockedDeposit());
    assertEquals(8_000_000L, viewModel.availableBalance());
    assertEquals("10.000.000 ₫", viewModel.balanceText());
    assertEquals("2.000.000 ₫", viewModel.lockedDepositText());
    assertEquals("8.000.000 ₫", viewModel.availableBalanceText());
  }
}
