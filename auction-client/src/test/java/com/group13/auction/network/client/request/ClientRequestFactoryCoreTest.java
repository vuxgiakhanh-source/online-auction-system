package com.group13.auction.network.client.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.auth.RegisterRequestDTO;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.search.SearchDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import org.junit.jupiter.api.Test;

/** Unit tests for core request packets created by {@link ClientRequestFactory}. */
class ClientRequestFactoryCoreTest {

  @Test
  void loginShouldCreateLoginPacketWithCredentialsPayload() {
    Packet<LoginRequestDTO> packet = ClientRequestFactory.login("bidder01", "secret");

    assertPacketType(packet, PacketType.LOGIN);
    assertEquals("bidder01", packet.getPayload().getUsername());
    assertEquals("secret", packet.getPayload().getPassword());
  }

  @Test
  void registerShouldCreateRegisterPacketWithAccountPayload() {
    Packet<RegisterRequestDTO> packet =
        ClientRequestFactory.register("bidder01", "secret", "bidder01@example.com");

    assertPacketType(packet, PacketType.REGISTER);
    assertEquals("bidder01", packet.getPayload().getUsername());
    assertEquals("secret", packet.getPayload().getPassword());
    assertEquals("bidder01@example.com", packet.getPayload().getEmail());
  }

  @Test
  void logoutShouldCreateLogoutPacketWithoutPayload() {
    Packet<Void> packet = ClientRequestFactory.logout();

    assertPacketWithoutPayload(packet, PacketType.LOGOUT);
  }

  @Test
  void profileRequestsShouldCreateExpectedPackets() {
    Packet<Void> myProfilePacket = ClientRequestFactory.getMyProfile();
    Packet<String> userProfilePacket = ClientRequestFactory.getUserProfile("U-1");
    Packet<Void> sellerRolePacket = ClientRequestFactory.requestSellerRole();

    assertPacketWithoutPayload(myProfilePacket, PacketType.GET_MY_PROFILE);
    assertPacketType(userProfilePacket, PacketType.GET_USER_PROFILE);
    assertEquals("U-1", userProfilePacket.getPayload());
    assertPacketWithoutPayload(sellerRolePacket, PacketType.REQUEST_SELLER_ROLE);
  }

  @Test
  void walletRequestsShouldCreateExpectedPackets() {
    Packet<PaymentDTOs.DepositRequestDTO> depositPacket = ClientRequestFactory.deposit(1_000_000L);
    Packet<PaymentDTOs.WithdrawRequestDTO> withdrawPacket =
        ClientRequestFactory.withdraw(500_000L);
    Packet<Void> balancePacket = ClientRequestFactory.getWalletBalance();

    assertPacketType(depositPacket, PacketType.DEPOSIT);
    assertEquals(1_000_000L, depositPacket.getPayload().getAmount());

    assertPacketType(withdrawPacket, PacketType.WITHDRAW);
    assertEquals(500_000L, withdrawPacket.getPayload().getAmount());

    assertPacketWithoutPayload(balancePacket, PacketType.GET_WALLET_BALANCE);
  }

  @Test
  void paymentAndSecondChanceRequestsShouldCreateExpectedPackets() {
    Packet<PaymentDTOs.PaymentRequestDTO> paymentPacket =
        ClientRequestFactory.requestPayment("A-1");
    Packet<String> confirmPacket = ClientRequestFactory.confirmItemReceived("A-1");
    Packet<String> acceptSecondChancePacket = ClientRequestFactory.acceptSecondChance("A-1");
    Packet<String> declineSecondChancePacket = ClientRequestFactory.declineSecondChance("A-1");

    assertPacketType(paymentPacket, PacketType.PAYMENT_REQUEST);
    assertEquals("A-1", paymentPacket.getPayload().getAuctionId());

    assertPacketType(confirmPacket, PacketType.CONFIRM_ITEM_RECEIVED);
    assertEquals("A-1", confirmPacket.getPayload());

    assertPacketType(acceptSecondChancePacket, PacketType.SECOND_CHANCE_ACCEPT);
    assertEquals("A-1", acceptSecondChancePacket.getPayload());

    assertPacketType(declineSecondChancePacket, PacketType.SECOND_CHANCE_DECLINE);
    assertEquals("A-1", declineSecondChancePacket.getPayload());
  }

  @Test
  void createAuctionShouldWrapProvidedRequestPayload() {
    AuctionDTOs.CreateAuctionRequestDTO request = new AuctionDTOs.CreateAuctionRequestDTO();
    request.setItemName("Vintage Camera");

    Packet<AuctionDTOs.CreateAuctionRequestDTO> packet =
        ClientRequestFactory.createAuction(request);

    assertPacketType(packet, PacketType.CREATE_AUCTION);
    assertSame(request, packet.getPayload());
  }

  @Test
  void getAuctionListWithRequestShouldWrapProvidedRequestPayload() {
    AuctionDTOs.AuctionListRequestDTO request = new AuctionDTOs.AuctionListRequestDTO();
    request.setStatusFilter("RUNNING");
    request.setScopeFilter("JOINED");

    Packet<AuctionDTOs.AuctionListRequestDTO> packet =
        ClientRequestFactory.getAuctionList(request);

    assertPacketType(packet, PacketType.GET_AUCTION_LIST);
    assertSame(request, packet.getPayload());
  }

  @Test
  void getAuctionListWithoutRequestShouldCreatePacketWithoutPayload() {
    Packet<Void> packet = ClientRequestFactory.getAuctionList();

    assertPacketWithoutPayload(packet, PacketType.GET_AUCTION_LIST);
  }

  @Test
  void searchItemsShouldWrapProvidedSearchPayload() {
    SearchDTOs.ItemSearchRequestDTO request = new SearchDTOs.ItemSearchRequestDTO();
    request.setKeyword("camera");
    request.setScopeFilter("ALL");

    Packet<SearchDTOs.ItemSearchRequestDTO> packet = ClientRequestFactory.searchItems(request);

    assertPacketType(packet, PacketType.SEARCH_ITEMS);
    assertSame(request, packet.getPayload());
  }

  @Test
  void getAuctionDetailShouldCreatePacketWithAuctionIdPayload() {
    Packet<String> packet = ClientRequestFactory.getAuctionDetail("A-1");

    assertPacketType(packet, PacketType.GET_AUCTION_DETAIL);
    assertEquals("A-1", packet.getPayload());
  }

  @Test
  void updateAuctionShouldWrapProvidedUpdatePayload() {
    AuctionDTOs.UpdateAuctionDTO request = new AuctionDTOs.UpdateAuctionDTO();
    request.setAuctionId("A-1");

    Packet<AuctionDTOs.UpdateAuctionDTO> packet = ClientRequestFactory.updateAuction(request);

    assertPacketType(packet, PacketType.UPDATE_AUCTION);
    assertSame(request, packet.getPayload());
  }

  @Test
  void cancelAuctionRequestsShouldWrapProvidedPayloads() {
    AuctionDTOs.CancelAuctionRequestDTO sellerRequest =
        new AuctionDTOs.CancelAuctionRequestDTO();
    sellerRequest.setAuctionId("A-1");
    sellerRequest.setReason("Wrong schedule");

    AuctionDTOs.AdminCancelAuctionDTO adminRequest =
        new AuctionDTOs.AdminCancelAuctionDTO();
    adminRequest.setAuctionId("A-2");
    adminRequest.setReason("Policy violation");

    Packet<AuctionDTOs.CancelAuctionRequestDTO> sellerPacket =
        ClientRequestFactory.requestCancelAuction(sellerRequest);
    Packet<AuctionDTOs.AdminCancelAuctionDTO> adminPacket =
        ClientRequestFactory.adminCancelAuction(adminRequest);

    assertPacketType(sellerPacket, PacketType.CANCEL_AUCTION_REQUEST);
    assertSame(sellerRequest, sellerPacket.getPayload());

    assertPacketType(adminPacket, PacketType.ADMIN_CANCEL_AUCTION);
    assertSame(adminRequest, adminPacket.getPayload());
  }

  @Test
  void adminGetAllAuctionsShouldCreatePacketWithoutPayload() {
    Packet<Void> packet = ClientRequestFactory.adminGetAllAuctions();

    assertPacketWithoutPayload(packet, PacketType.ADMIN_GET_ALL_AUCTIONS);
  }

  @Test
  void auctionSessionRequestsShouldCreatePacketsWithAuctionIdPayload() {
    Packet<String> joinPacket = ClientRequestFactory.joinAuction("A-1");
    Packet<String> watchPacket = ClientRequestFactory.watchAuction("A-1");
    Packet<String> leavePacket = ClientRequestFactory.leaveAuction("A-1");

    assertPacketType(joinPacket, PacketType.JOIN_AUCTION);
    assertEquals("A-1", joinPacket.getPayload());

    assertPacketType(watchPacket, PacketType.WATCH_AUCTION);
    assertEquals("A-1", watchPacket.getPayload());

    assertPacketType(leavePacket, PacketType.LEAVE_AUCTION);
    assertEquals("A-1", leavePacket.getPayload());
  }

  @Test
  void placeBidShouldCreatePlaceBidPacketWithBidPayload() {
    Packet<BidDTOs.BidRequestDTO> packet = ClientRequestFactory.placeBid("A-1", 2_500_000L);

    assertPacketType(packet, PacketType.PLACE_BID);
    assertEquals("A-1", packet.getPayload().getAuctionId());
    assertEquals(2_500_000L, packet.getPayload().getAmount());
  }

  @Test
  void autoBidRequestsShouldCreateExpectedPackets() {
    Packet<BidDTOs.AutoBidRequestDTO> registerPacket =
        ClientRequestFactory.registerAutoBid("A-1", 5_000_000L);
    Packet<BidDTOs.AutoBidRequestDTO> updatePacket =
        ClientRequestFactory.updateAutoBid("A-1", 6_000_000L);
    Packet<String> cancelPacket = ClientRequestFactory.cancelAutoBid("A-1");
    Packet<String> statusPacket = ClientRequestFactory.getAutoBidStatus("A-1");

    assertPacketType(registerPacket, PacketType.REGISTER_AUTO_BID);
    assertEquals("A-1", registerPacket.getPayload().getAuctionId());
    assertEquals(5_000_000L, registerPacket.getPayload().getMaxBid());

    assertPacketType(updatePacket, PacketType.UPDATE_AUTO_BID);
    assertEquals("A-1", updatePacket.getPayload().getAuctionId());
    assertEquals(6_000_000L, updatePacket.getPayload().getMaxBid());

    assertPacketType(cancelPacket, PacketType.CANCEL_AUTO_BID);
    assertEquals("A-1", cancelPacket.getPayload());

    assertPacketType(statusPacket, PacketType.GET_AUTO_BID_STATUS);
    assertEquals("A-1", statusPacket.getPayload());
  }

  @Test
  void getBidHistoryShouldCreatePacketWithAuctionIdPayload() {
    Packet<String> packet = ClientRequestFactory.getBidHistory("A-1");

    assertPacketType(packet, PacketType.GET_BID_HISTORY);
    assertEquals("A-1", packet.getPayload());
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