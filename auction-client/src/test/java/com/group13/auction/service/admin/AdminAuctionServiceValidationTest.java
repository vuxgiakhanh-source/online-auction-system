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

/** Unit tests for validation and permission branches in {@link AdminAuctionService}. */
class AdminAuctionServiceValidationTest {

  @BeforeEach
  void clearSession() {
    AppContext.getInstance().getSessionManager().clearSession();
  }

  @Test
  void getAllAuctionsForAdminShouldFailWhenCurrentUserIsNotAdmin() {
    AdminAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.getAllAuctionsForAdmin(),
        "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void cancelAuctionAsAdminShouldFailWhenCurrentUserIsNotAdmin() {
    startBidderSession();
    AdminAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.cancelAuctionAsAdmin("A-1", "POLICY_VIOLATION"),
        "Tài khoản hiện tại không có quyền Admin.");
  }

  @Test
  void cancelAuctionAsAdminShouldFailWhenAuctionIdIsBlank() {
    startStaffAdminSession();
    AdminAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.cancelAuctionAsAdmin("   ", "POLICY_VIOLATION"),
        "Thiếu mã phiên đấu giá cần hủy.");
  }

  @Test
  void cancelAuctionAsAdminShouldFailWhenReasonIsBlank() {
    startStaffAdminSession();
    AdminAuctionService service = createService();

    assertFutureFailsWithMessage(
        service.cancelAuctionAsAdmin("A-1", "   "),
        "Vui lòng chọn lý do hủy phiên đấu giá.");
  }

  private static AdminAuctionService createService() {
    return new AdminAuctionService(ClientNetworkFacade.getDefault());
  }

  private static void startBidderSession() {
    AppContext.getInstance()
        .getSessionManager()
        .startSession(
            UserSession.of(
                "token",
                "BIDDER-1",
                "bidder01",
                "bidder01@example.com",
                List.of("BIDDER"),
                "ACTIVE"));
  }

  private static void startStaffAdminSession() {
    AppContext.getInstance()
        .getSessionManager()
        .startSession(
            UserSession.of(
                "token",
                "ADMIN-1",
                "staff01",
                "staff01@example.com",
                List.of("ADMIN"),
                "ACTIVE",
                "STAFF"));
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}