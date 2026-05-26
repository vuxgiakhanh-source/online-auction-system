package com.group13.auction.network.client.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import org.junit.jupiter.api.Test;

/** Unit tests for support request packets created by {@link ClientRequestFactory}. */
class ClientRequestFactorySupportTest {

  @Test
  void adminUserModerationRequestsShouldCreateExpectedPackets() {
    AdminDTOs.AdminBanUserDTO banRequest = new AdminDTOs.AdminBanUserDTO();
    banRequest.setUserId("U-1");
    banRequest.setReason("FRAUD");

    Packet<AdminDTOs.AdminBanUserDTO> banPacket =
        ClientRequestFactory.adminBanUser(banRequest);
    Packet<String> unbanPacket = ClientRequestFactory.adminUnbanUser("U-1");
    Packet<Void> allUsersPacket = ClientRequestFactory.adminGetAllUsers();
    Packet<Void> accountBansPacket = ClientRequestFactory.adminGetAccountBans();

    assertPacketType(banPacket, PacketType.ADMIN_BAN_USER);
    assertSame(banRequest, banPacket.getPayload());

    assertPacketType(unbanPacket, PacketType.ADMIN_UNBAN_USER);
    assertEquals("U-1", unbanPacket.getPayload());

    assertPacketWithoutPayload(allUsersPacket, PacketType.ADMIN_GET_ALL_USERS);
    assertPacketWithoutPayload(accountBansPacket, PacketType.ADMIN_GET_ACCOUNT_BANS);
  }

  @Test
  void adminStaffRequestsShouldCreateExpectedPackets() {
    AdminDTOs.CreateStaffAdminDTO request = new AdminDTOs.CreateStaffAdminDTO();
    request.setUsername("staff01");
    request.setPassword("secret");
    request.setEmail("staff01@example.com");

    Packet<AdminDTOs.CreateStaffAdminDTO> createStaffPacket =
        ClientRequestFactory.adminCreateStaff(request);
    Packet<Void> allStaffPacket = ClientRequestFactory.adminGetAllStaff();

    assertPacketType(createStaffPacket, PacketType.ADMIN_CREATE_STAFF);
    assertSame(request, createStaffPacket.getPayload());

    assertPacketWithoutPayload(allStaffPacket, PacketType.ADMIN_GET_ALL_STAFF);
  }

  @Test
  void adminApproveSellerRoleShouldCreatePacketWithUserIdPayload() {
    Packet<String> packet = ClientRequestFactory.adminApproveSellerRole("U-1");

    assertPacketType(packet, PacketType.ADMIN_APPROVE_SELLER_ROLE);
    assertEquals("U-1", packet.getPayload());
  }

  @Test
  void ratingRequestsShouldCreateExpectedPackets() {
    RatingDTOs.RateSellerRequestDTO sellerRating = new RatingDTOs.RateSellerRequestDTO();
    sellerRating.setSellerId("SELLER-1");
    sellerRating.setAuctionId("A-1");
    sellerRating.setRating(4.5);
    sellerRating.setComment("Good seller.");

    RatingDTOs.RateBidderRequestDTO bidderRating = new RatingDTOs.RateBidderRequestDTO();
    bidderRating.setBidderId("BIDDER-1");
    bidderRating.setAuctionId("A-1");
    bidderRating.setRating(5.0);
    bidderRating.setComment("Fast payment.");

    Packet<RatingDTOs.RateSellerRequestDTO> sellerPacket =
        ClientRequestFactory.rateSeller(sellerRating);
    Packet<RatingDTOs.RateBidderRequestDTO> bidderPacket =
        ClientRequestFactory.rateBidder(bidderRating);
    Packet<String> historyPacket = ClientRequestFactory.getUserRatings("U-1");

    assertPacketType(sellerPacket, PacketType.RATE_SELLER);
    assertSame(sellerRating, sellerPacket.getPayload());

    assertPacketType(bidderPacket, PacketType.RATE_BIDDER);
    assertSame(bidderRating, bidderPacket.getPayload());

    assertPacketType(historyPacket, PacketType.GET_USER_RATINGS);
    assertEquals("U-1", historyPacket.getPayload());
  }

  @Test
  void qualityReportUserAndSellerRequestsShouldCreateExpectedPackets() {
    ReportDTOs.QualityReportRequestDTO request =
        new ReportDTOs.QualityReportRequestDTO();
    request.setAuctionId("A-1");
    request.setDescription("Sản phẩm không đúng mô tả.");

    Packet<ReportDTOs.QualityReportRequestDTO> submitPacket =
        ClientRequestFactory.submitQualityReport(request);
    Packet<Void> myReportsPacket = ClientRequestFactory.getMyQualityReports();
    Packet<Void> sellerReportsPacket = ClientRequestFactory.getSellerQualityReports();

    assertPacketType(submitPacket, PacketType.SUBMIT_QUALITY_REPORT);
    assertSame(request, submitPacket.getPayload());

    assertPacketWithoutPayload(myReportsPacket, PacketType.GET_MY_QUALITY_REPORTS);
    assertPacketWithoutPayload(sellerReportsPacket, PacketType.GET_SELLER_QUALITY_REPORTS);
  }

  @Test
  void qualityReportAdminRequestsShouldCreateExpectedPackets() {
    Packet<String> getReportsPacket = ClientRequestFactory.adminGetQualityReports("PENDING");
    Packet<String> approvePacket = ClientRequestFactory.adminApproveQualityReport("QR-1");
    Packet<String> rejectPacket = ClientRequestFactory.adminRejectQualityReport("QR-1");

    assertPacketType(getReportsPacket, PacketType.ADMIN_GET_QUALITY_REPORTS);
    assertEquals("PENDING", getReportsPacket.getPayload());

    assertPacketType(approvePacket, PacketType.ADMIN_APPROVE_QUALITY_REPORT);
    assertEquals("QR-1", approvePacket.getPayload());

    assertPacketType(rejectPacket, PacketType.ADMIN_REJECT_QUALITY_REPORT);
    assertEquals("QR-1", rejectPacket.getPayload());
  }

  @Test
  void notificationRequestsShouldCreateExpectedPackets() {
    Packet<Void> getNotificationsPacket = ClientRequestFactory.getNotifications();
    Packet<String> markReadPacket = ClientRequestFactory.markNotificationRead("N-1");

    assertPacketWithoutPayload(getNotificationsPacket, PacketType.GET_NOTIFICATIONS);

    assertPacketType(markReadPacket, PacketType.MARK_NOTIFICATION_READ);
    assertEquals("N-1", markReadPacket.getPayload());
  }

  @Test
  void pingShouldCreatePingPacketWithCurrentTimePayload() {
    long before = System.currentTimeMillis();

    Packet<Long> packet = ClientRequestFactory.ping();

    long after = System.currentTimeMillis();

    assertPacketType(packet, PacketType.PING);
    assertNotNull(packet.getPayload());
    assertTrue(packet.getPayload() >= before);
    assertTrue(packet.getPayload() <= after);
  }

  @Test
  void chatbotAskShouldWrapProvidedRequestPayload() {
    ChatbotDTOs.ChatbotAskRequestDTO request =
        ChatbotDTOs.ChatbotAskRequestDTO.byQuery("Làm sao để đặt giá?");

    Packet<ChatbotDTOs.ChatbotAskRequestDTO> packet =
        ClientRequestFactory.chatbotAsk(request);

    assertPacketType(packet, PacketType.CHATBOT_ASK);
    assertSame(request, packet.getPayload());
  }

  @Test
  void chatbotGetFaqListShouldWrapProvidedRequestPayload() {
    ChatbotDTOs.ChatbotFaqListRequestDTO request =
        new ChatbotDTOs.ChatbotFaqListRequestDTO("PAYMENT");

    Packet<ChatbotDTOs.ChatbotFaqListRequestDTO> packet =
        ClientRequestFactory.chatbotGetFaqList(request);

    assertPacketType(packet, PacketType.CHATBOT_GET_FAQ_LIST);
    assertSame(request, packet.getPayload());
  }

  private static void assertPacketType(Packet<?> packet, PacketType expectedType) {
    assertNotNull(packet);
    assertEquals(expectedType, packet.getType());
    assertTrue(packet.getTimestamp() > 0L);
  }

  private static void assertPacketWithoutPayload(Packet<?> packet, PacketType expectedType) {
    assertPacketType(packet, expectedType);
    assertNull(packet.getPayload());
  }
}