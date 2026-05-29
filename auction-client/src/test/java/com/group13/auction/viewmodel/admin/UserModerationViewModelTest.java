package com.group13.auction.viewmodel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserModerationViewModel}. */
class UserModerationViewModelTest {

  @Test
  void gettersShouldReturnBannedUserModerationDisplayData() {
    UserModerationViewModel viewModel =
        new UserModerationViewModel(
            "U-1",
            "bidder01",
            "bidder01@example.com",
            "BIDDER",
            "BANNED",
            "1.8 / 5.0",
            true,
            "Spam bid nhiều lần",
            "admin01",
            "26/05/2026 20:30");

    assertEquals("U-1", viewModel.getUserId());
    assertEquals("bidder01", viewModel.getUsername());
    assertEquals("bidder01@example.com", viewModel.getEmail());
    assertEquals("BIDDER", viewModel.getRole());
    assertEquals("BANNED", viewModel.getStatus());
    assertEquals("1.8 / 5.0", viewModel.getRatingText());
    assertTrue(viewModel.isBanned());
    assertEquals("Spam bid nhiều lần", viewModel.getBanReason());
    assertEquals("admin01", viewModel.getBannedBy());
    assertEquals("26/05/2026 20:30", viewModel.getBannedAt());
  }

  @Test
  void bannedShouldReturnFalseForActiveUser() {
    UserModerationViewModel viewModel =
        new UserModerationViewModel(
            "U-2",
            "seller01",
            "seller01@example.com",
            "SELLER",
            "ACTIVE",
            "4.9 / 5.0",
            false,
            "",
            "",
            "");

    assertFalse(viewModel.isBanned());
    assertEquals("4.9 / 5.0", viewModel.getRatingText());
    assertEquals("", viewModel.getBanReason());
    assertEquals("", viewModel.getBannedBy());
    assertEquals("", viewModel.getBannedAt());
  }
}
