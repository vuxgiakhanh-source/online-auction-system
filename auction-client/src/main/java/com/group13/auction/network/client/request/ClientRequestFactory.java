package com.group13.auction.network.client.request;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.auth.LoginRequestDTO;
import com.group13.auction.common.dto.auth.RegisterRequestDTO;
import com.group13.auction.common.dto.bank.SystemBankDTOs;
import com.group13.auction.common.dto.bid.BidDTOs;
import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.dto.search.SearchDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;

/**
 * Factory tạo packet request từ client gửi lên server.
 *
 * <p>Lớp này không tạo DTO/protocol mới. Toàn bộ payload đều dùng DTO từ {@code auction-common},
 * đúng vai trò dùng chung giữa client và server.
 */
public final class ClientRequestFactory {

  private ClientRequestFactory() {
    // Utility class.
  }

  // Auth

  public static Packet<LoginRequestDTO> login(String username, String password) {
    return Packet.of(PacketType.LOGIN, new LoginRequestDTO(username, password));
  }

  public static Packet<RegisterRequestDTO> register(
      String username, String password, String email) {
    return Packet.of(PacketType.REGISTER, new RegisterRequestDTO(username, password, email));
  }

  public static Packet<Void> logout() {
    return Packet.of(PacketType.LOGOUT);
  }

  // User / profile

  public static Packet<Void> getMyProfile() {
    return Packet.of(PacketType.GET_MY_PROFILE);
  }

  public static Packet<String> getUserProfile(String userId) {
    return Packet.of(PacketType.GET_USER_PROFILE, userId);
  }

  public static Packet<Void> requestSellerRole() {
    return Packet.of(PacketType.REQUEST_SELLER_ROLE);
  }

  // Wallet / payment

  public static Packet<PaymentDTOs.DepositRequestDTO> deposit(long amount) {
    PaymentDTOs.DepositRequestDTO request = new PaymentDTOs.DepositRequestDTO(amount);
    return Packet.of(PacketType.DEPOSIT, request);
  }

  public static Packet<PaymentDTOs.WithdrawRequestDTO> withdraw(long amount) {
    PaymentDTOs.WithdrawRequestDTO request = new PaymentDTOs.WithdrawRequestDTO(amount);
    return Packet.of(PacketType.WITHDRAW, request);
  }

  public static Packet<Void> getWalletBalance() {
    return Packet.of(PacketType.GET_WALLET_BALANCE);
  }

  public static Packet<PaymentDTOs.PaymentRequestDTO> requestPayment(String auctionId) {
    PaymentDTOs.PaymentRequestDTO request = new PaymentDTOs.PaymentRequestDTO(auctionId);
    return Packet.of(PacketType.PAYMENT_REQUEST, request);
  }

  public static Packet<String> confirmItemReceived(String auctionId) {
    return Packet.of(PacketType.CONFIRM_ITEM_RECEIVED, auctionId);
  }

  public static Packet<String> acceptSecondChance(String auctionId) {
    return Packet.of(PacketType.SECOND_CHANCE_ACCEPT, auctionId);
  }

  public static Packet<String> declineSecondChance(String auctionId) {
    return Packet.of(PacketType.SECOND_CHANCE_DECLINE, auctionId);
  }

  // Auction

  public static Packet<AuctionDTOs.CreateAuctionRequestDTO> createAuction(
      AuctionDTOs.CreateAuctionRequestDTO request) {
    return Packet.of(PacketType.CREATE_AUCTION, request);
  }

  public static Packet<AuctionDTOs.AuctionListRequestDTO> getAuctionList(
      AuctionDTOs.AuctionListRequestDTO request) {
    return Packet.of(PacketType.GET_AUCTION_LIST, request);
  }

  public static Packet<SearchDTOs.ItemSearchRequestDTO> searchItems(
      SearchDTOs.ItemSearchRequestDTO request) {
    return Packet.of(PacketType.SEARCH_ITEMS, request);
  }

  public static Packet<Void> getAuctionList() {
    return Packet.of(PacketType.GET_AUCTION_LIST);
  }

  public static Packet<String> getAuctionDetail(String auctionId) {
    return Packet.of(PacketType.GET_AUCTION_DETAIL, auctionId);
  }

  public static Packet<AuctionDTOs.UpdateAuctionDTO> updateAuction(
      AuctionDTOs.UpdateAuctionDTO request) {
    return Packet.of(PacketType.UPDATE_AUCTION, request);
  }

  public static Packet<AuctionDTOs.CancelAuctionRequestDTO> requestCancelAuction(
      AuctionDTOs.CancelAuctionRequestDTO request) {
    return Packet.of(PacketType.CANCEL_AUCTION_REQUEST, request);
  }

  public static Packet<AuctionDTOs.AdminCancelAuctionDTO> adminCancelAuction(
      AuctionDTOs.AdminCancelAuctionDTO request) {
    return Packet.of(PacketType.ADMIN_CANCEL_AUCTION, request);
  }

  public static Packet<Void> adminGetAllAuctions() {
    return Packet.of(PacketType.ADMIN_GET_ALL_AUCTIONS);
  }

  public static Packet<Void> adminGetSystemBankSummary() {
    return Packet.of(PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY);
  }

  public static Packet<SystemBankDTOs.FinancialTransactionListRequestDTO>
  adminGetFinancialTransactions(SystemBankDTOs.FinancialTransactionListRequestDTO request) {
    return Packet.of(PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS, request);
  }

  // Bidding / realtime auction session

  public static Packet<String> joinAuction(String auctionId) {
    return Packet.of(PacketType.JOIN_AUCTION, auctionId);
  }

  public static Packet<String> watchAuction(String auctionId) {
    return Packet.of(PacketType.WATCH_AUCTION, auctionId);
  }

  public static Packet<String> leaveAuction(String auctionId) {
    return Packet.of(PacketType.LEAVE_AUCTION, auctionId);
  }

  public static Packet<BidDTOs.BidRequestDTO> placeBid(String auctionId, long amount) {
    return Packet.of(PacketType.PLACE_BID, new BidDTOs.BidRequestDTO(auctionId, amount));
  }

  public static Packet<BidDTOs.AutoBidRequestDTO> registerAutoBid(String auctionId, long maxBid) {
    return Packet.of(
        PacketType.REGISTER_AUTO_BID, new BidDTOs.AutoBidRequestDTO(auctionId, maxBid));
  }

  public static Packet<BidDTOs.AutoBidRequestDTO> updateAutoBid(String auctionId, long maxBid) {
    return Packet.of(PacketType.UPDATE_AUTO_BID, new BidDTOs.AutoBidRequestDTO(auctionId, maxBid));
  }

  public static Packet<String> cancelAutoBid(String auctionId) {
    return Packet.of(PacketType.CANCEL_AUTO_BID, auctionId);
  }

  public static Packet<String> getAutoBidStatus(String auctionId) {
    return Packet.of(PacketType.GET_AUTO_BID_STATUS, auctionId);
  }

  public static Packet<String> getBidHistory(String auctionId) {
    return Packet.of(PacketType.GET_BID_HISTORY, auctionId);
  }

  // Admin / moderation

  public static Packet<AdminDTOs.AdminBanUserDTO> adminBanUser(AdminDTOs.AdminBanUserDTO request) {
    return Packet.of(PacketType.ADMIN_BAN_USER, request);
  }

  public static Packet<String> adminUnbanUser(String userId) {
    return Packet.of(PacketType.ADMIN_UNBAN_USER, userId);
  }

  public static Packet<Void> adminGetAllUsers() {
    return Packet.of(PacketType.ADMIN_GET_ALL_USERS);
  }

  public static Packet<Void> adminGetAccountBans() {
    return Packet.of(PacketType.ADMIN_GET_ACCOUNT_BANS);
  }

  public static Packet<AdminDTOs.CreateStaffAdminDTO> adminCreateStaff(
      AdminDTOs.CreateStaffAdminDTO request) {
    return Packet.of(PacketType.ADMIN_CREATE_STAFF, request);
  }

  public static Packet<Void> adminGetAllStaff() {
    return Packet.of(PacketType.ADMIN_GET_ALL_STAFF);
  }

  public static Packet<String> adminApproveSellerRole(String userId) {
    return Packet.of(PacketType.ADMIN_APPROVE_SELLER_ROLE, userId);
  }

  // Rating

  public static Packet<RatingDTOs.RateSellerRequestDTO> rateSeller(
      RatingDTOs.RateSellerRequestDTO request) {
    return Packet.of(PacketType.RATE_SELLER, request);
  }

  public static Packet<RatingDTOs.RateBidderRequestDTO> rateBidder(
      RatingDTOs.RateBidderRequestDTO request) {
    return Packet.of(PacketType.RATE_BIDDER, request);
  }

  public static Packet<String> getUserRatings(String userId) {
    return Packet.of(PacketType.GET_USER_RATINGS, userId);
  }

  // Quality report

  public static Packet<ReportDTOs.QualityReportRequestDTO> submitQualityReport(
      ReportDTOs.QualityReportRequestDTO request) {
    return Packet.of(PacketType.SUBMIT_QUALITY_REPORT, request);
  }

  public static Packet<Void> getMyQualityReports() {
    return Packet.of(PacketType.GET_MY_QUALITY_REPORTS);
  }

  public static Packet<Void> getSellerQualityReports() {
    return Packet.of(PacketType.GET_SELLER_QUALITY_REPORTS);
  }

  public static Packet<String> adminGetQualityReports(String statusFilter) {
    return Packet.of(PacketType.ADMIN_GET_QUALITY_REPORTS, statusFilter);
  }

  public static Packet<String> adminApproveQualityReport(String reportId) {
    return Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT, reportId);
  }

  public static Packet<String> adminRejectQualityReport(String reportId) {
    return Packet.of(PacketType.ADMIN_REJECT_QUALITY_REPORT, reportId);
  }

  // Notification / system

  public static Packet<Void> getNotifications() {
    return Packet.of(PacketType.GET_NOTIFICATIONS);
  }

  public static Packet<String> markNotificationRead(String notificationId) {
    return Packet.of(PacketType.MARK_NOTIFICATION_READ, notificationId);
  }

  public static Packet<Long> ping() {
    return Packet.of(PacketType.PING, System.currentTimeMillis());
  }

  // Chatbot

  public static Packet<com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotAskRequestDTO>
  chatbotAsk(com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotAskRequestDTO request) {
    return Packet.of(PacketType.CHATBOT_ASK, request);
  }

  public static Packet<com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotFaqListRequestDTO>
  chatbotGetFaqList(
      com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotFaqListRequestDTO request) {
    return Packet.of(PacketType.CHATBOT_GET_FAQ_LIST, request);
  }
}