package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.viewmodel.wallet.WalletViewModel;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link WalletViewModelMapper}. */
class WalletViewModelMapperTest {

  @Test
  void toViewModelShouldReturnEmptyWalletWhenDtoIsNull() {
    WalletViewModel viewModel = WalletViewModelMapper.toViewModel(null);

    assertEquals(0L, viewModel.balance());
    assertEquals(0L, viewModel.lockedDeposit());
    assertEquals(0L, viewModel.availableBalance());
    assertEquals("--", viewModel.balanceText());
    assertEquals("--", viewModel.lockedDepositText());
    assertEquals("--", viewModel.availableBalanceText());
  }

  @Test
  void toViewModelShouldMapWalletAmounts() {
    PaymentDTOs.WalletBalanceResponseDTO dto =
        new PaymentDTOs.WalletBalanceResponseDTO(10_000_000L, 2_000_000L, 8_000_000L);

    WalletViewModel viewModel = WalletViewModelMapper.toViewModel(dto);

    assertEquals(10_000_000L, viewModel.balance());
    assertEquals(2_000_000L, viewModel.lockedDeposit());
    assertEquals(8_000_000L, viewModel.availableBalance());
  }

  @Test
  void toViewModelShouldFormatWalletAmountsAsVnd() {
    PaymentDTOs.WalletBalanceResponseDTO dto =
        new PaymentDTOs.WalletBalanceResponseDTO(10_000_000L, 2_000_000L, 8_000_000L);

    WalletViewModel viewModel = WalletViewModelMapper.toViewModel(dto);

    assertCurrencyTextContains(viewModel.balanceText(), "10.000.000");
    assertCurrencyTextContains(viewModel.lockedDepositText(), "2.000.000");
    assertCurrencyTextContains(viewModel.availableBalanceText(), "8.000.000");
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}
