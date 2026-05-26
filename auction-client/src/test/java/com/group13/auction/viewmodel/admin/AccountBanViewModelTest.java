package com.group13.auction.viewmodel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AccountBanViewModel}. */
class AccountBanViewModelTest {

  @Test
  void gettersShouldReturnAccountBanDisplayData() {
    AccountBanViewModel viewModel =
        new AccountBanViewModel(
            "U-1",
            "bidder01",
            "bidder01@example.com",
            "Spam bid nhiều lần",
            "admin01",
            "26/05/2026 20:30");

    assertEquals("U-1", viewModel.getUserId());
    assertEquals("bidder01", viewModel.getUsername());
    assertEquals("bidder01@example.com", viewModel.getEmail());
    assertEquals("Spam bid nhiều lần", viewModel.getReason());
    assertEquals("admin01", viewModel.getBannedBy());
    assertEquals("26/05/2026 20:30", viewModel.getBannedAt());
  }
}