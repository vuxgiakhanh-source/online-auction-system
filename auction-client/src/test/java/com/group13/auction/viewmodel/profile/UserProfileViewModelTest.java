package com.group13.auction.viewmodel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserProfileViewModel}. */
class UserProfileViewModelTest {

  @Test
  void gettersShouldReturnProfileDisplayData() {
    UserProfileViewModel viewModel =
        new UserProfileViewModel(
            "U-1",
            "bidder01",
            "bidder01@example.com",
            "Bidder, Seller",
            "Seller",
            "Đang hoạt động",
            "4.8/5",
            "10.000.000 ₫",
            "2.000.000 ₫",
            "8.000.000 ₫",
            "01/05/2026 08:00",
            "26/05/2026 20:00",
            "0 lần",
            true,
            true,
            false,
            false,
            false);

    assertEquals("U-1", viewModel.userId());
    assertEquals("bidder01", viewModel.username());
    assertEquals("bidder01@example.com", viewModel.email());
    assertEquals("Bidder, Seller", viewModel.rolesText());
    assertEquals("Seller", viewModel.primaryRoleText());
    assertEquals("Đang hoạt động", viewModel.accountStatusText());
    assertEquals("4.8/5", viewModel.ratingText());
    assertEquals("10.000.000 ₫", viewModel.balanceText());
    assertEquals("2.000.000 ₫", viewModel.lockedDepositText());
    assertEquals("8.000.000 ₫", viewModel.availableBalanceText());
    assertEquals("01/05/2026 08:00", viewModel.createdAtText());
    assertEquals("26/05/2026 20:00", viewModel.updatedAtText());
    assertEquals("0 lần", viewModel.restoredText());
    assertTrue(viewModel.bidder());
    assertTrue(viewModel.seller());
    assertFalse(viewModel.admin());
    assertFalse(viewModel.canRequestSellerRole());
    assertFalse(viewModel.penalized());
  }

  @Test
  void flagsShouldRepresentAdminAndPenaltyState() {
    UserProfileViewModel viewModel =
        new UserProfileViewModel(
            "U-2",
            "admin01",
            "admin01@example.com",
            "Admin",
            "Admin",
            "Bị hạn chế",
            "--",
            "0 ₫",
            "0 ₫",
            "0 ₫",
            "--",
            "--",
            "1 lần",
            false,
            false,
            true,
            false,
            true);

    assertFalse(viewModel.bidder());
    assertFalse(viewModel.seller());
    assertTrue(viewModel.admin());
    assertFalse(viewModel.canRequestSellerRole());
    assertTrue(viewModel.penalized());
  }

  @Test
  void canRequestSellerRoleShouldReturnTrueForEligibleBidder() {
    UserProfileViewModel viewModel =
        new UserProfileViewModel(
            "U-3",
            "bidder02",
            "bidder02@example.com",
            "Bidder",
            "Bidder",
            "Đang hoạt động",
            "--",
            "1.000.000 ₫",
            "0 ₫",
            "1.000.000 ₫",
            "--",
            "--",
            "0 lần",
            true,
            false,
            false,
            true,
            false);

    assertTrue(viewModel.canRequestSellerRole());
  }
}
