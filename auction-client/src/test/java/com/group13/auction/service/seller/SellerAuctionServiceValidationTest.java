package com.group13.auction.service.seller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.viewmodel.seller.AuctionFormViewModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** Unit tests for validation branches in {@link SellerAuctionService}. */
class SellerAuctionServiceValidationTest {

  @Test
  void createAuctionShouldFailWhenUserIsNotLoggedIn() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), new SessionManager());

    assertFutureFailsWithMessage(
        service.createAuction(validForm()),
        "Người dùng chưa đăng nhập.");
  }

  @Test
  void createAuctionShouldFailWhenUserIsNotSeller() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), bidderSessionManager());

    assertFutureFailsWithMessage(
        service.createAuction(validForm()),
        "Tài khoản hiện tại chưa có quyền Seller.");
  }

  @Test
  void createAuctionShouldFailWhenFormIsNull() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), sellerSessionManager());

    assertFutureFailsWithMessage(
        service.createAuction(null),
        "form must not be null");
  }

  @Test
  void createAuctionShouldFailWhenFormValidationFails() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), sellerSessionManager());
    AuctionFormViewModel invalidForm =
        new AuctionFormViewModel(
            "   ",
            "Camera film cổ.",
            "ELECTRONICS",
            1_000_000D,
            1_500_000D,
            validStartTime(),
            validEndTime(),
            Map.of(),
            List.of());

    assertFutureFailsWithMessage(
        service.createAuction(invalidForm),
        "Tên sản phẩm không được để trống.");
  }

  @Test
  void updateOpenAuctionEndTimeShouldFailWhenUserIsNotSeller() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), bidderSessionManager());

    assertFutureFailsWithMessage(
        service.updateOpenAuctionEndTime("A-1", validEndTime()),
        "Tài khoản hiện tại chưa có quyền Seller.");
  }

  @Test
  void updateOpenAuctionEndTimeShouldFailWhenAuctionIdIsBlank() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), sellerSessionManager());

    assertFutureFailsWithMessage(
        service.updateOpenAuctionEndTime("   ", validEndTime()),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void updateOpenAuctionEndTimeShouldFailWhenNewEndTimeIsNull() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), sellerSessionManager());

    assertFutureFailsWithMessage(
        service.updateOpenAuctionEndTime("A-1", null),
        "Thời gian kết thúc mới không được để trống.");
  }

  @Test
  void requestCancelAuctionShouldFailWhenUserIsNotSeller() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), bidderSessionManager());

    assertFutureFailsWithMessage(
        service.requestCancelAuction("A-1", "Sai lịch đấu giá."),
        "Tài khoản hiện tại chưa có quyền Seller.");
  }

  @Test
  void requestCancelAuctionShouldFailWhenAuctionIdIsBlank() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), sellerSessionManager());

    assertFutureFailsWithMessage(
        service.requestCancelAuction("   ", "Sai lịch đấu giá."),
        "Thiếu mã phiên đấu giá.");
  }

  @Test
  void requestCancelAuctionShouldFailWhenReasonIsBlank() {
    SellerAuctionService service =
        new SellerAuctionService(ClientNetworkFacade.getDefault(), sellerSessionManager());

    assertFutureFailsWithMessage(
        service.requestCancelAuction("A-1", "   "),
        "Lý do hủy phiên không được để trống.");
  }

  private static AuctionFormViewModel validForm() {
    return new AuctionFormViewModel(
        "Vintage Camera",
        "Camera film cổ.",
        "ELECTRONICS",
        1_000_000D,
        1_500_000D,
        validStartTime(),
        validEndTime(),
        Map.of(),
        List.of());
  }

  private static SessionManager sellerSessionManager() {
    SessionManager sessionManager = new SessionManager();
    sessionManager.startSession(
        UserSession.of(
            "token",
            "SELLER-1",
            "seller01",
            "seller01@example.com",
            List.of("SELLER"),
            "ACTIVE"));
    return sessionManager;
  }

  private static SessionManager bidderSessionManager() {
    SessionManager sessionManager = new SessionManager();
    sessionManager.startSession(
        UserSession.of(
            "token",
            "BIDDER-1",
            "bidder01",
            "bidder01@example.com",
            List.of("BIDDER"),
            "ACTIVE"));
    return sessionManager;
  }

  private static LocalDateTime validStartTime() {
    return LocalDateTime.of(2026, 5, 26, 20, 0);
  }

  private static LocalDateTime validEndTime() {
    return LocalDateTime.of(2026, 5, 26, 21, 0);
  }

  private static void assertFutureFailsWithMessage(
      CompletableFuture<?> future, String expectedMessage) {
    CompletionException exception = assertThrows(CompletionException.class, future::join);

    assertInstanceOf(RuntimeException.class, exception.getCause());
    assertEquals(expectedMessage, exception.getCause().getMessage());
  }
}