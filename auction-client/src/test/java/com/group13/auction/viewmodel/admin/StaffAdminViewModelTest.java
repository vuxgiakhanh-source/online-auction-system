package com.group13.auction.viewmodel.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link StaffAdminViewModel}. */
class StaffAdminViewModelTest {

  @Test
  void gettersShouldReturnStaffAdminDisplayData() {
    StaffAdminViewModel viewModel =
        new StaffAdminViewModel(
            "A-ADMIN-1", "staff01", "staff01@example.com", "STAFF_ADMIN", "ACTIVE");

    assertEquals("A-ADMIN-1", viewModel.getAdminId());
    assertEquals("staff01", viewModel.getUsername());
    assertEquals("staff01@example.com", viewModel.getEmail());
    assertEquals("STAFF_ADMIN", viewModel.getAdminType());
    assertEquals("ACTIVE", viewModel.getStatus());
  }
}
