package com.group13.auction.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for validation and permission branches in {@link AdminUserService}. */
class AdminUserServiceValidationTest {

  @BeforeEach
  void clearSession() {
    AppContext.getInstance().getSessionManager().clearSession();
  }

  @Test
  void getAllUsersShouldFailWhenCurrentUserIsNotAdmin() {
    AdminUserService service = createService();

    assertFutureFailsWithMessage(service.getAllUsers(), "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void getAccountBansShouldFailWhenCurrentUserIsNotAdmin() {
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.getAccountBans(), "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void banUserShouldFailWhenCurrentUserIsNotAdmin() {
    startBidderSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.banUser("U-1", "FRAUD"), "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void banUserShouldFailWhenUserIdIsBlank() {
    startStaffAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(service.banUser("   ", "FRAUD"), "Thiếu mã người dùng cần ban.");
  }

  @Test
  void banUserShouldFailWhenReasonIsBlank() {
    startStaffAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.banUser("U-1", "   "), "Vui lòng chọn lý do ban tài khoản.");
  }

  @Test
  void unbanUserShouldFailWhenCurrentUserIsNotAdmin() {
    startBidderSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.unbanUser("U-1"), "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void unbanUserShouldFailWhenUserIdIsBlank() {
    startStaffAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(service.unbanUser("   "), "Thiếu mã người dùng cần mở khóa.");
  }

  @Test
  void getAllStaffAdminsShouldFailWhenCurrentUserIsNotMasterAdmin() {
    startStaffAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.getAllStaffAdmins(), "Tài khoản hiện tại không có quyền System Admin.");
  }

  @Test
  void createStaffAdminShouldFailWhenCurrentUserIsNotMasterAdmin() {
    startStaffAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.createStaffAdmin("staff01", "secret1", "staff01@example.com"),
        "Tài khoản hiện tại không có quyền System Admin.");
  }

  @Test
  void createStaffAdminShouldFailWhenUsernameIsBlank() {
    startMasterAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.createStaffAdmin("   ", "secret1", "staff01@example.com"),
        "Vui lòng nhập tên đăng nhập Staff Admin.");
  }

  @Test
  void createStaffAdminShouldFailWhenPasswordIsBlank() {
    startMasterAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.createStaffAdmin("staff01", "   ", "staff01@example.com"),
        "Vui lòng nhập mật khẩu Staff Admin.");
  }

  @Test
  void createStaffAdminShouldFailWhenEmailIsBlank() {
    startMasterAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.createStaffAdmin("staff01", "secret1", "   "), "Vui lòng nhập email Staff Admin.");
  }

  @Test
  void getSellerApprovalCandidatesShouldFailWhenCurrentUserIsNotAdmin() {
    startBidderSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.getSellerApprovalCandidates(), "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void approveSellerRoleShouldFailWhenCurrentUserIsNotAdmin() {
    startBidderSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.approveSellerRole("U-1"), "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void approveSellerRoleShouldFailWhenUserIdIsBlank() {
    startStaffAdminSession();
    AdminUserService service = createService();

    assertFutureFailsWithMessage(
        service.approveSellerRole("   "), "Thiếu mã người dùng cần duyệt Seller.");
  }

  private static AdminUserService createService() {
    return new AdminUserService(ClientNetworkFacade.getDefault());
  }

  private static void startBidderSession() {
    startSession(List.of("BIDDER"), null);
  }

  private static void startStaffAdminSession() {
    startSession(List.of("ADMIN"), "STAFF");
  }

  private static void startMasterAdminSession() {
    startSession(List.of("ADMIN"), "MASTER");
  }

  private static void startSession(List<String> roles, String adminType) {
    AppContext.getInstance()
        .getSessionManager()
        .startSession(
            UserSession.of(
                "token", "U-1", "user01", "user01@example.com", roles, "ACTIVE", adminType));
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}
