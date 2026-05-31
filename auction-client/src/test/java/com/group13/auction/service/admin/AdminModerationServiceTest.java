package com.group13.auction.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AdminModerationService}. */
class AdminModerationServiceTest {

  @BeforeEach
  void clearSession() {
    AppContext.getInstance().getSessionManager().clearSession();
  }

  @Test
  void currentUserIsAdminShouldReturnFalseWhenThereIsNoSession() {
    AdminModerationService service = new AdminModerationService();

    assertFalse(service.currentUserIsAdmin());
    assertFalse(service.currentUserIsMasterAdmin());
  }

  @Test
  void currentUserIsAdminShouldReturnFalseForBidderSession() {
    startBidderSession();
    AdminModerationService service = new AdminModerationService();

    assertFalse(service.currentUserIsAdmin());
    assertFalse(service.currentUserIsMasterAdmin());
  }

  @Test
  void currentUserIsAdminShouldReturnTrueForStaffAdminSession() {
    startStaffAdminSession();
    AdminModerationService service = new AdminModerationService();

    assertTrue(service.currentUserIsAdmin());
    assertFalse(service.currentUserIsMasterAdmin());
  }

  @Test
  void currentUserIsMasterAdminShouldReturnTrueForMasterAdminSession() {
    startMasterAdminSession();
    AdminModerationService service = new AdminModerationService();

    assertTrue(service.currentUserIsAdmin());
    assertTrue(service.currentUserIsMasterAdmin());
  }

  @Test
  void getCurrentAdminAccessLabelShouldReturnDeniedLabelWhenThereIsNoAdminSession() {
    AdminModerationService service = new AdminModerationService();

    assertEquals(
        "Bạn không có quyền truy cập khu vực Admin.", service.getCurrentAdminAccessLabel());

    startBidderSession();

    assertEquals(
        "Bạn không có quyền truy cập khu vực Admin.", service.getCurrentAdminAccessLabel());
  }

  @Test
  void getCurrentAdminAccessLabelShouldReturnStaffAdminLabel() {
    startStaffAdminSession();
    AdminModerationService service = new AdminModerationService();

    assertEquals("Tài khoản hiện tại có quyền Staff Admin.", service.getCurrentAdminAccessLabel());
  }

  @Test
  void getCurrentAdminAccessLabelShouldReturnSystemAdminLabel() {
    startMasterAdminSession();
    AdminModerationService service = new AdminModerationService();

    assertEquals("Tài khoản hiện tại có quyền System Admin.", service.getCurrentAdminAccessLabel());
  }

  @Test
  void getAccessDeniedMessageShouldReturnStableCopy() {
    AdminModerationService service = new AdminModerationService();

    assertEquals(
        "You do not have permission to access the Admin area.", service.getAccessDeniedMessage());
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

  private static void startMasterAdminSession() {
    AppContext.getInstance()
        .getSessionManager()
        .startSession(
            UserSession.of(
                "token",
                "MASTER-1",
                "master01",
                "master01@example.com",
                List.of("ADMIN"),
                "ACTIVE",
                "MASTER"));
  }
}
