package com.group13.auction.viewmodel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SellerApprovalViewModel}. */
class SellerApprovalViewModelTest {

  @Test
  void gettersShouldReturnSellerApprovalDisplayData() {
    SellerApprovalViewModel viewModel =
        new SellerApprovalViewModel(
            "U-1",
            "bidder01",
            "bidder01@example.com",
            "BIDDER",
            "4.2 / 5.0",
            "Đang chờ duyệt quyền Seller",
            true);

    assertEquals("U-1", viewModel.getUserId());
    assertEquals("bidder01", viewModel.getUsername());
    assertEquals("bidder01@example.com", viewModel.getEmail());
    assertEquals("BIDDER", viewModel.getRole());
    assertEquals("4.2 / 5.0", viewModel.getRatingText());
    assertEquals("Đang chờ duyệt quyền Seller", viewModel.getNote());
    assertTrue(viewModel.isApprovable());
  }

  @Test
  void approvableShouldReturnFalseWhenUserCannotBeApproved() {
    SellerApprovalViewModel viewModel =
        new SellerApprovalViewModel(
            "U-2",
            "seller01",
            "seller01@example.com",
            "SELLER",
            "1.9 / 5.0",
            "Người dùng đã có quyền Seller",
            false);

    assertFalse(viewModel.isApprovable());
    assertEquals("1.9 / 5.0", viewModel.getRatingText());
  }
}
