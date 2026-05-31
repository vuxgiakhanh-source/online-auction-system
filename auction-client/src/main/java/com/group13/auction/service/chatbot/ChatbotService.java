package com.group13.auction.service.chatbot;

import com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotAskRequestDTO;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotFaqListRequestDTO;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotFaqListResponseDTO;
import com.group13.auction.common.dto.chatbot.ChatbotDTOs.ChatbotResponseDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.ChatbotViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.network.client.support.NetworkClientException;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.chatbot.ChatbotFaqViewModel;
import com.group13.auction.viewmodel.chatbot.ChatbotMessageViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service gọi chatbot phía server và chuyển response thành dữ liệu hiển thị cho JavaFX client.
 *
 * <p>Chatbot server không yêu cầu đăng nhập, nên service này chỉ đảm bảo WebSocket đã kết nối trước
 * khi gửi request. Logic tìm FAQ và quyết định câu trả lời vẫn nằm hoàn toàn ở server.
 */
public final class ChatbotService {

  private static final long REQUEST_TIMEOUT_SECONDS = 12L;

  private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "chatbot-service-timeout");
            thread.setDaemon(true);
            return thread;
          });

  private final ClientNetworkFacade networkFacade;

  /** Tạo chatbot service dùng network facade mặc định của app. */
  public ChatbotService() {
    this(ClientNetworkFacade.getDefault());
  }

  /**
   * Tạo chatbot service với dependency truyền vào, hữu ích cho test.
   *
   * @param networkFacade facade tầng network
   */
  public ChatbotService(ClientNetworkFacade networkFacade) {
    this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
  }

  /**
   * Gửi câu hỏi tự do tới OMNI.
   *
   * @param query câu hỏi người dùng nhập
   * @return future chứa tin nhắn phản hồi của OMNI
   */
  public CompletableFuture<ChatbotMessageViewModel> askByQuery(String query) {
    String normalizedQuery = query == null ? "" : query.trim();
    if (normalizedQuery.isBlank()) {
      return AuctionServiceSupport.failedFuture("Vui lòng nhập câu hỏi cho OMNI.");
    }

    Packet<?> packet =
        ClientRequestFactory.chatbotAsk(ChatbotAskRequestDTO.byQuery(normalizedQuery));
    return sendAskRequest(packet, "Không nhận được phản hồi từ OMNI.")
        .thenApply(ChatbotViewModelMapper::toBotMessage);
  }

  /**
   * Gửi yêu cầu lấy câu trả lời theo FAQ id.
   *
   * @param faqId mã FAQ người dùng chọn
   * @return future chứa tin nhắn phản hồi của OMNI
   */
  public CompletableFuture<ChatbotMessageViewModel> askByFaqId(String faqId) {
    String normalizedFaqId = faqId == null ? "" : faqId.trim();
    if (normalizedFaqId.isBlank()) {
      return AuctionServiceSupport.failedFuture("Câu hỏi gợi ý không hợp lệ.");
    }

    Packet<?> packet =
        ClientRequestFactory.chatbotAsk(ChatbotAskRequestDTO.byFaqId(normalizedFaqId));
    return sendAskRequest(packet, "Không tải được câu trả lời FAQ từ OMNI.")
        .thenApply(ChatbotViewModelMapper::toBotMessage);
  }

  /**
   * Lấy danh sách FAQ theo category. Truyền {@code null} hoặc chuỗi rỗng để lấy tất cả.
   *
   * @param category nhóm FAQ
   * @return future chứa danh sách câu hỏi gợi ý
   */
  public CompletableFuture<List<ChatbotFaqViewModel>> getFaqList(String category) {
    String normalizedCategory = normalizeCategory(category);
    ChatbotFaqListRequestDTO request = new ChatbotFaqListRequestDTO(normalizedCategory);

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.chatbotGetFaqList(request),
            PacketType.CHATBOT_FAQ_LIST_SUCCESS,
            ChatbotFaqListResponseDTO.class,
            "Không tải được danh sách câu hỏi gợi ý của OMNI.")
        .thenApply(ChatbotViewModelMapper::toFaqViewModels);
  }

  private CompletableFuture<ChatbotResponseDTO> sendAskRequest(
      Packet<?> packet, String fallbackErrorMessage) {
    CompletableFuture<ChatbotResponseDTO> future = new CompletableFuture<>();

    try {
      ensureConnected();
      networkFacade.sendAndExpect(
          packet,
          (responseType, payload) -> {
            if (responseType == PacketType.CHATBOT_ANSWER
                || responseType == PacketType.CHATBOT_NOT_FOUND) {
              completeAskResponse(payload, future);
              return;
            }

            future.completeExceptionally(new NetworkClientException(fallbackErrorMessage));
          });

      scheduleTimeout(future, fallbackErrorMessage);
    } catch (RuntimeException exception) {
      future.completeExceptionally(exception);
    }

    return future;
  }

  private void ensureConnected() {
    if (networkFacade.isConnected()) {
      return;
    }

    boolean connected = networkFacade.connectBlocking();
    if (!connected) {
      throw new NetworkClientException(
          "Không thể kết nối tới hệ thống. Vui lòng kiểm tra kết nối và thử lại.");
    }
  }

  private void completeAskResponse(
      com.google.gson.JsonElement payload, CompletableFuture<ChatbotResponseDTO> future) {
    try {
      future.complete(PacketCodec.fromElement(payload, ChatbotResponseDTO.class));
    } catch (RuntimeException exception) {
      future.completeExceptionally(
          new NetworkClientException("OMNI chưa xử lý được phản hồi. Vui lòng thử lại.", exception));
    }
  }

  private void scheduleTimeout(
      CompletableFuture<ChatbotResponseDTO> future, String fallbackErrorMessage) {
    ScheduledFuture<?> timeoutTask =
        TIMEOUT_EXECUTOR.schedule(
            () ->
                future.completeExceptionally(
                    new NetworkClientException("Hệ thống chưa phản hồi. " + fallbackErrorMessage)),
            REQUEST_TIMEOUT_SECONDS,
            TimeUnit.SECONDS);

    future.whenComplete((response, throwable) -> timeoutTask.cancel(false));
  }

  private String normalizeCategory(String category) {
    if (category == null || category.isBlank()) {
      return null;
    }
    return category.trim().toUpperCase();
  }
}
