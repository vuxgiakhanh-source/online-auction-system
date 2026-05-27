package com.group13.auction.chatbot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.group13.auction.chatbot.model.ChatbotResponse;
import com.group13.auction.chatbot.model.FAQ;
import com.group13.auction.chatbot.provider.ChatbotProvider;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.handler.PacketHandler;
import com.group13.auction.network.server.session.ClientSession;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler xử lý tất cả packet liên quan đến Chatbot hỗ trợ khách hàng.
 *
 * <p>Implement {@link PacketHandler} để tích hợp liền mạch vào kiến trúc WebSocket hiện có — được
 * đăng ký vào {@code PacketRouter} trong {@code AuctionWebSocketServer}.
 *
 * <h3>Các PacketType được xử lý:</h3>
 *
 * <ul>
 *   <li>{@code CHATBOT_ASK} — Người dùng hỏi tự do hoặc chọn FAQ theo ID.
 *   <li>{@code CHATBOT_GET_FAQ_LIST} — Lấy danh sách câu hỏi theo category.
 * </ul>
 *
 * <h3>Thiết kế tách biệt nghiệp vụ:</h3>
 *
 * <p>Handler này không chứa logic tìm kiếm — toàn bộ logic tìm kiếm nằm trong {@link
 * ChatbotProvider} (Singleton). Handler chỉ thực hiện:
 *
 * <ol>
 *   <li>Parse payload JSON từ client.
 *   <li>Gọi đúng phương thức của {@code ChatbotProvider}.
 *   <li>Serialize kết quả và gửi về qua {@code ClientSession}.
 * </ol>
 *
 * <p>Chatbot hoạt động được ngay cả khi client chưa đăng nhập (unauthenticated) — phù hợp với vai
 * trò "hỗ trợ khách hàng" không yêu cầu xác thực.
 *
 * @author Group 13 — Chatbot Module
 * @version 1.0
 * @see ChatbotProvider
 * @see PacketHandler
 */
public class ChatbotHandler implements PacketHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatbotHandler.class);

  /**
   * Tập PacketType mà handler này chịu trách nhiệm xử lý. Dùng EnumSet để kiểm tra {@code
   * supports()} trong O(1).
   */
  private static final Set<PacketType> SUPPORTED_PACKET_TYPES =
      EnumSet.of(PacketType.CHATBOT_ASK, PacketType.CHATBOT_GET_FAQ_LIST);

  /**
   * Provider chatbot — truy xuất qua Singleton để tái sử dụng dữ liệu FAQ đã nạp vào bộ nhớ từ lúc
   * server khởi động.
   */
  private final ChatbotProvider chatbotProvider;

  /** Gson instance — tái sử dụng (thread-safe khi không custom TypeAdapter). */
  private final Gson gson;

  // ── Constructor ───────────────────────────────────────────────────────────

  /**
   * Constructor — lấy instance Singleton của ChatbotProvider.
   *
   * <p>Không nhận tham số nào từ ngoài — chatbot module hoạt động hoàn toàn độc lập (không cần
   * AccountService, AuctionService, v.v.). Đây là điểm thể hiện tính "Microservice style" của
   * module.
   */
  public ChatbotHandler() {
    // Lấy instance Singleton — ChatbotProvider đã nạp FAQ từ lần gọi đầu tiên
    this.chatbotProvider = ChatbotProvider.getInstance();
    this.gson = new Gson();
    log.info(
        "[ChatbotHandler] Khởi tạo — ChatbotProvider đang giữ {} FAQ.",
        chatbotProvider.getTotalFaqCount());
  }

  // ══════════════════════════════════════════════════════════════════════════
  // PacketHandler interface implementation
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Kiểm tra handler này có xử lý được PacketType cho trước không.
   *
   * @param type loại packet từ client
   * @return true nếu là CHATBOT_ASK hoặc CHATBOT_GET_FAQ_LIST
   */
  @Override
  public boolean supports(PacketType type) {
    return SUPPORTED_PACKET_TYPES.contains(type);
  }

  /**
   * Entry point xử lý packet chatbot từ client.
   *
   * <p>Dispatch tới phương thức xử lý tương ứng dựa trên {@code PacketType}.
   *
   * @param session session của client gửi request
   * @param type loại packet (CHATBOT_ASK hoặc CHATBOT_GET_FAQ_LIST)
   * @param payload JSON payload chứa câu hỏi hoặc category
   * @param requestId ID request để echo về (hỗ trợ frontend tracking)
   */
  @Override
  public void handle(
      ClientSession session, PacketType type, JsonElement payload, String requestId) {
    log.debug("[ChatbotHandler] Nhận packet {} từ session {}", type, session);

    switch (type) {
      case CHATBOT_ASK -> handleChatbotAsk(session, payload, requestId);
      case CHATBOT_GET_FAQ_LIST -> handleGetFaqList(session, payload, requestId);
      default -> log.warn("[ChatbotHandler] PacketType không mong đợi: {}", type);
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Xử lý từng loại packet
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Xử lý packet {@code CHATBOT_ASK} — tìm câu trả lời cho câu hỏi của người dùng.
   *
   * <h3>Logic phân nhánh:</h3>
   *
   * <ol>
   *   <li>Nếu payload có trường {@code "faqId"} (VD: "FAQ_001") → tra cứu trực tiếp theo ID (người
   *       dùng chọn từ menu gợi ý).
   *   <li>Nếu payload có trường {@code "query"} → tìm kiếm theo từ khóa tự do.
   *   <li>Nếu payload không hợp lệ → trả về NOT_FOUND với thông báo lỗi.
   * </ol>
   *
   * <h3>Payload JSON từ client:</h3>
   *
   * <pre>{@code
   * // Cách 1: Chọn FAQ theo ID
   * { "faqId": "FAQ_001" }
   *
   * // Cách 2: Nhập câu hỏi tự do
   * { "query": "giá thầu tối thiểu là bao nhiêu?" }
   * }</pre>
   *
   * @param session session của client
   * @param payload JSON payload
   * @param requestId ID request để echo
   */
  private void handleChatbotAsk(ClientSession session, JsonElement payload, String requestId) {
    // Guard: Payload bắt buộc phải có
    if (payload == null || !payload.isJsonObject()) {
      sendNotFoundResponse(session, requestId, "[payload không hợp lệ]");
      return;
    }

    JsonObject requestBody = payload.getAsJsonObject();
    ChatbotResponse response;

    // Phân nhánh: Tìm theo ID (ưu tiên cao hơn) hay tìm theo query?
    if (requestBody.has("faqId") && !requestBody.get("faqId").isJsonNull()) {
      // Người dùng chọn FAQ từ menu → tra cứu O(1) theo ID
      String faqId = requestBody.get("faqId").getAsString().trim();
      log.debug("[ChatbotHandler] Tìm theo faqId='{}'", faqId);
      response = chatbotProvider.getAnswerByQuestionId(faqId);

    } else if (requestBody.has("query") && !requestBody.get("query").isJsonNull()) {
      // Người dùng nhập câu hỏi tự do → tìm kiếm theo keyword
      String query = requestBody.get("query").getAsString().trim();
      log.debug("[ChatbotHandler] Tìm theo free-text query='{}'", query);
      response = chatbotProvider.searchByQuery(query);

    } else {
      // Payload có nhưng thiếu cả faqId lẫn query
      sendNotFoundResponse(session, requestId, "[thiếu faqId hoặc query]");
      return;
    }

    // Gửi phản hồi về đúng PacketType tương ứng với trạng thái
    PacketType responseType =
        response.isSuccess() ? PacketType.CHATBOT_ANSWER : PacketType.CHATBOT_NOT_FOUND;

    sendResponse(session, responseType, response, requestId);
  }

  /**
   * Xử lý packet {@code CHATBOT_GET_FAQ_LIST} — trả về danh sách câu hỏi gợi ý.
   *
   * <p>Frontend dùng endpoint này để hiển thị menu câu hỏi nhanh theo từng chủ đề, giúp người dùng
   * không cần gõ câu hỏi mà chỉ cần click.
   *
   * <h3>Payload JSON từ client:</h3>
   *
   * <pre>{@code
   * // Lấy FAQ theo category
   * { "category": "BIDDING" }
   *
   * // Lấy tất cả FAQ (bỏ trống category)
   * { "category": null }
   * }</pre>
   *
   * <h3>Response gồm 2 phần trong packet:</h3>
   *
   * <pre>{@code
   * {
   *   "header": { ChatbotResponse với status=FAQ_LIST },
   *   "faqs":   [ { id, category, question } ]  // Không trả answer để giảm payload
   * }
   * }</pre>
   *
   * @param session session của client
   * @param payload JSON payload chứa category
   * @param requestId ID request để echo
   */
  private void handleGetFaqList(ClientSession session, JsonElement payload, String requestId) {
    // Lấy category từ payload (null nếu không có → trả tất cả)
    String category = null;
    if (payload != null && payload.isJsonObject()) {
      JsonObject requestBody = payload.getAsJsonObject();
      if (requestBody.has("category") && !requestBody.get("category").isJsonNull()) {
        String rawCategory = requestBody.get("category").getAsString().trim();
        if (!rawCategory.isBlank()) {
          category = rawCategory.toUpperCase();
        }
      }
    }

    // Lấy danh sách FAQ từ Provider
    List<FAQ> faqs = chatbotProvider.getFaqsByCategory(category);
    ChatbotResponse headerInfo = ChatbotResponse.ofFaqList(category);

    // Xây dựng response JSON ghép cả header lẫn list FAQ
    // (danh sách FAQ rút gọn — không kèm answer để giảm kích thước packet)
    JsonObject combinedPayload = new JsonObject();
    combinedPayload.add("header", gson.toJsonTree(headerInfo));
    combinedPayload.add("faqs", buildFaqSummaryArray(faqs));
    combinedPayload.addProperty("totalCount", faqs.size());

    log.debug(
        "[ChatbotHandler] Trả về {} FAQ thuộc category='{}'",
        faqs.size(),
        category == null ? "TẤT CẢ" : category);

    // Gửi packet thành công
    @SuppressWarnings("unchecked")
    Packet<JsonObject> responsePacket =
        new Packet<>(PacketType.CHATBOT_FAQ_LIST_SUCCESS, combinedPayload, requestId);
    session.send(responsePacket);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // Private helpers
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Gửi phản hồi CHATBOT_NOT_FOUND về client với câu hỏi gốc.
   *
   * @param session session của client
   * @param requestId ID request để echo
   * @param query câu hỏi gốc (dùng trong thông điệp NOT_FOUND)
   */
  private void sendNotFoundResponse(ClientSession session, String requestId, String query) {
    ChatbotResponse notFound = ChatbotResponse.ofNotFound(query);
    sendResponse(session, PacketType.CHATBOT_NOT_FOUND, notFound, requestId);
  }

  /**
   * Serialize và gửi ChatbotResponse về client qua WebSocket.
   *
   * @param session session của client
   * @param packetType loại packet phản hồi (CHATBOT_ANSWER / CHATBOT_NOT_FOUND)
   * @param response đối tượng phản hồi cần gửi
   * @param requestId ID request để echo về client
   */
  private void sendResponse(
      ClientSession session, PacketType packetType, ChatbotResponse response, String requestId) {
    Packet<ChatbotResponse> packet = new Packet<>(packetType, response, requestId);
    session.send(packet);
  }

  /**
   * Xây dựng JsonArray danh sách FAQ rút gọn (chỉ id, category, question — không kèm answer).
   *
   * <p>Tránh gửi toàn bộ nội dung answer khi chỉ cần hiển thị menu gợi ý, giúp giảm kích thước
   * packet WebSocket đáng kể.
   *
   * @param faqs danh sách FAQ đầy đủ
   * @return JsonArray chứa các FAQ rút gọn
   */
  private com.google.gson.JsonArray buildFaqSummaryArray(List<FAQ> faqs) {
    com.google.gson.JsonArray array = new com.google.gson.JsonArray();

    for (FAQ faq : faqs) {
      JsonObject summary = new JsonObject();
      summary.addProperty("id", faq.getId());
      summary.addProperty("category", faq.getCategory());
      summary.addProperty("question", faq.getQuestion());
      // Không đưa answer vào summary để giảm payload
      array.add(summary);
    }

    return array;
  }
}
