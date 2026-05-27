package com.group13.auction.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.viewmodel.admin.AccountBanViewModel;
import com.group13.auction.viewmodel.admin.SellerApprovalViewModel;
import com.group13.auction.viewmodel.admin.StaffAdminViewModel;
import com.group13.auction.viewmodel.admin.UserModerationViewModel;
import com.group13.auction.viewmodel.profile.UserProfileViewModel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserViewModelMapper}. */
class UserViewModelMapperTest {

  @Test
  void toProfileViewModelShouldReturnEmptyProfileWhenDtoIsNull() {
    UserProfileViewModel viewModel = UserViewModelMapper.toProfileViewModel(null);

    assertEquals("--", viewModel.userId());
    assertEquals("--", viewModel.username());
    assertEquals("--", viewModel.email());
    assertEquals("--", viewModel.rolesText());
    assertEquals("--", viewModel.primaryRoleText());
    assertEquals("--", viewModel.accountStatusText());
    assertFalse(viewModel.bidder());
    assertFalse(viewModel.seller());
    assertFalse(viewModel.admin());
    assertFalse(viewModel.canRequestSellerRole());
    assertFalse(viewModel.penalized());
  }

  @Test
  void toProfileViewModelShouldMapBidderSellerProfile() {
    UserDTO dto = createUser("U-1", "bidder01", "bidder01@example.com");
    dto.setRoles(List.of("BIDDER_SELLER"));
    dto.setAccountStatus("ACTIVE");
    dto.setRating(4.8D);
    dto.setBalance(10_000_000L);
    dto.setLockedDeposit(2_000_000L);
    dto.setAvailableBalance(8_000_000L);
    dto.setCreatedAt(LocalDateTime.of(2026, 5, 1, 8, 0));
    dto.setUpdatedAt(LocalDateTime.of(2026, 5, 26, 20, 0));
    dto.setTimesRestored(1);

    UserProfileViewModel viewModel = UserViewModelMapper.toProfileViewModel(dto);

    assertEquals("U-1", viewModel.userId());
    assertEquals("bidder01", viewModel.username());
    assertEquals("bidder01@example.com", viewModel.email());
    assertEquals("BIDDER_SELLER", viewModel.rolesText());
    assertEquals("Bidder / Seller", viewModel.primaryRoleText());
    assertEquals("Đang hoạt động", viewModel.accountStatusText());
    assertEquals("4.8 / 5.0", viewModel.ratingText());
    assertCurrencyTextContains(viewModel.balanceText(), "10.000.000");
    assertCurrencyTextContains(viewModel.lockedDepositText(), "2.000.000");
    assertCurrencyTextContains(viewModel.availableBalanceText(), "8.000.000");
    assertEquals("01/05/2026 08:00", viewModel.createdAtText());
    assertEquals("26/05/2026 20:00", viewModel.updatedAtText());
    assertEquals("1 lần", viewModel.restoredText());
    assertTrue(viewModel.bidder());
    assertTrue(viewModel.seller());
    assertFalse(viewModel.admin());
    assertFalse(viewModel.canRequestSellerRole());
    assertFalse(viewModel.penalized());
  }

  @Test
  void toProfileViewModelShouldAllowActiveNonSellerBidderToRequestSellerRole() {
    UserDTO dto = createUser("U-2", "bidder02", "bidder02@example.com");
    dto.setRoles(List.of("BIDDER"));
    dto.setAccountStatus("ACTIVE");

    UserProfileViewModel viewModel = UserViewModelMapper.toProfileViewModel(dto);

    assertTrue(viewModel.bidder());
    assertFalse(viewModel.seller());
    assertFalse(viewModel.admin());
    assertTrue(viewModel.canRequestSellerRole());
  }

  @Test
  void toProfileViewModelShouldMapAdminTypeAsAdminProfile() {
    UserDTO dto = createUser("U-3", "staff01", "staff01@example.com");
    dto.setRoles(List.of("ADMIN"));
    dto.setAdminType("STAFF");
    dto.setAccountStatus("SUSPENDED");

    UserProfileViewModel viewModel = UserViewModelMapper.toProfileViewModel(dto);

    assertTrue(viewModel.admin());
    assertEquals("Admin STAFF", viewModel.primaryRoleText());
    assertEquals("Tạm khóa", viewModel.accountStatusText());
    assertFalse(viewModel.canRequestSellerRole());
  }

  @Test
  void toModerationViewModelShouldReturnEmptyRowWhenDtoIsNull() {
    UserModerationViewModel viewModel = UserViewModelMapper.toModerationViewModel(null);

    assertEquals("--", viewModel.getUserId());
    assertEquals("--", viewModel.getUsername());
    assertEquals("--", viewModel.getEmail());
    assertEquals("--", viewModel.getRole());
    assertEquals("--", viewModel.getStatus());
    assertFalse(viewModel.isBanned());
    assertEquals("--", viewModel.getBanReason());
    assertEquals("--", viewModel.getBannedBy());
    assertEquals("--", viewModel.getBannedAt());
  }

  @Test
  void toModerationViewModelShouldMapBannedUserWithReasonText() {
    UserDTO dto = createUser("U-4", "baduser", "bad@example.com");
    dto.setRoles(List.of("BIDDER"));
    dto.setAccountStatus("BANNED");
    dto.setActiveBanReason("FRAUD");
    dto.setBannedByUsername("admin01");
    dto.setBannedAt(LocalDateTime.of(2026, 5, 26, 20, 30));

    UserModerationViewModel viewModel = UserViewModelMapper.toModerationViewModel(dto);

    assertEquals("U-4", viewModel.getUserId());
    assertEquals("baduser", viewModel.getUsername());
    assertEquals("bad@example.com", viewModel.getEmail());
    assertEquals("BIDDER", viewModel.getRole());
    assertEquals("Bị cấm", viewModel.getStatus());
    assertTrue(viewModel.isBanned());
    assertEquals("Gian lận", viewModel.getBanReason());
    assertEquals("admin01", viewModel.getBannedBy());
    assertEquals("26/05/2026 20:30", viewModel.getBannedAt());
  }

  @Test
  void toModerationViewModelsShouldReturnEmptyListWhenInputIsNull() {
    assertTrue(UserViewModelMapper.toModerationViewModels((UserDTO[]) null).isEmpty());
    assertTrue(UserViewModelMapper.toModerationViewModels((List<UserDTO>) null).isEmpty());
  }

  @Test
  void toModerationViewModelsShouldMapArrayAndListInOrder() {
    UserDTO first = createUser("U-1", "user01", "user01@example.com");
    UserDTO second = createUser("U-2", "user02", "user02@example.com");

    assertEquals(
        List.of("U-1", "U-2"),
        UserViewModelMapper.toModerationViewModels(new UserDTO[] {first, second}).stream()
            .map(UserModerationViewModel::getUserId)
            .toList());

    assertEquals(
        List.of("U-1", "U-2"),
        UserViewModelMapper.toModerationViewModels(List.of(first, second)).stream()
            .map(UserModerationViewModel::getUserId)
            .toList());
  }

  @Test
  void toAccountBanViewModelShouldReturnEmptyRowWhenDtoIsNull() {
    AccountBanViewModel viewModel = UserViewModelMapper.toAccountBanViewModel(null);

    assertEquals("--", viewModel.getUserId());
    assertEquals("--", viewModel.getUsername());
    assertEquals("--", viewModel.getEmail());
    assertEquals("--", viewModel.getReason());
    assertEquals("--", viewModel.getBannedBy());
    assertEquals("--", viewModel.getBannedAt());
  }

  @Test
  void toAccountBanViewModelShouldMapBanRecord() {
    AdminDTOs.AccountBanDTO dto = new AdminDTOs.AccountBanDTO();
    dto.setUserId("U-1");
    dto.setUsername("bidder01");
    dto.setEmail("bidder01@example.com");
    dto.setReason("LOW_RATING");
    dto.setBannedByUsername("admin01");
    dto.setBannedAt(LocalDateTime.of(2026, 5, 26, 20, 30));

    AccountBanViewModel viewModel = UserViewModelMapper.toAccountBanViewModel(dto);

    assertEquals("U-1", viewModel.getUserId());
    assertEquals("bidder01", viewModel.getUsername());
    assertEquals("bidder01@example.com", viewModel.getEmail());
    assertEquals("Rating thấp", viewModel.getReason());
    assertEquals("admin01", viewModel.getBannedBy());
    assertEquals("26/05/2026 20:30", viewModel.getBannedAt());
  }

  @Test
  void toAccountBanViewModelsShouldReturnEmptyListWhenInputIsNull() {
    assertTrue(UserViewModelMapper.toAccountBanViewModels(null).isEmpty());
  }

  @Test
  void toStaffAdminViewModelShouldMapStaffAdmin() {
    UserDTO dto = createUser("ADMIN-1", "staff01", "staff01@example.com");
    dto.setAdminType("STAFF");
    dto.setAccountStatus("ACTIVE");

    StaffAdminViewModel viewModel = UserViewModelMapper.toStaffAdminViewModel(dto);

    assertEquals("ADMIN-1", viewModel.getAdminId());
    assertEquals("staff01", viewModel.getUsername());
    assertEquals("staff01@example.com", viewModel.getEmail());
    assertEquals("STAFF", viewModel.getAdminType());
    assertEquals("Đang hoạt động", viewModel.getStatus());
  }

  @Test
  void toStaffAdminViewModelsShouldReturnEmptyListWhenInputIsNull() {
    assertTrue(UserViewModelMapper.toStaffAdminViewModels(null).isEmpty());
  }

  @Test
  void toSellerApprovalViewModelShouldApproveActiveNonSellerUser() {
    UserDTO dto = createUser("U-1", "bidder01", "bidder01@example.com");
    dto.setRoles(List.of("BIDDER"));
    dto.setAccountStatus("ACTIVE");

    SellerApprovalViewModel viewModel = UserViewModelMapper.toSellerApprovalViewModel(dto);

    assertEquals("U-1", viewModel.getUserId());
    assertEquals("bidder01", viewModel.getUsername());
    assertEquals("bidder01@example.com", viewModel.getEmail());
    assertEquals("BIDDER", viewModel.getRole());
    assertEquals("Có thể duyệt quyền Seller bằng API hiện có.", viewModel.getNote());
    assertTrue(viewModel.isApprovable());
  }

  @Test
  void toSellerApprovalViewModelShouldRejectSellerAdminInactiveAndPenalizedUser() {
    UserDTO seller = createUser("U-1", "seller01", "seller01@example.com");
    seller.setRoles(List.of("SELLER"));
    seller.setAccountStatus("ACTIVE");

    UserDTO admin = createUser("U-2", "admin01", "admin01@example.com");
    admin.setRoles(List.of("ADMIN"));
    admin.setAccountStatus("ACTIVE");

    UserDTO inactive = createUser("U-3", "inactive01", "inactive01@example.com");
    inactive.setRoles(List.of("BIDDER"));
    inactive.setAccountStatus("SUSPENDED");

    UserDTO penalized = createUser("U-4", "penalized01", "penalized01@example.com");
    penalized.setRoles(List.of("BIDDER"));
    penalized.setAccountStatus("ACTIVE");
    penalized.setHasEverBeenPenalized(true);

    assertFalse(UserViewModelMapper.toSellerApprovalViewModel(seller).isApprovable());
    assertEquals(
        "Người dùng đã có quyền Seller.",
        UserViewModelMapper.toSellerApprovalViewModel(seller).getNote());

    assertFalse(UserViewModelMapper.toSellerApprovalViewModel(admin).isApprovable());
    assertEquals(
        "Tài khoản Admin không cần duyệt Seller.",
        UserViewModelMapper.toSellerApprovalViewModel(admin).getNote());

    assertFalse(UserViewModelMapper.toSellerApprovalViewModel(inactive).isApprovable());
    assertEquals(
        "Chỉ tài khoản ACTIVE mới có thể duyệt Seller.",
        UserViewModelMapper.toSellerApprovalViewModel(inactive).getNote());

    assertFalse(UserViewModelMapper.toSellerApprovalViewModel(penalized).isApprovable());
    assertEquals(
        "Tài khoản từng bị phạt, không auto-approve Seller.",
        UserViewModelMapper.toSellerApprovalViewModel(penalized).getNote());
  }

  @Test
  void toSellerApprovalViewModelsShouldFilterOnlyApprovableUsers() {
    UserDTO approvable = createUser("U-1", "bidder01", "bidder01@example.com");
    approvable.setRoles(List.of("BIDDER"));
    approvable.setAccountStatus("ACTIVE");

    UserDTO seller = createUser("U-2", "seller01", "seller01@example.com");
    seller.setRoles(List.of("SELLER"));
    seller.setAccountStatus("ACTIVE");

    List<SellerApprovalViewModel> viewModels =
        UserViewModelMapper.toSellerApprovalViewModels(new UserDTO[] {approvable, seller});

    assertEquals(1, viewModels.size());
    assertEquals("U-1", viewModels.get(0).getUserId());
  }

  private static UserDTO createUser(String id, String username, String email) {
    UserDTO dto = new UserDTO();
    dto.setId(id);
    dto.setUsername(username);
    dto.setEmail(email);
    dto.setRoles(List.of("BIDDER"));
    dto.setAccountStatus("ACTIVE");
    return dto;
  }

  private static void assertCurrencyTextContains(String actual, String expectedAmount) {
    String normalized = actual.replace('\u00A0', ' ').replace('\u202F', ' ');

    assertTrue(normalized.contains(expectedAmount));
    assertTrue(normalized.contains("₫"));
  }
}
