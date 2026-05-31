package com.group13.auction.network.client.facade;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.config.SocketConfig;
import com.group13.auction.network.client.AuctionWebSocketClient;
import com.group13.auction.network.client.handler.ServerResponseHandler;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.network.client.session.ClientPacketDispatcher;
import com.group13.auction.network.client.support.NetworkClientException;
import com.group13.auction.network.client.support.PacketExpectation;
import com.group13.auction.network.client.support.PacketExpectations;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Facade chính của tầng network client.
 *
 * <p>Controller/service phía client chỉ nên gọi lớp này thay vì gọi trực tiếp {@link
 * AuctionWebSocketClient}. Cách tách này giữ nguyên code teammate đã viết, đồng thời gom toàn bộ
 * request API của client vào một điểm ổn định.
 */
public final class ClientNetworkFacade {

  private static volatile ClientNetworkFacade defaultInstance;

  private final URI serverUri;
  private final ClientPacketDispatcher dispatcher;

  private AuctionWebSocketClient client;
  private boolean dispatcherAttached;

  private ClientNetworkFacade(URI serverUri) {
    this.serverUri = Objects.requireNonNull(serverUri, "serverUri");
    this.dispatcher = new ClientPacketDispatcher();
  }

  /**
   * Lấy facade mặc định dùng cấu hình trong {@link SocketConfig}.
   *
   * @return singleton facade mặc định của ứng dụng
   */
  public static ClientNetworkFacade getDefault() {
    return getInstance(SocketConfig.serverUri());
  }

  /**
   * Lấy facade singleton theo URI server.
   *
   * <p>Ứng dụng JavaFX bình thường chỉ nên dùng một server URI trong một lần chạy. Nếu cần đổi
   * server URI sau khi đã tạo instance, hãy gọi {@link #shutdown()} trước.
   *
   * @param serverUri URI WebSocket server
   * @return singleton facade
   */
  public static ClientNetworkFacade getInstance(URI serverUri) {
    if (defaultInstance == null) {
      synchronized (ClientNetworkFacade.class) {
        if (defaultInstance == null) {
          defaultInstance = new ClientNetworkFacade(serverUri);
        }
      }
    }
    return defaultInstance;
  }

  /** Mở kết nối bất đồng bộ tới server. */
  public synchronized void connect() {
    AuctionWebSocketClient wsClient = ensureClient();
    if (!wsClient.isOpen()) {
      wsClient.connect();
    }
  }

  /**
   * Mở kết nối và chờ tối đa theo cấu hình mặc định.
   *
   * @return {@code true} nếu kết nối mở thành công trong thời gian chờ
   */
  public synchronized boolean connectBlocking() {
    return connectBlocking(SocketConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  /**
   * Mở kết nối và chờ tối đa theo timeout truyền vào.
   *
   * @param timeout thời gian chờ
   * @param unit đơn vị thời gian
   * @return {@code true} nếu kết nối mở thành công trong thời gian chờ
   */
  public synchronized boolean connectBlocking(long timeout, TimeUnit unit) {
    try {
      AuctionWebSocketClient wsClient = ensureClient();
      if (wsClient.isOpen()) {
        return true;
      }
      return wsClient.connectBlocking(timeout, unit);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new NetworkClientException("Kết nối bị gián đoạn. Vui lòng thử lại.", exception);
    } catch (RuntimeException exception) {
      throw new NetworkClientException(
          "Không thể kết nối tới hệ thống. Vui lòng kiểm tra kết nối và thử lại." + serverUri, exception);
    }
  }

  /**
   * Kiểm tra WebSocket hiện có đang mở hay không.
   *
   * @return {@code true} nếu đang kết nối
   */
  public synchronized boolean isConnected() {
    return client != null && client.isOpen();
  }

  /**
   * Lấy URI server facade đang dùng.
   *
   * @return URI server
   */
  public URI getServerUri() {
    return serverUri;
  }

  /**
   * Đăng ký listener nhận typed event từ server.
   *
   * @param listener listener của controller/service
   */
  public void addListener(ClientEventListener listener) {
    dispatcher.addListener(Objects.requireNonNull(listener, "listener"));
  }

  /**
   * Hủy đăng ký listener nhận typed event.
   *
   * @param listener listener cần hủy
   */
  public void removeListener(ClientEventListener listener) {
    dispatcher.removeListener(listener);
  }

  /**
   * Đăng ký raw handler trong trường hợp service cần đọc packet thô.
   *
   * @param handler raw handler
   */
  public synchronized void addRawHandler(ServerResponseHandler handler) {
    ensureClient().addHandler(Objects.requireNonNull(handler, "handler"));
  }

  /**
   * Hủy raw handler đã đăng ký.
   *
   * @param handler raw handler cần hủy
   */
  public synchronized void removeRawHandler(ServerResponseHandler handler) {
    if (client != null) {
      client.removeHandler(handler);
    }
  }

  /**
   * Gửi packet bất kỳ theo kiểu fire-and-forget.
   *
   * @param packet packet cần gửi
   */
  public synchronized void send(Packet<?> packet) {
    ensureClient().send(Objects.requireNonNull(packet, "packet"));
  }

  /**
   * Gửi packet và bắt cặp response theo requestId.
   *
   * <p>Callback chạy trên thread WebSocket. Nếu cập nhật JavaFX UI, controller/service cần tự bọc
   * bằng {@code Platform.runLater(...)}.
   *
   * @param packet packet cần gửi
   * @param callback callback nhận raw response payload
   */
  public synchronized void sendAndExpect(
      Packet<?> packet, BiConsumer<PacketType, JsonElement> callback) {
    Objects.requireNonNull(packet, "packet");
    Objects.requireNonNull(callback, "callback");

    PacketExpectation expectation = PacketExpectations.require(packet.getType());
    PacketType failureType = expectation.failureType().orElse(PacketType.SYSTEM_ERROR);
    ensureClient().sendAndExpect(packet, expectation.successType(), failureType, callback);
  }

  /** Đóng kết nối và giải phóng tài nguyên network. */
  public synchronized void shutdown() {
    if (client != null) {
      client.shutdown();
      client = null;
      dispatcherAttached = false;
    }
    synchronized (ClientNetworkFacade.class) {
      defaultInstance = null;
    }
  }

  // Auth

  public void login(String username, String password) {
    send(ClientRequestFactory.login(username, password));
  }

  public void register(String username, String password, String email) {
    send(ClientRequestFactory.register(username, password, email));
  }

  public void logout() {
    send(ClientRequestFactory.logout());
  }

  // User / profile

  public void getMyProfile() {
    send(ClientRequestFactory.getMyProfile());
  }

  public void getUserProfile(String userId) {
    send(ClientRequestFactory.getUserProfile(userId));
  }

  public void requestSellerRole() {
    send(ClientRequestFactory.requestSellerRole());
  }

  // Wallet / payment

  public void deposit(long amount) {
    send(ClientRequestFactory.deposit(amount));
  }

  public void withdraw(long amount) {
    send(ClientRequestFactory.withdraw(amount));
  }

  public void getWalletBalance() {
    send(ClientRequestFactory.getWalletBalance());
  }

  public void requestPayment(String auctionId) {
    send(ClientRequestFactory.requestPayment(auctionId));
  }

  public void acceptSecondChance(String auctionId) {
    send(ClientRequestFactory.acceptSecondChance(auctionId));
  }

  public void declineSecondChance(String auctionId) {
    send(ClientRequestFactory.declineSecondChance(auctionId));
  }

  // Auction

  public void createAuction(AuctionDTOs.CreateAuctionRequestDTO request) {
    send(ClientRequestFactory.createAuction(request));
  }

  public void getAuctionList(AuctionDTOs.AuctionListRequestDTO request) {
    send(ClientRequestFactory.getAuctionList(request));
  }

  public void getAuctionList() {
    send(ClientRequestFactory.getAuctionList());
  }

  public void getAuctionDetail(String auctionId) {
    send(ClientRequestFactory.getAuctionDetail(auctionId));
  }

  public void updateAuction(AuctionDTOs.UpdateAuctionDTO request) {
    send(ClientRequestFactory.updateAuction(request));
  }

  public void requestCancelAuction(AuctionDTOs.CancelAuctionRequestDTO request) {
    send(ClientRequestFactory.requestCancelAuction(request));
  }

  public void adminCancelAuction(AuctionDTOs.AdminCancelAuctionDTO request) {
    send(ClientRequestFactory.adminCancelAuction(request));
  }

  public void adminGetAllAuctions() {
    send(ClientRequestFactory.adminGetAllAuctions());
  }

  // Bidding / realtime auction session

  public void joinAuction(String auctionId) {
    send(ClientRequestFactory.joinAuction(auctionId));
  }

  public void watchAuction(String auctionId) {
    send(ClientRequestFactory.watchAuction(auctionId));
  }

  public void leaveAuction(String auctionId) {
    send(ClientRequestFactory.leaveAuction(auctionId));
  }

  public void placeBid(String auctionId, long amount) {
    send(ClientRequestFactory.placeBid(auctionId, amount));
  }

  public void registerAutoBid(String auctionId, long maxBid) {
    send(ClientRequestFactory.registerAutoBid(auctionId, maxBid));
  }

  public void updateAutoBid(String auctionId, long maxBid) {
    send(ClientRequestFactory.updateAutoBid(auctionId, maxBid));
  }

  public void cancelAutoBid(String auctionId) {
    send(ClientRequestFactory.cancelAutoBid(auctionId));
  }

  public void getAutoBidStatus(String auctionId) {
    send(ClientRequestFactory.getAutoBidStatus(auctionId));
  }

  public void getBidHistory(String auctionId) {
    send(ClientRequestFactory.getBidHistory(auctionId));
  }

  // Admin / moderation

  public void adminBanUser(AdminDTOs.AdminBanUserDTO request) {
    send(ClientRequestFactory.adminBanUser(request));
  }

  public void adminUnbanUser(String userId) {
    send(ClientRequestFactory.adminUnbanUser(userId));
  }

  public void adminGetAllUsers() {
    send(ClientRequestFactory.adminGetAllUsers());
  }

  public void adminCreateStaff(AdminDTOs.CreateStaffAdminDTO request) {
    send(ClientRequestFactory.adminCreateStaff(request));
  }

  public void adminGetAllStaff() {
    send(ClientRequestFactory.adminGetAllStaff());
  }

  public void adminApproveSellerRole(String userId) {
    send(ClientRequestFactory.adminApproveSellerRole(userId));
  }

  // Rating

  public void rateSeller(RatingDTOs.RateSellerRequestDTO request) {
    send(ClientRequestFactory.rateSeller(request));
  }

  public void rateBidder(RatingDTOs.RateBidderRequestDTO request) {
    send(ClientRequestFactory.rateBidder(request));
  }

  public void getUserRatings(String userId) {
    send(ClientRequestFactory.getUserRatings(userId));
  }

  // Quality report

  public void submitQualityReport(ReportDTOs.QualityReportRequestDTO request) {
    send(ClientRequestFactory.submitQualityReport(request));
  }

  public void adminGetQualityReports(String statusFilter) {
    send(ClientRequestFactory.adminGetQualityReports(statusFilter));
  }

  public void adminApproveQualityReport(String reportId) {
    send(ClientRequestFactory.adminApproveQualityReport(reportId));
  }

  public void adminRejectQualityReport(String reportId) {
    send(ClientRequestFactory.adminRejectQualityReport(reportId));
  }

  // Notification / system

  public void getNotifications() {
    send(ClientRequestFactory.getNotifications());
  }

  public void markNotificationRead(String notificationId) {
    send(ClientRequestFactory.markNotificationRead(notificationId));
  }

  public void ping() {
    send(ClientRequestFactory.ping());
  }

  // Chatbot

  public void chatbotAsk(
      com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotAskRequestDTO request) {
    send(ClientRequestFactory.chatbotAsk(request));
  }

  public void chatbotGetFaqList(
      com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotFaqListRequestDTO request) {
    send(ClientRequestFactory.chatbotGetFaqList(request));
  }

  private AuctionWebSocketClient ensureClient() {
    if (client == null) {
      client = AuctionWebSocketClient.getInstance(serverUri);
    }
    if (!dispatcherAttached) {
      client.addHandler(dispatcher);
      dispatcherAttached = true;
    }
    return client;
  }
}
